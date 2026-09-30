package com.virpemart.billing.print;

import java.awt.font.FontRenderContext;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.Arrays;
import java.util.List;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;

import com.virpemart.billing.model.PrinterSetup;

/**
 * Prints receipts on a Windows printer through its normal Windows driver, so any printer works:
 * a thermal receipt printer or a normal A4 printer.
 */
public final class SystemReceiptPrinter implements ReceiptPrinter {

    /** Blank space printed after the last line of a roll receipt, so the cutter does not cut through the text. */
    private static final double ROLL_BOTTOM_SPACE = ReceiptRenderer.mm(12);

    @Override
    public List<String> printerNames() {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public void print(Receipt receipt, PrinterSetup setup) throws PrinterException {
        prepare(receipt, setup).print();
    }

    /**
     * A print job for the receipt, ready to start. Public so a developer check can send it to a file instead of
     * paper (for example with "Microsoft Print to PDF").
     */
    public PrinterJob prepare(Receipt receipt, PrinterSetup setup) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(findPrinter(setup.printerName()));
        job.setJobName(receipt.title());
        ReceiptRenderer renderer = new ReceiptRenderer(setup.paper());
        job.setPrintable(new ReceiptPrintable(receipt, renderer), pageFormat(job, receipt, renderer));
        return job;
    }

    private static PrintService findPrinter(String name) throws PrinterException {
        if (name == null) {
            PrintService standard = PrintServiceLookup.lookupDefaultPrintService();
            if (standard == null) {
                throw new PrinterException("Windows has no default printer. Choose the bill printer in Settings.");
            }
            return standard;
        }
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(service -> service.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new PrinterException("The printer \"" + name
                        + "\" was not found in Windows. Check that it is installed, or choose another printer in Settings."));
    }

    /**
     * For a receipt roll, asks for paper exactly as wide as the roll and as long as the bill, so the printer feeds
     * only what is needed. If the printer driver does not allow that, Windows picks its nearest paper and a long
     * bill continues on the next page. For A4 the driver's normal page is used.
     */
    private static PageFormat pageFormat(PrinterJob job, Receipt receipt, ReceiptRenderer renderer) {
        PageFormat format = job.defaultPage();
        format.setOrientation(PageFormat.PORTRAIT);
        if (renderer.paper().isRoll()) {
            double width = renderer.paperWidth();
            double contentHeight = renderer.height(receipt, new FontRenderContext(null, true, true));
            double height = contentHeight + ROLL_BOTTOM_SPACE + ReceiptRenderer.mm(4);
            Paper paper = new Paper();
            paper.setSize(width, height);
            double side = (width - renderer.textWidth()) / 2;
            paper.setImageableArea(side, ReceiptRenderer.mm(2), renderer.textWidth(), height - ReceiptRenderer.mm(2));
            format.setPaper(paper);
        }
        return job.validatePage(format);
    }
}
