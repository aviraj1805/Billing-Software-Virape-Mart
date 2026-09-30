package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.TestDatabases;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.UserRepository;

class DevOwnerBootstrapTest {

    @Test
    void createsTheDevOwnerOnceOnly(@TempDir Path temp) {
        Database database = TestDatabases.migrated(temp);
        DevOwnerBootstrap bootstrap = new DevOwnerBootstrap(database, new UserRepository(), TestDatabases.FIXED_CLOCK);

        User first = bootstrap.ensureDevOwner();
        User second = bootstrap.ensureDevOwner();

        assertTrue(first.isOwner());
        assertEquals(first, second);
        assertEquals(1L, (long) database.query(new UserRepository()::count));
    }
}
