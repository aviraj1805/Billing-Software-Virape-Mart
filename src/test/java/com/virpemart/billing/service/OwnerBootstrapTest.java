package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.TestDatabases;
import com.virpemart.billing.model.Role;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.UserRepository;

class OwnerBootstrapTest {

    @Test
    void createsTheOwnerOnceOnly(@TempDir Path temp) {
        Database database = TestDatabases.migrated(temp);
        OwnerBootstrap bootstrap = new OwnerBootstrap(database, new UserRepository(), TestDatabases.FIXED_CLOCK);

        User first = bootstrap.ensureOwner();
        User second = bootstrap.ensureOwner();

        assertTrue(first.isOwner());
        assertEquals("owner", first.username());
        assertEquals("Owner", first.displayName());
        assertEquals(first, second);
        assertEquals(1L, (long) database.query(new UserRepository()::count));
    }

    @Test
    void usesAnExistingOwnerSuchAsTheDevelopmentOwner(@TempDir Path temp) {
        Database database = TestDatabases.migrated(temp);
        UserRepository users = new UserRepository();
        database.runInTransaction(c -> users.insert(c, "helper", "Helper", "!", Role.STAFF, "2026-09-30T10:00:00"));
        User devOwner = database.inTransaction(c ->
                users.insert(c, "dev-owner", "Developer (Owner)", "!", Role.OWNER, "2026-09-30T10:00:00"));

        User signedIn = new OwnerBootstrap(database, users, TestDatabases.FIXED_CLOCK).ensureOwner();

        assertEquals(devOwner, signedIn);
        assertEquals(2L, (long) database.query(users::count), "no new account is made");
    }
}
