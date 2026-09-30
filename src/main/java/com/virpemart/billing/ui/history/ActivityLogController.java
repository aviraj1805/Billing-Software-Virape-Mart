package com.virpemart.billing.ui.history;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.AuditEntry;
import com.virpemart.billing.service.AuditService;
import com.virpemart.billing.service.ValidationException;
import com.virpemart.billing.ui.common.Format;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

/**
 * The owner's Activity log: the audit records (who changed what, and when), newest first, with a date range and a
 * search box. The records can only be read.
 */
public class ActivityLogController {

    private static final int LIMIT = 1000;

    /** Plain words for the action codes stored in the audit log. */
    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("BALANCE_CORRECTED", "Khata balance corrected"),
            Map.entry("BILL_CANCELLED", "Bill cancelled"),
            Map.entry("BILL_REPRINTED", "Bill printed again"),
            Map.entry("RATE_CHANGED", "Rate changed on a bill"),
            Map.entry("CATEGORY_CREATED", "Category added"),
            Map.entry("CATEGORY_RENAMED", "Category renamed"),
            Map.entry("CATEGORY_SWITCHED_OFF", "Category switched off"),
            Map.entry("CATEGORY_SWITCHED_ON", "Category switched on"),
            Map.entry("CUSTOMER_CREATED", "Customer added"),
            Map.entry("CUSTOMER_UPDATED", "Customer edited"),
            Map.entry("CUSTOMER_SWITCHED_OFF", "Customer switched off"),
            Map.entry("CUSTOMER_SWITCHED_ON", "Customer switched on"),
            Map.entry("PRODUCT_CREATED", "Product added"),
            Map.entry("PRODUCT_UPDATED", "Product edited"),
            Map.entry("PRODUCT_DELETED", "Product deleted"),
            Map.entry("PRODUCT_SWITCHED_OFF", "Product switched off"),
            Map.entry("PRODUCT_SWITCHED_ON", "Product switched on"),
            Map.entry("PRODUCTS_IMPORTED", "Products imported"),
            Map.entry("SHOP_DETAILS_CHANGED", "Shop details changed"),
            Map.entry("PRINTER_SETTINGS_CHANGED", "Printer settings changed"),
            Map.entry("BACKUP_MADE", "Backup made"),
            Map.entry("BACKUP_RESTORE_STARTED", "Backup restore started"),
            Map.entry("BACKUP_RESTORED", "Backup restored"));

    private final AppContext context;
    private final AuditService audit;

    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private TextField searchField;
    @FXML
    private Label messageLabel;
    @FXML
    private TableView<AuditEntry> table;
    @FXML
    private TableColumn<AuditEntry, String> timeColumn;
    @FXML
    private TableColumn<AuditEntry, String> whatColumn;
    @FXML
    private TableColumn<AuditEntry, String> detailsColumn;
    @FXML
    private TableColumn<AuditEntry, String> byColumn;
    @FXML
    private Label countLabel;

    private boolean changingDates;

    public ActivityLogController(AppContext context) {
        this.context = context;
        this.audit = context.services().audit();
    }

    @FXML
    private void initialize() {
        fromPicker.setConverter(Format.dateInput());
        toPicker.setConverter(Format.dateInput());
        fromPicker.valueProperty().addListener((obs, oldDate, newDate) -> reloadUnlessChangingDates());
        toPicker.valueProperty().addListener((obs, oldDate, newDate) -> reloadUnlessChangingDates());
        searchField.textProperty().addListener((obs, oldText, newText) -> reload());

        column(timeColumn, entry -> Format.dateTimeShort(entry.createdAt()));
        column(whatColumn, entry -> ACTIONS.getOrDefault(entry.action(), entry.action()));
        column(detailsColumn, entry -> entry.details() == null ? "" : entry.details());
        column(byColumn, entry -> entry.userName() == null ? "The app" : entry.userName());
        table.setPlaceholder(new Label("Nothing recorded in these dates."));
        table.setRowFactory(tableView -> new TableRow<>() {
            @Override
            protected void updateItem(AuditEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setTooltip(empty || entry == null || entry.details() == null ? null : new Tooltip(entry.details()));
            }
        });

        LocalDate today = LocalDate.now(context.clock());
        changingDates = true;
        fromPicker.setValue(today.minusDays(6));
        toPicker.setValue(today);
        changingDates = false;
    }

    /** Loads the records again. Called each time the Activity log tab is opened. */
    public void refresh() {
        reload();
    }

    @FXML
    private void showToday() {
        LocalDate today = LocalDate.now(context.clock());
        setDates(today, today);
    }

    @FXML
    private void showLastWeek() {
        LocalDate today = LocalDate.now(context.clock());
        setDates(today.minusDays(6), today);
    }

    @FXML
    private void showAllDates() {
        setDates(null, null);
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

    private void reload() {
        messageLabel.getStyleClass().remove("error-text");
        List<AuditEntry> entries;
        try {
            entries = audit.search(fromPicker.getValue(), toPicker.getValue(), searchField.getText(), LIMIT);
        } catch (ValidationException e) {
            messageLabel.setText(e.getMessage());
            messageLabel.getStyleClass().add("error-text");
            return;
        }
        messageLabel.setText("Every change to rates, bills, products, customers, settings and backups is recorded "
                + "here. Records cannot be changed or deleted.");
        table.getItems().setAll(entries);
        countLabel.setText(entries.size() + (entries.size() == 1 ? " record" : " records")
                + (entries.size() == LIMIT ? ". Showing the newest " + LIMIT + "; choose fewer dates to see older ones."
                : ""));
    }

    private static void column(TableColumn<AuditEntry, String> column, Function<AuditEntry, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }
}
