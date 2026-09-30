package com.virpemart.billing.ui.billing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.BillTotals;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.SavedBill;
import com.virpemart.billing.service.BillRequest;
import com.virpemart.billing.service.BillingService;
import com.virpemart.billing.service.UserFacingException;
import com.virpemart.billing.service.ValidationException;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.FormErrors;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Views;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The payment window shown by "Save and pay". Collects Cash / UPI / Card amounts and saves the bill.
 *
 * <p>The numbers shown here are a preview for the person at the counter; {@link BillingService}
 * recalculates and checks everything when saving.
 */
public class BillPaymentController {

    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");

    /** What happened, for the message on the billing screen. */
    public record Result(SavedBill bill, String summary) {
    }

    private final BillingService billing;

    @FXML
    private Label customerLabel;
    @FXML
    private Label billTotalLabel;
    @FXML
    private Label previousTitle;
    @FXML
    private Label previousLabel;
    @FXML
    private Label withDuesTitle;
    @FXML
    private Label withDuesLabel;
    @FXML
    private HBox khataQuickBox;
    @FXML
    private HBox walkInQuickBox;
    @FXML
    private Button payBillButton;
    @FXML
    private Button payAllButton;
    @FXML
    private TextField cashField;
    @FXML
    private TextField upiField;
    @FXML
    private TextField cardField;
    @FXML
    private Label receivedTitle;
    @FXML
    private TextField receivedField;
    @FXML
    private Label statusLabel;
    @FXML
    private Label errorLabel;

    private FormErrors errors;
    private Stage stage;
    private CustomerSummary customer;
    private String walkInName;
    private Cart cart;
    private BillTotals totals;
    private Result result;

    public BillPaymentController(AppContext context) {
        this.billing = context.services().billing();
    }

    /**
     * Opens the payment window and waits.
     *
     * @param customer   the khata customer, or null for walk-in
     * @param walkInName optional name for a walk-in bill
     * @return the saved bill, or empty if the user went back to the bill
     */
    public static Optional<Result> open(Window owner, AppContext context, CustomerSummary customer, String walkInName,
                                        Cart cart) {
        Views.Loaded<BillPaymentController> view = Views.load("bill-payment.fxml", context);
        BillPaymentController form = view.controller();
        form.stage = Views.dialog(owner, "Save and pay", view.root());
        form.stage.setResizable(false);
        form.fill(customer, walkInName, cart);
        form.stage.showAndWait();
        return Optional.ofNullable(form.result);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(errorLabel, Map.<String, Control>of(
                "cash", cashField, "upi", upiField, "card", cardField, "received", receivedField));
        for (TextField field : List.of(cashField, upiField, cardField, receivedField)) {
            field.textProperty().addListener((obs, oldText, newText) -> updateStatus());
        }
    }

    private void fill(CustomerSummary khataCustomer, String name, Cart bill) {
        customer = khataCustomer;
        walkInName = name;
        cart = bill;
        totals = bill.totals();
        boolean khata = customer != null;

        customerLabel.setText(khata ? customer.customer().name() + "  (" + customer.customer().customerNo() + ")"
                : "Walk-in customer" + (name == null || name.isBlank() ? "" : ": " + name.strip()));
        billTotalLabel.setText(Format.money(totals.total()));

        for (Label label : List.of(previousTitle, previousLabel, withDuesTitle, withDuesLabel)) {
            label.setVisible(khata);
            label.setManaged(khata);
        }
        show(khataQuickBox, khata);
        show(walkInQuickBox, !khata);
        receivedTitle.setVisible(!khata);
        receivedTitle.setManaged(!khata);
        receivedField.setVisible(!khata);
        receivedField.setManaged(!khata);

        if (khata) {
            Money previous = customer.balance();
            previousTitle.setText(previous.isNegative() ? "Advance" : "Previous dues");
            previousLabel.setText(previous.isNegative() ? "-" + Format.money(previous.negate()) : Format.money(previous));
            Money withDues = totals.total().plus(previous);
            withDuesLabel.setText(Format.money(withDues));
            payBillButton.setText("Bill amount (" + Format.money(totals.total()) + ")");
            payAllButton.setText("Full total with dues (" + Format.money(withDues) + ")");
            payAllButton.setDisable(!withDues.isPositive() || withDues.equals(totals.total()));
            // Empty on purpose: the owner decides how much is paid now.
        } else {
            cashField.setText(totals.total().toPlainString());
        }
        updateStatus();
        Platform.runLater(() -> {
            cashField.requestFocus();
            cashField.selectAll();
        });
    }

    private static void show(HBox box, boolean visible) {
        box.setVisible(visible);
        box.setManaged(visible);
    }

    // ------------------------------------------------------------------ quick buttons

    @FXML
    private void payBillAmount() {
        setAmounts(totals.total(), null, null);
    }

    @FXML
    private void payFullTotal() {
        setAmounts(totals.total().plus(customer.balance()), null, null);
    }

    @FXML
    private void payNothing() {
        setAmounts(null, null, null);
    }

    @FXML
    private void allCash() {
        setAmounts(totals.total(), null, null);
    }

    @FXML
    private void allUpi() {
        setAmounts(null, totals.total(), null);
    }

    @FXML
    private void allCard() {
        setAmounts(null, null, totals.total());
    }

    private void setAmounts(Money cash, Money upi, Money card) {
        cashField.setText(cash == null ? "" : cash.toPlainString());
        upiField.setText(upi == null ? "" : upi.toPlainString());
        cardField.setText(card == null ? "" : card.toPlainString());
        cashField.requestFocus();
    }

