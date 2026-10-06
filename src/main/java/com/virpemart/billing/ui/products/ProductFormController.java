package com.virpemart.billing.ui.products;

import java.util.Map;
import java.util.Optional;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Unit;
import com.virpemart.billing.service.BusinessRuleException;
import com.virpemart.billing.service.ProductInput;
import com.virpemart.billing.service.ProductService;
import com.virpemart.billing.service.ValidationException;
import com.virpemart.billing.ui.common.FormErrors;
import com.virpemart.billing.ui.common.Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

/** The "Add product" / "Edit product" window. All checks happen in {@link ProductService}. */
public class ProductFormController {

    private final AppContext context;
    private final ProductService products;

    @FXML
    private Label codeLabel;
    @FXML
    private TextField nameField;
    @FXML
    private TextField nameMrField;
    @FXML
    private ComboBox<Unit> unitBox;
    @FXML
    private TextField packSizeField;
    @FXML
    private Label rateLabel;
    @FXML
    private TextField rateField;
    @FXML
    private Label errorLabel;

    private FormErrors errors;
    private Stage stage;
    private Product existing;
    private Product saved;

    public ProductFormController(AppContext context) {
        this.context = context;
        this.products = context.services().products();
    }

    /**
     * Opens the form and waits until it is closed.
     *
     * @param existing the product to edit, or null to add a new one
     * @return the saved product, or empty if the user cancelled
     */
    public static Optional<Product> open(Window owner, AppContext context, Product existing) {
        Views.Loaded<ProductFormController> view = Views.load("product-form.fxml", context);
        ProductFormController form = view.controller();
        form.stage = Views.dialog(owner,
                existing == null ? "Add product" : "Edit product " + existing.code(), view.root());
        form.stage.setResizable(false);
        form.fill(existing);
        form.stage.showAndWait();
        return Optional.ofNullable(form.saved);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(errorLabel, Map.<String, Control>of(
                "name", nameField,
                "nameMr", nameMrField,
                "unit", unitBox,
                "packSize", packSizeField,
                "rate", rateField));
        unitBox.getItems().setAll(Unit.values());
        unitBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Unit unit) {
                return unit == null ? "" : switch (unit) {
                    case KG -> "kg  (loose, weighed)";
                    case L -> "litre  (loose, measured)";
                    case PCS -> "piece / packet";
                };
            }

            @Override
            public Unit fromString(String text) {
                return Unit.parse(text).orElse(null);
            }
        });
        unitBox.valueProperty().addListener((obs, oldUnit, newUnit) -> updateRateLabel(newUnit));
    }

    private void fill(Product product) {
        this.existing = product;
        if (product == null) {
            codeLabel.setText("New product. The code is given automatically.");
        } else {
            codeLabel.setText("Code " + product.code() + (product.active() ? "" : "  (switched off)"));
            nameField.setText(product.name());
            nameMrField.setText(product.nameMr());
            unitBox.setValue(product.unit());
            packSizeField.setText(product.packSize());
            rateField.setText(product.rate().toInputString());
        }
        updateRateLabel(unitBox.getValue());
        Platform.runLater(nameField::requestFocus);
    }

    private void updateRateLabel(Unit unit) {
        rateLabel.setText(unit == null ? "Rate (₹) *" : switch (unit) {
            case KG -> "Rate per kg (₹) *";
            case L -> "Rate per litre (₹) *";
            case PCS -> "Rate per piece (₹) *";
        });
    }

    @FXML
    private void save() {
        errors.clear();
        Unit unit = unitBox.getValue();
        ProductInput input = new ProductInput(
                nameField.getText(),
                nameMrField.getText(),
                unit == null ? null : unit.name(),
                packSizeField.getText(),
                rateField.getText());
        try {
            saved = (existing == null) ? products.create(input) : products.update(existing.id(), input);
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
