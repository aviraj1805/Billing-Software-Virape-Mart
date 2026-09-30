package com.virpemart.billing.ui.customers;

import java.util.List;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.Customer;
import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.LedgerEntry;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.StatementLine;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.CustomerRepository.DuesTotals;
import com.virpemart.billing.service.CustomerService;
import com.virpemart.billing.service.LedgerService;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.history.BillHistoryController;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import javafx.stage.Window;

/**
 * The Customers screen: search on the left, the chosen customer's details and khata on the right.
 *
 * <p>Keyboard: Ctrl+F search, Ctrl+N add customer, Ctrl+P receive payment, Enter edit details.
 */
public class CustomersController {

    private static final int LIMIT = 500;
    private static final PseudoClass INACTIVE = PseudoClass.getPseudoClass("inactive");
    private static final PseudoClass DUES = PseudoClass.getPseudoClass("dues");
    private static final PseudoClass ADVANCE = PseudoClass.getPseudoClass("advance");

    private final AppContext context;
    private final CustomerService customers;
    private final LedgerService ledger;

    @FXML
    private VBox root;
    @FXML
    private Label duesTotalLabel;
    @FXML
    private TextField searchField;
    @FXML
    private CheckBox onlyDues;
    @FXML
    private CheckBox showInactive;
    @FXML
    private TableView<CustomerSummary> table;
    @FXML
    private TableColumn<CustomerSummary, String> numberColumn;
    @FXML
    private TableColumn<CustomerSummary, String> nameColumn;
    @FXML
    private TableColumn<CustomerSummary, String> phoneColumn;
    @FXML
    private TableColumn<CustomerSummary, String> balanceColumn;
    @FXML
    private Label countLabel;
    @FXML
    private Label nothingSelectedLabel;
    @FXML
    private VBox detailPane;
    @FXML
    private Label customerNameLabel;
    @FXML
    private Label customerInfoLabel;
    @FXML
    private Label customerNotesLabel;
    @FXML
    private Label balanceLabel;
    @FXML
    private Button paymentButton;
    @FXML
    private Button correctButton;
    @FXML
    private Button toggleActiveButton;
    @FXML
    private TableView<StatementLine> ledgerTable;
    @FXML
    private TableColumn<StatementLine, String> dateColumn;
    @FXML
    private TableColumn<StatementLine, String> detailsColumn;
    @FXML
    private TableColumn<StatementLine, String> addedColumn;
    @FXML
    private TableColumn<StatementLine, String> paidColumn;
    @FXML
    private TableColumn<StatementLine, String> runningColumn;
    @FXML
    private TableColumn<StatementLine, String> byColumn;

    /** The customer shown on the right, or null. */
    private CustomerSummary shown;

    public CustomersController(AppContext context) {
        this.context = context;
        this.customers = context.services().customers();
        this.ledger = context.services().ledger();
    }

    @FXML
    private void initialize() {
        column(numberColumn, s -> s.customer().customerNo());
        column(nameColumn, s -> s.customer().name());
        column(phoneColumn, s -> Format.phone(s.customer().phone()));
        column(balanceColumn, s -> s.balance().isZero() ? "" : Format.balanceShort(s.balance()));
        table.setRowFactory(tableView -> {
            TableRow<CustomerSummary> row = new TableRow<>() {
                @Override
                protected void updateItem(CustomerSummary item, boolean empty) {
                    super.updateItem(item, empty);
                    pseudoClassStateChanged(INACTIVE, !empty && item != null && !item.customer().active());
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    editCustomer();
                }
            });
            return row;
        });
        table.setPlaceholder(new Label());

