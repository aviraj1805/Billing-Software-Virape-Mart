package com.virpemart.billing.ui.billing;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.BillCorrection;
import com.virpemart.billing.model.BillSummary;
import com.virpemart.billing.model.BillTotals;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.CartLine;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.Jama;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PrintAfterSave;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.model.Unit;
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
import javafx.scene.control.TableCell;
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
 * <p>Fast keyboard flow: type a product (F2), Enter, type the quantity, Enter, then the rate (already filled in with
 * the product's rate; type another rate for this bill only), Enter to add. Repeat. F12 to save and pay.
 * F9 opens the newest bills to see, print, correct or cancel.
 *
 * <p>Products show their Marathi name only, and quantities show without a unit, loose items with three decimals
 * ("0.250"; the user's choices, version 1.4.0).
 * To change a line, double-click it (or Enter): it goes up into the boxes at the top and its row stays empty until it
 * is put back into the same row with Enter; Esc puts it back unchanged.
 * The cart and all arithmetic live in {@link Cart}; saving happens in {@link BillingService}.
 */
public class BillingController {

    private static final int PRODUCT_RESULTS = 30;
    /** Stands for the built-in "जमा" item in the product search list. It is never saved as a product. */
    private static final Product JAMA_CHOICE =
            new Product(0, "", Jama.NAME, null, null, Unit.PCS, null, Money.ZERO, null, true);
    private static final int CUSTOMER_RESULTS = 20;
    private static final int RECENT_BILLS = 5;
    private static final PseudoClass RATE_CHANGED = PseudoClass.getPseudoClass("rate-changed");
    private static final PseudoClass BEING_CHANGED = PseudoClass.getPseudoClass("being-changed");
    /** The empty row shown in place of a line that is being changed at the top. Compared by identity (==). */
    private static final CartLine EMPTY_ROW =
            new CartLine(null, "", null, Unit.PCS, null, Quantity.ONE, Money.ZERO, null, null);
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
    /** While a line is being changed at the top: its position on the bill (from 0) and the line as it was. */
    private Integer changingIndex;
    private CartLine changingLine;
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
    private TextField rateField;
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
                text -> withJama(text, products.search(text, false, PRODUCT_RESULTS)),
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
                updateEntryBoxes();
            }
        });
        qtyField.setOnAction(event -> moveToRate());
        rateField.setOnAction(event -> addSelectedProduct());

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

        updateEntryBoxes();
        refreshCart();
        showWalkIn();
    }

    // ------------------------------------------------------------------ cart table

    private void setUpCartTable() {
        // The line number is the row's position, so the empty row of a line being changed keeps its number too.
        lineNoColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(""));
        lineNoColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });
        // Marathi name only, as on the printed bill. The जमा line and the empty row show no quantity, rate or amount.
        column(itemColumn, line -> line == EMPTY_ROW ? "" : line.isJama() ? line.name()
                : line.nameOnBill() + (line.productId() == null ? "   (not in list)" : ""));
        column(qtyColumn, line -> hasNoNumbers(line) ? "" : line.quantity().toBillString(line.unit()));
        column(rateColumn, line -> hasNoNumbers(line) ? "" : Format.money(line.rate()) + (line.rateChanged() ? " *" : ""));
        column(amountColumn, line -> hasNoNumbers(line) ? "" : Format.money(line.lineTotal()));
        cartTable.setPlaceholder(new Label("No items yet. Type a product name above (F2)."));
        cartTable.setRowFactory(table -> {
            TableRow<CartLine> row = new TableRow<>() {
                @Override
                protected void updateItem(CartLine line, boolean empty) {
                    super.updateItem(line, empty);
                    pseudoClassStateChanged(RATE_CHANGED, !empty && line != null && line.rateChanged());
                    pseudoClassStateChanged(BEING_CHANGED, !empty && line == EMPTY_ROW);
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    changeItem();
                }
            });
            return row;
        });
        cartTable.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) -> updateLineButtons());
    }

    private static void column(TableColumn<CartLine, String> column, Function<CartLine, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    private static boolean hasNoNumbers(CartLine line) {
        return line == EMPTY_ROW || line.isJama();
    }

    /**
     * Shows the cart lines and totals again after any change. A line being changed at the top is shown as an empty
     * row in its place, so every row keeps its position (row N of the table is line N of the bill once it is back).
     */
    private void refreshCart() {
        int selected = cartTable.getSelectionModel().getSelectedIndex();
        List<CartLine> rows = new ArrayList<>(cart.lines());
        if (changingIndex != null) {
            rows.add(changingIndex, EMPTY_ROW);
        }
        cartTable.getItems().setAll(rows);
        if (selected >= 0 && selected < rows.size() && rows.get(selected) != EMPTY_ROW) {
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
        boolean none = cartTable.getSelectionModel().getSelectedIndex() < 0
                || cartTable.getSelectionModel().getSelectedItem() == EMPTY_ROW;
        qtyButton.setDisable(none);
        rateButton.setDisable(none);
        removeButton.setDisable(none);
    }

    // ------------------------------------------------------------------ adding items

    /**
     * Adds the built-in "जमा" item to the search results: first when the whole word is typed ("जमा" or "jama"),
     * last when only its start is typed ("ज", "jam"), so it never pushes a real product down while typing.
     */
    private static List<Product> withJama(String text, List<Product> found) {
        if (Jama.matchesFully(text)) {
            List<Product> withJama = new ArrayList<>(found.size() + 1);
            withJama.add(JAMA_CHOICE);
            withJama.addAll(found);
            return withJama;
        }
        if (Jama.matchesStart(text)) {
            List<Product> withJama = new ArrayList<>(found);
            withJama.add(JAMA_CHOICE);
            return withJama;
        }
        return found;
    }

    /**
     * One row of the search list: the Marathi name and the rate only, for example "साखर   ·   ₹44" (the user's
     * choice, version 1.4.0). The search itself still finds English names and codes; they are just not shown.
     */
    private static String describeProduct(Product p) {
        if (p == JAMA_CHOICE) {
            return Jama.NAME + "   ·   money paid, no items (rate 0)";
        }
        return p.nameOnBill() + "   ·   " + Format.money(p.rate());
    }

    /** What the search box shows once a product is chosen. */
    private static String describeProductShort(Product p) {
        return p.nameOnBill();
    }

    private void chooseProduct(Product product) {
        if (product == JAMA_CHOICE) {
            if (changingIndex != null) {
                showEntryError("Finish changing item " + (changingIndex + 1) + " first: Enter puts it back, Esc"
                        + " keeps it as it was.");
                return;
            }
            addJama();
            return;
        }
        chosenProduct = product;
        productPopup.setTextQuietly(describeProductShort(product));
        updateEntryBoxes();
        showEntryInfo(changingPrefix() + describeProduct(product));
        qtyField.setText("1");
        rateField.setText(product.rate().toInputString());
        qtyField.requestFocus();
        qtyField.selectAll();
    }

    /**
     * The hint inside the quantity box tells whether decimals are allowed; no unit is shown (the user's choice,
     * version 1.4.0). With no product chosen, the quantity and rate boxes are empty.
     */
    private void updateEntryBoxes() {
        if (chosenProduct == null) {
            qtyField.setPromptText("Qty");
            rateField.clear();
        } else {
            qtyField.setPromptText(chosenProduct.unit().isLoose() ? "e.g. 0.250" : "e.g. 2");
        }
    }

    /** Enter in the quantity box: checks the quantity, then goes to the rate box (already filled in). */
    private void moveToRate() {
        if (chosenProduct == null) {
            showEntryError("First choose a product from the list (type its name, then press Enter).");
            productSearch.requestFocus();
            return;
        }
        if (readQuantity() != null) {
            rateField.requestFocus();
            rateField.selectAll();
        }
    }

    /**
     * Adds the chosen product with the quantity and rate typed next to it (Enter in the rate box, or "Add").
     * A rate different from the product's rate is for this bill only; the product's saved rate is not changed.
     */
    @FXML
    private void addSelectedProduct() {
        if (chosenProduct == null) {
            showEntryError("First choose a product from the list (type its name, then press Enter).");
            productSearch.requestFocus();
            return;
        }
        Quantity quantity = readQuantity();
        if (quantity == null) {
            return;
        }
        Money rate;
        Integer changed = changingIndex;
        try {
            rate = Money.parse(rateField.getText().replace("₹", "").strip());
            int index;
            if (changed != null) {
                // A changed line goes back into its own row, never into another row of the same product.
                index = cart.insertProduct(changed, chosenProduct, quantity, rate);
                changingIndex = null;
                changingLine = null;
            } else {
                index = cart.addProduct(chosenProduct, quantity, rate);
            }
            refreshCart();
            cartTable.getSelectionModel().select(index);
            cartTable.scrollTo(index);
        } catch (IllegalArgumentException e) {
            showEntryError(rateField.getText().isBlank() ? "Please type the rate." : e.getMessage());
            rateField.requestFocus();
            rateField.selectAll();
            return;
        }
        Money normalRate = chosenProduct.rate();
        chosenProduct = null;
        productPopup.setTextQuietly("");
        qtyField.clear();
        updateEntryBoxes();
        String done = changed != null ? "Item " + (changed + 1) + " changed" : "Added";
        showEntryInfo(rate.equals(normalRate) ? done + ". Type the next product."
                : done + " at " + Format.money(rate) + " for this bill only (normal rate " + Format.money(normalRate)
                        + "). Type the next product.");
        productSearch.requestFocus();
    }

    /** The quantity typed for the chosen product, or null after showing what is wrong with it. */
    private Quantity readQuantity() {
        try {
            Quantity quantity = Quantity.parse(qtyField.getText());
            Cart.checkQuantity(chosenProduct.unit(), quantity);
            return quantity;
        } catch (IllegalArgumentException e) {
            showEntryError(e.getMessage());
            qtyField.requestFocus();
            qtyField.selectAll();
            return null;
        }
    }

    private void showEntryError(String message) {
        if (!entryLabel.getStyleClass().contains("error-text")) {
            entryLabel.getStyleClass().add("error-text");
        }
        entryLabel.setText(message);
    }

    private void showEntryInfo(String message) {
        entryLabel.getStyleClass().remove("error-text");
        entryLabel.setText(message);
    }

    /** "Changing item 3: " while a line is being changed at the top, otherwise nothing. */
    private String changingPrefix() {
        return changingIndex == null ? "" : "Changing item " + (changingIndex + 1) + ": ";
    }

    /**
     * Adds "जमा" to the bill at once: it has no quantity or rate to type. The money paid is typed in the payment
     * window when the bill is saved.
     */
    private void addJama() {
        int index = cart.addJama();
        refreshCart();
        cartTable.getSelectionModel().select(index);
        cartTable.scrollTo(index);
        chosenProduct = null;
        productPopup.setTextQuietly("");
        qtyField.clear();
        updateEntryBoxes();
        showEntryInfo(Jama.NAME + " added. Press Save (F12) and type the money paid.");
        productSearch.requestFocus();
    }

    /** True (after telling the user) when the line is जमा, which has no quantity or rate to change. */
    private boolean isJamaLine(CartLine line, String title) {
        if (line.isJama()) {
            Dialogs.warning(window(), title, Jama.NAME + " has no quantity or rate. Type the money paid in the"
                    + " payment window (Save, F12).");
            cartTable.requestFocus();
            return true;
        }
        return false;
    }

    @FXML
    private void addOneOff() {
        putBackUnchanged();
        OneOffItemController.open(window(), context, cart).ifPresent(index -> {
            refreshCart();
            cartTable.getSelectionModel().select(index.intValue());
            cartTable.scrollTo(index.intValue());
            productSearch.requestFocus();
        });
    }

    // ------------------------------------------------------------------ changing lines

    /**
     * Double-click, Enter or "Change item": the selected line goes up into the boxes at the top (product, Qty, Rate)
     * to be changed with the usual keys, and its row stays empty in its place (the user's choice, version 1.4.0).
     * Enter in the Rate box puts it back into the same row; Esc puts it back unchanged. An item not in the product
     * list opens its own window instead, filled in, because the boxes at the top only hold products from the list.
     */
    @FXML
    private void changeItem() {
        int index = cartTable.getSelectionModel().getSelectedIndex();
        if (index < 0 || cartTable.getSelectionModel().getSelectedItem() == EMPTY_ROW) {
            return;
        }
        // Another line still at the top goes back first. Its empty row was in the table, so the row number chosen
        // is now exactly the position of the line on the bill.
        putBackUnchanged();
        CartLine line = cart.lines().get(index);
        if (isJamaLine(line, "Change item")) {
            return;
        }
        if (line.productId() == null) {
            changeOneOff(index);
            return;
        }
        Optional<Product> product = products.find(line.productId());
        if (product.isEmpty()) {
            Dialogs.warning(window(), "Change item", "\"" + line.nameOnBill() + "\" is no longer in the product list."
                    + " Remove it from the bill and add it again.");
            cartTable.requestFocus();
            return;
        }
        changingLine = cart.remove(index);
        changingIndex = index;
        chosenProduct = product.get();
        productPopup.setTextQuietly(describeProductShort(chosenProduct));
        updateEntryBoxes();
        qtyField.setText(line.quantity().toBillString(line.unit()));
        rateField.setText(line.rate().toInputString());
        refreshCart();
        cartTable.getSelectionModel().clearSelection();
        cartTable.scrollTo(index);
        showEntryInfo("Changing item " + (index + 1) + ". Change the qty or rate (F2 for another product), Enter to"
                + " put it back. Esc keeps it as it was.");
        qtyField.requestFocus();
        qtyField.selectAll();
    }

    /**
     * Puts the line that is being changed at the top back into its row exactly as it was. Called by Esc and before
     * anything else is done with the bill, so a line is never lost. Does nothing when no line is being changed.
     */
    private void putBackUnchanged() {
        if (changingIndex == null) {
            return;
        }
        int index = changingIndex;
        cart.insert(index, changingLine);
        changingIndex = null;
        changingLine = null;
        chosenProduct = null;
        productPopup.setTextQuietly("");
        qtyField.clear();
        updateEntryBoxes();
        refreshCart();
        cartTable.getSelectionModel().select(index);
        showEntryInfo("Item " + (index + 1) + " kept as it was. Type the next product.");
    }

    /** Changes an item that is not in the product list in its own window, filled in; it stays in its row. */
    private void changeOneOff(int index) {
        OneOffItemController.openToChange(window(), context, cart, index).ifPresent(changed -> {
            refreshCart();
            cartTable.getSelectionModel().select(changed.intValue());
            showEntryInfo("Item " + (changed + 1) + " changed. Type the next product.");
        });
        cartTable.requestFocus();
    }

    @FXML
    private void changeRate() {
        int index = cartTable.getSelectionModel().getSelectedIndex();
        if (index < 0 || cartTable.getSelectionModel().getSelectedItem() == EMPTY_ROW) {
            return;
        }
        putBackUnchanged();
        CartLine line = cart.lines().get(index);
        if (isJamaLine(line, "Change rate")) {
            return;
        }
        String prompt = line.nameOnBill() + "\nRate for this bill only (₹)"
                + (line.productRate() == null ? "" : "\nNormal rate: " + Format.money(line.productRate())) + ":";
        Dialogs.askText(window(), "Change rate", prompt, line.rate().toInputString()).ifPresent(text -> {
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
        if (index >= 0 && cartTable.getSelectionModel().getSelectedItem() != EMPTY_ROW) {
            putBackUnchanged();
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
        putBackUnchanged();
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
        putBackUnchanged();
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
            showEntryInfo("Continuing the held bill. Type the next product.");
            productSearch.requestFocus();
        });
    }

    @FXML
    private void clearBill() {
        putBackUnchanged();
        if (!cart.isEmpty() && !Dialogs.confirm(window(), "Clear bill",
                "Remove all " + cart.lines().size() + " items from this bill?", "Clear bill")) {
            return;
        }
        resetBill();
    }

    @FXML
    private void saveAndPay() {
        if (changingIndex != null) {
            showEntryError("Item " + (changingIndex + 1) + " is being changed. Press Enter to put it back, or Esc to"
                    + " keep it as it was. Then save.");
            if (chosenProduct == null) {
                productSearch.requestFocus();
            } else {
                qtyField.requestFocus();
            }
            return;
        }
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
            showBanner("Bill " + billNo + " is saved but was NOT printed. Use \"Recent bills\" (F9) to print it.",
                    false);
            ErrorHandler.handle(error);
        });
    }

    /**
     * Opens the Recent bills window (F9): see and print, correct or cancel one of the newest bills. A bill chosen for
     * correction is already cancelled; its customer and items are loaded here to be changed and saved again.
     */
    @FXML
    private void recentBills() {
        putBackUnchanged();
        if (billing.lastBillNo().isEmpty()) {
            Dialogs.info(window(), "Recent bills", "No bills have been saved yet.");
            return;
        }
        RecentBillsController.open(window(), context).ifPresent(this::loadCorrection);
        reloadCustomer();
        productSearch.requestFocus();
    }

    /**
     * Puts a cancelled bill's customer and items on the screen to make the corrected bill. A bill that was open is
     * put on hold first, so nothing is lost.
     */
    private void loadCorrection(BillCorrection correction) {
        long oldBillNo = correction.cancelled().billNo();
        List<String> notes = new ArrayList<>();
        if (!cart.isEmpty()) {
            held.add(new HeldBill(customer, walkInNameField.getText(), cart.copy(), LocalTime.now(context.clock())));
            notes.add("The bill that was open is on hold.");
        }
        cart.replaceWith(Cart.of(correction.lines()));
        walkInNameField.setText(correction.walkInName() == null ? "" : correction.walkInName());
        showWalkIn();
        if (correction.customerId() != null) {
            Optional<CustomerSummary> khataCustomer = customers.find(correction.customerId());
            if (khataCustomer.isPresent() && khataCustomer.get().customer().active()) {
                setCustomer(khataCustomer.get());
            } else {
                notes.add("The khata customer of bill " + oldBillNo
                        + " is switched off: switch them on in Customers, or save this as a walk-in bill.");
            }
        }
        refreshCart();
        updateHeldButton();
        showEntryInfo("Correcting bill " + oldBillNo + ". Change the items, then save (F12).");
        notes.addFirst("Bill " + oldBillNo + " is cancelled. Its items are on the screen: change what is wrong, then"
                + " Save and pay (F12). Enter the payment again; the customer does not pay twice.");
        showBanner(String.join("  ", notes), false);
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
        changingIndex = null;
        changingLine = null;
        chosenProduct = null;
        productPopup.setTextQuietly("");
        customerPopup.setTextQuietly("");
        qtyField.clear();
        walkInNameField.clear();
        updateEntryBoxes();
        showEntryInfo("Search a product and press Enter. Type the quantity, Enter. Change the rate if needed, Enter to add.");
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
        int items = cart.lines().size() + (changingIndex == null ? 0 : 1);
        if (items > 0) {
            parts.add("the open bill (" + items + (items == 1 ? " item)" : " items)"));
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
            case F9 -> recentBills();
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
                    changeItem();
                } else {
                    return;
                }
            }
            case ESCAPE -> {
                // With a search list open, Esc only closes the list (SearchPopup handles it).
                if (changingIndex != null && !productPopup.isShowing() && !customerPopup.isShowing()) {
                    putBackUnchanged();
                    productSearch.requestFocus();
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
