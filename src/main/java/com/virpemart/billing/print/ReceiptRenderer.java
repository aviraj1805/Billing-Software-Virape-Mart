package com.virpemart.billing.print;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.virpemart.billing.model.PaperSize;
import com.virpemart.billing.print.ReceiptLine.Pair;
import com.virpemart.billing.print.ReceiptLine.Rule;
import com.virpemart.billing.print.ReceiptLine.Style;
import com.virpemart.billing.print.ReceiptLine.Text;

/**
 * Draws a {@link Receipt} with Java 2D, on paper or into a picture for the on-screen preview.
 *
 * <p>Text uses the Windows font "Nirmala UI", which has both English and Marathi (Devanagari) letters and the
 * rupee sign. Java's text layout joins Marathi letters correctly. Long text wraps to the paper width.
 * Sizes are in points: 72 points = 1 inch = 25.4 mm.
 */
public final class ReceiptRenderer {

    /** Font with English and Marathi letters, included in Windows 8 and later. */
    public static final String FONT_FAMILY = "Nirmala UI";

    private final PaperSize paper;
    private final Font normal;
    private final Font bold;
    private final Font large;
    private final Font small;

    /** One printed line after wrapping: text pieces placed across the width, or a dashed rule. */
    record Row(double ascent, double height, List<Placed> pieces, boolean rule) {
    }

    /** A piece of text and how far from the left edge it starts. */
    record Placed(TextLayout layout, double x) {
    }

    public ReceiptRenderer(PaperSize paper) {
        this.paper = paper;
        float size = switch (paper) {
            case ROLL_58 -> 8f;
            case ROLL_80 -> 9.5f;
            case A4 -> 11f;
        };
        Font base = baseFont().deriveFont(size);
        normal = base;
        bold = base.deriveFont(Font.BOLD);
        large = base.deriveFont(Font.BOLD, size * 1.35f);
        small = base.deriveFont(size * 0.9f);
    }

    /** "Nirmala UI" if Windows has it, otherwise Java's general font, which borrows letters from other fonts. */
    static Font baseFont() {
        Font font = new Font(FONT_FAMILY, Font.PLAIN, 10);
        return FONT_FAMILY.equals(font.getFamily(Locale.ENGLISH)) ? font : new Font(Font.DIALOG, Font.PLAIN, 10);
    }

    public PaperSize paper() {
        return paper;
    }

    /** Width of the paper itself, in points. A4 is a sheet, so the full sheet width. */
    public double paperWidth() {
        return switch (paper) {
            case ROLL_58 -> mm(58);
            case ROLL_80 -> mm(80);
            case A4 -> mm(210);
        };
    }

    /**
     * Width of the printed text block, in points. Receipt printers cannot print right to the edge of the roll:
     * a 58 mm roll prints about 48 mm wide and an 80 mm roll about 72 mm wide. On A4 the bill is a 150 mm column.
     */
    public double textWidth() {
        return switch (paper) {
            case ROLL_58 -> mm(48);
            case ROLL_80 -> mm(72);
            case A4 -> mm(150);
        };
    }

    /** Converts millimetres to points. */
    public static double mm(double millimetres) {
        return millimetres * 72 / 25.4;
    }

    // ------------------------------------------------------------------ layout

    /** Fits every line to {@code width}, wrapping long text. */
    List<Row> layout(Receipt receipt, FontRenderContext frc, double width) {
        List<Row> rows = new ArrayList<>();
        for (ReceiptLine line : receipt.lines()) {
            switch (line) {
                case Text text -> wrap(rows, text.text(), font(text.style()), frc, width, text.centered(), null);
                case Pair pair -> wrap(rows, pair.left(), font(pair.style()), frc, width, false, pair.right());
                case Rule rule -> {
                    double height = small.getSize2D() * 0.9;
                    rows.add(new Row(0, height, List.of(), true));
                }
            }
        }
        return rows;
    }