        ledgerColumn(dateColumn, line -> Format.dateTimeShort(line.entry().createdAt()));
        ledgerColumn(detailsColumn, line -> describe(line.entry()));
        ledgerColumn(addedColumn, line -> line.entry().amount().isPositive() ? Format.money(line.entry().amount()) : "");
        ledgerColumn(paidColumn, line -> line.entry().amount().isNegative()
                ? Format.money(line.entry().amount().negate()) : "");
        ledgerColumn(runningColumn, line -> Format.balanceShort(line.balanceAfter()));
        ledgerColumn(byColumn, line -> line.entry().createdBy().split(" ")[0]);
        ledgerTable.setPlaceholder(new Label("No entries yet."));
        ledgerTable.setRowFactory(tableView -> new TableRow<>() {
            @Override
            protected void updateItem(StatementLine line, boolean empty) {
                super.updateItem(line, empty);
                setTooltip(empty || line == null ? null : new Tooltip(
                        Format.dateTime(line.entry().createdAt()) + "\n" + describe(line.entry())
                                + "\nBy " + line.entry().createdBy()
                                + "\nBalance after: " + Format.balance(line.balanceAfter())));
            }
        });

        searchField.textProperty().addListener((obs, oldText, newText) -> refreshList());
        onlyDues.selectedProperty().addListener((obs, oldValue, newValue) -> refreshList());
        showInactive.selectedProperty().addListener((obs, oldValue, newValue) -> refreshList());
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showDetail(newValue));

        boolean owner = isOwner();
        for (Button ownerOnly : List.of(correctButton, toggleActiveButton)) {
            ownerOnly.setVisible(owner);
            ownerOnly.setManaged(owner);
        }

        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        refreshList();
        Platform.runLater(searchField::requestFocus);
    }

    private static void column(TableColumn<CustomerSummary, String> column, Function<CustomerSummary, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    private static void ledgerColumn(TableColumn<StatementLine, String> column, Function<StatementLine, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    /** One khata line in words, like it would be written in the paper khata. */
    static String describe(LedgerEntry entry) {
        String text = switch (entry.type()) {
            case OPENING -> "Old dues (paper khata)";
            case SALE_CREDIT -> "Bill " + entry.billNo() + " (on account)";
            case PAYMENT -> entry.billNo() != null
                    ? "Paid with bill " + entry.billNo() + " (" + entry.paymentMode().label() + ")"
                    : "Payment received, " + entry.paymentMode().label();
            case CANCEL_REVERSAL -> "Bill " + entry.billNo() + " cancelled";
            case ADJUSTMENT -> "Correction";
        };
        boolean automaticNote = entry.type() == com.virpemart.billing.model.LedgerEntryType.OPENING
                || (entry.type() == com.virpemart.billing.model.LedgerEntryType.PAYMENT && entry.billNo() != null);
        boolean showNote = entry.note() != null && !automaticNote;
        return showNote ? text + ": " + entry.note() : text;
    }

    // ------------------------------------------------------------------ list

    private void refreshList() {
        Long selectedId = shown == null ? null : shown.customer().id();
        List<CustomerSummary> found = customers.search(searchField.getText(), showInactive.isSelected(),
                onlyDues.isSelected(), LIMIT + 1);
        boolean more = found.size() > LIMIT;
        table.getItems().setAll(more ? found.subList(0, LIMIT) : found);

        boolean filtered = !searchField.getText().isBlank() || onlyDues.isSelected();
        ((Label) table.getPlaceholder()).setText(filtered ? "No customers match."
                : "No customers yet. Click \"+ Add customer\".");
        countLabel.setText(more ? "Showing the first " + LIMIT + ". Type in the search box to narrow the list."
                : found.size() + (found.size() == 1 ? " customer" : " customers"));

        DuesTotals totals = customers.duesTotals();
        duesTotalLabel.setText(totals.customersWithDues() == 0 ? "Nobody owes money"
                : "Total dues " + Format.money(totals.totalDues()) + " from " + totals.customersWithDues()
                        + (totals.customersWithDues() == 1 ? " customer" : " customers"));

        if (selectedId != null) {
            select(selectedId);
        }
    }

    private void select(long customerId) {
        for (CustomerSummary item : table.getItems()) {
            if (item.customer().id() == customerId) {
                table.getSelectionModel().select(item);
                table.scrollTo(item);
                showDetail(item);
                return;
            }
        }
        // Not in the filtered list any more (for example switched off): keep showing it on the right.
        customers.find(customerId).ifPresent(this::showDetail);
    }

    // ------------------------------------------------------------------ detail

    private void showDetail(CustomerSummary summary) {
        if (summary == null) {
            return; // keep the last customer on screen while the list refreshes
        }
        shown = summary;
        Customer customer = summary.customer();
        nothingSelectedLabel.setVisible(false);
        detailPane.setVisible(true);

        customerNameLabel.setText(customer.name() + (customer.active() ? "" : "   (switched off)"));
        StringBuilder info = new StringBuilder(customer.customerNo());
        if (customer.phone() != null) {
            info.append("   ·   ").append(Format.phone(customer.phone()));
        }
        if (customer.address() != null) {
            info.append("   ·   ").append(customer.address());
        }
        customerInfoLabel.setText(info.toString());
        customerNotesLabel.setText(customer.notes() == null ? "" : customer.notes());
        customerNotesLabel.setVisible(customer.notes() != null);
        customerNotesLabel.setManaged(customer.notes() != null);

        Money balance = summary.balance();
        balanceLabel.setText(Format.balance(balance));
        balanceLabel.pseudoClassStateChanged(DUES, balance.isPositive());
        balanceLabel.pseudoClassStateChanged(ADVANCE, balance.isNegative());
        toggleActiveButton.setText(customer.active() ? "Switch off" : "Switch on");

        List<StatementLine> lines = ledger.statement(customer.id());
        ledgerTable.getItems().setAll(lines);
        if (!lines.isEmpty()) {
            ledgerTable.scrollTo(lines.size() - 1); // newest entry at the bottom, like the paper khata
        }
    }

    /** Reloads the list and the customer on the right after a change. */
    private void reloadAfterChange(long customerId) {
        shown = customers.find(customerId).orElse(null);
        refreshList();
        select(customerId);
    }

    // ------------------------------------------------------------------ actions

    @FXML
    private void addCustomer() {
        CustomerFormController.open(window(), context, null).ifPresent(saved -> {
            searchField.clear();
            reloadAfterChange(saved.customer().id());
        });
    }

    @FXML
    private void editCustomer() {
        if (shown != null) {
            CustomerFormController.open(window(), context, shown)
                    .ifPresent(saved -> reloadAfterChange(saved.customer().id()));
        }
    }

    @FXML
    private void showBills() {
        if (shown != null) {
            BillHistoryController.openForCustomer(window(), context, shown);
        }
    }

    @FXML
    private void receivePayment() {
        if (shown != null) {
            PaymentFormController.open(window(), context, shown)
                    .ifPresent(saved -> reloadAfterChange(saved.customer().id()));
        }
    }

    @FXML
    private void correctBalance() {
        if (shown != null) {
            CorrectionFormController.open(window(), context, shown)
                    .ifPresent(saved -> reloadAfterChange(saved.customer().id()));
        }
    }

    @FXML
    private void toggleActive() {
        if (shown == null) {
            return;
        }
        Customer customer = shown.customer();
        if (customer.active()) {
            String duesNote = shown.hasDues()
                    ? "\n\nThey still owe " + Format.money(shown.balance()) + ". The dues stay in the khata." : "";
            if (!Dialogs.confirm(window(), "Switch off customer", "Switch off " + customer.name() + " ("
                    + customer.customerNo() + ")?\n\nThey will be hidden from billing. Their khata is kept."
                    + duesNote, "Switch off")) {
                return;
            }
        }
        customers.setActive(customer.id(), !customer.active());
        reloadAfterChange(customer.id());
    }

    // ------------------------------------------------------------------ keyboard

    private void onKeyPressed(KeyEvent event) {
        if (event.isShortcutDown() && event.getCode() == KeyCode.F) {
            searchField.requestFocus();
            searchField.selectAll();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.N) {
            addCustomer();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.P) {
            receivePayment();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && searchField.isFocused() && !searchField.getText().isEmpty()) {
            searchField.clear();
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN && searchField.isFocused() && !table.getItems().isEmpty()) {
            table.requestFocus();
            if (table.getSelectionModel().isEmpty()) {
                table.getSelectionModel().selectFirst();
            }
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER && table.isFocused()) {
            editCustomer();
            event.consume();
        }
    }

    private boolean isOwner() {
        return context.session().currentUser().map(User::isOwner).orElse(false);
    }

    private Window window() {
        return root.getScene().getWindow();
    }
}
