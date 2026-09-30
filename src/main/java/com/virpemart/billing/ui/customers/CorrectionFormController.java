package com.virpemart.billing.ui.customers;

import java.util.Map;
import java.util.Optional;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.Money;
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
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;
import javafx.stage.Window;

/** The owner's "Correct balance" window. A reason is always required. */
public class CorrectionFormController {

    private final LedgerService ledger;

    @FXML
    private Label customerLabel;
    @FXML
    private Label currentLabel;
    @FXML
    private ToggleGroup directionGroup;
    @FXML
    private RadioButton reduceButton;
    @FXML
    private RadioButton increaseButton;
    @FXML
    private TextField amountField;
    @FXML
    private TextField reasonField;
    @FXML
    private Label previewLabel;
    @FXML
    private Label errorLabel;

    private FormErrors errors;
    private Stage stage;
    private CustomerSummary customer;
    private CustomerSummary saved;

    public CorrectionFormController(AppContext context) {
        this.ledger = context.services().ledger();
    }

    /** Opens the window and waits. Returns the customer with the new balance, or empty if cancelled. */
    public static Optional<CustomerSummary> open(Window owner, AppContext context, CustomerSummary customer) {
        Views.Loaded<CorrectionFormController> view = Views.load("correction-form.fxml", context);
        CorrectionFormController form = view.controller();
        form.stage = Views.dialog(owner, "Correct balance", view.root());
        form.stage.setResizable(false);
        form.fill(customer);
        form.stage.showAndWait();
        return Optional.ofNullable(form.saved);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(errorLabel, Map.<String, Control>of(
                "direction", reduceButton, "amount", amountField, "reason", reasonField));
        amountField.textProperty().addListener((obs, oldText, newText) -> updatePreview());
        directionGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> updatePreview());
    }

    private void fill(CustomerSummary summary) {
        customer = summary;
        customerLabel.setText(summary.customer().name() + "  (" + summary.customer().customerNo() + ")");
        currentLabel.setText("Now: " + Format.balance(summary.balance()));
        Platform.runLater(reduceButton::requestFocus);
    }

    /** Shows the balance after the correction. Only a preview; the service does the real check. */
    private void updatePreview() {
        Money amount;
        try {
            amount = Money.parse(amountField.getText().replace("₹", "").strip());
        } catch (IllegalArgumentException e) {
            previewLabel.setText("");
            return;
        }
        if (!amount.isPositive() || directionGroup.getSelectedToggle() == null) {
            previewLabel.setText("");
            return;
        }
        Money after = increaseButton.isSelected() ? customer.balance().plus(amount) : customer.balance().minus(amount);
        previewLabel.setText("After this correction: " + Format.balance(after) + ".");
    }

    @FXML
    private void save() {
        errors.clear();
        if (directionGroup.getSelectedToggle() == null) {
            errors.show("Please choose \"Reduce dues\" or \"Increase dues\".", "direction");
            return;
        }
        try {
            saved = ledger.correctBalance(customer.customer().id(), increaseButton.isSelected(),
                    amountField.getText(), reasonField.getText());
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
