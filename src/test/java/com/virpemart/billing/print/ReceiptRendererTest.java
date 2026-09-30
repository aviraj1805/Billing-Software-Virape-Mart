package com.virpemart.billing.print;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.virpemart.billing.model.PaperSize;
import com.virpemart.billing.model.ShopDetails;
import com.virpemart.billing.print.ReceiptLine.Pair;
import com.virpemart.billing.print.ReceiptLine.Style;
import com.virpemart.billing.print.ReceiptLine.Text;
import com.virpemart.billing.print.ReceiptRenderer.Row;

class ReceiptRendererTest {

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final ShopDetails SHOP = new ShopDetails("Virpe Mart", "विरपे मार्ट", List.of("Main Road"),
            null, List.of("Thank you"));

    @Test
    void longTextWrapsWithinThePaperWidth() {
        ReceiptRenderer renderer = new ReceiptRenderer(PaperSize.ROLL_58);
        String longName = "Fortune Kachi Ghani Pure Mustard Oil Pouch 1 litre special offer pack";
        Receipt receipt = new Receipt("t", List.of(new Text(longName, false, Style.NORMAL)));

        List<Row> rows = renderer.layout(receipt, FRC, renderer.textWidth());

        assertTrue(rows.size() > 1, "a long name wraps onto more lines");
        for (Row row : rows) {
            assertTrue(row.pieces().getFirst().layout().getVisibleAdvance() <= renderer.textWidth() + 0.5);
        }
    }

    @Test
    void amountSitsAgainstTheRightEdge() {
        ReceiptRenderer renderer = new ReceiptRenderer(PaperSize.ROLL_80);
        Receipt receipt = new Receipt("t", List.of(new Pair("Paid now (Cash)", "₹100.00", Style.NORMAL)));

        Row row = renderer.layout(receipt, FRC, renderer.textWidth()).getFirst();

        assertEquals(2, row.pieces().size());
        ReceiptRenderer.Placed amount = row.pieces().get(1);
        assertEquals(renderer.textWidth(), amount.x() + amount.layout().getAdvance(), 0.01);
    }

    @Test
    void widerPaperGivesAWiderBill() {
        assertTrue(new ReceiptRenderer(PaperSize.ROLL_58).textWidth() < new ReceiptRenderer(PaperSize.ROLL_80).textWidth());
        assertEquals(ReceiptRenderer.mm(72), new ReceiptRenderer(PaperSize.ROLL_80).textWidth(), 0.001);
        assertEquals(226.77, ReceiptRenderer.mm(80), 0.01);
    }

    @Test
    void previewPictureIsDrawn() {
        Receipt sample = ReceiptBuilder.sample(SHOP, LocalDateTime.of(2026, 9, 30, 10, 0));

        for (PaperSize paper : PaperSize.values()) {
            BufferedImage image = new ReceiptRenderer(paper).toImage(sample, 2);
            assertTrue(image.getWidth() > 100 && image.getHeight() > image.getWidth() / 2, paper.name());
            assertTrue(hasDarkPixels(image), "text was drawn on " + paper);
        }
    }

    @Test
    void longBillsAreSplitIntoPages() {
        List<Row> rows = List.of(row(10), row(10), row(10), row(10), row(10));

        assertEquals(List.of(0), ReceiptPrintable.pageStarts(rows, 100));
        assertEquals(List.of(0, 2, 4), ReceiptPrintable.pageStarts(rows, 25));
        assertEquals(List.of(0, 1, 2, 3, 4), ReceiptPrintable.pageStarts(rows, 5), "a too-tall row still prints");
    }

    @Test
    void windowsFontHasMarathiLettersAndTheRupeeSign() {
        Font font = ReceiptRenderer.baseFont();
        if (ReceiptRenderer.FONT_FAMILY.equals(font.getFamily(Locale.ENGLISH))) {
            assertEquals(-1, font.canDisplayUpTo("साखर तूर डाळ ₹"));
        }
    }

    private static Row row(double height) {
        return new Row(height * 0.8, height, List.of(), false);
    }

    private static boolean hasDarkPixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                if ((image.getRGB(x, y) & 0xFF) < 100) {
                    return true;
                }
            }
        }
        return false;
    }
}
