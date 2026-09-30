package com.virpemart.billing.model;

/**
 * Which printer prints the bills, and how.
 *
 * @param printerName Windows printer name, or null to use the Windows default printer
 * @param paper       paper in the printer
 * @param afterSave   whether a bill prints automatically, after asking, or not at all
 */
public record PrinterSetup(String printerName, PaperSize paper, PrintAfterSave afterSave) {

    /** Used until the owner saves printer settings. */
    public static final PrinterSetup DEFAULT = new PrinterSetup(null, PaperSize.ROLL_80, PrintAfterSave.ASK);
}
