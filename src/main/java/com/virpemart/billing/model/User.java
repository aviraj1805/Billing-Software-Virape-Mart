package com.virpemart.billing.model;

/**
 * A person who can sign in to the app. The password hash is deliberately not part of this record,
 * so it never travels around the app or ends up in logs.
 */
public record User(long id, String username, String displayName, Role role, boolean active) {

    public boolean isOwner() {
        return role == Role.OWNER;
    }
}
