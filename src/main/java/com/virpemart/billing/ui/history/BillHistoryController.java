package com.virpemart.billing.ui.history;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.BillSearch;
import com.virpemart.billing.model.BillSummary;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.User;
import com.virpemart.billing.service.BillingService;
import com.virpemart.billing.service.BusinessRuleException;
import com.virpemart.billing.service.ValidationException;
import com.virpemart.billing.ui.billing.ReceiptPreviewController;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Views;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
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
 * The Bill History screen: find saved bills by date, bill number or customer, see and reprint them, and (owner only)
 * cancel them. The same screen opens in a window to show one khata customer's bills (their purchase history).
 */
public class BillHistoryController {

    /** More rows than this would make the table slow and hard to read. */
    private static final int LIMIT = 500;
    private static final PseudoClass CANCELLED = PseudoClass.getPseudoClass("cancelled");

    private final AppContext context;
    private final BillingService billing;
    private final boolean owner;

    @FXML
    private VBox root;
    @FXML
    private Label titleLabel;
    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
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
    private TableColumn<BillSummary, String> paidColumn;
    @FXML
    private TableColumn<BillSummary, String> khataColumn;
    @FXML
    private TableColumn<BillSummary, String> statusColumn;
    @FXML
    private Label countLabel;
    @FXML
    private Button viewButton;
    @FXML
    private Button cancelBillButton;

    /** Set when the screen shows one customer's bills only. */
    private Long customerId;
    /** True while dates are changed in code, so the list is loaded once instead of twice. */
    private boolean changingDates;

    public BillHistoryController(AppContext context) {
        this.context = context;
        this.billing = context.services().billing();
        this.owner = context.session().currentUser().map(User::isOwner).orElse(false);
    }

    /** Opens a window with all bills of one khata customer, newest first. */
    public static void openForCustomer(Window owner, AppContext context, CustomerSummary customer) {
        Views.Loaded<BillHistoryController> view = Views.load("bill-history.fxml", context);
        BillHistoryController history = view.controller();
        history.customerId = customer.customer().id();
        history.titleLabel.setText("Bills of " + customer.customer().name() + " (" + customer.customer().customerNo()
                + ")");
        history.setDates(null, null);
        Stage stage = Views.dialog(owner, "Bills of " + customer.customer().name(), view.root());
        stage.setWidth(Math.min(1100, owner.getWidth() - 40));
        stage.setHeight(Math.min(700, owner.getHeight() - 40));
        stage.setOnShown(event -> {
            history.table.requestFocus();
            history.table.getSelectionModel().selectFirst();
        });
        stage.showAndWait();
    }

    @FXML
    private void initialize() {
        fromPicker.setConverter(Format.dateInput());
        toPicker.setConverter(Format.dateInput());
        fromPicker.valueProperty().addListener((obs, oldDate, newDate) -> reloadUnlessChangingDates());
        toPicker.valueProperty().addListener((obs, oldDate, newDate) -> reloadUnlessChangingDates());
        searchField.textProperty().addListener((obs, oldText, newText) -> reload());

        column(billNoColumn, bill -> String.valueOf(bill.billNo()));
        column(dateColumn, bill -> Format.dateTimeShort(bill.createdAt()));
        column(customerColumn, BillSummary::customerText);
        column(itemsColumn, bill -> String.valueOf(bill.lineCount()));
        column(totalColumn, bill -> Format.money(bill.total()));
        column(paidColumn, bill -> Format.money(bill.paid()));
        column(khataColumn, bill -> bill.toAccount().isZero() ? "" : Format.money(bill.toAccount()));
        column(statusColumn, bill -> bill.cancelled() ? "Cancelled" : "");
        table.setPlaceholder(new Label("No bills found. Try other dates, or clear the search."));
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
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);

        cancelBillButton.setVisible(owner);
        cancelBillButton.setManaged(owner);

