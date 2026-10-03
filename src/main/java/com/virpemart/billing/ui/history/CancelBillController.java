package com.virpemart.billing.ui.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.BillCorrection;
import com.virpemart.billing.model.CancelledBill;
import com.virpemart.billing.service.BillingService;
import com.virpemart.billing.service.UserFacingException;
import com.virpemart.billing.service.ValidationException;
import com.virpemart.billing.ui.common.FormErrors;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Views;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The owner's "Cancel bill" window. It shows what cancelling will do (money to give back, khata amount taken off)
 * and needs a reason. There is no default button, so pressing Enter never cancels a bill by accident.
 *
 * <p>The same window corrects a bill ({@link #openCorrection}): the bill is cancelled and its items are given back
 * to be changed on the Billing screen and saved as a new bill.
 */
public class CancelBillController {

    private final BillingService billing;

    @FXML
    private Label billLabel;
    @FXML
    private Label effectsLabel;
    @FXML
    private TextField reasonField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button confirmButton;

    private FormErrors errors;
    private Stage stage;
    private long billNo;
    private CancelledBill result;
    private boolean correcting;
    private BillCorrection correction;

    public CancelBillController(AppContext context) {
        this.billing = context.services().billing();
    }

    /**
     * Opens the window and waits.
     *
     * @return what was done, or empty if the owner kept the bill
     * @throws com.virpemart.billing.service.BusinessRuleException if the bill cannot be cancelled
     */
    public static Optional<CancelledBill> open(Window owner, AppContext context, long billNo) {
        Views.Loaded<CancelBillController> view = Views.load("cancel-bill.fxml", context);
        CancelBillController form = view.controller();
        form.billNo = billNo;
        form.show(form.billing.cancelPreview(billNo));
        form.stage = Views.dialog(owner, "Cancel bill " + billNo, view.root());
        form.stage.setResizable(false);
        form.stage.setOnShown(event -> form.reasonField.requestFocus());
        form.stage.showAndWait();
        return Optional.ofNullable(form.result);
    }

    /**
     * Opens the window to correct a bill and waits. The reason is filled in ("Bill corrected") and can be changed.
     *
     * @return the old bill's customer and items, or empty if the owner kept the bill
     * @throws com.virpemart.billing.service.BusinessRuleException if the bill cannot be cancelled
     */
    public static Optional<BillCorrection> openCorrection(Window owner, AppContext context, long billNo) {
        Views.Loaded<CancelBillController> view = Views.load("cancel-bill.fxml", context);
        CancelBillController form = view.controller();
        form.billNo = billNo;
        form.correcting = true;
        form.showCorrection(form.billing.cancelPreview(billNo));
        form.stage = Views.dialog(owner, "Correct bill " + billNo, view.root());
        form.stage.setResizable(false);
        form.stage.setOnShown(event -> form.reasonField.requestFocus());
        form.stage.showAndWait();
        return Optional.ofNullable(form.correction);
    }

    /** The message after cancelling: what the owner should do now. */
    public static String whatToDoNow(CancelledBill done) {
        List<String> lines = new ArrayList<>();
        lines.add("Bill " + done.billNo() + " is cancelled.");
        lines.addAll(effects(done));
        return String.join("\n\n", lines);
    }

    @FXML
    private void initialize() {
        errors = new FormErrors(errorLabel, Map.<String, Control>of("reason", reasonField));
    }

    private void show(CancelledBill preview) {
        billLabel.setText("Cancel bill " + preview.billNo() + "  ·  " + Format.money(preview.total())
                + (preview.customerName() == null ? "" : "  ·  " + preview.customerName()));
        List<String> lines = new ArrayList<>();
        lines.add("The bill is kept and marked CANCELLED. Its number is never used again.");
        lines.addAll(effects(preview));
        lines.add("If the customer still takes some items, make a new bill for them.");
        effectsLabel.setText("• " + String.join("\n• ", lines));
    }

    private void showCorrection(CancelledBill preview) {
        billLabel.setText("Correct bill " + preview.billNo() + "  ·  " + Format.money(preview.total())
                + (preview.customerName() == null ? "" : "  ·  " + preview.customerName()));
        List<String> lines = new ArrayList<>();
        lines.add("Bill " + preview.billNo() + " is cancelled (saved bills are never changed). It stays in History "
                + "marked CANCELLED.");
        lines.add("Its customer and items open on the Billing screen. Change what is wrong and save it as a new "
                + "bill with a new number.");
        if (preview.giveBack().isPositive()) {
            lines.add("The " + Format.money(preview.giveBack()) + " paid for this bill goes with it. When you save "
                    + "the new bill, enter the payment again; the customer does not pay twice.");
        }
        if (preview.takenOffKhata().isPositive()) {
            lines.add(Format.money(preview.takenOffKhata()) + " is taken off " + preview.customerName()
                    + "'s khata; the new bill adds its own amount.");
        }
        if (preview.duesPaymentKept().isPositive()) {
            lines.add(Format.money(preview.duesPaymentKept())
                    + " paid towards old dues with this bill stays paid in the khata.");
        }
        effectsLabel.setText("• " + String.join("\n• ", lines));
        reasonField.setText("Bill corrected");
        confirmButton.setText("Cancel and correct");
    }

    private static List<String> effects(CancelledBill bill) {
        List<String> lines = new ArrayList<>();
        if (bill.giveBack().isPositive()) {
            lines.add("Give back " + Format.money(bill.giveBack()) + " paid for this bill.");
        }
        if (bill.takenOffKhata().isPositive()) {
            lines.add(Format.money(bill.takenOffKhata()) + " is taken off " + bill.customerName() + "'s khata.");
        }
        if (bill.duesPaymentKept().isPositive()) {
            lines.add(Format.money(bill.duesPaymentKept())
                    + " paid towards old dues with this bill stays paid in the khata.");
        }
        return lines;
    }

    @FXML
    private void cancelBill() {
        errors.clear();
        try {
            if (correcting) {
                correction = billing.correct(billNo, reasonField.getText());
            } else {
                result = billing.cancel(billNo, reasonField.getText());
            }
            stage.close();
        } catch (ValidationException e) {
            errors.show(e.getMessage(), e.field());
        } catch (UserFacingException e) {
            errors.show(e.getMessage(), null);
        }
    }

    @FXML
    private void keep() {
        stage.close();
    }
}