    /** Total height of the receipt in points. */
    public double height(Receipt receipt, FontRenderContext frc) {
        return layout(receipt, frc, textWidth()).stream().mapToDouble(Row::height).sum();
    }

    /**
     * Breaks text into rows no wider than {@code width}. If {@code right} is given, it is placed on the first row
     * against the right edge, and the text on the left wraps before reaching it.
     */
    private static void wrap(List<Row> rows, String text, Font font, FontRenderContext frc, double width,
                             boolean centered, String right) {
        TextLayout rightLayout = right == null || right.isEmpty() ? null : new TextLayout(printable(right, font), font, frc);
        double gap = font.getSize2D();
        double leftWidth = rightLayout == null ? width : Math.max(width * 0.3, width - rightLayout.getAdvance() - gap);
        String safe = printable(text, font);
        if (safe.isBlank()) {
            safe = " ";
        }
        AttributedString attributed = new AttributedString(safe);
        attributed.addAttribute(TextAttribute.FONT, font);
        LineBreakMeasurer measurer = new LineBreakMeasurer(attributed.getIterator(), frc);
        boolean first = true;
        while (measurer.getPosition() < safe.length()) {
            TextLayout layout = measurer.nextLayout((float) (first ? leftWidth : width));
            List<Placed> pieces = new ArrayList<>();
            double x = centered ? Math.max(0, (width - layout.getVisibleAdvance()) / 2) : 0;
            pieces.add(new Placed(layout, x));
            double ascent = layout.getAscent();
            double descent = layout.getDescent() + layout.getLeading();
            if (first && rightLayout != null) {
                pieces.add(new Placed(rightLayout, width - rightLayout.getAdvance()));
                ascent = Math.max(ascent, rightLayout.getAscent());
                descent = Math.max(descent, rightLayout.getDescent() + rightLayout.getLeading());
            }
            rows.add(new Row(ascent, ascent + descent, pieces, false));
            first = false;
        }
    }

    /** If the font has no rupee sign, "Rs." is printed instead, so no empty boxes appear. */
    private static String printable(String text, Font font) {
        if (text.indexOf('₹') >= 0 && !font.canDisplay('₹')) {
            return text.replace("₹", "Rs.");
        }
        return text;
    }

    private Font font(Style style) {
        return switch (style) {
            case NORMAL -> normal;
            case BOLD -> bold;
            case LARGE -> large;
            case SMALL -> small;
        };
    }

    // ------------------------------------------------------------------ drawing

    /** Draws rows {@code from} (inclusive) to {@code to} (exclusive), starting at the top-left corner x, y. */
    static void draw(Graphics2D g, List<Row> rows, int from, int to, double x, double y, double width) {
        g.setColor(Color.BLACK);
        double top = y;
        for (int i = from; i < to; i++) {
            Row row = rows.get(i);
            if (row.rule()) {
                double middle = top + row.height() / 2;
                g.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[] {2f, 1.5f}, 0f));
                g.draw(new Line2D.Double(x, middle, x + width, middle));
            } else {
                for (Placed piece : row.pieces()) {
                    piece.layout().draw(g, (float) (x + piece.x()), (float) (top + row.ascent()));
                }
            }
            top += row.height();
        }
    }

    /**
     * Draws the receipt into a picture for the on-screen preview: white paper, black text.
     *
     * @param scale picture pixels per point; 2 gives a sharp preview
     */
    public BufferedImage toImage(Receipt receipt, double scale) {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        double width = textWidth();
        double margin = mm(4);
        List<Row> rows = layout(receipt, frc, width);
        double height = rows.stream().mapToDouble(Row::height).sum();
        int pixelWidth = (int) Math.ceil((width + 2 * margin) * scale);
        int pixelHeight = (int) Math.ceil((height + 2 * margin) * scale);
        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, pixelWidth, pixelHeight);
            g.scale(scale, scale);
            draw(g, rows, 0, rows.size(), margin, margin, width);
        } finally {
            g.dispose();
        }
        return image;
    }
}
