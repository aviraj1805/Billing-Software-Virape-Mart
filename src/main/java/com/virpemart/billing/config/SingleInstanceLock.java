package com.virpemart.billing.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Makes sure only one copy of the app uses the data folder at a time.
 *
 * <p>Two copies writing bills at once could confuse bill numbers, so the second copy is told
 * that the app is already open. Windows releases the lock automatically if the app crashes.
 */
public final class SingleInstanceLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private SingleInstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Tries to take the lock.
     *
     * @return the lock, or empty if another copy of the app already holds it
     * @throws IOException if the lock file cannot be opened at all
     */
    public static Optional<SingleInstanceLock> tryAcquire(Path lockFile) throws IOException {
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return Optional.empty();
            }
            return Optional.of(new SingleInstanceLock(channel, lock));
        } catch (OverlappingFileLockException e) {
            // Already locked by this same program (only happens in tests).
            channel.close();
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
