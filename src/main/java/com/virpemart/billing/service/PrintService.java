package com.virpemart.billing.service;

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
import com.virpemart.billing.repository.AuditRepository;

/**
 * Prints bills. Printing always happens after a bill is saved, so a printer problem never loses a bill:
 * the bill can be printed again later. Printing again is written to the audit log.
 *
 * <p>Printing can take a few seconds, so screens call the print methods away from the screen thread.
 */
public final class PrintService {

    private static final Logger LOG = LoggerFactory.getLogger(PrintService.class);

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
    public Receipt billReceipt(long billNo, boolean reprint) {
        return ReceiptBuilder.forBill(billing.bill(billNo), settings.shopDetails(), reprint);
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
     * @param reprint true when printing the bill again later; the copy is marked "DUPLICATE COPY" and the
     *                reprint is written to the audit log
     * @throws PrintFailedException if the printer did not accept the bill
     */
    public void printBill(long billNo, boolean reprint) {
        User user = session.requireSignedIn();
        BillDetails bill = billing.bill(billNo);
        send(ReceiptBuilder.forBill(bill, settings.shopDetails(), reprint), settings.printerSetup());
        if (reprint) {
            database.runInTransaction(c -> audit.insert(c, user.id(), "BILL_REPRINTED", "bills", bill.id(),
                    "Bill " + billNo + " printed again", DbTime.now(clock)));
        }
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
