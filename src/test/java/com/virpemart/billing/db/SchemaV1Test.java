package com.virpemart.billing.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that the database itself protects the money rules, even if app code has a bug.
 */
class SchemaV1Test {

    private static final String NOW = "'2026-09-30T10:00:00'";

    @TempDir
    Path temp;

    private Database database;

    @BeforeEach
    void setUp() {
        database = TestDatabases.migrated(temp);
        run("INSERT INTO users (id, username, display_name, password_hash, role, created_at, updated_at)"
                + " VALUES (1, 'owner', 'Owner', 'x', 'OWNER', " + NOW + ", " + NOW + ")");
        run("INSERT INTO customers (id, customer_no, name, created_at, updated_at)"
                + " VALUES (1, 'C0001', 'Ramesh', " + NOW + ", " + NOW + ")");
    }

    @Test
    void allTablesExist() {
        for (String table : List.of("settings", "users", "categories", "products", "customers", "bills",
                "bill_items", "bill_payments", "customer_ledger", "audit_log", "schema_version")) {
            assertEquals(1, count("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'"),
                    "missing table " + table);
        }
    }

    // ---------------------------------------------------------------- bills

    @Test
    void validBillIsAccepted() {
        assertDoesNotThrow(() -> insertBill(1, "1", 24975, 25, 25000, 20000, 5000));
    }

    @Test
    void billTotalsMustAddUp() {
        assertRejected(() -> insertBill(1, "1", 24975, 25, 99999, 99999, 0), "total must be subtotal + round-off");
        assertRejected(() -> insertBill(2, "1", 24975, 25, 25000, 20000, 1), "paid + to-account must equal total");
    }

    @Test
    void walkInBillCannotGoOnAccount() {
        assertRejected(() -> insertBill(1, "NULL", 1000, 0, 1000, 500, 500), "walk-in credit");
    }

    @Test
    void roundOffMustBeWithinHalfARupee() {
        assertRejected(() -> insertBill(1, "1", 1000, 100, 1100, 1100, 0), "round-off of one rupee");
    }

    @Test
    void billNumbersAreUnique() {
        insertBill(1, "1", 1000, 0, 1000, 1000, 0);
        assertRejected(() -> insertBill(1, "1", 2000, 0, 2000, 2000, 0), "duplicate bill number");
    }

    @Test
    void savedBillCannotBeChangedOrDeleted() {
        insertBill(1, "1", 1000, 0, 1000, 1000, 0);

        assertMessage(() -> run("UPDATE bills SET total_paise = 1, subtotal_paise = 1 WHERE bill_no = 1"),
                "cannot be changed");
        assertMessage(() -> run("DELETE FROM bills WHERE bill_no = 1"), "cannot be deleted");
    }

    @Test
    void billCanBeCancelledOnceWithDetails() {
        insertBill(1, "1", 1000, 0, 1000, 1000, 0);

        assertRejected(() -> run("UPDATE bills SET status = 'CANCELLED' WHERE bill_no = 1"),
                "cancel without reason");
        run("UPDATE bills SET status = 'CANCELLED', cancel_reason = 'Wrong item', cancelled_by = 1, cancelled_at = "
                + NOW + " WHERE bill_no = 1");
        assertEquals(1, count("SELECT COUNT(*) FROM bills WHERE status = 'CANCELLED'"));

        assertMessage(() -> run("UPDATE bills SET status = 'FINAL', cancel_reason = NULL, cancelled_by = NULL,"
                + " cancelled_at = NULL WHERE bill_no = 1"), "cannot be changed");
    }

    @Test
    void billMustBelongToRealUserAndCustomer() {
        assertRejected(() -> run("INSERT INTO bills (bill_no, created_at, customer_id, subtotal_paise, round_off_paise,"
                + " total_paise, paid_paise, to_account_paise, created_by) VALUES (5, " + NOW + ", 1, 0, 0, 0, 0, 0, 99)"),
                "unknown user");
        assertRejected(() -> insertBill(6, "42", 0, 0, 0, 0, 0), "unknown customer");
    }

