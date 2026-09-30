package com.virpemart.billing.service;

import java.time.Clock;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.Role;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.UserRepository;

/**
 * Development only: makes sure a "dev-owner" user exists, so screens that need an owner can be
 * built and tested before the real login screen arrives in Phase 7.
 *
 * <p>The account's password hash is "!", which can never match any password, so nobody can sign in
 * as this user through a login screen. It is only used on development data folders.
 */
public final class DevOwnerBootstrap {

    public static final String USERNAME = "dev-owner";
    private static final String UNUSABLE_PASSWORD_HASH = "!";

    private final Database database;
    private final UserRepository users;
    private final Clock clock;

    public DevOwnerBootstrap(Database database, UserRepository users, Clock clock) {
        this.database = database;
        this.users = users;
        this.clock = clock;
    }

    /** Returns the development owner, creating it the first time. */
    public User ensureDevOwner() {
        return database.inTransaction(connection -> {
            var existing = users.findByUsername(connection, USERNAME);
            if (existing.isPresent()) {
                return existing.get();
            }
            return users.insert(connection, USERNAME, "Developer (Owner)", UNUSABLE_PASSWORD_HASH,
                    Role.OWNER, DbTime.now(clock));
        });
    }
}
