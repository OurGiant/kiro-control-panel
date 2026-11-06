package com.ourgiant.kirocontrolpanel.sessions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a V3 session folder in the shape kiro-cli 2.16.2's bundled V3 engine produced on a
 * real machine (issue #147), trimmed to the fields the app reads plus a few it must ignore.
 */
final class V3SessionFixtures {
    static final String WORKSPACE_HASH = "1818c5a3fb50f799";

    private V3SessionFixtures() {
    }

    /** Writes {@code <sessionsRoot>/<hash>/<sessionId>/session.json + messages.jsonl}; returns session.json. */
    static Path writeV3Session(Path sessionsRoot, String sessionId, String workspace, String title) throws IOException {
        Path sessionDir = sessionsRoot.resolve(WORKSPACE_HASH).resolve(sessionId);
        Files.createDirectories(sessionDir.resolve("sub-executions"));
        Path sessionJson = sessionDir.resolve("session.json");
        Files.writeString(sessionJson, """
            {
              "schemaVersion": "1.0.0",
              "dataModelVersion": 1,
              "id": "%s",
              "title": "%s",
              "agentMode": "vibe",
              "workspacePaths": ["%s"],
              "createdAt": "2026-08-07T11:31:13.879Z",
              "lastModifiedAt": "2026-08-07T11:33:59.922Z",
              "modelId": "auto",
              "status": "idle"
            }
            """.formatted(sessionId, title, workspace));
        Files.writeString(sessionDir.resolve("messages.jsonl"), String.join("\n",
            "{\"id\":\"s0\",\"timestamp\":\"2026-08-07T11:31:13.900Z\",\"payload\":{\"type\":\"session_start\",\"agentType\":\"vibe\",\"content\":\"You are Kiro (system prompt, must not be indexed)\"}}",
            "{\"id\":\"u1\",\"timestamp\":\"2026-08-07T11:31:22.545Z\",\"payload\":{\"type\":\"user\",\"content\":\"what can you tell me about this project?\",\"images\":[],\"documents\":[]}}",
            "{\"id\":\"t1\",\"timestamp\":\"2026-08-07T11:31:22.560Z\",\"payload\":{\"type\":\"turn_start\",\"executionId\":\"e1\"}}",
            "{\"id\":\"m1\",\"timestamp\":\"2026-08-07T11:31:23.000Z\",\"payload\":{\"type\":\"session_metadata\",\"key\":\"contextUsage\",\"value\":{\"usagePercentage\":2.5}}}",
            "{\"id\":\"c1\",\"timestamp\":\"2026-08-07T11:31:30.000Z\",\"payload\":{\"type\":\"tool_call\",\"toolCallId\":\"c1\",\"toolName\":\"readFile\",\"args\":{\"path\":\"pom.xml\"},\"kind\":\"read\"}}",
            "{\"id\":\"r1\",\"timestamp\":\"2026-08-07T11:31:31.000Z\",\"payload\":{\"type\":\"tool_result\",\"toolCallId\":\"c1\",\"content\":\"<project>tool output, must not be indexed</project>\"}}",
            "{\"id\":\"c2\",\"timestamp\":\"2026-08-07T11:31:40.000Z\",\"payload\":{\"type\":\"tool_call\",\"toolCallId\":\"c2\",\"toolName\":\"fs_write\",\"args\":{\"path\":\"README.md\"},\"kind\":\"edit\"}}",
            "{\"id\":\"c3\",\"timestamp\":\"2026-08-07T11:31:41.000Z\",\"payload\":{\"type\":\"tool_call\",\"toolCallId\":\"c3\",\"toolName\":\"strReplace\",\"args\":{\"path\":\"/abs/Other.java\"},\"kind\":\"edit\"}}",
            "{\"id\":\"a1\",\"timestamp\":\"2026-08-07T11:33:00.000Z\",\"payload\":{\"type\":\"assistant\",\"content\":\"This is AWS IDP SAML UI, a Swing app.\"}}",
            "{\"id\":\"us1\",\"timestamp\":\"2026-08-07T11:33:01.000Z\",\"payload\":{\"type\":\"usage_summary\",\"promptTurnSummaries\":[{\"unit\":\"credit\",\"unitPlural\":\"credits\",\"usage\":1.25},{\"unit\":\"token\",\"usage\":999}]}}",
            "{\"id\":\"m2\",\"timestamp\":\"2026-08-07T11:33:02.000Z\",\"payload\":{\"type\":\"session_metadata\",\"key\":\"contextUsage\",\"value\":{\"usagePercentage\":7.75}}}",
            "{\"id\":\"te1\",\"timestamp\":\"2026-08-07T11:33:03.000Z\",\"payload\":{\"type\":\"turn_end\",\"stopReason\":\"end_turn\"}}",
            ""));
        Files.writeString(sessionDir.resolve("publish.cursor"), "109196:32");
        return sessionJson;
    }
}
