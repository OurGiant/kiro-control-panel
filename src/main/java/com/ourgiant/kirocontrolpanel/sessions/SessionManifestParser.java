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
 * {@code results.<id>.tool.kind.BuiltIn.<ToolName>}. Only the {@code FileRead}
 * shape has been observed in real data -- it's a read, not a write, so it
 * deliberately does not contribute to {@code filesTouched} (which is meant to
 * answer "what did this session write or edit"). File-write and shell-exec
 * {@code BuiltIn} shapes are unconfirmed; unrecognized tool kinds are skipped
 * (logged at debug), never treated as an error, so the parser degrades
 * gracefully rather than guessing field names.
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

    public static SessionSidecar parseSidecar(Path jsonSidecar) throws IOException {
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
                    default -> logger.debug(
                        "Unrecognized BuiltIn tool kind '{}', skipping files/repos extraction for it", toolName);
                }
            }
        }
    }
}
