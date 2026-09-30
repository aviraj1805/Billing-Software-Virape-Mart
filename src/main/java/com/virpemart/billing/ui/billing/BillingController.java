package com.virpemart.billing.ui.billing;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.BillSummary;
import com.virpemart.billing.model.BillTotals;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PrintAfterSave;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.service.BillingService;
import com.virpemart.billing.service.BusinessRuleException;
import com.virpemart.billing.service.CustomerService;
import com.virpemart.billing.service.PrintService;
import com.virpemart.billing.service.ProductService;
import com.virpemart.billing.service.SettingsService;
import com.virpemart.billing.ui.common.Background;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.ErrorHandler;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.SearchPopup;
import com.virpemart.billing.ui.customers.CustomerFormController;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * The Billing screen.
 *
 * <p>Fast keyboard flow: type a product (F2), Enter, type the quantity, Enter. Repeat. F12 to save and pay.
 * The cart and all arithmetic live in {@link Cart}; saving happens in {@link BillingService}.
 */
public class BillingController {

    private static final int PRODUCT_RESULTS = 30;
    private static final int CUSTOMER_RESULTS = 20;
    private static final int RECENT_BILLS = 5;
    private static final PseudoClass RATE_CHANGED = PseudoClass.getPseudoClass("rate-changed");
    private static final DateTimeFormatter HELD_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /** A bill put aside to serve another customer. Kept in memory only. */
    private record HeldBill(CustomerSummary customer, String walkInName, Cart cart, LocalTime heldAt) {

        String describe() {
            String who = customer != null ? customer.customer().name()
                    : (walkInName == null || walkInName.isBlank() ? "Walk-in" : walkInName.strip());
            BillTotals totals = cart.totals();
            return who + "  ·  " + totals.lineCount() + (totals.lineCount() == 1 ? " item" : " items") + "  ·  "
                    + Format.money(totals.total()) + "  ·  held at " + heldAt.format(HELD_TIME);
        }
    }

    private final AppContext context;
    private final ProductService products;
    private final CustomerService customers;
    private final BillingService billing;
    private final PrintService printing;
    private final SettingsService settings;

    private final Cart cart = new Cart();
    private final List<HeldBill> held = new ArrayList<>();
    private CustomerSummary customer;
    private Product chosenProduct;
    private SearchPopup<Product> productPopup;
    private SearchPopup<CustomerSummary> customerPopup;

    @FXML
    private VBox root;
    @FXML
    private Label bannerLabel;
    @FXML
    private Button heldButton;
    @FXML
    private TextField productSearch;
    @FXML
    private TextField qtyField;
    @FXML
    private Label qtyUnitLabel;
    @FXML
    private Label entryLabel;
    @FXML
    private TableView<CartLine> cartTable;
    @FXML
    private TableColumn<CartLine, String> lineNoColumn;
    @FXML
    private TableColumn<CartLine, String> itemColumn;
    @FXML
    private TableColumn<CartLine, String> qtyColumn;
    @FXML
    private TableColumn<CartLine, String> rateColumn;
    @FXML
    private TableColumn<CartLine, String> mrpColumn;
    @FXML
    private TableColumn<CartLine, String> amountColumn;
    @FXML
    private Button qtyButton;
    @FXML
    private Button rateButton;
    @FXML
    private Button removeButton;
    @FXML
    private TextField customerSearch;
    @FXML
    private VBox walkInBox;
    @FXML
    private TextField walkInNameField;
    @FXML
    private VBox khataBox;
    @FXML
    private Label customerNameLabel;
    @FXML
    private Label customerInfoLabel;
    @FXML
    private Label customerDuesLabel;
    @FXML
    private ListView<BillSummary> recentBillsList;
    @FXML
    private Label itemsLabel;
    @FXML
    private Label subtotalLabel;
    @FXML
    private Label roundOffLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label savingsLabel;
    @FXML
    private Label previousDuesTitle;
    @FXML
    private Label previousDuesLabel;
    @FXML
    private Label withDuesTitle;
    @FXML
    private Label withDuesLabel;
    @FXML
    private Button saveButton;

    public BillingController(AppContext context) {
        this.context = context;
        this.products = context.services().products();
        this.customers = context.services().customers();
        this.billing = context.services().billing();
        this.printing = context.services().printing();
        this.settings = context.services().settings();
    }

