package com.virpemart.billing;

import java.io.IOException;
import java.time.Clock;

import com.virpemart.billing.config.AppPaths;
import com.virpemart.billing.config.SingleInstanceLock;
import com.virpemart.billing.db.Database;
import com.virpemart.billing.service.Services;
import com.virpemart.billing.service.Session;

/**
 * Everything the running app shares: folders, database, clock, the signed-in session and the services.
 * It is created once by {@link Startup} and handed to the screens. Screens should use
 * {@link #services()} rather than the database directly.
 *
 * @param schemaVersion database schema version after startup upgrades
 */
public record AppContext(
        AppPaths paths,
        Database database,
        Clock clock,
        Session session,
        Services services,
        int schemaVersion,
        SingleInstanceLock instanceLock) implements AutoCloseable {

    /** Releases the single-instance lock. Called when the app closes. */
    @Override
    public void close() throws IOException {
        instanceLock.close();
    }
}
