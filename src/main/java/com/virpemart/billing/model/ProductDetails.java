package com.virpemart.billing.model;

/**
 * The checked, cleaned-up details of a product, ready to be saved.
 *
 * @param nameMr     Marathi name, or null
 * @param categoryId category, or null for none
 * @param packSize   free text such as "1 kg" or "500 g", or null
 * @param rate       selling rate per kg, per litre or per piece
 * @param mrp        printed MRP, or null if the product has none
 */
public record ProductDetails(
        String name,
        String nameMr,
        Long categoryId,
        Unit unit,
        String packSize,
        Money rate,
        Money mrp) {

    /** True if the selling rate is higher than the MRP, which is usually a typing mistake. */
    public boolean rateAboveMrp() {
        return mrp != null && rate.compareTo(mrp) > 0;
    }
}
