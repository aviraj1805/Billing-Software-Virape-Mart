package com.virpemart.billing.print;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.virpemart.billing.model.BillDetails;
import com.virpemart.billing.model.BillTotals;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.model.ShopDetails;
import com.virpemart.billing.model.Unit;
import com.virpemart.billing.print.ReceiptLine.Pair;
import com.virpemart.billing.print.ReceiptLine.Rule;
import com.virpemart.billing.print.ReceiptLine.Style;
import com.virpemart.billing.print.ReceiptLine.Text;

/**
 * Decides what a printed bill says, line by line. It does not draw anything, so it can be tested easily.
 *
 * <p>Layout, top to bottom: shop heading, bill number and date, customer, items (Marathi name, then
 * quantity x rate and amount), the bill total, then payment. A khata customer's bill shows the previous dues,
 * what was paid now and the balance left, in the user's Marathi wording. Subtotal, round off, MRP and savings are
 * not printed (the user's choice); they are still saved with the bill.
 */
public final class ReceiptBuilder {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", Locale.ENGLISH);
    private static final Rule RULE = new Rule();

    // Printed wording chosen by the user. Do not add English next to it.
    static final String BILL_TOTAL = "एकूण";
    static final String PREVIOUS_DUES = "मागील बाकी";
    static final String PAID_NOW = "जमा";
    static final String BALANCE_DUES = "एकूण बाकी";
    private static final String INDENT = "     ";

    private ReceiptBuilder() {
    }

    /**
     * The printed form of a saved bill.
     *
     * @param reprint true when the bill is printed again later; it is then marked "DUPLICATE COPY"
     */
    public static Receipt forBill(BillDetails bill, ShopDetails shop, boolean reprint) {
        String banner = null;
        if (bill.isCancelled()) {
            banner = "*** CANCELLED BILL ***";
        } else if (reprint) {
            banner = "DUPLICATE COPY";
        }
        return new Receipt(shop.name() + " bill " + bill.billNo(), build(bill, shop, banner));
    }

    /** A made-up khata bill with Marathi names, to check the printer and the bill heading. */
    public static Receipt sample(ShopDetails shop, LocalDateTime now) {
        List<CartLine> lines = List.of(
                new CartLine(1L, "Sugar", "साखर", Unit.KG, null, Quantity.parse("0.5"),
                        Money.ofRupees(44), Money.ofRupees(44), null),
                new CartLine(2L, "Toor Dal", "तूर डाळ", Unit.KG, null, Quantity.parse("0.250"),
                        Money.ofRupees(150), Money.ofRupees(150), null),
                new CartLine(3L, "Tata Salt", "टाटा मीठ", Unit.PCS, "1 kg", Quantity.ofWhole(2),
                        Money.ofRupees(28), Money.ofRupees(28), Money.ofRupees(30)));
        BillTotals totals = Cart.totalsOf(lines);
        Money previous = Money.ofRupees(480);
        Money paid = Money.ofRupees(100);
        Money toAccount = totals.total().minus(paid);
        BillDetails bill = new BillDetails(0, 123, now, "C0001", "Sample Customer", lines, totals,
                List.of(new PaymentPart(PaymentMode.CASH, paid)), List.of(), toAccount, previous,
                previous.plus(toAccount), null);
        return new Receipt("Test print", build(bill, shop, "TEST PRINT - NOT A REAL BILL"));
    }

