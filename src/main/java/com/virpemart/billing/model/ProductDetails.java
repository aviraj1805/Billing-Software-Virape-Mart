package com.virpemart.billing.model;

/**
 * The checked, cleaned-up details of a product, ready to be saved.
 *
 * @param nameMr     Marathi name, or null
 * @param categoryId category, or null. No longer chosen or shown; an edited product keeps the category it had
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

    /**
     * The same details, but with the category and MRP the product already has. Neither is shown or typed any more,
     * so an edit must keep them exactly as they are stored.
     */
    public ProductDetails keepingHiddenValuesOf(Product before) {
        return new ProductDetails(name, nameMr, before.categoryId(), unit, packSize, rate, before.mrp());
    }
}
