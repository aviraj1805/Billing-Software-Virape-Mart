package com.virpemart.billing.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DatabaseBackup;
import com.virpemart.billing.db.DatabaseCheck;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.db.PendingRestore;
import com.virpemart.billing.model.BackupInfo;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;

/**
 * Backups of the shop data.
 *
 * <ul>
 *   <li>Automatic: one file per day, "auto-YYYY-MM-DD.db" in the backups folder on this laptop. It is made when the
 *       app opens (if today's does not exist yet) and brought up to date when the app closes. Daily files are kept
 *       for 30 days; older ones are kept only as the last file of each month, for 12 months.</li>
 *   <li>"Back up now": the owner copies the data to any folder, for example a pendrive.</li>
 *   <li>Restore: a backup is checked, then put in place the next time the app starts ({@link PendingRestore}).
 *       The data it replaces is kept.</li>
 * </ul>
 * Automatic backups never stop the app: a problem is written to the log.
 */
public final class BackupService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
    private static final String AUTO_PREFIX = "auto-";
    private static final int KEEP_DAILY_DAYS = 30;
    private static final int KEEP_MONTHLY_MONTHS = 12;
    private static final DateTimeFormatter MANUAL_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private final Database database;
    private final Path backupsDir;
    private final AuditRepository audit;
    private final Session session;
    private final Clock clock;

    public BackupService(Database database, Path backupsDir, AuditRepository audit, Session session, Clock clock) {
        this.database = database;
        this.backupsDir = backupsDir.toAbsolutePath().normalize();
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    /** The folder on this laptop that holds the automatic backups. */
    public Path backupsFolder() {
        return backupsDir;
    }

    // ------------------------------------------------------------------ automatic

    /** When the app opens: makes today's backup if there is none yet, then removes backups that are too old. */
    public void backupOnStartup() {
        try {
            Path today = automaticFile(LocalDate.now(clock));
            if (!Files.exists(today)) {
                DatabaseBackup.snapshot(database, today);
                LOG.info("Automatic backup made: {}", today);
            }
            removeOldAutomaticBackups();
        } catch (RuntimeException | IOException e) {
            LOG.warn("Automatic backup at startup failed", e);
        }
    }

    /** When the app closes: brings today's backup up to date with everything done today. */
    public void backupOnClose() {
        Path today = automaticFile(LocalDate.now(clock));
        Path temporary = today.resolveSibling(today.getFileName() + ".tmp");
        try {
            Files.deleteIfExists(temporary);
            DatabaseBackup.snapshot(database, temporary);
            Files.move(temporary, today, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.info("Automatic backup updated: {}", today);
        } catch (RuntimeException | IOException e) {
            LOG.warn("Automatic backup at close failed", e);
        }
    }

    /** When the newest automatic backup was written, if there is one. */
    public Optional<LocalDateTime> lastAutomaticBackup() {
        try {
            return automaticBackups().keySet().stream().max(Comparator.naturalOrder())
                    .map(this::automaticFile).map(BackupService::modifiedAt);
        } catch (IOException e) {
            LOG.warn("Could not list backups", e);
            return Optional.empty();
        }
    }

    private Path automaticFile(LocalDate day) {
        return backupsDir.resolve(AUTO_PREFIX + day + ".db");
    }

    /** Automatic backup files by their day. Other files in the folder are never touched. */
    private Map<LocalDate, Path> automaticBackups() throws IOException {
        Map<LocalDate, Path> files = new HashMap<>();
        if (!Files.isDirectory(backupsDir)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir, AUTO_PREFIX + "*.db")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                try {
                    files.put(LocalDate.parse(name.substring(AUTO_PREFIX.length(), name.length() - 3)), file);
                } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
                    // not one of ours; leave it alone
                }
            }
        }
        return files;
    }

    private void removeOldAutomaticBackups() throws IOException {
        Map<LocalDate, Path> files = automaticBackups();
        for (LocalDate day : daysToRemove(List.copyOf(files.keySet()), LocalDate.now(clock))) {
            Files.deleteIfExists(files.get(day));
            LOG.info("Old automatic backup removed: {}", files.get(day));
        }
    }

    /**
     * Which daily backups are no longer needed: keep every day of the last 30 days, and before that only the last
     * backup of each month, for the last 12 months.
     */
    static List<LocalDate> daysToRemove(List<LocalDate> days, LocalDate today) {
        LocalDate keepDailyFrom = today.minusDays(KEEP_DAILY_DAYS - 1);
        YearMonth keepMonthlyFrom = YearMonth.from(today).minusMonths(KEEP_MONTHLY_MONTHS - 1);
        Map<YearMonth, LocalDate> lastOfMonth = new HashMap<>();
        for (LocalDate day : days) {
            lastOfMonth.merge(YearMonth.from(day), day, (a, b) -> a.isAfter(b) ? a : b);
        }
        List<LocalDate> remove = new ArrayList<>();
        for (LocalDate day : days) {
            boolean recent = !day.isBefore(keepDailyFrom);
            boolean monthly = day.equals(lastOfMonth.get(YearMonth.from(day)))
                    && !YearMonth.from(day).isBefore(keepMonthlyFrom);
            if (!recent && !monthly) {
                remove.add(day);
            }
        }
        remove.sort(Comparator.naturalOrder());
        return remove;
    }

    // ------------------------------------------------------------------ by the owner

    /**
     * Copies the data into the chosen folder, for example a pendrive. Owner only.
     *
     * @return the new backup file
     */
    public Path backupNow(Path folder) {
        User user = session.requireOwner();
        if (folder == null || !Files.isDirectory(folder)) {
            throw new BusinessRuleException("That folder cannot be found. Check that the pendrive is plugged in.");
        }
        Path target = folder.resolve("VirpeMart-backup-" + LocalDateTime.now(clock).format(MANUAL_STAMP) + ".db");
        try {
            DatabaseBackup.snapshot(database, target);
        } catch (RuntimeException e) {
            LOG.warn("Backup to {} failed", target, e);
            throw new BusinessRuleException("The backup could not be written to " + folder
                    + ". Check that the pendrive is plugged in and not full, then try again.");
        }
        database.runInTransaction(c -> audit.insert(c, user.id(), "BACKUP_MADE", null, null,
                "Backup saved to " + target, DbTime.now(clock)));
        return target;
    }

    /**
     * Checks a backup file and describes it, so the owner can decide whether to restore it. Owner only.
     * Nothing is changed.
     *
     * @throws BusinessRuleException if the file cannot be restored, with the reason
     */
    public BackupInfo checkBackup(Path file) {
        session.requireOwner();
        if (file == null) {
            throw new BusinessRuleException("Please choose a backup file.");
        }
        if (file.toAbsolutePath().normalize().equals(database.file())) {
            throw new BusinessRuleException("That is the data file the app is using now, not a backup.");
        }
        DatabaseCheck.Result result = DatabaseCheck.inspect(file);
        if (!result.ok()) {
            throw new BusinessRuleException(switch (result.problem()) {
                case NOT_A_DATABASE -> "This file is not a Virpe Mart backup. Please choose a file ending in .db"
                        + " from the backups folder or your pendrive.";
                case DAMAGED -> "This backup file is damaged and cannot be used. Please choose another backup.";
                case NOT_OURS -> "This file is not a Virpe Mart backup. Please choose another file.";
            });
        }
        int current = database.query(c -> DatabaseCheck.inspect(c).schemaVersion());
        if (result.schemaVersion() > current) {
            throw new BusinessRuleException("This backup was made by a newer version of the app. "
                    + "Install the newer version first, then restore it.");
        }
        return new BackupInfo(file, modifiedAt(file), result.billCount(),
                result.lastBillAt() == null ? null : DbTime.parse(result.lastBillAt()));
    }

    /**
     * Prepares the backup to replace the current data the next time the app starts. The app must then be closed.
     * Owner only. A copy of the current data is kept, so this can be undone by restoring that copy.
     */
    public BackupInfo restore(Path file) {
        User user = session.requireOwner();
        BackupInfo info = checkBackup(file);
        database.runInTransaction(c -> audit.insert(c, user.id(), "BACKUP_RESTORE_STARTED", null, null,
                "Restore of " + file + " prepared; it finishes when the app starts again", DbTime.now(clock)));
        try {
            PendingRestore.stage(file, database.file(), "backup " + file.getFileName());
        } catch (IOException e) {
            LOG.warn("Could not prepare restore of {}", file, e);
            throw new BusinessRuleException("The backup could not be copied. Check that the file is still there, "
                    + "then try again.");
        }
        return info;
    }

    /**
     * The newest backup in the folder that is undamaged, for when the data file itself is damaged.
     * Files named "replaced-..." are skipped: they are data that was replaced earlier.
     */
    public static Optional<BackupInfo> newestGoodBackup(Path backupsDir) {
        if (!Files.isDirectory(backupsDir)) {
            return Optional.empty();
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir, "*.db")) {
            stream.forEach(files::add);
        } catch (IOException e) {
            LOG.warn("Could not list backups in {}", backupsDir, e);
            return Optional.empty();
        }
        files.sort(Comparator.comparing(BackupService::modifiedAt).reversed());
        for (Path file : files) {
            if (file.getFileName().toString().startsWith("replaced-")) {
                continue;
            }
            DatabaseCheck.Result result = DatabaseCheck.inspect(file);
            if (result.ok()) {
                return Optional.of(new BackupInfo(file, modifiedAt(file), result.billCount(),
                        result.lastBillAt() == null ? null : DbTime.parse(result.lastBillAt())));
            }
        }
        return Optional.empty();
    }

    private static LocalDateTime modifiedAt(Path file) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return LocalDateTime.MIN;
        }
    }
}
