package com.ourgiant.kirocontrolpanel.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects where the user stands on the kiro-cli V2 to V3 move and runs the kiro-cli commands
 * that make it (issue #147). Every change goes through kiro-cli itself rather than this app
 * rewriting Kiro's files, because kiro-cli owns the conversion rules and writes its own
 * {@code <name>.json.bak} backups:
 * <ul>
 *   <li>{@code kiro-cli update --non-interactive} -- older 2.x builds lack {@code agent upgrade}</li>
 *   <li>{@code kiro-cli agent upgrade <dir> [--dry-run]} -- converts V2 agent configs</li>
 *   <li>{@code kiro-cli settings chat.agentEngine v3|v2} -- the saved default engine</li>
 * </ul>
 * Sessions need no command: V3 leaves the V2 store alone, and resuming a V2 session on V3
 * makes a V3 copy (see {@code SessionsPanel}'s "Continue in V3...").
 */
public class KiroCliV3MigrationService {
    private static final Logger logger = LoggerFactory.getLogger(KiroCliV3MigrationService.class);
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
    private static final long QUICK_TIMEOUT_SECONDS = 20;
    private static final long UPDATE_TIMEOUT_SECONDS = 600;

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();
    private final String binary;

    public KiroCliV3MigrationService() {
        this("kiro-cli");
    }

    /** Package-private seam for tests. */
    KiroCliV3MigrationService(String binary) {
        this.binary = binary;
    }

    /** One finished kiro-cli invocation: its exit code and combined stdout/stderr. */
    public record CommandResult(List<String> command, int exitCode, String output) {
        public boolean succeeded() {
            return exitCode == 0;
        }
    }

    /**
     * Reads the current state. Blocks on a few short kiro-cli calls, so call it off the EDT.
     *
     * @param agentDirs    every agents folder the app manages (global plus pinned workspaces)
     * @param sessionsCliDir the V2 session store
     * @param sessionsDir  the parent of both stores, where V3 keeps its per-workspace folders
     */
    public KiroCliV3Status detect(List<Path> agentDirs, Path sessionsCliDir, Path sessionsDir) {
        CommandResult version;
        try {
            version = run(List.of(binary, "--version"), QUICK_TIMEOUT_SECONDS);
        } catch (IOException e) {
            logger.info("kiro-cli not runnable, skipping V3 migration check: {}", e.getMessage());
            return KiroCliV3Status.notInstalled();
        }
        String cliVersion = version.succeeded() ? parseVersion(version.output()) : null;
        String savedEngine = readSavedEngine();
        boolean agentUpgradeAvailable = commandSucceeds(List.of(binary, "agent", "upgrade", "--help"));
        return new KiroCliV3Status(true, cliVersion, savedEngine, agentUpgradeAvailable,
            countV2Sessions(sessionsCliDir), countV3Sessions(sessionsDir), findLegacyAgents(agentDirs));
    }

    public CommandResult updateCli() throws IOException {
        return run(List.of(binary, "update", "--non-interactive"), UPDATE_TIMEOUT_SECONDS);
    }

    /** Converts every V2 agent config in {@code agentsDir}; kiro-cli backs each one up as
     * {@code <name>.json.bak} first. {@code dryRun} only reports what would change. */
    public CommandResult upgradeAgents(Path agentsDir, boolean dryRun) throws IOException {
        List<String> command = new ArrayList<>(List.of(binary, "agent", "upgrade", agentsDir.toString()));
        if (dryRun) {
            command.add("--dry-run");
        }
        return run(command, QUICK_TIMEOUT_SECONDS * 3);
    }

    /** @param engine {@code "v3"} or {@code "v2"} -- nothing else is passed to kiro-cli. */
    public CommandResult setDefaultEngine(String engine) throws IOException {
        if (!"v3".equals(engine) && !"v2".equals(engine)) {
            throw new IllegalArgumentException("Unknown kiro-cli agent engine: " + engine);
        }
        return run(List.of(binary, "settings", "chat.agentEngine", engine), QUICK_TIMEOUT_SECONDS);
    }

    // -- detection helpers, package-private for tests --

    static String parseVersion(String versionOutput) {
        Matcher matcher = VERSION_PATTERN.matcher(versionOutput == null ? "" : versionOutput);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** {@code kiro-cli settings <key>} prints the value (possibly JSON-quoted) and exits 0, or
     * exits non-zero with "No value associated with ..." when it's unset. */
    static String parseSavedEngine(CommandResult result) {
        if (!result.succeeded()) {
            return null;
        }
        String value = result.output().trim().replace("\"", "").toLowerCase(java.util.Locale.ROOT);
        return value.equals("v2") || value.equals("v3") ? value : null;
    }

    /**
     * V3 skips an agent JSON that still uses V2's {@code allowedTools} or {@code toolsSettings}
     * without a {@code permissions} block, until {@code agent upgrade} converts it (per Kiro's
     * "Upgrade from CLI 2.x" docs). Upgraded files keep their V2 fields next to the new
     * {@code permissions} block, so they don't match here.
     */
    public static boolean needsV3Upgrade(JsonNode agent) {
        return agent != null && agent.isObject()
            && !agent.has("permissions")
            && (agent.has("allowedTools") || agent.has("toolsSettings"));
    }

    List<Path> findLegacyAgents(List<Path> agentDirs) {
        List<Path> legacy = new ArrayList<>();
        for (Path dir : agentDirs) {
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            // Top-level *.json only, matching what `kiro-cli agent upgrade <dir>` itself checks.
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : files) {
                    try {
                        if (needsV3Upgrade(mapper.readTree(file.toFile()))) {
                            legacy.add(file);
                        }
                    } catch (IOException e) {
                        logger.debug("Skipping unreadable agent file {}", file, e);
                    }
                }
            } catch (IOException e) {
                logger.warn("Failed to list agents folder {}", dir, e);
            }
        }
        legacy.sort(null);
        return legacy;
    }

    static int countV2Sessions(Path sessionsCliDir) {
        if (sessionsCliDir == null || !Files.isDirectory(sessionsCliDir)) {
            return 0;
        }
        int count = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(sessionsCliDir, "*.json")) {
            for (Path ignored : files) {
                count++;
            }
        } catch (IOException e) {
            logger.warn("Failed to count V2 sessions in {}", sessionsCliDir, e);
        }
        return count;
    }

    static int countV3Sessions(Path sessionsDir) {
        try {
            return com.ourgiant.kirocontrolpanel.sessions.SessionIndexService.listV3Sidecars(sessionsDir).size();
        } catch (IOException e) {
            logger.warn("Failed to count V3 sessions in {}", sessionsDir, e);
            return 0;
        }
    }

    private String readSavedEngine() {
        try {
            return parseSavedEngine(run(List.of(binary, "settings", "chat.agentEngine"), QUICK_TIMEOUT_SECONDS));
        } catch (IOException e) {
            return null;
        }
    }

    private boolean commandSucceeds(List<String> command) {
        try {
            return run(command, QUICK_TIMEOUT_SECONDS).succeeded();
        } catch (IOException e) {
            return false;
        }
    }

    /** Runs a kiro-cli command with stdin closed, so nothing can hang on an interactive prompt. */
    CommandResult run(List<String> command, long timeoutSeconds) throws IOException {
        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
            .start();
        try {
            // Read on a helper thread so a chatty process can't fill the pipe and block forever.
            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try {
                    output.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    logger.debug("Lost output of {}", command, e);
                }
            }, "kiro-cli-v3-migration-output");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reader.join(1000);
                return new CommandResult(command, -1, output + "\n(timed out after " + timeoutSeconds + "s)");
            }
            reader.join(5000);
            return new CommandResult(command, process.exitValue(), stripAnsi(output.toString()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandResult(command, -1, "(interrupted)");
        }
    }

    private static java.io.File nullDevice() {
        return new java.io.File(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
            .contains("win") ? "NUL" : "/dev/null");
    }

    /** kiro-cli colors its output; the log pane is plain text. */
    static String stripAnsi(String text) {
        return text.replaceAll("\u001B\\[[0-9;?]*[ -/]*[@-~]", "");
    }
}
