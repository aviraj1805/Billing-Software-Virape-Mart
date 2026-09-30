package com.virpemart.billing.print;

import java.util.List;
import java.util.stream.Collectors;

import com.virpemart.billing.print.ReceiptLine.Pair;
import com.virpemart.billing.print.ReceiptLine.Rule;
import com.virpemart.billing.print.ReceiptLine.Text;

/**
 * A bill laid out as lines, ready to be drawn on paper or on screen by {@link ReceiptRenderer}.
 *
 * @param title name of the print job, for example "Virpe Mart bill 12"
 */
public record Receipt(String title, List<ReceiptLine> lines) {

    public Receipt {
        lines = List.copyOf(lines);
    }

    /** The receipt as simple text, one line per entry. Used by tests and log messages. */
    public String toPlainText() {
        return lines.stream().map(line -> switch (line) {
            case Text text -> text.text();
            case Pair pair -> pair.left() + " | " + pair.right();
            case Rule rule -> "----";
        }).collect(Collectors.joining("\n"));
    }
}
