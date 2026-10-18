package com.virpemart.billing.ui.billing;

import java.util.Map;
import java.util.Optional;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.model.Unit;
import com.virpemart.billing.ui.common.FormErrors;
import com.virpemart.billing.ui.common.Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * "Item not in list": adds a one-off item to the current bill without creating a product, or changes one that is
 * already on the bill (filled in, and kept in its row).
 */
public class OneOffItemController {

    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<Unit> unitBox;
    @FXML
    private TextField qtyField;
    @FXML
    private Label rateLabel;
    @FXML
    private TextField rateField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button addButton;

    private FormErrors errors;
    private Stage stage;
    private Cart cart;
    /** The line being changed, or null when a new item is added. */
    private Integer changeIndex;
    private Integer addedIndex;

    public OneOffItemController(AppContext context) {
        // No services needed: the item only goes into the cart. It is checked again when the bill is saved.
    }

    /** Opens the window and waits. Returns the index of the new line, or empty if cancelled. */
    public static Optional<Integer> open(Window owner, AppContext context, Cart cart) {
        Views.Loaded<OneOffItemController> view = Views.load("one-off-item.fxml", context);
        OneOffItemController form = view.controller();
        form.cart = cart;
        form.stage = Views.dialog(owner, "Item not in list", view.root());
        form.stage.setResizable(false);
        Platform.runLater(form.nameField::requestFocus);
        form.stage.showAndWait();
        return Optional.ofNullable(form.addedIndex);
    }

    /**
     * Opens the window filled in with a one-off line of the bill, to change it. The line stays in its row.
     * Returns the index of the changed line, or empty if cancelled (then nothing changes).
     */
    public static Optional<Integer> openToChange(Window owner, AppContext context, Cart cart, int index) {
        CartLine line = cart.lines().get(index);
        Views.Loaded<OneOffItemController> view = Views.load("one-off-item.fxml", context);
        OneOffItemController form = view.controller();
        form.cart = cart;
        form.changeIndex = index;
        form.nameField.setText(line.name());
        form.unitBox.setValue(line.unit());
        form.qtyField.setText(line.quantity().toBillString(line.unit()));
        form.rateField.setText(line.rate().toInputString());
        form.addButton.setText("Change item " + (index + 1));
        form.stage = Views.dialog(owner, "Change item not in list", view.root());
        form.stage.setResizable(false);
        Platform.runLater(() -> {
            form.qtyField.requestFocus();
            form.qtyField.selectAll();
        });
        form.stage.showAndWait();
        return Optional.ofNullable(form.addedIndex);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(errorLabel, Map.<String, Control>of("name", nameField, "qty", qtyField, "rate", rateField));
        unitBox.getItems().setAll(Unit.values());
        unitBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Unit unit) {
                return unit == null ? "" : unit.description();
            }

            @Override
            public Unit fromString(String text) {
                return Unit.parse(text).orElse(null);
            }
        });
        unitBox.setValue(Unit.PCS);
        unitBox.valueProperty().addListener((obs, oldUnit, unit) ->
                rateLabel.setText("Rate per " + unit.shortLabel() + " (₹) *"));
        rateLabel.setText("Rate per " + Unit.PCS.shortLabel() + " (₹) *");
    }

    @FXML
    private void add() {
        errors.clear();
        String field = "name";
        try {
            if (nameField.getText() == null || nameField.getText().isBlank()) {
                throw new IllegalArgumentException("Please enter the item name.");
            }
            field = "qty";
            Quantity quantity = Quantity.parse(qtyField.getText());
            field = "rate";
            Money rate = Money.parse(rateField.getText().replace("₹", "").strip());
            field = "qty";
            Cart.checkQuantity(unitBox.getValue(), quantity);
            field = "rate";
            addedIndex = changeIndex == null
                    ? cart.addOneOff(nameField.getText(), unitBox.getValue(), quantity, rate)
                    : cart.replaceOneOff(changeIndex, nameField.getText(), unitBox.getValue(), quantity, rate);
            stage.close();
        } catch (IllegalArgumentException e) {
            errors.show(e.getMessage(), field);
        }
    }

    @FXML
    private void cancel() {
        stage.close();
    }
}
