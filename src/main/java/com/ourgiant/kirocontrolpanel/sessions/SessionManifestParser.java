package com.ourgiant.kirocontrolpanel.sessions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses a kiro-cli session's {@code <uuid>.json} sidecar and paired
 * {@code <uuid>.jsonl} transcript into a {@link SessionManifest}. Pure,
 * {@code Path}-parameterized, no I/O beyond reading the two given files --
 * mirrors {@code changelog.ChangeLogService.loadSince}'s "skip and log one
 * bad line, never abort the whole file" tolerance.
 * <p>
 * Transcript line shape, confirmed against real seeded kiro-cli 2.13.0 data
 * (issue #117): each line is {@code {"version":"v1","kind":<str>,"data":{...}}}
 * with {@code kind} one of {@code Prompt}/{@code AssistantMessage}/
 * {@code ToolResults}. Tool calls appear as {@code AssistantMessage} content
 * entries of kind {@code toolUse}; their results appear in the following
 * {@code ToolResults} record, keyed by tool-use id, under
 * {@code results.<id>.tool.kind.BuiltIn.<ToolName>}. {@code FileRead} is a
 * read, not a write, so it deliberately does not contribute to
 * {@code filesTouched} (which is meant to answer "what did this session write
 * or edit"). {@code FileWrite} (confirmed against real data, issue #144) is
 * {@code {"command":"create"|"strReplace"|..., "path":"...", ...}} -- {@code
 * path} is present regardless of the {@code command} sub-type, so it's taken
 * unconditionally. {@code ExecuteCmd} (also confirmed, issue #144) is {@code
 * {"command":"<shell string>","working_dir":...}} -- a raw shell command, not
 * a structured file reference, so it's deliberately left out of
 * {@code filesTouched}: reliably recovering touched paths from arbitrary
 * shell syntax would mean guessing/heuristic-parsing rather than reading a
 * confirmed field, which this parser avoids. Other unrecognized tool kinds
 * are skipped (logged at debug), never treated as an error, so the parser
 * degrades gracefully rather than guessing field names.
 */
public final class SessionManifestParser {
    private static final Logger logger = LoggerFactory.getLogger(SessionManifestParser.class);
    private static final ObjectMapper MAPPER = JsonMapperFactory.createMapper();

    private SessionManifestParser() {
    }

    /** Full parse from scratch -- used for a first-time index or a rebuild. */
    public static SessionManifest buildManifest(Path jsonSidecar, Path jsonlFile) throws IOException {
        SessionSidecar sidecar = parseSidecar(jsonSidecar);
        TranscriptStats stats = parseTranscript(jsonlFile, 0);
        String title = sidecar.title() != null && !sidecar.title().isBlank()
            ? sidecar.title()
            : stats.firstPromptText();
        return new SessionManifest(
            sidecar.sessionId(),
            sidecar.cwd(),
            sidecar.createdAtEpochMilli(),
            sidecar.updatedAtEpochMilli(),
            title,
            sidecar.sessionCreatedReason(),
            stats.messageCount(),
            stats.filesTouched(),
            stats.reposTouched(),
            jsonSidecar.toAbsolutePath().normalize().toString(),
            jsonlFile.toAbsolutePath().normalize().toString());
    }

    /** Parses either engine's sidecar, picking the layout from the path (see {@link SessionEngine}). */
    public static SessionSidecar parseSidecar(Path jsonSidecar) throws IOException {
        if (SessionEngine.ofSidecar(jsonSidecar) == SessionEngine.V3) {
            return parseV3Sidecar(jsonSidecar);
        }
        JsonNode root = MAPPER.readTree(jsonSidecar.toFile());
        String title = root.hasNonNull("title") ? root.get("title").asText() : null;
        return new SessionSidecar(
            root.path("session_id").asText(""),
            root.path("cwd").asText(""),
            parseInstant(root.path("created_at").asText(null)),
            parseInstant(root.path("updated_at").asText(null)),
            title,
            root.path("session_created_reason").asText(""));
    }

    /**
     * Parses the credit/context-usage subset of {@code session_state}, confirmed against real
     * seeded data (issue #120 follow-up): {@code rts_model_state} holds a current/latest
     * {@code context_usage_percentage} snapshot and {@code model_info.context_window_tokens};
     * {@code conversation_metadata.user_turn_metadatas[]} holds one entry per completed user
     * turn, each with a {@code metering_usage} array of {@code {value, unit, unitPlural}}
     * entries -- summed here, but only entries whose {@code unit} is exactly {@code "credit"}
     * are counted, so a future differently-united entry is skipped (logged at debug) rather
     * than silently added into a total it doesn't belong in.
     */
    public static SessionUsage parseUsage(Path jsonSidecar) throws IOException {
        if (SessionEngine.ofSidecar(jsonSidecar) == SessionEngine.V3) {
            return parseV3Usage(jsonSidecar);
        }
        JsonNode root = MAPPER.readTree(jsonSidecar.toFile());
        JsonNode rts = root.path("session_state").path("rts_model_state");
        Double contextUsagePercentage = rts.hasNonNull("context_usage_percentage")
            ? rts.get("context_usage_percentage").asDouble() : null;
        JsonNode modelInfo = rts.path("model_info");
        Integer contextWindowTokens = modelInfo.hasNonNull("context_window_tokens")
            ? modelInfo.get("context_window_tokens").asInt() : null;
        String modelId = modelInfo.hasNonNull("model_id") ? modelInfo.get("model_id").asText() : null;

        JsonNode turns = root.path("session_state").path("conversation_metadata").path("user_turn_metadatas");
        int turnCount = turns.isArray() ? turns.size() : 0;
        double totalCredits = 0;
        if (turns.isArray()) {
            for (JsonNode turn : turns) {
                for (JsonNode entry : turn.path("metering_usage")) {
                    if ("credit".equals(entry.path("unit").asText(""))) {
                        totalCredits += entry.path("value").asDouble(0);
                    } else {
                        logger.debug("Skipping metering_usage entry with unrecognized unit '{}' in {}",
                            entry.path("unit").asText(""), jsonSidecar);
                    }
                }
            }
        }
        return new SessionUsage(turnCount, totalCredits, contextUsagePercentage, contextWindowTokens, modelId);
    }

    private static long parseInstant(String isoText) {
        if (isoText == null || isoText.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(isoText).toEpochMilli();
        } catch (DateTimeParseException e) {
            logger.warn("Unparseable session timestamp '{}'", isoText, e);
            return 0L;
        }
    }

    /**
     * Walks {@code jsonlFile} starting at {@code fromLineInclusive} (0 for a full parse,
     * or a previously-stored line count to resume an append-only file without re-reading
     * its already-indexed prefix). Malformed lines are skipped and logged, never fatal to
     * the rest of the file -- same convention as {@code ChangeLogService.loadSince}.
     */
    public static TranscriptStats parseTranscript(Path jsonlFile, long fromLineInclusive) throws IOException {
        if (!Files.isRegularFile(jsonlFile)) {
            return new TranscriptStats(0, List.of(), List.of(), null, 0, List.of());
        }
        if (SessionEngine.ofTranscript(jsonlFile) == SessionEngine.V3) {
            return parseV3Transcript(jsonlFile, fromLineInclusive);
        }
        List<String> lines = Files.readAllLines(jsonlFile);
        long totalLines = lines.size();
        int fromIndex = (int) Math.min(fromLineInclusive, totalLines);

        int messageCount = 0;
        Set<String> filesTouched = new LinkedHashSet<>();
        Set<String> reposTouched = new LinkedHashSet<>();
        List<TranscriptMessage> messages = new ArrayList<>();
        String firstPromptText = null;

        for (String line : lines.subList(fromIndex, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode root;
            try {
                root = MAPPER.readTree(line);
            } catch (IOException e) {
                logger.warn("Skipping unparseable session transcript line in {}", jsonlFile, e);
                continue;
            }
            String kind = root.path("kind").asText("");
            JsonNode data = root.path("data");
            switch (kind) {
                case "Prompt" -> {
                    messageCount++;
                    String text = joinTextContent(data.path("content"));
                    if (text != null) {
                        messages.add(new TranscriptMessage(data.path("message_id").asText(null), "user", text));
                        if (firstPromptText == null) {
                            firstPromptText = text;
                        }
                    }
                }
                case "AssistantMessage" -> {
                    messageCount++;
                    String text = joinTextContent(data.path("content"));
                    if (text != null) {
                        messages.add(new TranscriptMessage(data.path("message_id").asText(null), "assistant", text));
                    }
                }
                case "ToolResults" -> collectToolResults(data.path("results"), filesTouched, reposTouched);
                default -> logger.debug("Unrecognized session transcript record kind '{}', skipping", kind);
            }
        }

        return new TranscriptStats(messageCount, List.copyOf(filesTouched), List.copyOf(reposTouched),
            firstPromptText, totalLines, List.copyOf(messages));
    }

    /** Joins every {@code text}-kind content entry (skipping {@code toolUse}/other kinds and
     * blank text) -- deliberately excludes tool call/result payloads from the FTS feed. */
    private static String joinTextContent(JsonNode contentArray) {
        if (!contentArray.isArray()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (JsonNode entry : contentArray) {
            if ("text".equals(entry.path("kind").asText(""))) {
                String text = entry.path("data").asText("");
                if (!text.isBlank()) {
                    if (joined.length() > 0) {
                        joined.append('\n');
                    }
                    joined.append(text);
                }
            }
        }
        return joined.isEmpty() ? null : joined.toString();
    }

    private static void collectToolResults(JsonNode resultsByToolUseId, Set<String> filesTouched, Set<String> reposTouched) {
        if (!resultsByToolUseId.isObject()) {
            return;
        }
        Iterator<JsonNode> entries = resultsByToolUseId.elements();
        while (entries.hasNext()) {
            JsonNode builtIn = entries.next().path("tool").path("kind").path("BuiltIn");
            if (!builtIn.isObject()) {
                continue;
            }
            Iterator<String> toolNames = builtIn.fieldNames();
            while (toolNames.hasNext()) {
                String toolName = toolNames.next();
                switch (toolName) {
                    case "FileRead" -> {
                        // A read, not a write -- filesTouched means "written or edited", so this
                        // is deliberately not added. See class Javadoc.
                    }
                    case "FileWrite" -> {
                        String path = builtIn.path(toolName).path("path").asText(null);
                        if (path != null && !path.isBlank()) {
                            filesTouched.add(path);
                        }
                    }
                    default -> logger.debug(
                        "Unrecognized BuiltIn tool kind '{}', skipping files/repos extraction for it", toolName);
                }
            }
        }
    }

    // -- V3 engine (issue #147) --
    //
    // Shapes confirmed against a real session written by kiro-cli 2.16.2's bundled V3 engine:
    // session.json carries {id, title, workspacePaths[], createdAt, lastModifiedAt, modelId};
    // each messages.jsonl line is {"id", "timestamp", "payload": {"type": ...}} with payload
    // types user/assistant (text in "content"), tool_call ({toolName, kind, args}),
    // tool_result, usage_summary (credits per turn), session_metadata (key "contextUsage"
    // holds the latest context %), plus turn/session bookkeeping that's ignored here.

    /** V3's placeholder title for a session that hasn't been named yet -- treated as no title,
     * so the opening prompt is shown instead, same as a V2 session with a null title. */
    private static final String V3_PLACEHOLDER_TITLE = "New Session";

    /**
     * Tool names the CLI's own UI treats as file writes, taken from the strings in
     * kiro-cli-chat 2.16.2. The real V3 session this was built against made no writes, so this
     * is backed up by ACP's standard {@code kind: "edit"}, which V3 tool calls also carry.
     */
    private static final Set<String> V3_WRITE_TOOL_NAMES = Set.of("fs_write", "Write", "create", "Edit", "fs_edit");

    private static SessionSidecar parseV3Sidecar(Path sessionJson) throws IOException {
        JsonNode root = MAPPER.readTree(sessionJson.toFile());
        String title = root.hasNonNull("title") ? root.get("title").asText() : null;
        if (V3_PLACEHOLDER_TITLE.equals(title)) {
            title = null;
        }
        String sessionId = root.path("id").asText("");
        if (sessionId.isBlank()) {
            sessionId = SessionEngine.V3.sessionIdFor(sessionJson);
        }
        return new SessionSidecar(
            sessionId,
            firstWorkspacePath(root),
            parseInstant(root.path("createdAt").asText(null)),
            parseInstant(root.path("lastModifiedAt").asText(null)),
            title,
            "");
    }

    private static String firstWorkspacePath(JsonNode sessionRoot) {
        JsonNode paths = sessionRoot.path("workspacePaths");
        return paths.isArray() && !paths.isEmpty() ? paths.get(0).asText("") : "";
    }

    private static TranscriptStats parseV3Transcript(Path messagesJsonl, long fromLineInclusive) throws IOException {
        List<String> lines = Files.readAllLines(messagesJsonl);
        long totalLines = lines.size();
        int fromIndex = (int) Math.min(fromLineInclusive, totalLines);
        Path workspaceDir = v3WorkspaceDir(messagesJsonl);

        int messageCount = 0;
        Set<String> filesTouched = new LinkedHashSet<>();
        List<TranscriptMessage> messages = new ArrayList<>();
        String firstPromptText = null;

        for (String line : lines.subList(fromIndex, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode root;
            try {
                root = MAPPER.readTree(line);
            } catch (IOException e) {
                logger.warn("Skipping unparseable session transcript line in {}", messagesJsonl, e);
                continue;
            }
            JsonNode payload = root.path("payload");
            String type = payload.path("type").asText("");
            switch (type) {
                case "user", "assistant" -> {
                    messageCount++;
                    String text = v3Text(payload.path("content"));
                    if (text != null) {
                        messages.add(new TranscriptMessage(root.path("id").asText(null), type, text));
                        if (firstPromptText == null && "user".equals(type)) {
                            firstPromptText = text;
                        }
                    }
                }
                case "tool_call" -> {
                    String toolName = payload.path("toolName").asText("");
                    boolean isWrite = "edit".equals(payload.path("kind").asText(""))
                        || V3_WRITE_TOOL_NAMES.contains(toolName);
                    String path = payload.path("args").path("path").asText(null);
                    if (isWrite && path != null && !path.isBlank()) {
                        filesTouched.add(resolveAgainst(workspaceDir, path));
                    }
                }
                default -> logger.debug("V3 transcript payload type '{}' carries nothing indexed, skipping", type);
            }
        }
        return new TranscriptStats(messageCount, List.copyOf(filesTouched), List.of(),
            firstPromptText, totalLines, List.copyOf(messages));
    }

    /** V3 tool calls can use workspace-relative paths ("pom.xml"); resolving them against the
     * session's workspace keeps the Files Touched pane's "Reveal File" working. */
    private static String resolveAgainst(Path workspaceDir, String path) {
        try {
            Path candidate = Path.of(path);
            if (candidate.isAbsolute() || workspaceDir == null) {
                return path;
            }
            return workspaceDir.resolve(candidate).normalize().toString();
        } catch (java.nio.file.InvalidPathException e) {
            return path;
        }
    }

    private static Path v3WorkspaceDir(Path messagesJsonl) {
        Path sessionJson = messagesJsonl.resolveSibling(SessionEngine.V3_SIDECAR_NAME);
        if (!Files.isRegularFile(sessionJson)) {
            return null;
        }
        try {
            String dir = firstWorkspacePath(MAPPER.readTree(sessionJson.toFile()));
            return dir.isBlank() ? null : Path.of(dir);
        } catch (IOException | java.nio.file.InvalidPathException e) {
            logger.debug("Couldn't read workspace path from {}", sessionJson, e);
            return null;
        }
    }

    /** User content is a plain string in the captured data; an array of {@code {text}} parts is
     * accepted too, in case richer messages use one. */
    private static String v3Text(JsonNode content) {
        if (content.isTextual()) {
            String text = content.asText();
            return text.isBlank() ? null : text;
        }
        if (content.isArray()) {
            StringBuilder joined = new StringBuilder();
            for (JsonNode part : content) {
                String text = part.isTextual() ? part.asText() : part.path("text").asText("");
                if (!text.isBlank()) {
                    if (joined.length() > 0) {
                        joined.append('\n');
                    }
                    joined.append(text);
                }
            }
            return joined.isEmpty() ? null : joined.toString();
        }
        return null;
    }

    /** Turns and credits come from {@code usage_summary} records (one per completed turn), the
     * context % from the latest {@code contextUsage} metadata record, and the model from
     * session.json. V3 doesn't record the context window size, so that stays null. */
    private static SessionUsage parseV3Usage(Path sessionJson) throws IOException {
        JsonNode session = MAPPER.readTree(sessionJson.toFile());
        String modelId = session.hasNonNull("modelId") ? session.get("modelId").asText() : null;
        Path messagesJsonl = SessionEngine.V3.transcriptFor(sessionJson);

        int turnCount = 0;
        double totalCredits = 0;
        Double contextUsagePercentage = null;
        if (Files.isRegularFile(messagesJsonl)) {
            for (String line : Files.readAllLines(messagesJsonl)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode payload;
                try {
                    payload = MAPPER.readTree(line).path("payload");
                } catch (IOException e) {
                    continue;
                }
                String type = payload.path("type").asText("");
                if ("usage_summary".equals(type)) {
                    turnCount++;
                    for (JsonNode entry : payload.path("promptTurnSummaries")) {
                        if ("credit".equals(entry.path("unit").asText(""))) {
                            totalCredits += entry.path("usage").asDouble(0);
                        } else {
                            logger.debug("Skipping V3 usage entry with unrecognized unit '{}' in {}",
                                entry.path("unit").asText(""), messagesJsonl);
                        }
                    }
                } else if ("session_metadata".equals(type) && "contextUsage".equals(payload.path("key").asText(""))) {
                    JsonNode pct = payload.path("value").path("usagePercentage");
                    if (pct.isNumber()) {
                        contextUsagePercentage = pct.asDouble();
                    }
                }
            }
        }
        return new SessionUsage(turnCount, totalCredits, contextUsagePercentage, null, modelId);
    }
}
