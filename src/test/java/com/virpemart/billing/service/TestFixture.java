package com.virpemart.billing.service;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.TestDatabases;
import com.virpemart.billing.model.Role;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.UserRepository;

/**
 * A real temporary database with an owner and a staff user, and all services wired up.
 * The owner is signed in to start with.
 */
final class TestFixture {

    final Database database;
    final Session session = new Session();
    final Services services;
    final FakePrinter printer = new FakePrinter();
    final User owner;
    final User staff;

    TestFixture(Path temp) {
        database = TestDatabases.migrated(temp);
        UserRepository users = new UserRepository();
        owner = database.inTransaction(c -> users.insert(c, "owner", "Owner", "x", Role.OWNER, "2026-09-30T10:00:00"));
        staff = database.inTransaction(c -> users.insert(c, "helper", "Helper", "x", Role.STAFF, "2026-09-30T10:00:00"));
        session.signIn(owner);
        services = Services.create(database, session, TestDatabases.FIXED_CLOCK, printer);
    }

    void signInStaff() {
        session.signIn(staff);
    }

    void signInOwner() {
        session.signIn(owner);
    }

    long count(String sql) {
        return database.query(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    /**
     * Saves a bill whose whole amount goes on the customer's account, with its SALE_CREDIT khata entry,
     * directly in the database. Used until the billing service exists.
     */
    void addBillOnAccount(long customerId, long billNo, long amountPaise) {
        database.runInTransaction(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO bills (bill_no, created_at, customer_id, subtotal_paise, round_off_paise,"
                        + " total_paise, paid_paise, to_account_paise, created_by) VALUES (" + billNo
                        + ", '2026-09-30T10:00:00', " + customerId + ", " + amountPaise + ", 0, " + amountPaise
                        + ", 0, " + amountPaise + ", " + owner.id() + ")");
                s.executeUpdate("INSERT INTO customer_ledger (customer_id, entry_type, amount_paise, bill_id,"
                        + " created_at, created_by) VALUES (" + customerId + ", 'SALE_CREDIT', " + amountPaise
                        + ", last_insert_rowid(), '2026-09-30T10:00:00', " + owner.id() + ")");
            }
        });
    }

    /** Puts a product on a saved bill directly in the database, so "is it billed?" rules can be tested. */
    void putOnABill(long productId) {
        database.runInTransaction(c -> {
            try (Statement s = c.createStatement()) {
                long billNo = 1 + count("SELECT COUNT(*) FROM bills");
                s.executeUpdate("INSERT INTO bills (bill_no, created_at, subtotal_paise, round_off_paise, total_paise,"
                        + " paid_paise, to_account_paise, created_by) VALUES (" + billNo
                        + ", '2026-09-30T10:00:00', 4400, 0, 4400, 4400, 0, " + owner.id() + ")");
                s.executeUpdate("INSERT INTO bill_items (bill_id, line_no, product_id, name, unit, qty_milli,"
                        + " rate_paise, line_total_paise) VALUES (last_insert_rowid(), 1, " + productId
                        + ", 'x', 'KG', 1000, 4400, 4400)");
            }
        });
    }
}
