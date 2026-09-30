package com.virpemart.billing.model;

/** What a signed-in person is allowed to do. See the permissions table in docs/requirements.md. */
public enum Role {
    /** The store owner: can do everything. */
    OWNER,
    /** The helper: can bill and handle customers, but cannot change products, cancel bills or change settings. */
    STAFF
}
