package com.virpemart.billing.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceLockTest {

    @Test
    void secondCopyIsRefusedUntilTheFirstCloses(@TempDir Path temp) throws Exception {
        Path lockFile = temp.resolve("app.lock");

        Optional<SingleInstanceLock> first = SingleInstanceLock.tryAcquire(lockFile);
        assertTrue(first.isPresent(), "first copy gets the lock");

        assertTrue(SingleInstanceLock.tryAcquire(lockFile).isEmpty(), "second copy is refused");

        first.get().close();
        Optional<SingleInstanceLock> again = SingleInstanceLock.tryAcquire(lockFile);
        assertTrue(again.isPresent(), "lock is available again after closing");
        again.get().close();
    }
}
