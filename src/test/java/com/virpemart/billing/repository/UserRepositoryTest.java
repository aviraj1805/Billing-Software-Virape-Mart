package com.virpemart.billing.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DatabaseException;
import com.virpemart.billing.db.TestDatabases;
import com.virpemart.billing.model.Role;
import com.virpemart.billing.model.User;

class UserRepositoryTest {

    private static final String NOW = "2026-09-30T10:00:00";

    @TempDir
    Path temp;

    private Database database;
    private final UserRepository users = new UserRepository();

    @BeforeEach
    void setUp() {
        database = TestDatabases.migrated(temp);
    }

    @Test
    void insertsAndFindsUsersIgnoringCase() {
        User saved = database.inTransaction(c -> users.insert(c, "Baba", "Baba Virpe", "hash", Role.OWNER, NOW));

        User found = database.query(c -> users.findByUsername(c, "baba")).orElseThrow();

        assertEquals(saved, found);
        assertTrue(found.isOwner());
        assertEquals(found, database.query(c -> users.findById(c, saved.id())).orElseThrow());
        assertEquals(1, database.query(users::count));
    }

    @Test
    void unknownUserIsEmpty() {
        assertTrue(database.query(c -> users.findByUsername(c, "nobody")).isEmpty());
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        database.runInTransaction(c -> users.insert(c, "helper", "Helper", "hash", Role.STAFF, NOW));

        assertThrows(DatabaseException.class, () ->
                database.runInTransaction(c -> users.insert(c, "HELPER", "Other", "hash", Role.STAFF, NOW)));
    }
}
