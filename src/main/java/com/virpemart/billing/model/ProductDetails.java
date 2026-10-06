package com.virpemart.billing.model;

/**
 * The checked, cleaned-up details of a product, ready to be saved.
 *
 * @param nameMr     Marathi name, or null
 * @param categoryId category, or null for none
 * @param packSize   free text such as "1 kg" or "500 g", or null
 * @param rate       selling rate per kg, per litre or per piece
 * @param mrp        printed MRP, or null. No longer typed or shown; an edited product keeps the MRP it had
 */
public record ProductDetails(
        String name,
        String nameMr,
        Long categoryId,
        Unit unit,
        String packSize,
        Money rate,
        Money mrp) {

    /** The same details with the given MRP (used to keep a product's stored MRP when it is edited). */
    public ProductDetails withMrp(Money keptMrp) {
        return new ProductDetails(name, nameMr, categoryId, unit, packSize, rate, keptMrp);
    }
}
