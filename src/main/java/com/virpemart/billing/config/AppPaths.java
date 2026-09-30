package com.virpemart.billing.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Where the app keeps its files.
 *
 * <ul>
 *   <li>On the store laptop: {@code %LOCALAPPDATA%\VirpeMart\}.</li>
 *   <li>During development: the folder given by the {@code virpemart.dataDir} system property
 *       (set by {@code mvnw javafx:run} to {@code <repo>\dev-data}) or the
 *       {@code VIRPEMART_DATA_DIR} environment variable.</li>
 * </ul>
 *
 * <p>Inside the base folder: {@code data\virpemart.db}, {@code backups\} and {@code logs\}.
 */
public final class AppPaths {

    /** System property that overrides the base folder. */
    public static final String DATA_DIR_PROPERTY = "virpemart.dataDir";

    /** Environment variable that overrides the base folder. */
    public static final String DATA_DIR_ENV = "VIRPEMART_DATA_DIR";

    private static final String DATABASE_FILE = "virpemart.db";

    private final Path baseDir;
    private final boolean overridden;

    private AppPaths(Path baseDir, boolean overridden) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.overridden = overridden;
    }

    /** Resolves the folders from the real system properties and environment. */
    public static AppPaths fromSystem() {
        return resolve(System.getProperty(DATA_DIR_PROPERTY), System.getenv());
    }

    /**
     * Resolves the folders. Kept separate from {@link #fromSystem()} so tests can pass their own values.
     *
     * @param propertyValue value of the {@code virpemart.dataDir} system property, or null
     * @param environment   environment variables
     */
    static AppPaths resolve(String propertyValue, Map<String, String> environment) {
        if (propertyValue != null && !propertyValue.isBlank()) {
            return new AppPaths(Path.of(propertyValue.strip()), true);
        }
        String envValue = environment.get(DATA_DIR_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return new AppPaths(Path.of(envValue.strip()), true);
        }
        String localAppData = environment.get("LOCALAPPDATA");
        Path root = (localAppData != null && !localAppData.isBlank())
                ? Path.of(localAppData)
                : Path.of(System.getProperty("user.home"), "AppData", "Local");
        return new AppPaths(root.resolve("VirpeMart"), false);
    }

    /** Uses the given folder as the base folder. Intended for tests. */
    public static AppPaths forBaseDir(Path baseDir) {
        return new AppPaths(baseDir, true);
    }

    /** Creates the data, backups and logs folders if they do not exist yet. */
    public AppPaths createDirectories() {
        try {
            Files.createDirectories(dataDir());
            Files.createDirectories(backupsDir());
            Files.createDirectories(logsDir());
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create app folders under " + baseDir, e);
        }
    }

    public Path baseDir() {
        return baseDir;
    }

    public Path dataDir() {
        return baseDir.resolve("data");
    }

    public Path databaseFile() {
        return dataDir().resolve(DATABASE_FILE);
    }

    public Path backupsDir() {
        return baseDir.resolve("backups");
    }

    public Path logsDir() {
        return baseDir.resolve("logs");
    }

    /** The file used to make sure only one copy of the app runs at a time. */
    public Path lockFile() {
        return dataDir().resolve("app.lock");
    }

    /**
     * True when the folder was chosen by an override (development or tests), false for the
     * real shop folder under {@code %LOCALAPPDATA%}.
     */
    public boolean isOverridden() {
        return overridden;
    }
}
