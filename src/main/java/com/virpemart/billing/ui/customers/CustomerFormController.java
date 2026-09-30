package com.virpemart.billing.ui.customers;

import java.util.Map;
import java.util.Optional;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.Customer;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.service.BusinessRuleException;
import com.virpemart.billing.service.CustomerInput;
import com.virpemart.billing.service.CustomerService;
import com.virpemart.billing.service.ValidationException;
import com.virpemart.billing.ui.common.FormErrors;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/** The "Add customer" / "Edit customer" window. All checks happen in {@link CustomerService}. */
public class CustomerFormController {

    private final CustomerService customers;

    @FXML
    private Label numberLabel;
    @FXML
    private TextField nameField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField addressField;
    @FXML
    private TextArea notesArea;
    @FXML
    private Label oldDuesLabel;
    @FXML
    private VBox oldDuesBox;
    @FXML
    private TextField oldDuesField;
    @FXML
    private Label errorLabel;

    private FormErrors errors;
    private Stage stage;
    private CustomerSummary existing;
    private CustomerSummary saved;

    public CustomerFormController(AppContext context) {
        this.customers = context.services().customers();
    }

    /**
     * Opens the form and waits until it is closed.
     *
     * @param existing the customer to edit, or null to add a new one
     * @return the saved customer, or empty if the user cancelled
     */
    public static Optional<CustomerSummary> open(Window owner, AppContext context, CustomerSummary existing) {
        Views.Loaded<CustomerFormController> view = Views.load("customer-form.fxml", context);
        CustomerFormController form = view.controller();
        form.stage = Views.dialog(owner,
                existing == null ? "Add customer" : "Edit customer " + existing.customer().customerNo(), view.root());
        form.stage.setResizable(false);
        form.fill(existing);
        form.stage.showAndWait();
        return Optional.ofNullable(form.saved);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(errorLabel, Map.<String, Control>of(
                "name", nameField,
                "phone", phoneField,
                "address", addressField,
                "notes", notesArea,
                "oldDues", oldDuesField));
    }

    private void fill(CustomerSummary summary) {
        existing = summary;
        boolean adding = summary == null;
        oldDuesLabel.setVisible(adding);
        oldDuesLabel.setManaged(adding);
        oldDuesBox.setVisible(adding);
        oldDuesBox.setManaged(adding);
        if (adding) {
            numberLabel.setText("New customer. The customer number is given automatically.");
        } else {
            Customer customer = summary.customer();
            numberLabel.setText("Customer " + customer.customerNo() + "   ·   " + Format.balance(summary.balance())
                    + "   (use \"Correct balance\" to change the khata)");
            nameField.setText(customer.name());
            phoneField.setText(Format.phone(customer.phone()));
            addressField.setText(customer.address());
            notesArea.setText(customer.notes());
        }
        Platform.runLater(nameField::requestFocus);
    }

    @FXML
    private void save() {
        errors.clear();
        CustomerInput input = new CustomerInput(nameField.getText(), phoneField.getText(), addressField.getText(),
                notesArea.getText(), existing == null ? oldDuesField.getText() : null);
        try {
            saved = existing == null ? customers.create(input) : customers.update(existing.customer().id(), input);
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
