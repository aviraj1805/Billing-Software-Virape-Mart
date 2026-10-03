package com.virpemart.billing.ui.billing;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.BillCorrection;
import com.virpemart.billing.model.BillSearch;
import com.virpemart.billing.model.BillSummary;
import com.virpemart.billing.model.User;
import com.virpemart.billing.service.BillingService;
import com.virpemart.billing.service.BusinessRuleException;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Views;
import com.virpemart.billing.ui.history.CancelBillController;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The "Recent bills" window on the Billing screen (F9): the newest bills, newest first, with the last bill already
 * selected, so F9 then Enter shows the last bill ready to print. From here a bill can be seen and printed, corrected
 * or cancelled (owner). The search box finds older bills by number, name or phone.
 */
public class RecentBillsController {

    /** How many of the newest bills are listed (the user's choice). */
    static final int RECENT = 20;
    private static final PseudoClass CANCELLED = PseudoClass.getPseudoClass("cancelled");

    private final AppContext context;
    private final BillingService billing;
    private final boolean owner;

    @FXML
    private VBox root;
    @FXML
    private TextField searchField;
    @FXML
    private Label messageLabel;
    @FXML
    private TableView<BillSummary> table;
    @FXML
    private TableColumn<BillSummary, String> billNoColumn;
    @FXML
    private TableColumn<BillSummary, String> dateColumn;
    @FXML
    private TableColumn<BillSummary, String> customerColumn;
    @FXML
    private TableColumn<BillSummary, String> itemsColumn;
    @FXML
    private TableColumn<BillSummary, String> totalColumn;
    @FXML
    private TableColumn<BillSummary, String> statusColumn;
    @FXML
    private Button viewButton;
    @FXML
    private Button correctButton;
    @FXML
    private Button cancelBillButton;

    private Stage stage;
    private BillCorrection correction;

    public RecentBillsController(AppContext context) {
        this.context = context;
        this.billing = context.services().billing();
        this.owner = context.session().currentUser().map(User::isOwner).orElse(false);
    }

    /**
     * Opens the window and waits.
     *
     * @return the customer and items of a bill the owner chose to correct (already cancelled), or empty
     */
    public static Optional<BillCorrection> open(Window owner, AppContext context) {
        Views.Loaded<RecentBillsController> view = Views.load("recent-bills.fxml", context);
        RecentBillsController recent = view.controller();
        recent.stage = Views.dialog(owner, "Recent bills", view.root());
        recent.stage.setOnShown(event -> {
            recent.table.requestFocus();
            recent.table.getSelectionModel().selectFirst();
        });
        recent.stage.showAndWait();
        return Optional.ofNullable(recent.correction);
    }

