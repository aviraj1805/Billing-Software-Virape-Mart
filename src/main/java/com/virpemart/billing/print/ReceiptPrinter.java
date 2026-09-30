package com.virpemart.billing.print;

import java.awt.print.PrinterException;
import java.util.List;

import com.virpemart.billing.model.PrinterSetup;

/**
 * Something that can print receipts. The app uses {@link SystemReceiptPrinter}; tests use a fake that only
 * remembers what it was asked to print, so tests never need a real printer.
 */
public interface ReceiptPrinter {

    /** Names of the printers installed in Windows, in alphabetical order. */
    List<String> printerNames();

    /**
     * Prints the receipt and returns when Windows has accepted it. This can take a few seconds,
     * so never call it on the screen thread.
     */
    void print(Receipt receipt, PrinterSetup setup) throws PrinterException;
}
