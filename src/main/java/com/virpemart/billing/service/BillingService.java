package com.virpemart.billing.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.BillDetails;
import com.virpemart.billing.model.BillSearch;
import com.virpemart.billing.model.BillSummary;
import com.virpemart.billing.model.BillTotals;
import com.virpemart.billing.model.CancelledBill;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.LedgerEntryType;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.SavedBill;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.BillRepository;
import com.virpemart.billing.repository.BillRepository.NewBill;
import com.virpemart.billing.repository.CustomerRepository;
import com.virpemart.billing.repository.LedgerRepository;
import com.virpemart.billing.repository.ProductRepository;

/**
 * Saves bills. Everything happens in one transaction: bill number, lines, payments, khata entries and
 * audit rows are saved together, or nothing is saved.
 *
 * <p>Rules:
 * <ul>
 *   <li>Totals are always recalculated here from the lines; the screen's numbers are never trusted.</li>
 *   <li>Product names, units, pack sizes and MRP are copied from the product list at the moment of saving.</li>
 *   <li>Walk-in customers pay exactly the bill total (change is handled at the counter).</li>
 *   <li>Khata customers may pay anything: the unpaid part of the bill goes on the khata, and money beyond the
 *       bill is recorded as a payment against old dues (or kept as advance).</li>
 *   <li>A changed rate is written to the audit log with who changed it.</li>
 * </ul>
 */