    // ------------------------------------------------------------------ live preview

    private void updateStatus() {
        Money cash = amountOrNull(cashField);
        Money upi = amountOrNull(upiField);
        Money card = amountOrNull(cardField);
        if (cash == null || upi == null || card == null) {
            setStatus("Please type amounts as numbers, like 450 or 450.50.", true);
            return;
        }
        Money paid = cash.plus(upi).plus(card);
        Money total = totals.total();

        if (customer == null) {
            int compare = paid.compareTo(total);
            String status = compare == 0 ? "Fully paid."
                    : compare < 0 ? "Still to pay: " + Format.money(total.minus(paid)) + "."
                    : Format.money(paid.minus(total)) + " more than the bill. Enter only the bill amount.";
            Money received = amountOrNull(receivedField);
            if (received != null && received.isPositive() && cash.isPositive()) {
                Money change = received.minus(cash);
                status += change.isNegative() ? "\nCash given is less than the cash amount."
                        : "\nGive back: " + Format.money(change);
            }
            setStatus(status, compare != 0);
        } else {
            Money forBill = paid.compareTo(total) <= 0 ? paid : total;
            Money toKhata = total.minus(forBill);
            Money againstDues = paid.minus(forBill);
            Money after = customer.balance().plus(toKhata).minus(againstDues);
            String status = "Paid now " + Format.money(paid) + ".  Added to khata " + Format.money(toKhata) + "."
                    + (againstDues.isPositive() ? "  " + Format.money(againstDues) + " paid towards old dues." : "")
                    + "\nNew balance: " + Format.balance(after);
            setStatus(status, false);
        }
    }

    private void setStatus(String text, boolean warning) {
        statusLabel.setText(text);
        statusLabel.pseudoClassStateChanged(WARNING, warning);
        stage().ifPresent(Stage::sizeToScene);
    }

    private Optional<Stage> stage() {
        return Optional.ofNullable(stage);
    }

    /** The amount typed in a box: zero if blank, null if it is not a valid amount. */
    private static Money amountOrNull(TextField field) {
        String text = field.getText() == null ? "" : field.getText().replace("₹", "").replace(",", "").strip();
        if (text.isEmpty()) {
            return Money.ZERO;
        }
        try {
            Money amount = Money.parse(text);
            return amount.isNegative() ? null : amount;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ save

    @FXML
    private void save() {
        errors.clear();
        List<PaymentPart> parts = new ArrayList<>();
        for (var entry : List.of(Map.entry("cash", cashField), Map.entry("upi", upiField), Map.entry("card", cardField))) {
            Money amount = amountOrNull(entry.getValue());
            if (amount == null) {
                errors.show("Please type a valid amount, like 450 or 450.50, or leave it empty.", entry.getKey());
                return;
            }
            if (amount.isPositive()) {
                PaymentMode mode = switch (entry.getKey()) {
                    case "cash" -> PaymentMode.CASH;
                    case "upi" -> PaymentMode.UPI;
                    default -> PaymentMode.CARD;
                };
                parts.add(new PaymentPart(mode, amount));
            }
        }
        Money paid = Money.sum(parts.stream().map(PaymentPart::amount).toList());

        if (customer != null && paid.isZero() && !Dialogs.confirm(stage, "Put on khata",
                "Nothing is paid now. Put the whole bill of " + Format.money(totals.total()) + " on "
                        + customer.customer().name() + "'s khata?\n\nNew balance: "
                        + Format.balance(customer.balance().plus(totals.total())), "Put on khata")) {
            cashField.requestFocus();
            return;
        }

        try {
            SavedBill saved = billing.save(new BillRequest(
                    customer == null ? null : customer.customer().id(), walkInName, cart.lines(), parts));
            result = new Result(saved, summary(saved, parts));
            stage.close();
        } catch (ValidationException e) {
            errors.show(e.getMessage(), e.field());
        } catch (UserFacingException e) {
            errors.show(e.getMessage(), null);
        }
    }

    /** One line for the green message on the billing screen. */
    private String summary(SavedBill saved, List<PaymentPart> parts) {
        StringBuilder text = new StringBuilder("Bill ").append(saved.billNo()).append(" saved");
        if (customer != null) {
            text.append(" for ").append(customer.customer().name());
        }
        text.append("  ·  Total ").append(Format.money(saved.totals().total()));
        List<String> paidParts = parts.stream()
                .map(p -> p.mode().label() + " " + Format.money(p.amount())).toList();
        text.append("  ·  ").append(paidParts.isEmpty() ? "Nothing paid now" : "Paid " + String.join(" + ", paidParts));
        if (customer == null) {
            Money received = amountOrNull(receivedField);
            Money cash = parts.stream().filter(p -> p.mode() == PaymentMode.CASH).map(PaymentPart::amount)
                    .findFirst().orElse(Money.ZERO);
            if (received != null && received.compareTo(cash) > 0 && cash.isPositive()) {
                text.append("  ·  Give back ").append(Format.money(received.minus(cash)));
            }
        } else {
            if (saved.toAccount().isPositive()) {
                text.append("  ·  ").append(Format.money(saved.toAccount())).append(" added to khata");
            }
            text.append("  ·  New balance: ").append(Format.balance(saved.balanceAfter()));
        }
        return text.toString();
    }

    @FXML
    private void cancel() {
        stage.close();
    }
}
