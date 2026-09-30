package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DatabaseCheck;
import com.virpemart.billing.db.PendingRestore;
import com.virpemart.billing.db.TestDatabases;
import com.virpemart.billing.model.BackupInfo;
import com.virpemart.billing.model.Cart;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.PaymentMode;
import com.virpemart.billing.model.PaymentPart;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Quantity;
import com.virpemart.billing.repository.AuditRepository;

class BackupServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private BackupService backups;
    private Path backupsDir;
    private Product sugar;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        backups = fixture.services.backups();
        backupsDir = backups.backupsFolder();
        sugar = fixture.services.products().create(new ProductInput("Sugar", null, null, "kg", null, "44", null));
    }

    private void saveABill() {
        Cart cart = new Cart();
        cart.addProduct(sugar, Quantity.ONE);
        fixture.services.billing().save(new BillRequest(null, null, cart.lines(),
                List.of(new PaymentPart(PaymentMode.CASH, Money.parse("44")))));
    }

    private static long bills(Path file) {
        DatabaseCheck.Result result = DatabaseCheck.inspect(file);
        assertTrue(result.ok(), "backup is a good database: " + result);
        return result.billCount();
    }

    private BackupService on(LocalDate day) {
        Clock clock = Clock.fixed(day.atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant(),
                ZoneId.of("Asia/Kolkata"));
        return new BackupService(fixture.database, backupsDir, new AuditRepository(), fixture.session, clock);
    }

    // ------------------------------------------------------------------ automatic

    @Test
    void theAppOpeningMakesOneBackupADayAndClosingUpdatesIt() {
        Path today = backupsDir.resolve("auto-2026-09-30.db");

        backups.backupOnStartup();
        assertEquals(0, bills(today));

        saveABill();
        backups.backupOnStartup();
        assertEquals(0, bills(today), "opening again the same day keeps the morning backup");

        backups.backupOnClose();
        assertEquals(1, bills(today), "closing brings it up to date");
        assertTrue(backups.lastAutomaticBackup().isPresent());
    }

    @Test
    void closingWithoutAnOpeningBackupStillMakesOne() {
        backups.backupOnClose();

        assertTrue(Files.exists(backupsDir.resolve("auto-2026-09-30.db")));
        assertFalse(Files.exists(backupsDir.resolve("auto-2026-09-30.db.tmp")));
    }

    @Test
    void keepsThirtyDaysAndOneBackupPerMonthForAYear() {
        LocalDate today = LocalDate.of(2026, 9, 30);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = LocalDate.of(2025, 8, 1); !day.isAfter(today); day = day.plusDays(1)) {
            days.add(day);
        }

        List<LocalDate> remove = BackupService.daysToRemove(days, today);

        assertEquals(days.size() - 30 - 11, remove.size(), "30 recent days + 11 older month ends are kept");
        assertFalse(remove.contains(LocalDate.of(2026, 9, 1)), "within 30 days");
        assertFalse(remove.contains(LocalDate.of(2026, 8, 31)), "last of August");
        assertFalse(remove.contains(LocalDate.of(2025, 10, 31)), "last of October last year");
        assertTrue(remove.contains(LocalDate.of(2026, 8, 30)));
        assertTrue(remove.contains(LocalDate.of(2025, 9, 30)), "more than 12 months ago");
        assertTrue(remove.contains(LocalDate.of(2025, 8, 31)));
    }

    @Test
    void aMonthsLastBackupIsKeptEvenIfItIsNotTheLastDay() {
        List<LocalDate> remove = BackupService.daysToRemove(
                List.of(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20)), LocalDate.of(2026, 9, 30));

        assertEquals(List.of(LocalDate.of(2026, 7, 10)), remove);
    }

    @Test
    void oldBackupsAreRemovedButOtherFilesAreNeverTouched() throws IOException {
        Files.createDirectories(backupsDir);
        for (String name : List.of("auto-2026-07-15.db", "auto-2026-07-31.db", "pre-upgrade-v2-to-v3.db",
                "auto-notes.db", "notes.txt", "VirpeMart-backup-2026-07-01-100000.db")) {
            Files.writeString(backupsDir.resolve(name), "x");
        }

        backups.backupOnStartup();

        assertFalse(Files.exists(backupsDir.resolve("auto-2026-07-15.db")));
        for (String name : List.of("auto-2026-07-31.db", "pre-upgrade-v2-to-v3.db", "auto-notes.db", "notes.txt",
                "VirpeMart-backup-2026-07-01-100000.db", "auto-2026-09-30.db")) {
            assertTrue(Files.exists(backupsDir.resolve(name)), name);
        }
    }

    @Test
    void automaticBackupProblemsDoNotStopTheApp() throws IOException {
        Path notAFolder = temp.resolve("file-in-the-way");
        Files.writeString(notAFolder, "x");
        BackupService broken = new BackupService(fixture.database, notAFolder, new AuditRepository(), fixture.session,
                TestDatabases.FIXED_CLOCK);

        broken.backupOnStartup();
        broken.backupOnClose();
        assertEquals(Optional.empty(), broken.lastAutomaticBackup());
    }

    // ------------------------------------------------------------------ back up now

    @Test
    void backupNowWritesToTheChosenFolderAndIsRecorded() throws IOException {
        saveABill();
        Path pendrive = Files.createDirectories(temp.resolve("pendrive"));

        Path file = backups.backupNow(pendrive);

        assertEquals(pendrive, file.getParent());
        assertTrue(file.getFileName().toString().startsWith("VirpeMart-backup-2026-09-30-"));
        assertEquals(1, bills(file));
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_MADE'"));
    }

    @Test
    void backupNowNeedsAFolderAndTheOwner() {
        assertThrows(BusinessRuleException.class, () -> backups.backupNow(temp.resolve("unplugged")));

        fixture.signInStaff();
        assertThrows(PermissionDeniedException.class, () -> backups.backupNow(temp));
    }

    // ------------------------------------------------------------------ restore

    @Test
    void restorePutsTheBackupInPlaceAtTheNextStartAndKeepsTheOldData() throws IOException {
        Path pendrive = Files.createDirectories(temp.resolve("pendrive"));
        Path backup = backups.backupNow(pendrive); // no bills yet
        saveABill();

        BackupInfo info = backups.restore(backup);

        assertEquals(0, info.billCount());
        assertTrue(PendingRestore.isPending(fixture.database.file()));
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM bills"), "nothing changes until the app starts again");

        // The app starts again.
        Optional<String> note = PendingRestore.finish(fixture.database.file(), backupsDir, TestDatabases.FIXED_CLOCK);

        assertTrue(note.orElseThrow().contains(backup.getFileName().toString()), note.get());
        assertFalse(PendingRestore.isPending(fixture.database.file()));
        assertEquals(0L, (long) new Database(fixture.database.file()).query(c -> DatabaseCheck.inspect(c).billCount()));
        try (Stream<Path> files = Files.list(backupsDir)) {
            Path replaced = files.filter(f -> f.getFileName().toString().startsWith("replaced-")
                    && f.getFileName().toString().endsWith(".db")).findFirst().orElseThrow();
            assertEquals(1, bills(replaced), "the data that was replaced is kept");
        }
    }

    @Test
    void onlyGoodVirpeMartBackupsCanBeRestored() throws Exception {
        Path garbage = Files.writeString(temp.resolve("photo.db"), "not a database at all");
        assertTrue(assertThrows(BusinessRuleException.class, () -> backups.checkBackup(garbage)).getMessage()
                .contains("not a Virpe Mart backup"));

        Database other = new Database(temp.resolve("other.db"));
        other.runInTransaction(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE notes (text TEXT)");
            }
        });
        assertThrows(BusinessRuleException.class, () -> backups.checkBackup(other.file()));

        assertThrows(BusinessRuleException.class, () -> backups.checkBackup(fixture.database.file()),
                "the live data file is not a backup");
        assertThrows(BusinessRuleException.class, () -> backups.checkBackup(temp.resolve("missing.db")));
        assertFalse(PendingRestore.isPending(fixture.database.file()));
    }

    @Test
    void aBackupFromANewerAppVersionIsRefused() throws IOException {
        Path newer = backups.backupNow(Files.createDirectories(temp.resolve("newer")));
        new Database(newer).runInTransaction(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO schema_version (version, description, checksum, applied_at)"
                        + " VALUES (99, 'future', 'x', '2027-01-01T00:00:00')");
            }
        });

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> backups.restore(newer));
        assertTrue(error.getMessage().contains("newer version"), error.getMessage());
    }

    @Test
    void aDamagedBackupIsRefused() throws IOException {
        Path copy = backups.backupNow(Files.createDirectories(temp.resolve("damaged")));
        byte[] bytes = Files.readAllBytes(copy);
        for (int i = 100; i < bytes.length; i += 7) {
            bytes[i] = (byte) 0xAB; // scribble over most of the file
        }
        Files.write(copy, bytes);

        assertThrows(BusinessRuleException.class, () -> backups.checkBackup(copy));
    }

    @Test
    void restoringIsForTheOwner() throws IOException {
        Path backup = backups.backupNow(Files.createDirectories(temp.resolve("b")));
        fixture.signInStaff();

        assertThrows(PermissionDeniedException.class, () -> backups.restore(backup));
    }

    @Test
    void theNewestGoodBackupIsFoundWhenTheDataIsDamaged() throws IOException {
        on(LocalDate.of(2026, 9, 28)).backupOnStartup();
        saveABill();
        on(LocalDate.of(2026, 9, 29)).backupOnStartup();
        Files.writeString(backupsDir.resolve("replaced-2026-09-30-100000.db"), "damaged data that was replaced");
        Files.setLastModifiedTime(backupsDir.resolve("auto-2026-09-28.db"),
                java.nio.file.attribute.FileTime.from(Instant.parse("2026-09-28T10:00:00Z")));
        Files.setLastModifiedTime(backupsDir.resolve("auto-2026-09-29.db"),
                java.nio.file.attribute.FileTime.from(Instant.parse("2026-09-29T10:00:00Z")));

        BackupInfo newest = BackupService.newestGoodBackup(backupsDir).orElseThrow();

        assertEquals("auto-2026-09-29.db", newest.file().getFileName().toString());
        assertEquals(1, newest.billCount());
        assertEquals(Optional.empty(), BackupService.newestGoodBackup(temp.resolve("nothing-here")));
    }
}