    @FXML
    private void initialize() {
        setUpCartTable();

        productPopup = new SearchPopup<>(productSearch,
                text -> products.search(text, null, false, PRODUCT_RESULTS),
                BillingController::describeProduct,
                this::chooseProduct);
        customerPopup = new SearchPopup<>(customerSearch,
                text -> customers.search(text, false, false, CUSTOMER_RESULTS),
                c -> c.customer().name() + "   " + c.customer().customerNo()
                        + (c.customer().phone() == null ? "" : "   " + Format.phone(c.customer().phone()))
                        + (c.balance().isZero() ? "" : "   " + Format.balance(c.balance())),
                this::setCustomer);

        productSearch.textProperty().addListener((obs, oldText, newText) -> {
            if (chosenProduct != null && !describeProductShort(chosenProduct).equals(newText)) {
                chosenProduct = null;
                updateQtyUnit();
            }
        });
        qtyField.setOnAction(event -> addSelectedProduct());

        recentBillsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(BillSummary bill, boolean empty) {
                super.updateItem(bill, empty);
                setText(empty || bill == null ? null : "Bill " + bill.billNo() + "  ·  "
                        + Format.dateTimeShort(bill.createdAt()) + "  ·  " + Format.money(bill.total())
                        + (bill.cancelled() ? "  (cancelled)"
                                : bill.toAccount().isPositive() ? "  (" + Format.money(bill.toAccount()) + " on khata)" : ""));
            }
        });
        recentBillsList.setPlaceholder(new Label("No bills yet."));
        recentBillsList.setOnMouseClicked(event -> {
            BillSummary bill = recentBillsList.getSelectionModel().getSelectedItem();
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && bill != null) {
                showBill(bill.billNo());
            }
        });

        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        // When the screen is shown again, refresh the customer's balance and put the cursor in product search.
        root.parentProperty().addListener((obs, oldParent, newParent) -> {
            if (newParent != null) {
                reloadCustomer();
                Platform.runLater(productSearch::requestFocus);
            }
        });

        updateQtyUnit();
        refreshCart();
        showWalkIn();
    }

    // ------------------------------------------------------------------ cart table

    private void setUpCartTable() {
        column(lineNoColumn, line -> String.valueOf(cart.lines().indexOf(line) + 1));
        column(itemColumn, line -> line.displayName() + (line.nameMr() == null ? "" : "   " + line.nameMr())
                + (line.productId() == null ? "   (not in list)" : ""));
        column(qtyColumn, line -> line.quantity().toPlainString() + " " + line.unit().shortLabel());
        column(rateColumn, line -> Format.money(line.rate()) + (line.rateChanged() ? " *" : ""));
        column(mrpColumn, line -> Format.money(line.mrp()));
        column(amountColumn, line -> Format.money(line.lineTotal()));
        cartTable.setPlaceholder(new Label("No items yet. Type a product name above (F2)."));
        cartTable.setRowFactory(table -> {
            TableRow<CartLine> row = new TableRow<>() {
                @Override
                protected void updateItem(CartLine line, boolean empty) {
                    super.updateItem(line, empty);
                    pseudoClassStateChanged(RATE_CHANGED, !empty && line != null && line.rateChanged());
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    changeQuantity();
                }
            });
            return row;
        });
        cartTable.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) -> updateLineButtons());
    }

    private static void column(TableColumn<CartLine, String> column, Function<CartLine, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    /** Shows the cart lines and totals again after any change. */
    private void refreshCart() {
        int selected = cartTable.getSelectionModel().getSelectedIndex();
        cartTable.getItems().setAll(cart.lines());
        if (selected >= 0 && selected < cart.lines().size()) {
            cartTable.getSelectionModel().select(selected);
        }
        refreshTotals();
        updateLineButtons();
    }

    private void refreshTotals() {
        BillTotals totals = cart.totals();
        itemsLabel.setText(totals.lineCount() + (totals.lineCount() == 1 ? " item" : " items"));
        subtotalLabel.setText(Format.money(totals.subtotal()));
        roundOffLabel.setText(totals.roundOff().isNegative() ? "-" + Format.money(totals.roundOff().negate())
                : "+" + Format.money(totals.roundOff()));
        totalLabel.setText(Format.money(totals.total()));
        savingsLabel.setText(totals.savings().isPositive() ? "You saved " + Format.money(totals.savings()) + " on MRP" : "");

        boolean khata = customer != null;
        for (Label label : List.of(previousDuesTitle, previousDuesLabel, withDuesTitle, withDuesLabel)) {
            label.setVisible(khata);
            label.setManaged(khata);
        }
        if (khata) {
            Money previous = customer.balance();
            previousDuesTitle.setText(previous.isNegative() ? "Advance" : "Previous dues");
            previousDuesLabel.setText(previous.isNegative() ? "-" + Format.money(previous.negate()) : Format.money(previous));
            withDuesLabel.setText(Format.money(totals.total().plus(previous)));
        }
        saveButton.setDisable(cart.isEmpty());
    }

    private void updateLineButtons() {
        boolean none = cartTable.getSelectionModel().getSelectedIndex() < 0;
        qtyButton.setDisable(none);
        rateButton.setDisable(none);
        removeButton.setDisable(none);
    }

    // ------------------------------------------------------------------ adding items

    private static String describeProduct(Product p) {
        return p.displayName() + (p.nameMr() == null ? "" : "   " + p.nameMr()) + "   ·   "
                + Format.money(p.rate()) + " / " + p.unit().shortLabel()
                + (p.mrp() == null ? "" : "   (MRP " + Format.money(p.mrp()) + ")") + "   ·   " + p.code();
    }

    private static String describeProductShort(Product p) {
        return p.displayName();
    }

    private void chooseProduct(Product product) {
        chosenProduct = product;
        productPopup.setTextQuietly(describeProductShort(product));
        updateQtyUnit();
        entryLabel.getStyleClass().remove("error-text");
        entryLabel.setText(describeProduct(product));
        qtyField.setText("1");
        qtyField.requestFocus();
        qtyField.selectAll();
    }

    private void updateQtyUnit() {
        if (chosenProduct == null) {
            qtyUnitLabel.setText("");
            qtyField.setPromptText("Qty");
        } else {
            qtyUnitLabel.setText(chosenProduct.unit().shortLabel());
            qtyField.setPromptText(chosenProduct.unit().isLoose() ? "e.g. 0.250" : "e.g. 2");
        }
    }

    @FXML
    private void addSelectedProduct() {
        if (chosenProduct == null) {
            showEntryError("First choose a product from the list (type its name, then press Enter).");
            productSearch.requestFocus();
            return;
        }
        try {
            Quantity quantity = Quantity.parse(qtyField.getText());
            int index = cart.addProduct(chosenProduct, quantity);
            refreshCart();
            cartTable.getSelectionModel().select(index);
            cartTable.scrollTo(index);
            chosenProduct = null;
            productPopup.setTextQuietly("");
            qtyField.clear();
            updateQtyUnit();
            entryLabel.getStyleClass().remove("error-text");
            entryLabel.setText("Added. Type the next product.");
            productSearch.requestFocus();
        } catch (IllegalArgumentException e) {
            showEntryError(e.getMessage());
            qtyField.requestFocus();
            qtyField.selectAll();
        }
    }

    private void showEntryError(String message) {
        if (!entryLabel.getStyleClass().contains("error-text")) {
            entryLabel.getStyleClass().add("error-text");
        }
        entryLabel.setText(message);
    }

    @FXML
    private void addOneOff() {
        OneOffItemController.open(window(), context, cart).ifPresent(index -> {
            refreshCart();
            cartTable.getSelectionModel().select(index.intValue());
            cartTable.scrollTo(index.intValue());
            productSearch.requestFocus();
        });
    }

    // ------------------------------------------------------------------ changing lines

    @FXML
    private void changeQuantity() {
        int index = cartTable.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        CartLine line = cart.lines().get(index);
        Dialogs.askText(window(), "Change quantity", line.displayName() + "\nNew quantity (" + line.unit().shortLabel() + "):",
                line.quantity().toPlainString()).ifPresent(text -> {
                    try {
                        cart.setQuantity(index, Quantity.parse(text));
                        refreshCart();
                    } catch (IllegalArgumentException e) {
                        Dialogs.warning(window(), "Change quantity", e.getMessage());
                    }
                });
        cartTable.requestFocus();
    }

    @FXML
    private void changeRate() {
        int index = cartTable.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        CartLine line = cart.lines().get(index);
        String prompt = line.displayName() + "\nRate for this bill only (₹ per " + line.unit().shortLabel() + ")"
                + (line.productRate() == null ? "" : "\nNormal rate: " + Format.money(line.productRate())) + ":";
        Dialogs.askText(window(), "Change rate", prompt, line.rate().toPlainString()).ifPresent(text -> {
            try {
                cart.setRate(index, Money.parse(text.replace("₹", "").strip()));
                refreshCart();
            } catch (IllegalArgumentException e) {
                Dialogs.warning(window(), "Change rate", e.getMessage());
            }
        });
        cartTable.requestFocus();
    }

    @FXML
    private void removeLine() {
        int index = cartTable.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            cart.remove(index);
            refreshCart();
            if (!cart.isEmpty()) {
                cartTable.getSelectionModel().select(Math.min(index, cart.lines().size() - 1));
            }
        }
    }

    // ------------------------------------------------------------------ customer

    private void setCustomer(CustomerSummary chosen) {
        customer = chosen;
        customerPopup.setTextQuietly("");
        walkInBox.setVisible(false);
        walkInBox.setManaged(false);
        khataBox.setVisible(true);
        khataBox.setManaged(true);
        customerNameLabel.setText(chosen.customer().name());
        customerInfoLabel.setText(chosen.customer().customerNo()
                + (chosen.customer().phone() == null ? "" : "   ·   " + Format.phone(chosen.customer().phone())));
        customerDuesLabel.setText(Format.balance(chosen.balance()));
        customerDuesLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("dues"), chosen.balance().isPositive());
        customerDuesLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("advance"), chosen.balance().isNegative());
        recentBillsList.getItems().setAll(billing.recentBills(chosen.customer().id(), RECENT_BILLS));
        refreshTotals();
        productSearch.requestFocus();
    }

    /** Reloads the chosen customer's balance (it may have changed in the Customers screen). */
    private void reloadCustomer() {
        if (customer != null) {
            Optional<CustomerSummary> fresh = customers.find(customer.customer().id());
            if (fresh.isPresent() && fresh.get().customer().active()) {
                setCustomer(fresh.get());
            } else {
                showWalkIn();
            }
        }
    }

    @FXML
    private void useWalkIn() {
        showWalkIn();
        productSearch.requestFocus();
    }

    private void showWalkIn() {
        customer = null;
        khataBox.setVisible(false);
        khataBox.setManaged(false);
        walkInBox.setVisible(true);
        walkInBox.setManaged(true);
        refreshTotals();
    }

    @FXML
    private void newCustomer() {
        CustomerFormController.open(window(), context, null).ifPresent(this::setCustomer);
    }

    // ------------------------------------------------------------------ hold, clear, save

    @FXML
    private void holdBill() {
        if (cart.isEmpty()) {
            Dialogs.info(window(), "Hold bill", "There is nothing to hold. Add items first.");
            return;
        }
        HeldBill bill = new HeldBill(customer, walkInNameField.getText(), cart.copy(), LocalTime.now(context.clock()));
        held.add(bill);
        resetBill();
        showBanner("Bill put on hold: " + bill.describe() + ". Click \"Held bills\" to continue it.", false);
    }

    @FXML
    private void resumeHeld() {
        if (held.isEmpty()) {
            Dialogs.info(window(), "Held bills", "No bills are on hold.");
            return;
        }
        List<String> choices = held.stream().map(HeldBill::describe).toList();
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.getFirst(), choices);
        dialog.initOwner(window());
        dialog.setTitle("Held bills");
        dialog.setHeaderText(null);
        dialog.setContentText("Continue which bill?");
        dialog.showAndWait().ifPresent(choice -> {
            HeldBill chosen = held.remove(choices.indexOf(choice));
            if (!cart.isEmpty()) {
                // Do not lose the bill that is open now: put it on hold in exchange.
                held.add(new HeldBill(customer, walkInNameField.getText(), cart.copy(), LocalTime.now(context.clock())));
                showBanner("The open bill was put on hold.", false);
            }
            cart.replaceWith(chosen.cart());
            walkInNameField.setText(chosen.walkInName());
            if (chosen.customer() != null) {
                customers.find(chosen.customer().customer().id()).ifPresentOrElse(this::setCustomer, this::showWalkIn);
            } else {
                showWalkIn();
            }
            refreshCart();
            updateHeldButton();
            entryLabel.getStyleClass().remove("error-text");
            entryLabel.setText("Continuing the held bill. Type the next product.");
            productSearch.requestFocus();
        });
    }

    @FXML
    private void clearBill() {
        if (!cart.isEmpty() && !Dialogs.confirm(window(), "Clear bill",
                "Remove all " + cart.lines().size() + " items from this bill?", "Clear bill")) {
            return;
        }
        resetBill();
    }

    @FXML
    private void saveAndPay() {
        if (cart.isEmpty()) {
            Dialogs.info(window(), "Save bill", "The bill has no items yet.");
            return;
        }
        BillPaymentController.open(window(), context, customer, walkInNameField.getText(), cart).ifPresent(result -> {
            resetBill();
            showBanner(result.summary(), true);
            printAfterSave(result.bill().billNo(), result.summary());
        });
    }

    /** Prints the new bill, asks first, or does nothing, as chosen in Settings. */
    private void printAfterSave(long billNo, String summary) {
        PrintAfterSave choice = settings.printerSetup().afterSave();
        boolean print = switch (choice) {
            case ALWAYS -> true;
            case NEVER -> false;
            case ASK -> Dialogs.question(window(), "Print bill", "Bill " + billNo + " is saved.\n\nPrint it now?",
                    "Print", "Don't print");
        };
        productSearch.requestFocus();
        if (!print) {
            return;
        }
        showBanner(summary + "  ·  Printing...", true);
        Background.run("print-bill-" + billNo, () -> {
            printing.printBill(billNo, false);
            return null;
        }, done -> showBanner(summary + "  ·  Printed", true), error -> {
            showBanner("Bill " + billNo + " is saved but was NOT printed. Use \"Reprint bill\" to print it.", false);
            ErrorHandler.handle(error);
        });
    }

    /** Asks for a bill number (the newest bill is filled in) and shows that bill with a Print button. */
    @FXML
    private void reprintBill() {
        Optional<Long> last = billing.lastBillNo();
        if (last.isEmpty()) {
            Dialogs.info(window(), "Reprint bill", "No bills have been saved yet.");
            return;
        }
        Dialogs.askText(window(), "Reprint bill", "Bill number:", String.valueOf(last.get())).ifPresent(text -> {
            try {
                showBill(Long.parseLong(text.strip()));
            } catch (NumberFormatException e) {
                Dialogs.warning(window(), "Reprint bill", "Please type a bill number, for example "
                        + last.get() + ".");
            }
        });
        productSearch.requestFocus();
    }

    private void showBill(long billNo) {
        try {
            ReceiptPreviewController.openBill(window(), context, billNo);
        } catch (BusinessRuleException e) {
            Dialogs.warning(window(), "Reprint bill", e.getMessage());
        }
    }

    /** Empties the bill and goes back to a walk-in customer, ready for the next customer. */
    private void resetBill() {
        cart.clear();
        chosenProduct = null;
        productPopup.setTextQuietly("");
        customerPopup.setTextQuietly("");
        qtyField.clear();
        walkInNameField.clear();
        updateQtyUnit();
        entryLabel.getStyleClass().remove("error-text");
        entryLabel.setText("Search a product, press Enter, type the quantity, press Enter again.");
        showWalkIn();
        refreshCart();
        updateHeldButton();
        productSearch.requestFocus();
    }

    private void updateHeldButton() {
        heldButton.setText("Held bills (" + held.size() + ")");
        heldButton.pseudoClassStateChanged(PseudoClass.getPseudoClass("has-held"), !held.isEmpty());
    }

    /** The message at the top: green for good news, amber when something needs attention. */
    private void showBanner(String text, boolean success) {
        bannerLabel.setText(text);
        bannerLabel.setVisible(true);
        bannerLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("success"), success);
        bannerLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("warning"), !success);
    }

    /**
     * Describes unsaved work, for the "close the app?" question.
     *
     * @return empty if there is nothing unsaved
     */
    public Optional<String> unsavedWork() {
        List<String> parts = new ArrayList<>();
        if (!cart.isEmpty()) {
            parts.add("the open bill (" + cart.lines().size() + (cart.lines().size() == 1 ? " item)" : " items)"));
        }
        if (!held.isEmpty()) {
            parts.add(held.size() + (held.size() == 1 ? " held bill" : " held bills"));
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(" and ", parts));
    }

    // ------------------------------------------------------------------ keyboard

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        switch (code) {
            case F2 -> {
                productSearch.requestFocus();
                productSearch.selectAll();
            }
            case F3 -> {
                customerSearch.requestFocus();
                customerSearch.selectAll();
            }
            case F4 -> addOneOff();
            case F8 -> holdBill();
            case F12 -> saveAndPay();
            case DELETE -> {
                if (cartTable.isFocused()) {
                    removeLine();
                } else {
                    return;
                }
            }
            case ENTER -> {
                if (cartTable.isFocused()) {
                    changeQuantity();
                } else {
                    return;
                }
            }
            case DOWN -> {
                if (productSearch.isFocused() && !productPopup.isShowing() && productSearch.getText().isBlank()
                        && !cart.isEmpty()) {
                    cartTable.requestFocus();
                    if (cartTable.getSelectionModel().isEmpty()) {
                        cartTable.getSelectionModel().selectLast();
                    }
                } else {
                    return;
                }
            }
            default -> {
                return;
            }
        }
        event.consume();
    }

    private Window window() {
        return root.getScene().getWindow();
    }
}
