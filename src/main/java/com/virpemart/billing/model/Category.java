package com.virpemart.billing.model;

/** A group of products, such as "Dal &amp; Pulses". Switched-off categories stay on old products. */
public record Category(long id, String name, boolean active) {
}
