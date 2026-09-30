package com.virpemart.billing.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPathsTest {

    @Test
    void usesLocalAppDataByDefault() {
        AppPaths paths = AppPaths.resolve(null, Map.of("LOCALAPPDATA", "C:\\Users\\Shop\\AppData\\Local"));

        assertEquals(Path.of("C:\\Users\\Shop\\AppData\\Local\\VirpeMart"), paths.baseDir());
        assertEquals(Path.of("C:\\Users\\Shop\\AppData\\Local\\VirpeMart\\data\\virpemart.db"), paths.databaseFile());
        assertFalse(paths.isOverridden(), "the real shop folder is not an override");
    }

    @Test
    void systemPropertyOverridesEverything(@TempDir Path temp) {
        AppPaths paths = AppPaths.resolve(temp.toString(),
                Map.of("LOCALAPPDATA", "C:\\x", AppPaths.DATA_DIR_ENV, "C:\\y"));

        assertEquals(temp.toAbsolutePath(), paths.baseDir());
        assertTrue(paths.isOverridden());
    }

    @Test
    void environmentVariableOverridesDefault(@TempDir Path temp) {
        AppPaths paths = AppPaths.resolve("  ", Map.of("LOCALAPPDATA", "C:\\x", AppPaths.DATA_DIR_ENV, temp.toString()));

        assertEquals(temp.toAbsolutePath(), paths.baseDir());
        assertTrue(paths.isOverridden());
    }

    @Test
    void createsAllFolders(@TempDir Path temp) {
        AppPaths paths = AppPaths.forBaseDir(temp.resolve("app")).createDirectories();

        assertTrue(Files.isDirectory(paths.dataDir()));
        assertTrue(Files.isDirectory(paths.backupsDir()));
        assertTrue(Files.isDirectory(paths.logsDir()));
    }
}
