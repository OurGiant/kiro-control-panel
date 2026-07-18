package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectoryWatcherTest {

    @TempDir
    Path tempDir;

    @Test
    @Timeout(5)
    void notifiesListenerWhenFileCreatedInWatchedDirectory() throws IOException, InterruptedException {
        DirectoryWatcher watcher = new DirectoryWatcher();
        CountDownLatch latch = new CountDownLatch(1);
        watcher.addListener(latch::countDown);

        watcher.watch(tempDir);
        Files.writeString(tempDir.resolve("new-file.txt"), "hello");

        assertTrue(latch.await(3, TimeUnit.SECONDS), "listener should fire after a file is created");
    }

    @Test
    void watchOnMissingDirectoryDoesNotThrow() throws IOException {
        DirectoryWatcher watcher = new DirectoryWatcher();

        watcher.watch(tempDir.resolve("does-not-exist"));
        // No exception means the no-op path was taken correctly.
    }

    @Test
    void watchOnNullDoesNotThrow() throws IOException {
        DirectoryWatcher watcher = new DirectoryWatcher();

        watcher.watch(null);
    }

    @Test
    @Timeout(5)
    void watchingSameDirectoryTwiceStillOnlyRegistersOnce() throws IOException, InterruptedException {
        DirectoryWatcher watcher = new DirectoryWatcher();
        CountDownLatch latch = new CountDownLatch(1);
        watcher.addListener(latch::countDown);

        watcher.watch(tempDir);
        watcher.watch(tempDir);
        Files.writeString(tempDir.resolve("new-file.txt"), "hello");

        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    void unwatchedDirectoryDoesNotNotify() throws IOException, InterruptedException {
        Path unwatchedDir = Files.createDirectory(tempDir.resolve("unwatched"));
        DirectoryWatcher watcher = new DirectoryWatcher();
        CountDownLatch latch = new CountDownLatch(1);
        watcher.addListener(latch::countDown);

        Files.writeString(unwatchedDir.resolve("new-file.txt"), "hello");

        assertFalse(latch.await(600, TimeUnit.MILLISECONDS), "listener should not fire for an unregistered directory");
    }
}