    @FXML
    private void initialize() {
        column(billNoColumn, bill -> String.valueOf(bill.billNo()));
        column(dateColumn, bill -> Format.dateTimeShort(bill.createdAt()));
        column(customerColumn, BillSummary::customerText);
        column(itemsColumn, bill -> String.valueOf(bill.lineCount()));
        column(totalColumn, bill -> Format.money(bill.total()));
        column(statusColumn, bill -> bill.cancelled() ? "CANCELLED" : "");
        table.setPlaceholder(new Label("No bills found."));
        table.setRowFactory(tableView -> {
            TableRow<BillSummary> row = new TableRow<>() {
                @Override
                protected void updateItem(BillSummary bill, boolean empty) {
                    super.updateItem(bill, empty);
                    pseudoClassStateChanged(CANCELLED, !empty && bill != null && bill.cancelled());
                    setTooltip(empty || bill == null ? null : new Tooltip("Bill " + bill.billNo() + "  ·  "
                            + Format.dateTime(bill.createdAt()) + "\n" + bill.customerText()
                            + (bill.cancelled() ? "\nCancelled" : "")));
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    viewSelected();
                }
            });
            return row;
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldBill, newBill) -> updateButtons());
        searchField.textProperty().addListener((obs, oldText, newText) -> reload());
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        // Typing while the list has the cursor goes into the search box, so a bill number can be typed straight away.
        table.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String typed = event.getCharacter();
            if (!typed.isEmpty() && Character.isLetterOrDigit(typed.charAt(0))) {
                searchField.requestFocus();
                searchField.appendText(typed);
                searchField.end();
                event.consume();
            }
        });

        cancelBillButton.setVisible(owner);
        cancelBillButton.setManaged(owner);
        correctButton.setVisible(owner);
        correctButton.setManaged(owner);
        reload();
    }

    /** The newest bills, or the bills matching the search box. */
    private void reload() {
        String text = searchField.getText();
        boolean searching = text != null && !text.isBlank();
        List<BillSummary> bills = billing.searchBills(new BillSearch(null, null, text, null, RECENT));
        BillSummary selected = table.getSelectionModel().getSelectedItem();
        table.getItems().setAll(bills);
        bills.stream().filter(b -> selected != null && b.id() == selected.id()).findFirst().ifPresentOrElse(
                b -> table.getSelectionModel().select(b), () -> table.getSelectionModel().selectFirst());
        if (searching) {
            messageLabel.setText(bills.isEmpty() ? "No bill matches \"" + text.strip() + "\"."
                    : "Bills matching \"" + text.strip() + "\", newest first. Clear the box to see the newest bills.");
        } else {
            messageLabel.setText("The newest " + RECENT + " bills, newest first. Use the arrow keys to choose a bill. "
                    + "Older bills: type a bill number above, or see History & Reports.");
        }
        updateButtons();
    }

    private void updateButtons() {
        BillSummary bill = table.getSelectionModel().getSelectedItem();
        viewButton.setDisable(bill == null);
        correctButton.setDisable(bill == null || bill.cancelled());
        cancelBillButton.setDisable(bill == null || bill.cancelled());
    }

    // ------------------------------------------------------------------ actions

    @FXML
    private void viewSelected() {
        BillSummary bill = table.getSelectionModel().getSelectedItem();
        if (bill == null) {
            return;
        }
        try {
            ReceiptPreviewController.openBill(stage, context, bill.billNo());
        } catch (BusinessRuleException e) {
            Dialogs.warning(stage, "Bill " + bill.billNo(), e.getMessage());
        }
        table.requestFocus();
    }

    /** Cancels the bill for correction; its customer and items then open on the Billing screen. */
    @FXML
    private void correctSelected() {
        BillSummary bill = table.getSelectionModel().getSelectedItem();
        if (bill == null || bill.cancelled()) {
            return;
        }
        try {
            Optional<BillCorrection> done = CancelBillController.openCorrection(stage, context, bill.billNo());
            if (done.isPresent()) {
                correction = done.get();
                stage.close();
            }
        } catch (BusinessRuleException e) {
            Dialogs.warning(stage, "Correct bill", e.getMessage());
            reload();
        }
    }

    @FXML
    private void cancelSelected() {
        BillSummary bill = table.getSelectionModel().getSelectedItem();
        if (bill == null || bill.cancelled()) {
            return;
        }
        try {
            CancelBillController.open(stage, context, bill.billNo()).ifPresent(done -> {
                reload();
                Dialogs.info(stage, "Bill cancelled", CancelBillController.whatToDoNow(done));
            });
        } catch (BusinessRuleException e) {
            Dialogs.warning(stage, "Cancel bill", e.getMessage());
            reload();
        }
        table.requestFocus();
    }

    @FXML
    private void close() {
        stage.close();
    }

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.ENTER && (table.isFocused() || searchField.isFocused())) {
            viewSelected();
        } else if (code == KeyCode.DOWN && searchField.isFocused()) {
            table.requestFocus();
        } else if (code == KeyCode.F && event.isShortcutDown()) {
            searchField.requestFocus();
            searchField.selectAll();
        } else {
            return;
        }
        event.consume();
    }

    private static void column(TableColumn<BillSummary, String> column, Function<BillSummary, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }
}
