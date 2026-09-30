package com.virpemart.billing.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.LedgerEntry;
import com.virpemart.billing.model.LedgerEntryType;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.StatementLine;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.CustomerRepository;
import com.virpemart.billing.repository.LedgerRepository;

/**
 * A customer's khata: statement with running balance, payments and owner corrections.
 *
 * <p>The balance is always the sum of all entries. Entries are never changed or deleted; a mistake is
 * fixed by adding a correcting entry, exactly like in a paper khata.
 */
public final class LedgerService {

    private static final int MIN_REASON_LENGTH = 3;
    private static final int MAX_NOTE_LENGTH = 200;

    private final Database database;
    private final CustomerRepository customers;
    private final LedgerRepository ledger;
    private final AuditRepository audit;
    private final Session session;
    private final Clock clock;

    public LedgerService(Database database, CustomerRepository customers, LedgerRepository ledger,
                         AuditRepository audit, Session session, Clock clock) {
        this.database = database;
        this.customers = customers;
        this.ledger = ledger;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    /** All khata entries of a customer, oldest first, each with the balance after it. */
    public List<StatementLine> statement(long customerId) {
        session.requireSignedIn();
        List<LedgerEntry> entries = database.query(c -> ledger.listForCustomer(c, customerId));
        List<StatementLine> lines = new ArrayList<>();
        Money balance = Money.ZERO;
        for (LedgerEntry entry : entries) {
            balance = balance.plus(entry.amount());
            lines.add(new StatementLine(entry, balance));
        }
        return lines;
    }

    /**
     * Records money received from a customer against their khata. Owner and staff.
     * Paying more than the dues is allowed; the extra is kept as advance.
     *
     * @return the customer with the new balance
     */
    public CustomerSummary receivePayment(long customerId, String amountText, PaymentMode mode, String note) {
        User user = session.requireSignedIn();
        Money amount = Amounts.parsePositive("amount", amountText, "amount", true);
        if (mode == null) {
            throw new ValidationException("mode", "Please choose how the customer paid: Cash, UPI or Card.");
        }
        String cleanNote = checkNote("note", note, false);
        return database.inTransaction(c -> {
            customers.findById(c, customerId)
                    .orElseThrow(() -> new BusinessRuleException("This customer no longer exists."));
            ledger.insert(c, customerId, LedgerEntryType.PAYMENT, amount.negate(), null, mode, cleanNote,
                    DbTime.now(clock), user.id());
            return customers.findById(c, customerId).orElseThrow();
        });
    }

    /**
     * Owner's correction to make the software khata match the paper khata. A reason is required.
     *
     * @param increaseDues true to add to the customer's dues, false to reduce them
     * @return the customer with the new balance
     */
    public CustomerSummary correctBalance(long customerId, boolean increaseDues, String amountText, String reason) {
        User user = session.requireOwner();
        Money amount = Amounts.parsePositive("amount", amountText, "amount", true);
        String cleanReason = checkNote("reason", reason, true);
        Money signed = increaseDues ? amount : amount.negate();
        return database.inTransaction(c -> {
            CustomerSummary before = customers.findById(c, customerId)
                    .orElseThrow(() -> new BusinessRuleException("This customer no longer exists."));
            String now = DbTime.now(clock);
            ledger.insert(c, customerId, LedgerEntryType.ADJUSTMENT, signed, null, null, cleanReason, now, user.id());
            CustomerSummary after = customers.findById(c, customerId).orElseThrow();
            audit.insert(c, user.id(), "BALANCE_CORRECTED", "customers", customerId,
                    before.customer().customerNo() + ": " + (increaseDues ? "+" : "-") + amount.toPlainString()
                            + ", balance " + before.balance().toPlainString() + " -> " + after.balance().toPlainString()
                            + ", reason: " + cleanReason, now);
            return after;
        });
    }

    private static String checkNote(String field, String text, boolean required) {
        String cleaned = Texts.clean(text);
        if (cleaned == null) {
            if (required) {
                throw new ValidationException(field, "Please write the reason for this correction.");
            }
            return null;
        }
        if (required && cleaned.length() < MIN_REASON_LENGTH) {
            throw new ValidationException(field, "Please write a clearer reason, so it can be understood later.");
        }
        if (cleaned.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException(field, "This is too long. Use at most " + MAX_NOTE_LENGTH + " letters.");
        }
        return cleaned;
    }
}
