package com.virpemart.billing.print;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.List;

import com.virpemart.billing.print.ReceiptRenderer.Row;

/**
 * Hands a receipt to Java's printing system one page at a time. On a receipt roll the whole bill is usually
 * one long page; on A4, or if the printer driver uses short pages, a long bill continues on the next page.
 */
public final class ReceiptPrintable implements Printable {

    private final Receipt receipt;
    private final ReceiptRenderer renderer;

    public ReceiptPrintable(Receipt receipt, ReceiptRenderer renderer) {
        this.receipt = receipt;
        this.renderer = renderer;
    }

    @Override
    public int print(Graphics graphics, PageFormat format, int pageIndex) {
        Graphics2D g = (Graphics2D) graphics;
        double width = Math.min(renderer.textWidth(), format.getImageableWidth());
        List<Row> rows = renderer.layout(receipt, g.getFontRenderContext(), width);
        List<Integer> pageStarts = pageStarts(rows, format.getImageableHeight());
        if (pageIndex >= pageStarts.size()) {
            return NO_SUCH_PAGE;
        }
        int from = pageStarts.get(pageIndex);
        int to = pageIndex + 1 < pageStarts.size() ? pageStarts.get(pageIndex + 1) : rows.size();
        double x = format.getImageableX() + (format.getImageableWidth() - width) / 2;
        ReceiptRenderer.draw(g, rows, from, to, x, format.getImageableY(), width);
        return PAGE_EXISTS;
    }

    /** The first row of each page. A page always gets at least one row, so printing always ends. */
    static List<Integer> pageStarts(List<Row> rows, double pageHeight) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        double used = 0;
        for (int i = 0; i < rows.size(); i++) {
            double height = rows.get(i).height();
            if (used > 0 && used + height > pageHeight) {
                starts.add(i);
                used = 0;
            }
            used += height;
        }
        return starts;
    }
}
