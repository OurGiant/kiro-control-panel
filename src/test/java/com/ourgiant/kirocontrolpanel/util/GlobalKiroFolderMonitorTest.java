package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalKiroFolderMonitorTest {

    @TempDir
    Path tempDir;

    @Test
    @Timeout(30)
    void firesCallbackWhenAFileChangesInTheRoot() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        new GlobalKiroFolderMonitor(tempDir, latch::countDown);

        Files.writeString(tempDir.resolve("mcp.json"), "{}");

        // Generous on purpose: macOS's WatchService implementation polls rather than
        // using native OS events, with much coarser latency than Linux's inotify-backed
        // one -- this isn't testing how fast the notification arrives, just that it does.
        assertTrue(latch.await(20, TimeUnit.SECONDS), "callback should fire after a file changes");
    }

    @Test
    @Timeout(30)
    void firesCallbackForAChangeInAPreExistingSubdirectory() throws IOException, InterruptedException {
        Path steeringDir = Files.createDirectory(tempDir.resolve("steering"));
        CountDownLatch latch = new CountDownLatch(1);
        new GlobalKiroFolderMonitor(tempDir, latch::countDown);

        Files.writeString(steeringDir.resolve("conventions.md"), "# hi");

        assertTrue(latch.await(20, TimeUnit.SECONDS), "callback should fire for a change nested under the root");
    }

    @Test
    @Timeout(30)
    void firesCallbackForAChangeInASubdirectoryCreatedAfterStartup() throws IOException, InterruptedException {
        AtomicInteger callCount = new AtomicInteger();
        CountDownLatch firstCallback = new CountDownLatch(1);
        CountDownLatch secondCallback = new CountDownLatch(1);
        CountDownLatch thirdCallback = new CountDownLatch(1);
        new GlobalKiroFolderMonitor(tempDir, () -> {
            switch (callCount.incrementAndGet()) {
                case 1 -> firstCallback.countDown();
                case 2 -> secondCallback.countDown();
                case 3 -> thirdCallback.countDown();
                default -> { }
            }
        });

        // WatchService doesn't recurse into subdirectories on its own -- a brand-new directory
        // needs its own explicit register() call, which only happens once the monitor has
        // processed the ENTRY_CREATE event for its *parent*. Each step below waits for the
        // callback confirming the previous step was processed (registration happens
        // synchronously in the poll loop before the debounced callback fires) before creating
        // the next nesting level, so this isn't racing the monitor's own registration.
        Path skillsDir = tempDir.resolve("skills");
        Files.createDirectory(skillsDir);
        assertTrue(firstCallback.await(20, TimeUnit.SECONDS), "callback should fire for the new top-level directory");

        Path mySkillDir = skillsDir.resolve("my-skill");
        Files.createDirectory(mySkillDir);
        assertTrue(secondCallback.await(20, TimeUnit.SECONDS),
            "callback should fire for a nested directory now that its parent is registered");

        Files.writeString(mySkillDir.resolve("SKILL.md"), "---\nname: x\n---\nbody");
        assertTrue(thirdCallback.await(20, TimeUnit.SECONDS),
            "callback should fire for a file written into the newly (recursively) registered subdirectory");
    }

    @Test
    @Timeout(30)
    void suppressesCallbackForAPathThisAppJustWrote() throws IOException, InterruptedException {
        Path selfWritten = tempDir.resolve("mcp.json");
        Path external = tempDir.resolve("steering.md");
        CountDownLatch latch = new CountDownLatch(1);
        new GlobalKiroFolderMonitor(tempDir, latch::countDown);

        SelfWriteTracker.markAboutToWrite(selfWritten);
        Files.writeString(selfWritten, "{}");

        // Bounded but well short of the 10s suppression window or the (still generous,
        // macOS-polling-driven) 20s timeout used elsewhere -- just confirms no callback
        // fires promptly, without making this test wait out the full window.
        assertFalse(latch.await(5, TimeUnit.SECONDS), "callback should not fire for a self-written path");

        Files.writeString(external, "unrelated change");
        assertTrue(latch.await(20, TimeUnit.SECONDS),
            "callback should still fire for an unrelated, non-self-written change");
    }

    @Test
    @Timeout(30)
    void neverReportsChangesUnderGitInternalDirectoryPresentAtStartup() throws IOException, InterruptedException {
        // Regression test for #60: GitAutoCommitter (#49) writes several files under .git/
        // per commit (index, COMMIT_EDITMSG, logs/HEAD, refs/, objects/...) that
        // SelfWriteTracker was never asked to track -- .git must be excluded from
        // monitoring entirely, not left to suppression to cover every file git might touch.
        Path gitDir = Files.createDirectory(tempDir.resolve(".git"));
        CountDownLatch latch = new CountDownLatch(1);
        new GlobalKiroFolderMonitor(tempDir, latch::countDown);

        Files.writeString(gitDir.resolve("index"), "not actually git's binary format, doesn't matter for this test");
        Path nestedRefs = Files.createDirectories(gitDir.resolve("refs").resolve("heads"));
        Files.writeString(nestedRefs.resolve("main"), "abc123");

        assertFalse(latch.await(5, TimeUnit.SECONDS), "callback should never fire for changes under .git");

        Files.writeString(tempDir.resolve("mcp.json"), "{}");
        assertTrue(latch.await(20, TimeUnit.SECONDS),
            "callback should still fire for an unrelated change outside .git");
    }

    @Test
    @Timeout(30)
    void neverReportsGitInternalDirectoryCreatedAfterStartup() throws IOException, InterruptedException {
        // Covers the other code path from the test above: .git doesn't exist yet when the
        // monitor starts (typical case -- git tracking is opt-in) and gets created later,
        // e.g. the first time a user enables it via the Config menu.
        CountDownLatch latch = new CountDownLatch(1);
        new GlobalKiroFolderMonitor(tempDir, latch::countDown);

        Path gitDir = Files.createDirectory(tempDir.resolve(".git"));
        Files.writeString(gitDir.resolve("index"), "not actually git's binary format, doesn't matter for this test");

        assertFalse(latch.await(5, TimeUnit.SECONDS),
            "creating .git itself should not fire a callback, nor should writes inside it afterward");

        Files.writeString(tempDir.resolve("mcp.json"), "{}");
        assertTrue(latch.await(20, TimeUnit.SECONDS),
            "callback should still fire for an unrelated change outside .git");
    }

    @Test
    void nonExistentRootDoesNotThrow() throws IOException {
        new GlobalKiroFolderMonitor(tempDir.resolve("does-not-exist"), () -> { });
        // No exception means the no-op registration path was taken correctly.
    }

    @Test
    @Timeout(30)
    void rapidChangesAreDebouncedIntoASingleCallback() throws IOException, InterruptedException {
        CountDownLatch atLeastOne = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger();
        new GlobalKiroFolderMonitor(tempDir, () -> {
            callCount.incrementAndGet();
            atLeastOne.countDown();
        });

        for (int i = 0; i < 5; i++) {
            Files.writeString(tempDir.resolve("mcp.json"), "{\"n\":" + i + "}");
        }

        assertTrue(atLeastOne.await(20, TimeUnit.SECONDS));
        // Give the debounce window a chance to have coalesced the burst before asserting.
        Thread.sleep(1500);
        assertTrue(callCount.get() <= 2, "expected the 1s debounce to coalesce 5 rapid writes into ~1 callback, got " + callCount.get());
    }
}