    private static List<ReceiptLine> build(BillDetails bill, ShopDetails shop, String banner) {
        List<ReceiptLine> out = new ArrayList<>();
        heading(out, shop);
        if (banner != null) {
            out.add(centered(banner, Style.BOLD));
        }
        if (bill.isCancelled()) {
            out.add(centered("Reason: " + bill.cancelReason(), Style.SMALL));
        }
        out.add(new Pair("Bill No. " + bill.billNo(), DATE_TIME.format(bill.createdAt()), Style.BOLD));
        if (bill.customerName() != null) {
            String who = bill.isKhata() ? bill.customerName() + " (" + bill.customerNo() + ")" : bill.customerName();
            out.add(left("Customer: " + who, Style.NORMAL));
        }
        out.add(RULE);
        out.add(new Pair("Item", "Amount", Style.SMALL));
        items(out, bill.lines());
        out.add(RULE);
        totals(out, bill.totals());
        out.add(RULE);
        if (bill.isKhata()) {
            khata(out, bill);
        } else {
            walkInPayment(out, bill);
        }
        if (!shop.footerLines().isEmpty()) {
            out.add(RULE);
            for (String line : shop.footerLines()) {
                out.add(centered(line, Style.NORMAL));
            }
        }
        return out;
    }

    private static void heading(List<ReceiptLine> out, ShopDetails shop) {
        out.add(centered(shop.name(), Style.LARGE));
        if (shop.secondLine() != null) {
            out.add(centered(shop.secondLine(), Style.BOLD));
        }
        for (String line : shop.addressLines()) {
            out.add(centered(line, Style.SMALL));
        }
        if (shop.phone() != null) {
            out.add(centered("Phone: " + shop.phone(), Style.SMALL));
        }
        out.add(RULE);
    }

    private static void items(List<ReceiptLine> out, List<CartLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            CartLine line = lines.get(i);
            out.add(left((i + 1) + ". " + printedName(line), Style.NORMAL));
            String detail = INDENT + quantity(line.quantity(), line.unit()) + " x " + line.rate().toGroupedString();
            out.add(new Pair(detail, line.lineTotal().toGroupedString(), Style.NORMAL));
        }
    }

    /**
     * The printed item name: the Marathi name only. An item without a Marathi name (for example a one-off item)
     * prints its English name with pack size, so no line is ever blank.
     */
    static String printedName(CartLine line) {
        return line.nameMr() != null ? line.nameMr() : line.displayName();
    }

    /** Only the final total ("एकूण") is printed; subtotal, round off and savings are not. */
    private static void totals(List<ReceiptLine> out, BillTotals totals) {
        out.add(new Pair(BILL_TOTAL, rupees(totals.total()), Style.LARGE));
    }

    private static void walkInPayment(List<ReceiptLine> out, BillDetails bill) {
        for (Map.Entry<PaymentMode, Money> paid : bill.paidByMode().entrySet()) {
            out.add(new Pair("Paid by " + paid.getKey().label(), rupees(paid.getValue()), Style.NORMAL));
        }
    }

    /**
     * Previous dues (मागील बाकी), money paid now (जमा) and the balance left (एकूण बाकी).
     * All money paid with the bill, in any mix of modes, is one "जमा" line.
     * An advance has no separate wording: it prints as a minus amount, for example "मागील बाकी -₹50.00".
     */
    private static void khata(List<ReceiptLine> out, BillDetails bill) {
        out.add(new Pair(PREVIOUS_DUES, rupees(bill.previousBalance()), Style.NORMAL));
        out.add(new Pair(PAID_NOW, rupees(bill.paidTotal()), Style.NORMAL));
        out.add(new Pair(BALANCE_DUES, rupees(bill.balanceAfter()), Style.LARGE));
    }

    /** "0.500 kg" for loose items (three decimals, as they are weighed), "2" for packets. */
    static String quantity(Quantity quantity, Unit unit) {
        if (unit.isLoose()) {
            return BigDecimal.valueOf(quantity.milli(), 3).toPlainString() + " " + unit.shortLabel();
        }
        return quantity.toPlainString();
    }

    /** "₹1,250.00" or "-₹5.00". */
    static String rupees(Money amount) {
        String grouped = amount.toGroupedString();
        return amount.isNegative() ? "-₹" + grouped.substring(1) : "₹" + grouped;
    }

    private static Text centered(String text, Style style) {
        return new Text(text, true, style);
    }

    private static Text left(String text, Style style) {
        return new Text(text, false, style);
    }
}
