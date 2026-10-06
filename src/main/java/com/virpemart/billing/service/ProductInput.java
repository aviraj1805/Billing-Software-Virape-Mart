package com.virpemart.billing.service;

/**
 * Product details exactly as the user typed them (in the form or in an imported sheet).
 * {@link ProductService#check(ProductInput)} turns this into checked {@link com.virpemart.billing.model.ProductDetails}.
 *
 * @param unit       "kg", "litre", "pcs" or a similar spelling
 * @param rate       rate text such as "44" or "44.50"
 *
 * <p>There is no MRP and no category: they are not shown or typed any more (the user's choice). The MRP and
 * category a product already has are kept.
 */
public record ProductInput(
        String name,
        String nameMr,
        String unit,
        String packSize,
        String rate) {
}
