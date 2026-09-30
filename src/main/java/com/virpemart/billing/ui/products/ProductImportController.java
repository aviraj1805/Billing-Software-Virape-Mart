package com.virpemart.billing.ui.products;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.service.ProductImportService;
import com.virpemart.billing.service.ProductImportService.Preview;
import com.virpemart.billing.service.ProductImportService.Result;
import com.virpemart.billing.service.ProductImportService.Row;
import com.virpemart.billing.service.ProductImportService.RowStatus;
import com.virpemart.billing.ui.common.Background;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.Views;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The "Import from Excel" window. Choosing a file shows a preview of every row;
 * nothing is saved until the owner clicks Import.
 */
public class ProductImportController {

    private static final PseudoClass ERROR_ROW = PseudoClass.getPseudoClass("import-error");
    private static final PseudoClass SKIPPED_ROW = PseudoClass.getPseudoClass("import-skipped");

    private final ProductImportService importer;

    @FXML
    private Button chooseButton;
    @FXML
    private Button templateButton;
    @FXML
    private Button importButton;
    @FXML
    private Button closeButton;
    @FXML
    private Label fileLabel;
    @FXML
    private Label summaryLabel;
    @FXML
    private TableView<Row> previewTable;
    @FXML
    private TableColumn<Row, String> rowColumn;
    @FXML
    private TableColumn<Row, String> nameColumn;
    @FXML
    private TableColumn<Row, String> nameMrColumn;
    @FXML
    private TableColumn<Row, String> categoryColumn;
    @FXML
    private TableColumn<Row, String> unitColumn;
    @FXML
    private TableColumn<Row, String> packColumn;
    @FXML
    private TableColumn<Row, String> rateColumn;
    @FXML
    private TableColumn<Row, String> mrpColumn;
    @FXML
    private TableColumn<Row, String> statusColumn;

    private Stage stage;
    private Path file;
    private Preview preview;

    public ProductImportController(AppContext context) {
        this.importer = context.services().productImport();
    }

    /** Opens the window and waits until it is closed. */
    public static void open(Window owner, AppContext context) {
        Views.Loaded<ProductImportController> view = Views.load("product-import.fxml", context);
        view.controller().stage = Views.dialog(owner, "Import products from Excel", view.root());
        view.controller().stage.showAndWait();
    }

    @FXML
    private void initialize() {
        column(rowColumn, row -> String.valueOf(row.rowNumber()));
        column(nameColumn, Row::name);
        column(nameMrColumn, Row::nameMr);
        column(categoryColumn, Row::category);
        column(unitColumn, Row::unit);
        column(packColumn, Row::packSize);
        column(rateColumn, Row::rate);
        column(mrpColumn, Row::mrp);
        column(statusColumn, ProductImportController::describe);
        previewTable.setPlaceholder(new Label("Choose a CSV file to see a preview here."));
        previewTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                setTooltip(empty || row == null ? null : new Tooltip("Row " + row.rowNumber() + ": " + describe(row)));
                pseudoClassStateChanged(ERROR_ROW, !empty && row != null && row.status() == RowStatus.ERROR);
                pseudoClassStateChanged(SKIPPED_ROW, !empty && row != null && row.status() == RowStatus.SKIPPED);
            }
        });
    }

    private static void column(TableColumn<Row, String> column, Function<Row, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    private static String describe(Row row) {
        return switch (row.status()) {
            case READY -> "Will be added" + (row.message().isEmpty() ? "" : ". " + row.message());
            case SKIPPED -> "Skipped: " + row.message();
            case ERROR -> "Problem: " + row.message();
        };
    }

    @FXML
    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the product list (CSV UTF-8)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        file = chosen.toPath();
        fileLabel.setText(file.getFileName().toString());
        preview = null;
        previewTable.getItems().clear();
        importButton.setDisable(true);
        summaryLabel.setText("Reading the file…");

        setBusy(true);
        Background.run("product-import-preview", () -> importer.preview(file), this::showPreview, () -> {
            setBusy(false);
            if (preview == null) {
                summaryLabel.setText("");
            }
        });
    }

    private void showPreview(Preview result) {
        preview = result;
        previewTable.getItems().setAll(result.rows());
        long ready = result.count(RowStatus.READY);
        long skipped = result.count(RowStatus.SKIPPED);
        long errors = result.count(RowStatus.ERROR);
        StringBuilder summary = new StringBuilder()
                .append(count(ready, "product", "products")).append(" will be added, ")
                .append(skipped).append(" skipped, ")
                .append(count(errors, "row has a problem.", "rows have problems."));
        if (!result.newCategories().isEmpty()) {
            summary.append("  New categories: ").append(String.join(", ", result.newCategories())).append('.');
        }
        if (errors > 0) {
            summary.append("  Rows with problems will not be imported; fix them in Excel and import again.");
        }
        summaryLabel.setText(summary.toString());
        importButton.setText("Import " + ready + (ready == 1 ? " product" : " products"));
        importButton.setDisable(ready == 0);
    }

    @FXML
    private void doImport() {
        if (file == null || preview == null) {
            return;
        }
        long ready = preview.count(RowStatus.READY);
        if (!Dialogs.confirm(stage, "Import products", "Add " + ready + " products to the product list?", "Import")) {
            return;
        }
        summaryLabel.setText("Importing…");
        setBusy(true);
        Background.run("product-import", () -> importer.importFile(file), this::showResult, () -> setBusy(false));
    }

    private void showResult(Result result) {
        String message = count(result.imported(), "product was", "products were") + " added."
                + (result.skipped() > 0 ? "\n" + count(result.skipped(), "row was", "rows were")
                        + " skipped because the product already exists." : "")
                + (result.errors() > 0 ? "\n" + count(result.errors(), "row had a problem", "rows had problems")
                        + " and " + (result.errors() == 1 ? "was" : "were") + " not imported." : "")
                + (result.newCategories().isEmpty() ? ""
                        : "\nNew categories: " + String.join(", ", result.newCategories()) + ".");
        Dialogs.info(stage, "Import finished", message);
        stage.close();
    }

    /** "1 row was" or "3 rows were". */
    private static String count(long number, String singular, String plural) {
        return number + " " + (number == 1 ? singular : plural);
    }

    @FXML
    private void saveTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save blank product list template");
        chooser.setInitialFileName("product-list-template.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            return;
        }
        try {
            Files.writeString(target.toPath(), ProductImportService.templateText(), StandardCharsets.UTF_8);
            Dialogs.info(stage, "Template saved", "Saved " + target.getName() + ".\n\n"
                    + "Open it in Excel, fill in your products (one per row), then save it as \"CSV UTF-8\".");
        } catch (IOException e) {
            Dialogs.warning(stage, "Template not saved",
                    "The template could not be saved there. If the file is open in Excel, close it and try again.");
        }
    }

    @FXML
    private void close() {
        stage.close();
    }

    private void setBusy(boolean busy) {
        chooseButton.setDisable(busy);
        templateButton.setDisable(busy);
        closeButton.setDisable(busy);
        if (busy) {
            importButton.setDisable(true);
        } else if (preview != null) {
            importButton.setDisable(preview.count(RowStatus.READY) == 0);
        }
    }
}
