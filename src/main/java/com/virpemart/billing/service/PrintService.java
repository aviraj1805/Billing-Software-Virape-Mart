package com.virpemart.billing.service;

import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.BillDetails;
import com.virpemart.billing.model.PrinterSetup;
import com.virpemart.billing.model.ShopDetails;
import com.virpemart.billing.model.User;
import com.virpemart.billing.print.Receipt;
import com.virpemart.billing.print.ReceiptBuilder;
import com.virpemart.billing.print.ReceiptPrinter;
import com.virpemart.billing.print.ReceiptRenderer;
import com.virpemart.billing.repository.AuditRepository;

/**
 * Prints bills. Printing always happens after a bill is saved, so a printer problem never loses a bill:
 * the bill can be printed again later. A bill printed again looks exactly like the first print (no "duplicate"
 * mark, the user's choice in version 1.3.0); printing again is only written to the audit log.
 *
 * <p>Printing can take a few seconds, so screens call the print methods away from the screen thread.
 */
public final class PrintService {

    private static final Logger LOG = LoggerFactory.getLogger(PrintService.class);
    /** Picture pixels per printed point for a bill sent on WhatsApp: sharp on a phone, small to send. */
    private static final double PICTURE_SCALE = 3;

    private final Database database;
    private final BillingService billing;
    private final SettingsService settings;
    private final AuditRepository audit;
    private final ReceiptPrinter printer;
    private final Session session;
    private final Clock clock;

    public PrintService(Database database, BillingService billing, SettingsService settings, AuditRepository audit,
                        ReceiptPrinter printer, Session session, Clock clock) {
        this.database = database;
        this.billing = billing;
        this.settings = settings;
        this.audit = audit;
        this.printer = printer;
        this.session = session;
        this.clock = clock;
    }

    /** How a saved bill looks on paper, for the preview. Owner and staff. */
    public Receipt billReceipt(long billNo) {
        return ReceiptBuilder.forBill(billing.bill(billNo), settings.shopDetails());
    }

    /** A made-up bill with the given shop heading, to preview or test the printer. */
    public Receipt sampleReceipt(ShopDetails shop) {
        session.requireSignedIn();
        return ReceiptBuilder.sample(shop, LocalDateTime.now(clock));
    }

    /** Printers installed in Windows. */
    public List<String> printerNames() {
        session.requireSignedIn();
        return printer.printerNames();
    }

    /**
     * Prints a saved bill on the printer chosen in Settings. Owner and staff.
     *
     * @param reprint true when printing the bill again later; the paper is the same, and the reprint is written to
     *                the audit log
     * @throws PrintFailedException if the printer did not accept the bill
     */
    public void printBill(long billNo, boolean reprint) {
        User user = session.requireSignedIn();
        BillDetails bill = billing.bill(billNo);
        send(ReceiptBuilder.forBill(bill, settings.shopDetails()), settings.printerSetup());
        if (reprint) {
            database.runInTransaction(c -> audit.insert(c, user.id(), "BILL_REPRINTED", "bills", bill.id(),
                    "Bill " + billNo + " printed again", DbTime.now(clock)));
        }
    }

    /**
     * The bill as a picture, to send to the customer on WhatsApp. It is drawn exactly like the printed bill (same
     * paper width and design). Owner and staff.
     * Making the picture is written to the audit log.
     *
     * @throws BusinessRuleException if the bill is cancelled
     */
    public BufferedImage billPicture(long billNo) {
        User user = session.requireSignedIn();
        BillDetails bill = billing.bill(billNo);
        if (bill.isCancelled()) {
            throw new BusinessRuleException("Bill " + billNo + " is cancelled, so it cannot be sent to the customer.");
        }
        Receipt receipt = ReceiptBuilder.forBill(bill, settings.shopDetails());
        BufferedImage picture = new ReceiptRenderer(settings.printerSetup().paper()).toImage(receipt, PICTURE_SCALE);
        database.runInTransaction(c -> audit.insert(c, user.id(), "BILL_SHARED", "bills", bill.id(),
                "Bill " + billNo + " picture copied to send on WhatsApp", DbTime.now(clock)));
        return picture;
    }

    /** Prints the sample bill on the given printer, to check it before saving the settings. Owner only. */
    public void printTest(PrinterSetup setup) {
        session.requireOwner();
        send(sampleReceipt(settings.shopDetails()), setup);
    }

    private void send(Receipt receipt, PrinterSetup setup) {
        try {
            printer.print(receipt, setup);
            LOG.info("Printed \"{}\"", receipt.title());
        } catch (PrinterException e) {
            LOG.warn("Printing \"{}\" failed", receipt.title(), e);
            throw new PrintFailedException("Printing did not work.\n\n" + e.getMessage()
                    + "\n\nCheck that the printer is switched on, connected and has paper,"
                    + " and that the right printer is chosen in Settings.", e);
        }
    }
}