public final class BillingService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_WALK_IN_NAME_LENGTH = 60;
    private static final int MAX_REASON_LENGTH = 200;

    private final Database database;
    private final BillRepository bills;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final LedgerRepository ledger;
    private final AuditRepository audit;
    private final Session session;
    private final Clock clock;

    public BillingService(Database database, BillRepository bills, ProductRepository products,
                          CustomerRepository customers, LedgerRepository ledger, AuditRepository audit,
                          Session session, Clock clock) {
        this.database = database;
        this.bills = bills;
        this.products = products;
        this.customers = customers;
        this.ledger = ledger;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    /** How the money received is split between this bill and old khata dues. */
    record Allocation(List<PaymentPart> forBill, List<PaymentPart> againstDues) {

        Money forBillTotal() {
            return Money.sum(forBill.stream().map(PaymentPart::amount).toList());
        }

        Money againstDuesTotal() {
            return Money.sum(againstDues.stream().map(PaymentPart::amount).toList());
        }
    }

    /** The newest bills of a khata customer, newest first. */
    public List<BillSummary> recentBills(long customerId, int limit) {
        session.requireSignedIn();
        return database.query(c -> bills.recentForCustomer(c, customerId, limit));
    }

    /**
     * Bills for the Bill History screen, newest first. Owner and staff.
     * A short number (up to 7 digits) is treated as a bill number and found on any date.
     *
     * @throws ValidationException (field "from") if the first day is after the last day
     */
    public List<BillSummary> searchBills(BillSearch search) {
        session.requireSignedIn();
        String text = Texts.clean(search.text());
        LocalDate from = search.from();
        LocalDate to = search.to();
        if (text != null && text.matches("\\d{1,7}")) {
            from = null; // a bill number: look on every date
            to = null;
        } else if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("from", "The first date is after the last date. Please check the dates.");
        }
        BillSearch clean = new BillSearch(from, to, text, search.customerId(), search.limit());
        return database.query(c -> bills.search(c, clean));
    }

    /**
     * Cancels a saved bill. Owner only. The bill is kept and marked CANCELLED with the reason; its number is never
     * used again. The part of the bill that went on the khata is taken off it (a CANCEL_REVERSAL entry).
     * Money paid for the bill at the counter is to be given back. Money paid towards old dues with the bill stays
     * paid, because it was not for this bill.
     *
     * @throws ValidationException (field "reason") if no reason is given
     */
    public CancelledBill cancel(long billNo, String reason) {
        User user = session.requireOwner();
        String cleanReason = Texts.clean(reason);
        if (cleanReason == null) {
            throw new ValidationException("reason", "Please type why the bill is cancelled.");
        }
        if (cleanReason.length() > MAX_REASON_LENGTH) {
            throw new ValidationException("reason", "The reason is too long. Use at most " + MAX_REASON_LENGTH
                    + " letters.");
        }
        return database.inTransaction(c -> {
            BillDetails bill = bills.findByNo(c, billNo)
                    .orElseThrow(() -> new BusinessRuleException("There is no bill number " + billNo + "."));
            String now = DbTime.now(clock);
            if (!bills.cancel(c, bill.id(), cleanReason, user.id(), now)) {
                throw new BusinessRuleException("Bill " + billNo + " is already cancelled.");
            }
            Optional<Long> customerId = bills.customerIdOf(c, bill.id());
            if (customerId.isPresent() && bill.toAccount().isPositive()) {
                ledger.insert(c, customerId.get(), LedgerEntryType.CANCEL_REVERSAL, bill.toAccount().negate(),
                        bill.id(), null, "Bill " + billNo + " cancelled: " + cleanReason, now, user.id());
            }
            audit.insert(c, user.id(), "BILL_CANCELLED", "bills", bill.id(), "Bill " + billNo + " ("
                    + bill.totals().total().toPlainString() + ") cancelled: " + cleanReason, now);
            return new CancelledBill(billNo, bill.paidForBillTotal(), bill.toAccount());
        });
    }

    /** A saved bill with everything needed to print it again. Owner and staff. */
    public BillDetails bill(long billNo) {
        session.requireSignedIn();
        return database.query(c -> bills.findByNo(c, billNo))
                .orElseThrow(() -> new BusinessRuleException("There is no bill number " + billNo + "."));
    }

    /** The newest bill number, or empty if no bill was saved yet. */
    public Optional<Long> lastBillNo() {
        session.requireSignedIn();
        return database.query(bills::lastBillNo);
    }

    /** Checks and saves a bill. Owner and staff. */
    public SavedBill save(BillRequest request) {
        User user = session.requireSignedIn();
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new BusinessRuleException("The bill has no items. Add at least one item.");
        }
        List<PaymentPart> payments = checkPayments(request.payments());
        Money paidTotal = Money.sum(payments.stream().map(PaymentPart::amount).toList());

        return database.inTransaction(c -> {
            List<CartLine> lines = checkLines(c, request.lines());
            BillTotals totals = Cart.totalsOf(lines);

            CustomerSummary customer = null;
            if (request.customerId() != null) {
                customer = customers.findById(c, request.customerId())
                        .orElseThrow(() -> new BusinessRuleException("This customer no longer exists."));
                if (!customer.customer().active()) {
                    throw new BusinessRuleException(customer.customer().name()
                            + " is switched off. Switch them on in Customers first.");
                }
            } else {
                checkWalkInPayment(paidTotal, totals.total());
            }

            Allocation allocation = allocate(payments, totals.total());
            Money paidForBill = allocation.forBillTotal();
            Money againstDues = allocation.againstDuesTotal();
            Money toAccount = totals.total().minus(paidForBill);

            Money previous = null;
            Money after = null;
            String customerName;
            if (customer != null) {
                previous = ledger.balance(c, customer.customer().id());
                after = previous.plus(toAccount).minus(againstDues);
                customerName = customer.customer().name();
            } else {
                customerName = checkWalkInName(request.walkInName());
            }

            String now = DbTime.now(clock);
            long billNo = bills.nextBillNo(c);
            Long customerId = customer == null ? null : customer.customer().id();
            long billId = bills.insertBill(c, new NewBill(billNo, now, customerId, customerName, totals,
                    paidForBill, toAccount, previous, after, user.id()));

            for (int i = 0; i < lines.size(); i++) {
                CartLine line = lines.get(i);
                bills.insertItem(c, billId, i + 1, line);
                if (line.rateChanged()) {
                    audit.insert(c, user.id(), "RATE_CHANGED", "bills", billId, "Bill " + billNo + ", "
                            + line.displayName() + ": " + line.productRate().toPlainString() + " -> "
                            + line.rate().toPlainString(), now);
                }
            }
            for (PaymentPart part : allocation.forBill()) {
                bills.insertPayment(c, billId, part.mode(), part.amount());
            }
            if (customerId != null) {
                if (toAccount.isPositive()) {
                    ledger.insert(c, customerId, LedgerEntryType.SALE_CREDIT, toAccount, billId, null, null, now,
                            user.id());
                }
                for (PaymentPart part : allocation.againstDues()) {
                    ledger.insert(c, customerId, LedgerEntryType.PAYMENT, part.amount().negate(), billId, part.mode(),
                            "Paid with bill " + billNo, now, user.id());
                }
            }
            return new SavedBill(billId, billNo, totals, paidTotal, paidForBill, againstDues, toAccount, previous,
                    after);
        });
    }

    /**
     * Fills the bill from the payments in the order given; whatever is left over goes against old dues.
     * Example: bill Rs 600, Cash 500 + UPI 300 gives the bill Cash 500 + UPI 100, and UPI 200 against dues.
     */
    static Allocation allocate(List<PaymentPart> payments, Money billTotal) {
        List<PaymentPart> forBill = new ArrayList<>();
        List<PaymentPart> againstDues = new ArrayList<>();
        Money stillDue = billTotal;
        for (PaymentPart part : payments) {
            Money toBill = part.amount().compareTo(stillDue) <= 0 ? part.amount() : stillDue;
            if (toBill.isPositive()) {
                forBill.add(new PaymentPart(part.mode(), toBill));
                stillDue = stillDue.minus(toBill);
            }
            Money rest = part.amount().minus(toBill);
            if (rest.isPositive()) {
                againstDues.add(new PaymentPart(part.mode(), rest));
            }
        }
        return new Allocation(forBill, againstDues);
    }

    // ------------------------------------------------------------------ checks

    /** Re-checks every line and copies product details from the product list. */
    private List<CartLine> checkLines(Connection connection, List<CartLine> requested) throws SQLException {
        List<CartLine> checked = new ArrayList<>();
        for (CartLine line : requested) {
            CartLine clean;
            if (line.productId() != null) {
                Product product = products.findById(connection, line.productId())
                        .orElseThrow(() -> new BusinessRuleException("\"" + line.displayName()
                                + "\" is no longer in the product list. Remove it from the bill and add it again."));
                clean = new CartLine(product.id(), product.name(), product.nameMr(), product.unit(),
                        product.packSize(), line.quantity(), line.rate(), product.rate(), product.mrp());
            } else {
                String name = Texts.clean(line.name());
                if (name == null) {
                    throw new ValidationException("lines", "A one-off item has no name.");
                }
                if (name.length() > MAX_NAME_LENGTH) {
                    throw new ValidationException("lines", "The item name \"" + name + "\" is too long.");
                }
                clean = new CartLine(null, name, null, line.unit(), null, line.quantity(), line.rate(), null, null);
            }
            try {
                Cart.checkQuantity(clean.unit(), clean.quantity());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("lines", clean.displayName() + ": " + e.getMessage());
            }
            if (clean.rate() == null || !clean.rate().isPositive()) {
                throw new ValidationException("lines", clean.displayName() + ": the rate must be more than zero.");
            }
            checked.add(clean);
        }
        return checked;
    }

    private static List<PaymentPart> checkPayments(List<PaymentPart> payments) {
        List<PaymentPart> checked = new ArrayList<>();
        if (payments == null) {
            return checked;
        }
        for (PaymentPart part : payments) {
            if (part.mode() == null) {
                throw new ValidationException("payment", "Please choose how the customer paid.");
            }
            if (part.amount() == null || part.amount().isNegative()) {
                throw new ValidationException("payment", "Payment amounts cannot be negative.");
            }
            if (part.amount().isPositive()) {
                checked.add(part);
            }
        }
        return checked;
    }

    private static void checkWalkInPayment(Money paid, Money total) {
        int compare = paid.compareTo(total);
        if (compare < 0) {
            throw new BusinessRuleException("A walk-in customer must pay the full bill of " + total
                    + ". Paid so far: " + paid + ". To give credit, choose a khata customer.");
        }
        if (compare > 0) {
            throw new BusinessRuleException("The payment " + paid + " is more than the bill " + total
                    + ". Enter only the bill amount and give the change back.");
        }
    }

    private static String checkWalkInName(String name) {
        String cleaned = Texts.clean(name);
        if (cleaned != null && cleaned.length() > MAX_WALK_IN_NAME_LENGTH) {
            throw new ValidationException("walkInName",
                    "The name on the bill is too long. Use at most " + MAX_WALK_IN_NAME_LENGTH + " letters.");
        }
        return cleaned;
    }
}