        showToday();
    }

    /** Loads the list again and puts the cursor in the search box. Called each time the Bills tab is opened. */
    public void refresh() {
        reload();
        Platform.runLater(searchField::requestFocus);
    }

    // ------------------------------------------------------------------ dates

    @FXML
    private void showToday() {
        LocalDate today = today();
        setDates(today, today);
    }

    @FXML
    private void showYesterday() {
        LocalDate yesterday = today().minusDays(1);
        setDates(yesterday, yesterday);
    }

    @FXML
    private void showThisMonth() {
        LocalDate today = today();
        setDates(today.withDayOfMonth(1), today);
    }

    @FXML
    private void showAllDates() {
        setDates(null, null);
    }

    private LocalDate today() {
        return LocalDate.now(context.clock());
    }

    private void setDates(LocalDate from, LocalDate to) {
        changingDates = true;
        try {
            fromPicker.setValue(from);
            toPicker.setValue(to);
        } finally {
            changingDates = false;
        }
        reload();
    }

    private void reloadUnlessChangingDates() {
        if (!changingDates) {
            reload();
        }
    }

    // ------------------------------------------------------------------ list

    /** Loads the bills that match the dates and search. The query is quick, so it runs straight away. */
    private void reload() {
        messageLabel.getStyleClass().remove("error-text");
        List<BillSummary> bills;
        try {
            bills = billing.searchBills(new BillSearch(fromPicker.getValue(), toPicker.getValue(),
                    searchField.getText(), customerId, LIMIT));
        } catch (ValidationException e) {
            messageLabel.setText(e.getMessage());
            messageLabel.getStyleClass().add("error-text");
            table.getItems().clear();
            countLabel.setText("");
            return;
        }
        BillSummary selected = table.getSelectionModel().getSelectedItem();
        table.getItems().setAll(bills);
        // Keep the same bill selected; otherwise select the newest, so Enter opens it straight away.
        bills.stream().filter(b -> selected != null && b.id() == selected.id()).findFirst().ifPresentOrElse(
                b -> table.getSelectionModel().select(b), () -> table.getSelectionModel().selectFirst());
        long cancelled = bills.stream().filter(BillSummary::cancelled).count();
        String count = bills.size() + (bills.size() == 1 ? " bill" : " bills")
                + (cancelled == 0 ? "" : " (" + cancelled + " cancelled)");
        if (bills.size() == LIMIT) {
            count += ". Showing the newest " + LIMIT + "; choose fewer dates to see older bills.";
        }
        countLabel.setText(count);
        messageLabel.setText("Type a bill number to find it on any date. Double-click a bill to see or reprint it.");
        updateButtons();
    }

    private void updateButtons() {
        BillSummary bill = table.getSelectionModel().getSelectedItem();
        viewButton.setDisable(bill == null);
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
            ReceiptPreviewController.openBill(window(), context, bill.billNo());
        } catch (BusinessRuleException e) {
            Dialogs.warning(window(), "Bill " + bill.billNo(), e.getMessage());
        }
        table.requestFocus();
    }

    @FXML
    private void cancelSelected() {
        BillSummary bill = table.getSelectionModel().getSelectedItem();
        if (bill == null || bill.cancelled()) {
            return;
        }
        try {
            CancelBillController.open(window(), context, bill.billNo()).ifPresent(done -> {
                reload();
                Dialogs.info(window(), "Bill cancelled", CancelBillController.whatToDoNow(done));
            });
        } catch (BusinessRuleException e) {
            Dialogs.warning(window(), "Cancel bill", e.getMessage());
            reload();
        }
    }

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && table.isFocused()) {
            viewSelected();
            event.consume();
        } else if (event.getCode() == KeyCode.F && event.isShortcutDown()) {
            searchField.requestFocus();
            searchField.selectAll();
            event.consume();
        }
    }

    private Window window() {
        return root.getScene().getWindow();
    }

    private static void column(TableColumn<BillSummary, String> column, Function<BillSummary, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }
}