    // ---------------------------------------------------------------- bill lines and payments

    @Test
    void billLinesAndPaymentsCannotBeChangedOrDeleted() {
        insertBill(1, "1", 1000, 0, 1000, 1000, 0);
        run("INSERT INTO bill_items (bill_id, line_no, name, unit, qty_milli, rate_paise, line_total_paise)"
                + " VALUES (1, 1, 'Sugar', 'KG', 250, 4000, 1000)");
        run("INSERT INTO bill_payments (bill_id, mode, amount_paise) VALUES (1, 'CASH', 1000)");

        assertMessage(() -> run("UPDATE bill_items SET rate_paise = 1"), "cannot be changed");
        assertMessage(() -> run("DELETE FROM bill_items"), "cannot be deleted");
        assertMessage(() -> run("UPDATE bill_payments SET amount_paise = 1"), "cannot be changed");
        assertMessage(() -> run("DELETE FROM bill_payments"), "cannot be deleted");
    }

    @Test
    void packedItemsNeedWholeQuantities() {
        insertBill(1, "1", 1000, 0, 1000, 1000, 0);

        assertRejected(() -> run("INSERT INTO bill_items (bill_id, line_no, name, unit, qty_milli, rate_paise,"
                + " line_total_paise) VALUES (1, 1, 'Soap', 'PCS', 1500, 1000, 1500)"), "1.5 pieces");
        assertDoesNotThrow(() -> run("INSERT INTO bill_items (bill_id, line_no, name, unit, qty_milli, rate_paise,"
                + " line_total_paise) VALUES (1, 1, 'Soap', 'PCS', 2000, 500, 1000)"));
    }

    @Test
    void unknownPaymentModeIsRejected() {
        insertBill(1, "1", 1000, 0, 1000, 1000, 0);
        assertRejected(() -> run("INSERT INTO bill_payments (bill_id, mode, amount_paise) VALUES (1, 'CHEQUE', 1000)"),
                "cheque is not a mode");
    }

    // ---------------------------------------------------------------- ledger

    @Test
    void ledgerEntriesCannotBeChangedOrDeleted() {
        run("INSERT INTO customer_ledger (customer_id, entry_type, amount_paise, created_at, created_by)"
                + " VALUES (1, 'OPENING', 50000, " + NOW + ", 1)");

        assertMessage(() -> run("UPDATE customer_ledger SET amount_paise = 1"), "cannot be changed");
        assertMessage(() -> run("DELETE FROM customer_ledger"), "cannot be deleted");
    }

    @Test
    void ledgerSignsFollowTheEntryType() {
        assertRejected(() -> run("INSERT INTO customer_ledger (customer_id, entry_type, amount_paise, payment_mode,"
                + " created_at, created_by) VALUES (1, 'PAYMENT', 500, 'CASH', " + NOW + ", 1)"), "positive payment");
        assertRejected(() -> run("INSERT INTO customer_ledger (customer_id, entry_type, amount_paise,"
                + " created_at, created_by) VALUES (1, 'PAYMENT', -500, " + NOW + ", 1)"), "payment without mode");
        assertRejected(() -> run("INSERT INTO customer_ledger (customer_id, entry_type, amount_paise,"
                + " created_at, created_by) VALUES (1, 'SALE_CREDIT', 500, " + NOW + ", 1)"), "credit without bill");
        assertRejected(() -> run("INSERT INTO customer_ledger (customer_id, entry_type, amount_paise,"
                + " created_at, created_by) VALUES (1, 'OPENING', 0, " + NOW + ", 1)"), "zero amount");
    }

