package com.virpemart.billing.model;

/**
 * A product the store sells. Each pack size is its own product, for example
 * "Sugar" (loose, per kg) and "Sugar 1 kg" (packet).
 *
 * @param code         automatic code such as P0001
 * @param nameMr       Marathi name, or null
 * @param categoryId   category, or null
 * @param categoryName category name for display, or null
 * @param packSize     pack size text such as "1 kg", or null
 * @param mrp          MRP, or null
 * @param active       false when switched off (hidden from billing, kept for old bills)
 */
public record Product(
        long id,
        String code,
        String name,
        String nameMr,
        Long categoryId,
        String categoryName,
        Unit unit,
        String packSize,
        Money rate,
        Money mrp,
        boolean active) {

    /** Name with pack size, for example "Tata Salt 1 kg". */
    public String displayName() {
        return packSize == null ? name : name + " " + packSize;
    }
}
