package com.virpemart.billing;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.virpemart.billing.config.AppInfo;
import com.virpemart.billing.config.AppPaths;
import com.virpemart.billing.config.SingleInstanceLock;
import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.MigrationException;
import com.virpemart.billing.db.MigrationRunner;
import com.virpemart.billing.repository.UserRepository;
import com.virpemart.billing.service.DevOwnerBootstrap;
import com.virpemart.billing.service.Session;

/**
 * The startup steps, in order:
 * <ol>
 *   <li>find and create the app folders;</li>
 *   <li>start logging into the logs folder;</li>
 *   <li>make sure no other copy of the app is running;</li>
 *   <li>open the database and upgrade its schema (with a backup first);</li>
 *   <li>prepare the session (development folders sign in a development owner automatically).</li>
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
            Database database = new Database(paths.databaseFile());
            MigrationRunner.Result migration =
                    new MigrationRunner(database, MigrationRunner.DEFAULT_LOCATION, paths.backupsDir(), clock).migrate();

            Session session = new Session();
            if (paths.isOverridden()) {
                session.signIn(new DevOwnerBootstrap(database, new UserRepository(), clock).ensureDevOwner());
                log.info("Development data folder: signed in as {}", DevOwnerBootstrap.USERNAME);
            }

            log.info("Startup complete, schema version {}", migration.toVersion());
            return new AppContext(paths, database, clock, session, migration.toVersion(), lock);
        } catch (RuntimeException e) {
            log.error("Startup failed", e);
            closeQuietly(lock, e);
            String reason = (e instanceof MigrationException) ? e.getMessage() + "\n\n" : "";
            throw new StartupException("The app could not open its database. Nothing was changed.\n\n"
                    + reason + "Details were saved in the log folder:\n" + paths.logsDir(), e);
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
