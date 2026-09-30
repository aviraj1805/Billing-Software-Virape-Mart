package com.virpemart.billing.service;

import java.time.Clock;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.Role;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.UserRepository;

/**
 * Finds the shop's owner account, creating it the first time, so the app can sign in automatically.
 *
 * <p>The user decided the shop needs no login screen (one laptop, no risk of other people using it). The app
 * therefore always works as the owner. Every change is still recorded in the audit log under this account.
 * The account's password hash is "!", which can never match any password.
 */
public final class OwnerBootstrap {

    /** Username of the owner account created on a new database. */
    public static final String USERNAME = "owner";
    private static final String DISPLAY_NAME = "Owner";
    private static final String UNUSABLE_PASSWORD_HASH = "!";

    private final Database database;
    private final UserRepository users;
    private final Clock clock;

    public OwnerBootstrap(Database database, UserRepository users, Clock clock) {
        this.database = database;
        this.users = users;
        this.clock = clock;
    }

    /** The first active owner account; a new "Owner" account is created if there is none. */
    public User ensureOwner() {
        return database.inTransaction(connection -> {
            var existing = users.findFirstActiveOwner(connection);
            if (existing.isPresent()) {
                return existing.get();
            }
            return users.insert(connection, USERNAME, DISPLAY_NAME, UNUSABLE_PASSWORD_HASH, Role.OWNER,
                    DbTime.now(clock));
        });
    }
}
