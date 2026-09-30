package com.virpemart.billing.ui.history;

import java.time.LocalDate;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.DaySummary;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.SalesReport;
import com.virpemart.billing.service.ReportService;
import com.virpemart.billing.service.UserFacingException;
import com.virpemart.billing.ui.common.Background;
import com.virpemart.billing.ui.common.ErrorHandler;
import com.virpemart.billing.ui.common.Format;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;

/**
 * The owner's sales report: totals for one day or a date range, money received split into Cash, UPI and Card
 * (at billing and khata payments), and a day-by-day table. The report is worked out away from the screen thread.
 */
public class ReportsController {

    private final AppContext context;
    private final ReportService reports;

    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private Button showButton;
    @FXML
    private Label messageLabel;
    @FXML
    private Label periodLabel;
    @FXML
    private Label billsLabel;
    @FXML
    private Label salesLabel;
    @FXML
    private Label onKhataLabel;
    @FXML
    private Label cancelledLabel;
    @FXML
    private GridPane moneyGrid;
    @FXML
    private TableView<DaySummary> dayTable;
    @FXML
    private TableColumn<DaySummary, String> dayColumn;
    @FXML
    private TableColumn<DaySummary, String> dayBillsColumn;
    @FXML
    private TableColumn<DaySummary, String> daySalesColumn;
    @FXML
    private TableColumn<DaySummary, String> dayKhataColumn;
    @FXML
    private TableColumn<DaySummary, String> dayCashColumn;
    @FXML
    private TableColumn<DaySummary, String> dayUpiColumn;
    @FXML
    private TableColumn<DaySummary, String> dayCardColumn;
    @FXML
    private TableColumn<DaySummary, String> dayTotalColumn;
    @FXML
    private TableColumn<DaySummary, String> dayCancelledColumn;

    public ReportsController(AppContext context) {
        this.context = context;
        this.reports = context.services().reports();
    }

    @FXML
    private void initialize() {
        fromPicker.setConverter(Format.dateInput());
        toPicker.setConverter(Format.dateInput());
        column(dayColumn, day -> Format.date(day.day()));
        column(dayBillsColumn, day -> String.valueOf(day.bills()));
        column(daySalesColumn, day -> Format.money(day.sales()));
        column(dayKhataColumn, day -> Format.money(day.onKhata()));
        column(dayCashColumn, day -> Format.money(day.received(PaymentMode.CASH)));
        column(dayUpiColumn, day -> Format.money(day.received(PaymentMode.UPI)));
        column(dayCardColumn, day -> Format.money(day.received(PaymentMode.CARD)));
        column(dayTotalColumn, day -> Format.money(day.receivedTotal()));
        column(dayCancelledColumn, day -> day.cancelledBills() == 0 ? "" : String.valueOf(day.cancelledBills()));
        dayTable.setPlaceholder(new Label("No bills or payments in these dates."));

        LocalDate today = today();
        fromPicker.setValue(today);
        toPicker.setValue(today);
    }

    /** Works the report out again for the chosen dates. Called each time the Reports tab is opened. */
    public void refresh() {
        load();
    }

    @FXML
    private void showToday() {
        LocalDate today = today();
        show(today, today);
    }

    @FXML
    private void showYesterday() {
        LocalDate yesterday = today().minusDays(1);
        show(yesterday, yesterday);
    }

    @FXML
    private void showThisMonth() {
        LocalDate today = today();
        show(today.withDayOfMonth(1), today);
    }

    @FXML
    private void showLastMonth() {
        LocalDate lastMonth = today().withDayOfMonth(1).minusMonths(1);
        show(lastMonth, lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()));
    }

    private LocalDate today() {
        return LocalDate.now(context.clock());
    }

    private void show(LocalDate from, LocalDate to) {
        fromPicker.setValue(from);
        toPicker.setValue(to);
        load();
    }

    /** Works out the report for the chosen dates in the background, then shows it. */
    @FXML
    private void load() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        showButton.setDisable(true);
        messageLabel.getStyleClass().remove("error-text");
        messageLabel.setText("Working out the report...");
        Background.run("sales-report", () -> reports.sales(from, to), this::showReport, error -> {
            showButton.setDisable(false);
            if (error instanceof UserFacingException problem) {
                messageLabel.setText(problem.getMessage());
                messageLabel.getStyleClass().add("error-text");
            } else {
                messageLabel.setText("");
                ErrorHandler.handle(error);
            }
        });
    }

    private void showReport(SalesReport report) {
        showButton.setDisable(false);
        messageLabel.setText("");
        DaySummary total = report.total();
        periodLabel.setText(report.from().equals(report.to()) ? Format.date(report.from())
                : Format.date(report.from()) + "  to  " + Format.date(report.to()));
        billsLabel.setText(String.valueOf(total.bills()));
        salesLabel.setText(Format.money(total.sales()));
        onKhataLabel.setText(Format.money(total.onKhata()));
        cancelledLabel.setText(total.cancelledBills() == 0 ? "None"
                : total.cancelledBills() + "  ·  " + Format.money(total.cancelledTotal()));
        fillMoneyGrid(total);
        dayTable.getItems().setAll(report.days());
    }

    /** Rows Cash, UPI, Card and Total; columns At billing, Khata payments and Total. */
    private void fillMoneyGrid(DaySummary total) {
        moneyGrid.getChildren().clear();
        addRow(0, "", "At billing", "Khata payments", "Total", "muted");
        int row = 1;
        for (PaymentMode mode : PaymentMode.values()) {
            addRow(row++, mode.label(), Format.money(total.paidAtBilling(mode)),
                    Format.money(total.khataPayments(mode)), Format.money(total.received(mode)), null);
        }
        addRow(row, "Total", Format.money(total.paidAtBillingTotal()), Format.money(total.khataPaymentsTotal()),
                Format.money(total.receivedTotal()), "total-label");
    }

    private void addRow(int row, String name, String atBilling, String khata, String sum, String style) {
        String[] texts = {name, atBilling, khata, sum};
        for (int column = 0; column < texts.length; column++) {
            Label label = new Label(texts[column]);
            if (style != null) {
                label.getStyleClass().add(style);
            }
            moneyGrid.add(label, column, row);
        }
    }

    private static void column(TableColumn<DaySummary, String> column, Function<DaySummary, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }
}
