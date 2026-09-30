package com.virpemart.billing.service;

/**
 * Product details exactly as the user typed them (in the form or in an imported sheet).
 * {@link ProductService#check(ProductInput)} turns this into checked {@link com.virpemart.billing.model.ProductDetails}.
 *
 * @param categoryId chosen category, or null for none
 * @param unit       "kg", "litre", "pcs" or a similar spelling
 * @param rate       rate text such as "44" or "44.50"
 * @param mrp        MRP text, or blank
 */
public record ProductInput(
        String name,
        String nameMr,
        Long categoryId,
        String unit,
        String packSize,
        String rate,
        String mrp) {
}
