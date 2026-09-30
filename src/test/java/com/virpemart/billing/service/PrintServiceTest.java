package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                new ProductInput("Tata Salt", "टाटा मीठ", null, "pcs", "1 kg", "28", "30"));
        Cart cart = new Cart();
        cart.addProduct(salt, Quantity.ofWhole(2));
        fixture.services.billing().save(new BillRequest(null, null, cart.lines(),
                List.of(new PaymentPart(PaymentMode.CASH, Money.parse("56")))));
        fixture.services.settings().saveShopDetails("Virpe Mart", null, "Main Road", null, "Thank you");
        fixture.services.settings().savePrinterSetup(
                new PrinterSetup("Fake Printer", PaperSize.ROLL_58, PrintAfterSave.ALWAYS));
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
    void reprintIsMarkedAndAudited() {
        fixture.signInStaff();

        printing.printBill(1, true);

        assertTrue(fixture.printer.printed.getFirst().toPlainText().contains("DUPLICATE COPY"));
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'BILL_REPRINTED'"
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
