package com.virpemart.billing;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.virpemart.billing.config.AppInfo;
import com.virpemart.billing.config.AppPaths;
import com.virpemart.billing.config.SingleInstanceLock;
import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DatabaseCheck;
import com.virpemart.billing.db.DatabaseException;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.db.MigrationException;
import com.virpemart.billing.db.MigrationRunner;
import com.virpemart.billing.db.PendingRestore;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.UserRepository;
import com.virpemart.billing.service.BackupService;
import com.virpemart.billing.service.OwnerBootstrap;
import com.virpemart.billing.service.Services;
import com.virpemart.billing.service.Session;

/**
 * The startup steps, in order:
 * <ol>
 *   <li>find and create the app folders;</li>
 *   <li>start logging into the logs folder;</li>
 *   <li>make sure no other copy of the app is running;</li>
 *   <li>finish a backup restore, if one is waiting;</li>
 *   <li>check that the data file is not damaged;</li>
 *   <li>open the database and upgrade its schema (with a backup first);</li>
 *   <li>sign in the owner (the shop uses no login screen);</li>
 *   <li>make today's automatic backup.</li>
 * </ol>
 */
public final class Startup {

    /** System property read by logback.xml to find the logs folder. */
    static final String LOG_DIR_PROPERTY = "virpemart.logDir";

    private Startup() {
    }

    public static AppContext start() throws StartupException {
        AppPaths paths;
        try {
            paths = AppPaths.fromSystem().createDirectories();
        } catch (RuntimeException e) {
            throw new StartupException("The app could not create its data folder. "
                    + "Please check that the disk is not full.", e);
        }

        // Must happen before the first logger is created, so log files go to the right folder.
        System.setProperty(LOG_DIR_PROPERTY, paths.logsDir().toString());
        Logger log = LoggerFactory.getLogger(Startup.class);
        log.info("Starting {} {} with data folder {}", AppInfo.name(), AppInfo.version(), paths.baseDir());

        SingleInstanceLock lock = acquireLock(paths, log);
        try {
            Clock clock = Clock.systemDefaultZone();
            Optional<String> restored = finishPendingRestore(paths, clock, log);
            Database database = new Database(paths.databaseFile());
            checkNotDamaged(paths, database, log);
            MigrationRunner.Result migration =
                    new MigrationRunner(database, MigrationRunner.DEFAULT_LOCATION, paths.backupsDir(), clock).migrate();

            Session session = new Session();
            User owner = new OwnerBootstrap(database, new UserRepository(), clock).ensureOwner();
            session.signIn(owner);
            log.info("Signed in as {}", owner.username());
            restored.ifPresent(note -> database.runInTransaction(c -> new AuditRepository().insert(c, null,
                    "BACKUP_RESTORED", null, null, note, DbTime.now(clock))));

            Services services = Services.create(database, session, clock, paths.backupsDir());
            services.backups().backupOnStartup();
            log.info("Startup complete, schema version {}", migration.toVersion());
            return new AppContext(paths, database, clock, session, services, migration.toVersion(), lock);
        } catch (StartupException e) {
            closeQuietly(lock, e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Startup failed", e);
            closeQuietly(lock, e);
            String reason = (e instanceof MigrationException) ? e.getMessage() + "\n\n" : "";
            throw new StartupException("The app could not open its database. Nothing was changed.\n\n"
                    + reason + "Details were saved in the log folder:\n" + paths.logsDir(), e);
        }
    }

    /** Puts a backup in place if the owner chose "Restore" last time. The replaced data is kept. */
    private static Optional<String> finishPendingRestore(AppPaths paths, Clock clock, Logger log)
            throws StartupException {
        try {
            Optional<String> restored = PendingRestore.finish(paths.databaseFile(), paths.backupsDir(), clock);
            restored.ifPresent(note -> log.info("Backup restore finished: {}", note));
            return restored;
        } catch (IOException e) {
            log.error("Could not finish restoring a backup", e);
            throw new StartupException("A backup could not be put in place. Close other programs and open the app "
                    + "again. Nothing was deleted.\n\nDetails were saved in the log folder:\n" + paths.logsDir(), e);
        }
    }

    /** Stops the start if SQLite finds the data file damaged, offering the newest good backup instead. */
    private static void checkNotDamaged(AppPaths paths, Database database, Logger log) throws DamagedDataException {
        if (!Files.exists(database.file())) {
            return; // first start: the file is created by the upgrade step
        }
        DatabaseCheck.Problem problem;
        try {
            problem = database.query(DatabaseCheck::inspect).problem();
        } catch (DatabaseException e) {
            log.error("The data file cannot be opened", e);
            problem = DatabaseCheck.Problem.NOT_A_DATABASE;
        }
        if (problem == DatabaseCheck.Problem.DAMAGED || problem == DatabaseCheck.Problem.NOT_A_DATABASE) {
            log.error("The data file {} is damaged ({})", database.file(), problem);
            throw new DamagedDataException(database.file(), BackupService.newestGoodBackup(paths.backupsDir()));
        }
    }

    private static SingleInstanceLock acquireLock(AppPaths paths, Logger log) throws StartupException {
        Optional<SingleInstanceLock> lock;
        try {
            lock = SingleInstanceLock.tryAcquire(paths.lockFile());
        } catch (IOException e) {
            log.error("Could not open lock file {}", paths.lockFile(), e);
            throw new StartupException("The app could not start because its data folder is not accessible.", e);
        }
        if (lock.isEmpty()) {
            log.warn("Another copy of the app is already running");
            throw new StartupException("Virpe Mart Billing is already open.\n\n"
                    + "Look for it on the taskbar at the bottom of the screen.");
        }
        return lock.get();
    }

    private static void closeQuietly(SingleInstanceLock lock, Exception original) {
        try {
            lock.close();
        } catch (IOException e) {
            original.addSuppressed(e);
        }
    }
}
