package com.ourgiant.kirocontrolpanel.util;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in: auto-commits every app-driven write under the global {@code ~/.kiro}
 * tree to a local git repo, so the folder becomes a self-maintaining audit
 * trail the user can inspect/revert with git directly -- no in-app version
 * browser, that's what git itself is for. Only ever runs {@code git add} on
 * the exact file just written, deliberately never {@code -A}: an
 * <em>external</em> change (the exact thing {@link KiroFolderMonitor}
 * flags) should stay visible as an uncommitted diff, not get silently swept
 * into an unrelated "Update X" commit. See #49.
 * <p>
 * Global-only, matching {@link KiroFolderMonitor}/#50: a workspace's
 * {@code .kiro} folder usually lives inside the user's own project repo, and
 * git-init'ing a nested repo there would confuse that repo's own
 * {@code git add .} (silently skipped as an embedded repo, or recorded as a
 * content-less gitlink).
 */
public final class GitAutoCommitter {
    private static final Logger logger = LoggerFactory.getLogger(GitAutoCommitter.class);
    private static final String GIT_BINARY = KiroSessionLauncher.detect(System.getProperty("os.name"))
        == KiroSessionLauncher.Platform.WINDOWS ? "git.exe" : "git";
    private static final long GIT_TIMEOUT_SECONDS = 10;
    private static final AppPreferences preferences = new AppPreferences();
    private static final String GITIGNORE_CONTENT = """
        # Kiro-managed, not something Kiro Control Panel edits -- ephemeral/large session state.
        extensions/
        sessions/
        """;

    private GitAutoCommitter() {
    }

    public static boolean isGitAvailable() {
        return KiroSessionLauncher.findOnPath(System.getenv("PATH"), GIT_BINARY, File::canExecute) != null;
    }

    /**
     * Idempotent: a no-op if {@code kiroHome} is already a git repo.
     * @return true if a repo exists at {@code kiroHome} (already did, or was just created)
     */
    public static boolean ensureRepoInitialized(Path kiroHome) {
        if (Files.isDirectory(kiroHome.resolve(".git"))) {
            return true;
        }
        if (!isGitAvailable()) {
            return false;
        }
        try {
            Files.createDirectories(kiroHome);
            if (!run(kiroHome, "init", "-b", "main")) {
                return false;
            }
            // Repo-local only (no --global) -- never touch the user's own git identity.
            run(kiroHome, "config", "user.name", "Kiro Control Panel");
            run(kiroHome, "config", "user.email", "kiro-control-panel@localhost");
            Path gitignore = kiroHome.resolve(".gitignore");
            if (!Files.exists(gitignore)) {
                SelfWriteTracker.markAboutToWrite(gitignore);
                Files.writeString(gitignore, GITIGNORE_CONTENT, StandardCharsets.UTF_8);
            }
            run(kiroHome, "add", "-A");
            run(kiroHome, "commit", "--no-verify", "-m", "Initial commit");
            return true;
        } catch (IOException e) {
            logger.warn("Failed to initialize git repo at {}", kiroHome, e);
            return false;
        }
    }

    /** Best-effort: never throws, and a git failure here never blocks the caller's own save. */
    public static void commitIfEnabled(Path filePath) {
        if (!preferences.isGitTrackingEnabled()) {
            return;
        }
        commit(KiroPaths.globalKiroHome(), filePath);
    }

    /**
     * Package-private, for tests: the actual git logic, without the preference check, against
     * an explicit root -- so tests can point it at a temp dir instead of the real global .kiro.
     */
    static void commit(Path kiroHome, Path filePath) {
        Path root = kiroHome.toAbsolutePath().normalize();
        Path absoluteFile = filePath.toAbsolutePath().normalize();
        if (!absoluteFile.startsWith(root)) {
            return; // workspace-scoped write -- out of scope, see class Javadoc
        }
        if (!ensureRepoInitialized(root)) {
            return;
        }
        String relativePath = root.relativize(absoluteFile).toString();
        if (!run(root, "add", relativePath)) {
            return;
        }
        // A no-op commit (e.g. Save with no actual edits) exits non-zero -- run() already
        // distinguishes that from a real failure and only logs the latter.
        run(root, "commit", "--no-verify", "-m", "Update " + relativePath);
    }

    private static boolean run(Path workingDir, String... args) {
        List<String> command = new ArrayList<>();
        command.add(GIT_BINARY);
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("git {} timed out in {}", String.join(" ", args), workingDir);
                return false;
            }
            if (process.exitValue() != 0) {
                // "nothing to commit" is an expected, frequent outcome (e.g. Save with no
                // actual edits) -- not worth logging as a failure every time it happens.
                if (!output.contains("nothing to commit")) {
                    logger.warn("git {} exited {} in {}: {}", String.join(" ", args), process.exitValue(), workingDir, output.trim());
                }
                return false;
            }
            return true;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to run git {} in {}", String.join(" ", args), workingDir, e);
            return false;
        }
    }
}
