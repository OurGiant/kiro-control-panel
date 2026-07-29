package com.ourgiant.kirocontrolpanel.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.util.AppVersion;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
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
 * section and issue #94: {@code kiro-cli acp} exposes an undocumented
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

    /** Package-private seam for tests: lets a nonexistent binary name exercise the "not on PATH" failure path. */
    static UsageSnapshot fetchUsage(String binary) {
        Process process;
        try {
            process = new ProcessBuilder(binary, "acp").start();
        } catch (IOException e) {
            throw new UsageFetchException(binary + " isn't on your PATH", e);
        }

        try (AcpClient client = new AcpClient(process)) {
            client.call("initialize", Map.of(
                "protocolVersion", 1,
                "clientCapabilities", Map.of(),
                "clientInfo", Map.of("name", "kiro-control-panel", "version", AppVersion.resolve())));

            JsonNode sessionResult = client.call("session/new", Map.of(
                "cwd", System.getProperty("user.dir"),
                "mcpServers", List.of()));
            String sessionId = sessionResult.path("sessionId").asText(null);
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
        }
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
