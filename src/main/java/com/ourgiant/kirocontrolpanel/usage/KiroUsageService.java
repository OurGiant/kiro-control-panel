package com.ourgiant.kirocontrolpanel.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.util.AppVersion;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import com.ourgiant.kirocontrolpanel.util.KiroCliSessionDeleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetches the user's personal credit/usage balance by driving the local
 * {@code kiro-cli} binary over its own Agent Client Protocol (ACP) interface
 * -- the same JSON-RPC-over-stdio surface editor integrations (e.g. Zed) use
 * -- rather than calling Kiro's private backend directly. See SPEC.md's Usage
 * section and issues #94/#149: kiro-cli's V3 engine has a dedicated
 * {@code _kiro/account/getUsage} method, tried first; the V2 engine instead exposes an undocumented
 * {@code _kiro.dev/commands/execute} method that can run the same {@code
 * /usage} slash command the interactive REPL supports, returning structured
 * data instead of TUI text.
 * <p>
 * This is a private CLI internal, not a published API -- it could change
 * shape or disappear between {@code kiro-cli} versions without notice, so
 * every failure mode here is expected to degrade gracefully rather than
 * crash (see {@link UsagePanel}'s fallback to the static note).
 */
public final class KiroUsageService {

    private static final Logger logger = LoggerFactory.getLogger(KiroUsageService.class);
    private static final ObjectMapper MAPPER = JsonMapperFactory.createMapper();
    private static final long CALL_TIMEOUT_SECONDS = 15;

    private KiroUsageService() {
    }

    public record UsageBreakdown(
        String resourceType,
        String displayName,
        double used,
        double limit,
        int percentage,
        boolean hasLimit,
        double currentOverages,
        double overageRate,
        double overageCharges,
        String currency
    ) {
    }

    public record UsageSnapshot(
        String planName,
        String billingCycleReset,
        boolean overagesEnabled,
        List<UsageBreakdown> breakdowns
    ) {
    }

    /** Thrown for any failure fetching live usage, with a short user-facing reason. */
    public static class UsageFetchException extends RuntimeException {
        public UsageFetchException(String message) {
            super(message);
        }

        public UsageFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Blocks on a subprocess round-trip -- run off the EDT (e.g. from a SwingWorker).
     * @throws UsageFetchException on any failure: kiro-cli not on PATH, protocol
     *     error, timeout, or an unrecognized response shape.
     */
    public static UsageSnapshot fetchUsage() {
        return fetchUsage("kiro-cli");
    }

    /**
     * Admin-managed plans: V3 answers with no usage data at all (issue #149). A definitive
     * answer rather than a failure, so it's shown as-is instead of falling back to V2.
     */
    public static class AdminManagedPlanException extends UsageFetchException {
        public AdminManagedPlanException(String message) {
            super(message);
        }
    }

    /**
     * Package-private seam for tests: lets a nonexistent binary name exercise the "not on PATH"
     * failure path. Tries the V3 engine's dedicated usage method first, then the V2 workaround
     * (issue #149): V3 removed {@code _kiro.dev/commands/execute}, while older kiro-cli builds and
     * machines that can't run V3 only have the V2 path.
     */
    static UsageSnapshot fetchUsage(String binary) {
        UsageFetchException v3Failure;
        try {
            return fetchUsageV3(binary);
        } catch (AdminManagedPlanException e) {
            throw e;
        } catch (UsageFetchException e) {
            logger.info("V3 usage lookup failed, falling back to V2: {}", e.getMessage());
            v3Failure = e;
        }
        try {
            return fetchUsageV2(binary);
        } catch (UsageFetchException e) {
            e.addSuppressed(v3Failure);
            throw e;
        }
    }

    /**
     * V3's {@code _kiro/account/getUsage}, confirmed by reading its handler in the V3 engine
     * bundled with kiro-cli 2.16.2: it ignores its params and needs no session, so unlike the V2
     * path nothing is created that has to be cleaned up afterward. It isn't listed in
     * {@code initialize}'s advertised extension methods in that build, so it's called directly
     * rather than gated on the list. An older kiro-cli that rejects {@code --agent-engine v3},
     * or a machine where V3 can't run, makes the process exit, which surfaces here as an
     * {@link IOException} and so a fallback.
     */
    static UsageSnapshot fetchUsageV3(String binary) {
        Process process = startAcp(binary, "acp", "--agent-engine", "v3");
        try (AcpClient client = new AcpClient(process)) {
            initialize(client);
            return parseV3Result(client.call("_kiro/account/getUsage", Map.of()));
        } catch (IOException e) {
            throw new UsageFetchException("Couldn't get usage from kiro-cli V3: " + e.getMessage(), e);
        }
    }

    /** Package-private for tests: {@code {success, message, data}} from {@code _kiro/account/getUsage}. */
    static UsageSnapshot parseV3Result(JsonNode result) {
        String message = result.path("message").asText("");
        if (!result.path("success").asBoolean(false)) {
            throw new UsageFetchException(message.isBlank() ? "kiro-cli V3 couldn't retrieve usage" : message);
        }
        JsonNode data = result.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new AdminManagedPlanException(message.isBlank() ? "Your plan is managed by admin" : message);
        }
        return parseSnapshot(data);
    }

    /** The original V2 workaround (issue #94): {@code /usage} run through {@code _kiro.dev/commands/execute}. */
    static UsageSnapshot fetchUsageV2(String binary) {
        Process process = startAcp(binary, "acp");

        // ACP's session/new has no "ephemeral" concept -- every call persists a real session to
        // ~/.kiro/sessions/cli/, even though this is purely a vehicle for one usage query and no
        // prompt is ever sent through it. Left alone, one of these accumulates on every single
        // usage check (every Control Panel window open -- see UsagePanel's construction-time
        // refresh), which is exactly what turned up as a wall of "(no prompt)" sessions in the
        // Sessions tab (issue #124). Clean it up in `finally` so a fetch failure can't orphan it.
        String sessionId = null;
        try (AcpClient client = new AcpClient(process)) {
            initialize(client);

            JsonNode sessionResult = client.call("session/new", Map.of(
                "cwd", System.getProperty("user.dir"),
                "mcpServers", List.of()));
            sessionId = sessionResult.path("sessionId").asText(null);
            if (sessionId == null) {
                throw new UsageFetchException("kiro-cli didn't return a session id");
            }

            JsonNode usageResult = client.call("_kiro.dev/commands/execute", Map.of(
                "sessionId", sessionId,
                "command", Map.of("command", "usage", "args", Map.of())));
            JsonNode data = usageResult.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new UsageFetchException("kiro-cli's usage response didn't include data");
            }
            return parseSnapshot(data);
        } catch (IOException e) {
            throw new UsageFetchException("Couldn't talk to kiro-cli: " + e.getMessage(), e);
        } finally {
            // sessionId is only ever non-null once the "acp" process above has already started
            // successfully against this exact binary, so delegating to the real-"kiro-cli"-only
            // public overload is equivalent to passing `binary` through -- see
            // KiroCliSessionDeleter's Javadoc for why deletion itself is best-effort/non-throwing.
            if (sessionId != null) {
                KiroCliSessionDeleter.delete(sessionId);
            }
        }
    }

    private static Process startAcp(String binary, String... args) {
        List<String> command = new ArrayList<>();
        command.add(binary);
        command.addAll(List.of(args));
        try {
            // stderr is discarded rather than left as an unread pipe that could fill up.
            return new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            throw new UsageFetchException(binary + " isn't on your PATH", e);
        }
    }

    private static void initialize(AcpClient client) throws IOException {
        client.call("initialize", Map.of(
            "protocolVersion", 1,
            "clientCapabilities", Map.of(),
            "clientInfo", Map.of("name", "kiro-control-panel", "version", AppVersion.resolve())));
    }

    /** Package-private for tests. */
    static UsageSnapshot parseSnapshot(JsonNode data) {
        List<UsageBreakdown> breakdowns = new ArrayList<>();
        for (JsonNode node : data.path("usageBreakdowns")) {
            breakdowns.add(new UsageBreakdown(
                node.path("resourceType").asText(""),
                node.path("displayName").asText(""),
                node.path("used").asDouble(),
                node.path("limit").asDouble(),
                node.path("percentage").asInt(),
                node.path("hasLimit").asBoolean(false),
                node.path("currentOverages").asDouble(),
                node.path("overageRate").asDouble(),
                node.path("overageCharges").asDouble(),
                node.path("currency").asText("")));
        }
        return new UsageSnapshot(
            data.path("planName").asText(""),
            data.path("billingCycleReset").asText(""),
            data.path("overagesEnabled").asBoolean(false),
            List.copyOf(breakdowns));
    }

    /** Minimal JSON-RPC-over-stdio client: newline-delimited JSON, one object per line, matched by numeric id. */
    private static final class AcpClient implements AutoCloseable {
        private final Process process;
        private final BufferedWriter writer;
        private final Thread readerThread;
        private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
        private final AtomicInteger nextId = new AtomicInteger();

        AcpClient(Process process) {
            this.process = process;
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.readerThread = new Thread(this::readLoop, "kiro-usage-acp-reader");
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        }

        private void readLoop() {
            try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("{")) {
                        continue;
                    }
                    JsonNode message;
                    try {
                        message = MAPPER.readTree(line);
                    } catch (IOException e) {
                        continue;
                    }
                    // Notifications (no "id") -- e.g. _kiro.dev/metadata -- aren't awaited by anyone; ignore them.
                    if (message.has("id")) {
                        CompletableFuture<JsonNode> future = pending.remove(message.get("id").asInt());
                        if (future != null) {
                            future.complete(message);
                        }
                    }
                }
            } catch (IOException e) {
                logger.debug("kiro-cli ACP stdout closed", e);
            } finally {
                for (CompletableFuture<JsonNode> future : pending.values()) {
                    future.completeExceptionally(new IOException("kiro-cli's ACP stream ended before responding"));
                }
                pending.clear();
            }
        }

        JsonNode call(String method, Object params) throws IOException {
            int id = nextId.incrementAndGet();
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pending.put(id, future);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            request.put("params", params);
            synchronized (writer) {
                writer.write(MAPPER.writeValueAsString(request));
                writer.write("\n");
                writer.flush();
            }

            JsonNode response;
            try {
                response = future.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                pending.remove(id);
                throw new IOException("kiro-cli didn't respond to " + method + " within "
                    + CALL_TIMEOUT_SECONDS + "s", e);
            } catch (ExecutionException e) {
                throw new IOException(method + " failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for kiro-cli's " + method + " response", e);
            }
            if (response.has("error")) {
                throw new IOException("kiro-cli returned an error for " + method + ": " + response.get("error"));
            }
            return response.path("result");
        }

        @Override
        public void close() {
            try {
                writer.close();
            } catch (IOException ignored) {
                // Process is being torn down regardless.
            }
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
