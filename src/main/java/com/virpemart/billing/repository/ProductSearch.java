package com.virpemart.billing.repository;

/**
 * What to search for in the product list.
 *
 * @param text            words typed by the user; every word must match the name, Marathi name,
 *                        pack size or code. Blank means "all products".
 * @param categoryId      only this category, or null for all
 * @param includeInactive also show switched-off products
 * @param limit           maximum number of results
 */
public record ProductSearch(String text, Long categoryId, boolean includeInactive, int limit) {
}
