package com.virpemart.billing.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * A checked backup file, described so the owner can decide whether to restore it.
 *
 * @param savedAt    when the file was last written
 * @param billCount  number of bills in it
 * @param lastBillAt time of its newest bill, or null if it has no bills
 */
public record BackupInfo(Path file, LocalDateTime savedAt, long billCount, LocalDateTime lastBillAt) {
}
