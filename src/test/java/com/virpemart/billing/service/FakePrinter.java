package com.virpemart.billing.service;

import java.awt.print.PrinterException;
import java.util.ArrayList;
import java.util.List;

import com.virpemart.billing.model.PrinterSetup;
import com.virpemart.billing.print.Receipt;
import com.virpemart.billing.print.ReceiptPrinter;

/** A pretend printer for tests: it remembers what it was asked to print, or fails when told to. */
final class FakePrinter implements ReceiptPrinter {

    final List<Receipt> printed = new ArrayList<>();
    final List<PrinterSetup> setups = new ArrayList<>();
    String failWith;

    @Override
    public List<String> printerNames() {
        return List.of("Fake Printer", "Microsoft Print to PDF");
    }

    @Override
    public void print(Receipt receipt, PrinterSetup setup) throws PrinterException {
        if (failWith != null) {
            throw new PrinterException(failWith);
        }
        printed.add(receipt);
        setups.add(setup);
    }
}
