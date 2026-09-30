package com.virpemart.billing.model;

/**
 * One line of a bill that is being made.
 *
 * @param productId    the product, or null for a one-off item typed on the bill
 * @param nameMr       Marathi name, or null
 * @param packSize     pack size text, or null
 * @param rate         rate used on this bill (per kg, litre or piece)
 * @param productRate  the product's saved rate, or null for one-off items; differs from {@code rate}
 *                     when the rate was changed for this bill
 * @param mrp          MRP, or null
 */
public record CartLine(
        Long productId,
        String name,
        String nameMr,
        Unit unit,
        String packSize,
        Quantity quantity,
        Money rate,
        Money productRate,
        Money mrp) {

    /** Quantity times rate, rounded to the paisa. */
    public Money lineTotal() {
        return rate.times(quantity);
    }

    /** How much the customer saves compared to MRP on this line (zero if no MRP or rate is not lower). */
    public Money savings() {
        if (mrp == null || mrp.compareTo(rate) <= 0) {
            return Money.ZERO;
        }
        return mrp.minus(rate).times(quantity);
    }

    /** True if the rate was changed for this bill. */
    public boolean rateChanged() {
        return productRate != null && !productRate.equals(rate);
    }

    /** Name with pack size, for example "Tata Salt 1 kg". */
    public String displayName() {
        return packSize == null ? name : name + " " + packSize;
    }

    public CartLine withQuantity(Quantity newQuantity) {
        return new CartLine(productId, name, nameMr, unit, packSize, newQuantity, rate, productRate, mrp);
    }

    public CartLine withRate(Money newRate) {
        return new CartLine(productId, name, nameMr, unit, packSize, quantity, newRate, productRate, mrp);
    }
}
