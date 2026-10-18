package com.virpemart.billing.model;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * How a product is sold.
 *
 * <ul>
 *   <li>{@link #KG} and {@link #L}: loose items, rate per kg or per litre, quantity with decimals (0.250).</li>
 *   <li>{@link #PCS}: packed items, rate per piece or packet, whole quantities only.</li>
 * </ul>
 *
 * <p>Short names "kg", "Ltr" and "Pkt" (the user's wording, version 1.3.0) are used where a unit is still shown, such
 * as the Products screen and the one-off item window. Since version 1.4.0 the Billing screen and the printed bill show
 * no unit after a quantity (see {@link Quantity#toBillString(Unit)}).
 */
public enum Unit {

    KG("kg", "kg (loose)", true),
    L("Ltr", "litre (loose)", true),
    PCS("Pkt", "piece / packet", false);

    /** Every spelling people commonly type, mapped to a unit. Keys are lower case without spaces or dots. */
    private static final Map<String, Unit> SPELLINGS = Map.ofEntries(
            Map.entry("kg", KG), Map.entry("kgs", KG), Map.entry("kilo", KG), Map.entry("kilos", KG),
            Map.entry("kilogram", KG), Map.entry("kilograms", KG), Map.entry("किलो", KG),
            Map.entry("kgloose", KG),
            Map.entry("l", L), Map.entry("ltr", L), Map.entry("ltrs", L), Map.entry("lit", L),
            Map.entry("litre", L), Map.entry("litres", L), Map.entry("liter", L), Map.entry("liters", L),
            Map.entry("लिटर", L), Map.entry("litreloose", L),
            Map.entry("pc", PCS), Map.entry("pcs", PCS), Map.entry("piece", PCS), Map.entry("pieces", PCS),
            Map.entry("nos", PCS), Map.entry("no", PCS), Map.entry("pkt", PCS), Map.entry("pkts", PCS),
            Map.entry("packet", PCS), Map.entry("packets", PCS), Map.entry("pack", PCS), Map.entry("packs", PCS),
            Map.entry("unit", PCS), Map.entry("units", PCS), Map.entry("नग", PCS), Map.entry("piece/packet", PCS));

    private final String shortLabel;
    private final String description;
    private final boolean loose;

    Unit(String shortLabel, String description, boolean loose) {
        this.shortLabel = shortLabel;
        this.description = description;
        this.loose = loose;
    }

    /** Short name used after numbers: "kg" in "0.5 kg", "Ltr" in "5 Ltr", "Pkt" in "3 Pkt". */
    public String shortLabel() {
        return shortLabel;
    }

    /** Longer name for lists, for example "kg (loose)". */
    public String description() {
        return description;
    }

    /** True for kg and litre, where quantities may have decimals. */
    public boolean isLoose() {
        return loose;
    }

    /**
     * Understands common spellings such as "kg", "Kgs", "ltr", "litre", "pcs", "packet" or "nos".
     *
     * @return the unit, or empty if the text is not recognised
     */
    public static Optional<Unit> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String key = text.strip().toLowerCase(Locale.ROOT)
                .replace(" ", "").replace(".", "").replace("(", "").replace(")", "");
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(SPELLINGS.get(key));
    }
}
