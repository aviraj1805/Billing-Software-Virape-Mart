package com.virpemart.billing.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.Customer;
import com.virpemart.billing.model.CustomerDetails;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.LedgerEntryType;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.CustomerRepository;
import com.virpemart.billing.repository.CustomerRepository.DuesTotals;
import com.virpemart.billing.repository.LedgerRepository;

/**
 * Account customers: search, add, edit and switch off/on.
 *
 * <p>Rules:
 * <ul>
 *   <li>Owner and staff can search, add and edit customers. Only the owner can switch a customer off/on.</li>
 *   <li>Customer numbers are automatic: C0001, C0002, ...</li>
 *   <li>Name is required. Phone is optional, but two customers cannot share a phone number.</li>
 *   <li>Old dues from the paper khata can be entered once, when the customer is added.</li>
 *   <li>Customers are never deleted, so their khata history is always kept.</li>
 * </ul>
 */
public final class CustomerService {

    /** Note written on the old-dues khata entry. */
    public static final String OLD_DUES_NOTE = "Old dues from paper khata";

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_ADDRESS_LENGTH = 200;
    private static final int MAX_NOTES_LENGTH = 500;

    private final Database database;
    private final CustomerRepository customers;
    private final LedgerRepository ledger;
    private final AuditRepository audit;
    private final Session session;
    private final Clock clock;

    public CustomerService(Database database, CustomerRepository customers, LedgerRepository ledger,
                           AuditRepository audit, Session session, Clock clock) {
        this.database = database;
        this.customers = customers;
        this.ledger = ledger;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reading

    /** Searches by name, phone or customer number; see {@link CustomerRepository#search}. */
    public List<CustomerSummary> search(String text, boolean includeInactive, boolean onlyWithDues, int limit) {
        session.requireSignedIn();
        return database.query(c -> customers.search(c, text, includeInactive, onlyWithDues, limit));
    }

    public Optional<CustomerSummary> find(long id) {
        session.requireSignedIn();
        return database.query(c -> customers.findById(c, id));
    }

    /** How many customers owe money and the total amount they owe. */
    public DuesTotals duesTotals() {
        session.requireSignedIn();
        return database.query(customers::duesTotals);
    }

    // ------------------------------------------------------------------ checking input

    /**
     * Checks and cleans typed customer details without saving anything.
     *
     * @throws ValidationException naming the first field with a problem
     */
    public CustomerDetails check(CustomerInput input) {
        String name = Texts.clean(input.name());
        if (name == null) {
            throw new ValidationException("name", "Please enter the customer's name.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("name", "The name is too long. Use at most " + MAX_NAME_LENGTH + " letters.");
        }
        String phone = normalizePhone(input.phone());
        String address = Texts.clean(input.address());
        if (address != null && address.length() > MAX_ADDRESS_LENGTH) {
            throw new ValidationException("address",
                    "The address is too long. Use at most " + MAX_ADDRESS_LENGTH + " letters.");
        }
        String notes = input.notes() == null || input.notes().isBlank() ? null : input.notes().strip();
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new ValidationException("notes", "The notes are too long. Use at most " + MAX_NOTES_LENGTH + " letters.");
        }
        return new CustomerDetails(name, phone, address, notes);
    }

    /**
     * Turns a typed phone number into 10 digits. Spaces, dashes, brackets, "+91" and a leading 0 are removed.
     *
     * @return the 10 digits, or null if the text is blank
     * @throws ValidationException if it is not a 10-digit Indian number
     */
    static String normalizePhone(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String digits = text.replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+91")) {
            digits = digits.substring(3);
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!digits.matches("\\d{10}")) {
            throw new ValidationException("phone",
                    "Please enter a 10-digit phone number, like 98765 43210, or leave it empty.");
        }
        return digits;
    }

    // ------------------------------------------------------------------ changing

    /** Adds a customer with the next automatic number, and their old dues if given. */
    public CustomerSummary create(CustomerInput input) {
        User user = session.requireSignedIn();
        CustomerDetails details = check(input);
        Money oldDues = Amounts.parsePositive("oldDues", input.oldDues(), "old dues", false);
        return database.inTransaction(c -> {
            checkPhoneFree(c, details.phone(), 0);
            String now = DbTime.now(clock);
            String number = String.format("C%04d", customers.maxNumber(c) + 1);
            long id = customers.insert(c, number, details, now);
            if (oldDues != null) {
                ledger.insert(c, id, LedgerEntryType.OPENING, oldDues, null, null, OLD_DUES_NOTE, now, user.id());
            }
            audit.insert(c, user.id(), "CUSTOMER_CREATED", "customers", id,
                    number + " " + details.name() + (oldDues == null ? "" : ", old dues " + oldDues.toPlainString()),
                    now);
            return customers.findById(c, id).orElseThrow();
        });
    }

    /** Saves changes to name, phone, address and notes. The khata is not affected. */
    public CustomerSummary update(long id, CustomerInput input) {
        User user = session.requireSignedIn();
        CustomerDetails details = check(input);
        return database.inTransaction(c -> {
            Customer before = customers.findById(c, id)
                    .orElseThrow(() -> new BusinessRuleException("This customer no longer exists."))
                    .customer();
            checkPhoneFree(c, details.phone(), id);
            String changes = describeChanges(before, details);
            if (!changes.isEmpty()) {
                String now = DbTime.now(clock);
                customers.update(c, id, details, now);
                audit.insert(c, user.id(), "CUSTOMER_UPDATED", "customers", id, before.customerNo() + ": " + changes, now);
            }
            return customers.findById(c, id).orElseThrow();
        });
    }

    /** Switches a customer off (hidden from billing, khata kept) or back on. Owner only. */
    public CustomerSummary setActive(long id, boolean active) {
        User user = session.requireOwner();
        return database.inTransaction(c -> {
            Customer customer = customers.findById(c, id)
                    .orElseThrow(() -> new BusinessRuleException("This customer no longer exists."))
                    .customer();
            if (customer.active() != active) {
                String now = DbTime.now(clock);
                customers.setActive(c, id, active, now);
                audit.insert(c, user.id(), active ? "CUSTOMER_SWITCHED_ON" : "CUSTOMER_SWITCHED_OFF", "customers", id,
                        customer.customerNo() + " " + customer.name(), now);
            }
            return customers.findById(c, id).orElseThrow();
        });
    }

    private void checkPhoneFree(Connection connection, String phone, long excludeId) throws SQLException {
        if (phone == null) {
            return;
        }
        Optional<Customer> other = customers.findByPhone(connection, phone, excludeId);
        if (other.isPresent()) {
            throw new ValidationException("phone", "This phone number already belongs to "
                    + other.get().customerNo() + " " + other.get().name() + ".");
        }
    }

    private static String describeChanges(Customer before, CustomerDetails after) {
        List<String> changes = new ArrayList<>();
        addChange(changes, "name", before.name(), after.name());
        addChange(changes, "phone", before.phone(), after.phone());
        addChange(changes, "address", before.address(), after.address());
        addChange(changes, "notes", before.notes(), after.notes());
        return String.join("; ", changes);
    }

    private static void addChange(List<String> changes, String label, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(label + " " + (before == null ? "(none)" : before) + " -> " + (after == null ? "(none)" : after));
        }
    }
}
