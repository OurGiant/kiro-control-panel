package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GitAutoCommitterTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void requireGit() {
        // git isn't installed in every environment this test might run in (e.g. a bare
        // container); GitHub's CI runners all have it, this is just a local-dev safety net.
        assumeTrue(GitAutoCommitter.isGitAvailable(), "git not found on PATH");
    }

    @Test
    void ensureRepoInitializedCreatesRepoWithGitignoreAndInitialCommit() throws IOException {
        boolean initialized = GitAutoCommitter.ensureRepoInitialized(tempDir);

        assertTrue(initialized);
        assertTrue(Files.isDirectory(tempDir.resolve(".git")));
        String gitignore = Files.readString(tempDir.resolve(".gitignore"));
        assertTrue(gitignore.contains("extensions/"));
        assertTrue(gitignore.contains("sessions/"));
        assertEquals(1, commitCount(tempDir));
    }

    @Test
    void ensureRepoInitializedIsIdempotent() throws IOException {
        GitAutoCommitter.ensureRepoInitialized(tempDir);

        boolean secondCall = GitAutoCommitter.ensureRepoInitialized(tempDir);

        assertTrue(secondCall);
        assertEquals(1, commitCount(tempDir), "should not create a second initial commit");
    }

    @Test
    void commitAddsOnlyTheGivenFileNotOtherPendingChanges() throws IOException {
        GitAutoCommitter.ensureRepoInitialized(tempDir);
        Path fileA = Files.writeString(tempDir.resolve("mcp.json"), "{}");
        Files.writeString(tempDir.resolve("steering.md"), "unrelated pending change");

        GitAutoCommitter.commit(tempDir, fileA);

        String status = gitStatusPorcelain(tempDir);
        assertFalse(status.contains("mcp.json"), "the file just written should be committed, not left pending");
        assertTrue(status.contains("steering.md"), "an unrelated pending file should be left untouched");
    }

    @Test
    void commitDoesNothingForAPathOutsideTheGivenRoot() throws IOException {
        Path outsideFile = Files.writeString(tempDir.resolve("outside.txt"), "hello");
        Path kiroHome = tempDir.resolve("kiro-home");

        GitAutoCommitter.commit(kiroHome, outsideFile);

        assertFalse(Files.isDirectory(kiroHome.resolve(".git")),
            "a path outside kiroHome should never trigger repo initialization");
    }

    @Test
    void repeatedNoOpSavesDoNotCreateExtraCommits() throws IOException {
        GitAutoCommitter.ensureRepoInitialized(tempDir);
        Path file = Files.writeString(tempDir.resolve("mcp.json"), "{}");
        GitAutoCommitter.commit(tempDir, file);
        int afterFirstCommit = commitCount(tempDir);

        // Same content written again -- nothing actually changed for git to commit.
        Files.writeString(file, "{}");
        GitAutoCommitter.commit(tempDir, file);

        assertEquals(afterFirstCommit, commitCount(tempDir), "a no-op save should not create an empty commit");
    }

    private static int commitCount(Path repo) throws IOException {
        String output = runGit(repo, "log", "--oneline");
        return output.isBlank() ? 0 : output.strip().split("\n").length;
    }

    private static String gitStatusPorcelain(Path repo) throws IOException {
        return runGit(repo, "status", "--porcelain");
    }

    private static String runGit(Path repo, String... args) throws IOException {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        try {
            Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(10, TimeUnit.SECONDS);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }
}
