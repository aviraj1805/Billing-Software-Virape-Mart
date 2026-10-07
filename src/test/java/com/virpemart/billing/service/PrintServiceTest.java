package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaperSize;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.PrintAfterSave;
import com.virpemart.billing.model.PrinterSetup;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.print.Receipt;
import com.virpemart.billing.print.ReceiptRenderer;

class PrintServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private PrintService printing;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        printing = fixture.services.printing();
        Product salt = fixture.services.products().create(
                new ProductInput("Tata Salt", "टाटा मीठ", "pcs", "1 kg", "28"));
        Cart cart = new Cart();
        cart.addProduct(salt, Quantity.ofWhole(2));
        fixture.services.billing().save(new BillRequest(null, null, cart.lines(),
                List.of(new PaymentPart(PaymentMode.CASH, Money.parse("56")))));
        fixture.services.settings().saveShopDetails("Virpe Mart", null, "Main Road", null, "Thank you");
        fixture.services.settings().savePrinterSetup(
                new PrinterSetup("Fake Printer", PaperSize.ROLL_58, PrintAfterSave.ALWAYS));
    }

    @Test
    void billPictureForWhatsAppLooksLikeTheBillWithoutPrintingAndIsAudited() {
        fixture.signInStaff();

        BufferedImage picture = printing.billPicture(1);

        ReceiptRenderer renderer = new ReceiptRenderer(PaperSize.ROLL_58);
        BufferedImage asPrinted = renderer.toImage(printing.billReceipt(1), 3);
        assertEquals(asPrinted.getWidth(), picture.getWidth(), "same paper width as the printed bill");
        assertEquals(asPrinted.getHeight(), picture.getHeight(), "same lines as the printed bill");
        assertTrue(fixture.printer.printed.isEmpty(), "nothing is sent to the printer");
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'BILL_SHARED'"
                + " AND details = 'Bill 1 picture copied to send on WhatsApp' AND user_id = " + fixture.staff.id()));
    }

    @Test
    void cancelledBillCannotBeSentOnWhatsApp() {
        fixture.services.billing().cancel(1, "Wrong items");

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> printing.billPicture(1));

        assertTrue(error.getMessage().contains("cancelled"), error.getMessage());
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'BILL_SHARED'"));
    }

    @Test
    void billIsPrintedOnTheChosenPrinterWithTheShopHeading() {
        fixture.signInStaff();

        printing.printBill(1, false);

        assertEquals(1, fixture.printer.printed.size());
        Receipt receipt = fixture.printer.printed.getFirst();
        assertEquals("Virpe Mart bill 1", receipt.title());
        String text = receipt.toPlainText();
        assertTrue(text.startsWith("Virpe Mart\nMain Road"), text);
        assertTrue(text.contains("टाटा मीठ"), text);
        assertTrue(text.contains("Thank you"), text);
        assertEquals(new PrinterSetup("Fake Printer", PaperSize.ROLL_58, PrintAfterSave.ALWAYS),
                fixture.printer.setups.getFirst());
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'BILL_REPRINTED'"),
                "the first print is not a reprint");
    }

    @Test
    void reprintIsExactlyTheSameAsTheFirstPrintAndIsAudited() {
        fixture.signInStaff();
        List<String> before = fixture.rows("SELECT * FROM bills");

        printing.printBill(1, false);
        printing.printBill(1, true);
        printing.printBill(1, true);

        assertEquals(3, fixture.printer.printed.size());
        Receipt first = fixture.printer.printed.getFirst();
        assertEquals(first, fixture.printer.printed.get(1), "no DUPLICATE COPY or other reprint mark");
        assertEquals(first, fixture.printer.printed.get(2));
        assertFalse(first.toPlainText().toUpperCase().contains("DUPLICATE"), first.toPlainText());
        assertEquals(before, fixture.rows("SELECT * FROM bills"), "printing never changes the saved bill");
        assertEquals(2, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'BILL_REPRINTED'"
                + " AND user_id = " + fixture.staff.id()));
    }

    @Test
    void printerProblemIsExplainedAndTheBillStaysSaved() {
        fixture.printer.failWith = "Printer is offline";

        PrintFailedException error = assertThrows(PrintFailedException.class, () -> printing.printBill(1, true));

        assertTrue(error.getMessage().contains("Printer is offline"), error.getMessage());
        assertTrue(error.getMessage().contains("switched on"), error.getMessage());
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM bills"));
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'BILL_REPRINTED'"),
                "a failed reprint is not recorded as printed");
    }

    @Test
    void unknownBillCannotBePrinted() {
        assertThrows(BusinessRuleException.class, () -> printing.printBill(99, false));
        assertTrue(fixture.printer.printed.isEmpty());
    }

    @Test
    void testPrintUsesTheSetupBeingTriedAndIsOwnerOnly() {
        PrinterSetup trying = new PrinterSetup("Microsoft Print to PDF", PaperSize.A4, PrintAfterSave.ASK);

        printing.printTest(trying);

        assertEquals(trying, fixture.printer.setups.getFirst());
        assertTrue(fixture.printer.printed.getFirst().toPlainText().contains("TEST PRINT"));

        fixture.signInStaff();
        assertThrows(PermissionDeniedException.class, () -> printing.printTest(trying));
    }

    @Test
    void printerNamesComeFromWindows() {
        assertEquals(List.of("Fake Printer", "Microsoft Print to PDF"), printing.printerNames());
    }
}