    @Test
    void balanceIsTheSumOfEntries() {
        insertBill(1, "1", 25000, 0, 25000, 5000, 20000);
        run("INSERT INTO customer_ledger (customer_id, entry_type, amount_paise, created_at, created_by)"
                + " VALUES (1, 'OPENING', 50000, " + NOW + ", 1)");
        run("INSERT INTO customer_ledger (customer_id, entry_type, amount_paise, bill_id, created_at, created_by)"
                + " VALUES (1, 'SALE_CREDIT', 20000, 1, " + NOW + ", 1)");
        run("INSERT INTO customer_ledger (customer_id, entry_type, amount_paise, payment_mode, created_at, created_by)"
                + " VALUES (1, 'PAYMENT', -30000, 'UPI', " + NOW + ", 1)");

        assertEquals(40000, count("SELECT SUM(amount_paise) FROM customer_ledger WHERE customer_id = 1"));
    }

    // ---------------------------------------------------------------- audit and products

    @Test
    void auditRecordsCannotBeChangedOrDeleted() {
        run("INSERT INTO audit_log (created_at, action) VALUES (" + NOW + ", 'TEST')");

        assertMessage(() -> run("UPDATE audit_log SET action = 'X'"), "cannot be changed");
        assertMessage(() -> run("DELETE FROM audit_log"), "cannot be deleted");
    }

    @Test
    void productCodesAreUniqueIgnoringCase() {
        run("INSERT INTO products (code, name, unit, rate_paise, created_at, updated_at)"
                + " VALUES ('SUG1', 'Sugar Loose', 'KG', 4400, " + NOW + ", " + NOW + ")");
        assertRejected(() -> run("INSERT INTO products (code, name, unit, rate_paise, created_at, updated_at)"
                + " VALUES ('sug1', 'Sugar Other', 'KG', 4400, " + NOW + ", " + NOW + ")"), "duplicate code");
        assertRejected(() -> run("INSERT INTO products (code, name, unit, rate_paise, created_at, updated_at)"
                + " VALUES ('X1', 'Bad unit', 'DOZEN', 100, " + NOW + ", " + NOW + ")"), "unknown unit");
    }

    @Test
    void billedProductCannotBeDeleted() {
        run("INSERT INTO products (id, code, name, unit, rate_paise, created_at, updated_at)"
                + " VALUES (1, 'SUG1', 'Sugar Loose', 'KG', 4000, " + NOW + ", " + NOW + ")");
        insertBill(1, "1", 1000, 0, 1000, 1000, 0);
        run("INSERT INTO bill_items (bill_id, line_no, product_id, name, unit, qty_milli, rate_paise, line_total_paise)"
                + " VALUES (1, 1, 1, 'Sugar Loose', 'KG', 250, 4000, 1000)");

        assertRejected(() -> run("DELETE FROM products WHERE id = 1"), "product used on a bill");
    }

    // ---------------------------------------------------------------- helpers

    private void insertBill(int billNo, String customerId, long subtotal, long roundOff, long total, long paid,
                            long toAccount) {
        run("INSERT INTO bills (id, bill_no, created_at, customer_id, subtotal_paise, round_off_paise, total_paise,"
                + " paid_paise, to_account_paise, created_by) VALUES (" + billNo + ", " + billNo + ", " + NOW + ", "
                + customerId + ", " + subtotal + ", " + roundOff + ", " + total + ", " + paid + ", " + toAccount + ", 1)");
    }

    private void run(String sql) {
        database.runInTransaction(c -> execute(c, sql));
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private long count(String sql) {
        return database.query(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    private static void assertRejected(Runnable action, String what) {
        assertThrows(DatabaseException.class, action::run, "should be rejected: " + what);
    }

    private static void assertMessage(Runnable action, String expectedText) {
        DatabaseException error = assertThrows(DatabaseException.class, action::run);
        assertTrue(error.getMessage().contains(expectedText),
                "expected message containing '" + expectedText + "' but was: " + error.getMessage());
    }
}
