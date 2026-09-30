package com.virpemart.billing.ui.customers;

import java.util.Map;
import java.util.Optional;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.service.BusinessRuleException;
import com.virpemart.billing.service.LedgerService;
import com.virpemart.billing.service.ValidationException;
import com.virpemart.billing.ui.common.FormErrors;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;
import javafx.stage.Window;

/** The "Receive payment" window: money paid against a customer's khata. */
public class PaymentFormController {

    private final LedgerService ledger;

    @FXML
    private Label customerLabel;
    @FXML
    private Label currentLabel;
    @FXML
    private TextField amountField;
    @FXML
    private ToggleGroup modeGroup;
    @FXML
    private ToggleButton cashButton;
    @FXML
    private ToggleButton upiButton;
    @FXML
    private ToggleButton cardButton;
    @FXML
    private TextField noteField;
    @FXML
    private Label previewLabel;
    @FXML
    private Label errorLabel;

    private FormErrors errors;
    private Stage stage;
    private CustomerSummary customer;
    private CustomerSummary saved;

    public PaymentFormController(AppContext context) {
        this.ledger = context.services().ledger();
    }

    /** Opens the window and waits. Returns the customer with the new balance, or empty if cancelled. */
    public static Optional<CustomerSummary> open(Window owner, AppContext context, CustomerSummary customer) {
        Views.Loaded<PaymentFormController> view = Views.load("payment-form.fxml", context);
        PaymentFormController form = view.controller();
        form.stage = Views.dialog(owner, "Receive payment", view.root());
        form.stage.setResizable(false);
        form.fill(customer);
        form.stage.showAndWait();
        return Optional.ofNullable(form.saved);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(errorLabel,
                Map.<String, Control>of("amount", amountField, "mode", cashButton, "note", noteField));
        // One payment mode is always chosen.
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
        amountField.textProperty().addListener((obs, oldText, newText) -> updatePreview());
    }

    private void fill(CustomerSummary summary) {
        customer = summary;
        customerLabel.setText(summary.customer().name() + "  (" + summary.customer().customerNo() + ")");
        currentLabel.setText("Now: " + Format.balance(summary.balance()));
        updatePreview();
        Platform.runLater(amountField::requestFocus);
    }

    /** Shows what the balance will be after this payment. Only a preview; the service does the real check. */
    private void updatePreview() {
        Optional<Money> amount = typedAmount();
        if (amount.isEmpty() || !amount.get().isPositive()) {
            previewLabel.setText("");
            return;
        }
        Money after = customer.balance().minus(amount.get());
        String text = "After this payment: " + Format.balance(after) + ".";
        if (after.isNegative()) {
            text += "\n" + Format.money(after.negate()) + " more than the dues will be kept as advance.";
        }
        previewLabel.setText(text);
    }

    private Optional<Money> typedAmount() {
        try {
            return Optional.of(Money.parse(amountField.getText().replace("₹", "").strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private PaymentMode selectedMode() {
        if (upiButton.isSelected()) {
            return PaymentMode.UPI;
        }
        if (cardButton.isSelected()) {
            return PaymentMode.CARD;
        }
        return cashButton.isSelected() ? PaymentMode.CASH : null;
    }

    @FXML
    private void save() {
        errors.clear();
        try {
            saved = ledger.receivePayment(customer.customer().id(), amountField.getText(), selectedMode(),
                    noteField.getText());
            stage.close();
        } catch (ValidationException e) {
            errors.show(e.getMessage(), e.field());
        } catch (BusinessRuleException e) {
            errors.show(e.getMessage(), null);
        }
    }

    @FXML
    private void cancel() {
        stage.close();
    }
}
