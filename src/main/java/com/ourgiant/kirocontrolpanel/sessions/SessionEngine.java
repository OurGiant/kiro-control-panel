package com.ourgiant.kirocontrolpanel.sessions;

import java.nio.file.Path;

/**
 * Which kiro-cli agent engine wrote a session, and therefore which on-disk layout it uses
 * (issue #147). The two stores sit side by side under {@code ~/.kiro/sessions/} and V2 can't
 * open V3 sessions, so the app has to tell them apart:
 * <ul>
 *   <li>{@link #V2}: {@code sessions/cli/<uuid>.json} sidecar + {@code <uuid>.jsonl} transcript.</li>
 *   <li>{@link #V3}: {@code sessions/<workspace-hash>/sess_<uuid>/session.json} +
 *       {@code messages.jsonl} in the same directory. The session id is the directory name
 *       (confirmed against real data written by kiro-cli 2.16.2's bundled V3 engine).</li>
 * </ul>
 */
public enum SessionEngine {
    V2,
    V3;

    static final String V2_STORE_DIR_NAME = "cli";
    static final String V3_SESSION_DIR_PREFIX = "sess_";
    static final String V3_SIDECAR_NAME = "session.json";
    static final String V3_TRANSCRIPT_NAME = "messages.jsonl";

    private static final String V2_SIDECAR_SUFFIX = ".json";
    private static final String V2_TRANSCRIPT_SUFFIX = ".jsonl";

    public static SessionEngine ofSidecar(Path sidecar) {
        return isV3File(sidecar, V3_SIDECAR_NAME) ? V3 : V2;
    }

    public static SessionEngine ofTranscript(Path transcript) {
        return isV3File(transcript, V3_TRANSCRIPT_NAME) ? V3 : V2;
    }

    /** True for a {@code sess_*} directory, i.e. one V3 session's own folder. */
    static boolean isV3SessionDir(Path dir) {
        Path name = dir.getFileName();
        return name != null && name.toString().startsWith(V3_SESSION_DIR_PREFIX);
    }

    public String sessionIdFor(Path sidecar) {
        if (this == V3) {
            return sidecar.getParent().getFileName().toString();
        }
        String name = sidecar.getFileName().toString();
        return name.endsWith(V2_SIDECAR_SUFFIX)
            ? name.substring(0, name.length() - V2_SIDECAR_SUFFIX.length())
            : name;
    }

    public Path transcriptFor(Path sidecar) {
        if (this == V3) {
            return sidecar.resolveSibling(V3_TRANSCRIPT_NAME);
        }
        return sidecar.resolveSibling(sessionIdFor(sidecar) + V2_TRANSCRIPT_SUFFIX);
    }

    /** Short label for the UI. */
    public String label() {
        return this == V3 ? "V3" : "V2";
    }

    private static boolean isV3File(Path file, String expectedName) {
        Path name = file.getFileName();
        Path parent = file.getParent();
        return name != null && expectedName.equals(name.toString())
            && parent != null && isV3SessionDir(parent);
    }
}
