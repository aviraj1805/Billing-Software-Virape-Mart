package com.virpemart.billing.ui.billing;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.BillDetails;
import com.virpemart.billing.model.PaperSize;
import com.virpemart.billing.print.Receipt;
import com.virpemart.billing.print.ReceiptRenderer;
import com.virpemart.billing.service.PrintService;
import com.virpemart.billing.ui.common.Background;
import com.virpemart.billing.ui.common.ErrorHandler;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Images;
import com.virpemart.billing.ui.common.Views;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * A window showing a saved bill exactly as it prints, with a Print button. Printing from here is a reprint:
 * the copy says "DUPLICATE COPY" and the reprint is recorded.
 */
public class ReceiptPreviewController {

    /** The preview is drawn this many pixels per printed point, then shown at half size, so it stays sharp. */
    private static final double DRAW_SCALE = 3;
    private static final double SHOW_SCALE = 1.5;
    /** An A4 bill is shrunk to fit the window. */
    private static final double MAX_WIDTH = 560;

    private final PrintService printing;

    @FXML
    private Label infoLabel;
    @FXML
    private ImageView receiptView;
    @FXML
    private Label statusLabel;
    @FXML
    private Button printButton;

    private Stage stage;
    private long billNo;

    public ReceiptPreviewController(AppContext context) {
        this.printing = context.services().printing();
    }

    /**
     * Opens the preview of a saved bill and waits until it is closed.
     *
     * @throws com.virpemart.billing.service.BusinessRuleException if there is no such bill
     */
    public static void openBill(Window owner, AppContext context, long billNo) {
        BillDetails bill = context.services().billing().bill(billNo);
        Receipt receipt = context.services().printing().billReceipt(billNo, true);
        PaperSize paper = context.services().settings().printerSetup().paper();

        Views.Loaded<ReceiptPreviewController> view = Views.load("receipt-preview.fxml", context);
        ReceiptPreviewController preview = view.controller();
        preview.billNo = billNo;
        preview.show(receipt, paper);
        preview.infoLabel.setText("Bill " + billNo + "  ·  " + Format.dateTime(bill.createdAt()) + "  ·  "
                + Format.money(bill.totals().total())
                + (bill.customerName() == null ? "" : "  ·  " + bill.customerName())
                + (bill.isCancelled() ? "  ·  CANCELLED" : ""));
        preview.stage = Views.dialog(owner, "Bill " + billNo, view.root());
        preview.stage.setHeight(Math.min(760, owner.getHeight() - 40));
        // A focused button takes the Enter key, so focus Print; otherwise Enter would press Close.
        preview.stage.setOnShown(event -> preview.printButton.requestFocus());
        preview.stage.showAndWait();
    }

    private void show(Receipt receipt, PaperSize paper) {
        Image image = Images.toFx(new ReceiptRenderer(paper).toImage(receipt, DRAW_SCALE));
        receiptView.setImage(image);
        receiptView.setFitWidth(Math.min(image.getWidth() / DRAW_SCALE * SHOW_SCALE, MAX_WIDTH));
        statusLabel.setText("Printer paper: " + paper.label() + ". A printed copy says \"DUPLICATE COPY\".");
    }

    @FXML
    private void print() {
        printButton.setDisable(true);
        statusLabel.setText("Printing...");
        Background.run("reprint-bill-" + billNo, () -> {
            printing.printBill(billNo, true);
            return null;
        }, done -> stage.close(), error -> {
            printButton.setDisable(false);
            statusLabel.setText("Not printed.");
            ErrorHandler.handle(error);
        });
    }

    @FXML
    private void close() {
        stage.close();
    }
}
