package com.virpemart.billing;

import java.nio.file.Path;
import java.util.Optional;

import com.virpemart.billing.model.BackupInfo;

/**
 * The shop data file is damaged, so the app cannot start. The newest good backup, if there is one, is offered to
 * the owner to restore.
 */
public class DamagedDataException extends StartupException {

    private static final long serialVersionUID = 1L;

    private final transient Path databaseFile;
    private final transient BackupInfo newestGoodBackup;

    public DamagedDataException(Path databaseFile, Optional<BackupInfo> newestGoodBackup) {
        super("The shop data file is damaged and cannot be opened.");
        this.databaseFile = databaseFile;
        this.newestGoodBackup = newestGoodBackup.orElse(null);
    }

    public Path databaseFile() {
        return databaseFile;
    }

    public Optional<BackupInfo> newestGoodBackup() {
        return Optional.ofNullable(newestGoodBackup);
    }
}
