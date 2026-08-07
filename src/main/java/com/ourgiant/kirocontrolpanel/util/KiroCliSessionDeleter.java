package com.ourgiant.kirocontrolpanel.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Deletes a saved kiro-cli chat session by ID via {@code kiro-cli chat --delete-session <id>} --
 * confirmed real via {@code kiro-cli chat --help}, verified by hand against a real leftover
 * session (both the command's own success output and that the session's files were actually
 * removed from disk) before this was first wired in for issue #124's throwaway-session cleanup.
 * Shared here since #126's bulk empty-session clean-up is a second, independent consumer of the
 * exact same mechanism.
 * <p>
 * Best-effort by design: never throws, only logs and returns {@code false} on failure, since
 * every caller treats deletion as cleanup where "try again later" is an acceptable outcome, not
 * a critical path that should turn into a bigger failure of its own.
 * <p>
 * {@code sessionId} is always required to be a well-formed UUID before it's passed to
 * {@link ProcessBuilder}. kiro-cli's ACP {@code session/new} response is inherently trusted (this
 * app just spawned the process and read its own stdout), but #126's bulk clean-up instead sources
 * it from {@code SessionManifest.sessionId()} -- free-form JSON text read back out of a session
 * file under {@code ~/.kiro/sessions/cli/} (see {@code SessionManifestParser.parseSidecar}), not
 * something kiro-cli handed this app directly. {@link ProcessBuilder}'s array form never invokes a
 * shell, so shell metacharacters in that text can't do anything -- but an unvalidated value could
 * still be read by kiro-cli's own argument parser as a flag rather than a session id (e.g. a value
 * starting with {@code -}). Validating against the one shape kiro-cli itself ever generates closes
 * that off entirely, rather than trying to enumerate unsafe characters. See #136.
 */
public final class KiroCliSessionDeleter {
    private static final Logger logger = LoggerFactory.getLogger(KiroCliSessionDeleter.class);
    private static final long TIMEOUT_SECONDS = 15;

    private KiroCliSessionDeleter() {
    }

    public static boolean delete(String sessionId) {
        return delete("kiro-cli", sessionId);
    }

    /** Package-private seam for tests: lets a nonexistent binary name exercise the failure path. */
    static boolean delete(String binary, String sessionId) {
        UUID parsedSessionId = parseSessionId(sessionId);
        if (parsedSessionId == null) {
            logger.warn("Refusing to delete kiro-cli session with a malformed id: {}", sessionId);
            return false;
        }
        // Deliberately pass parsedSessionId.toString() below, not the original sessionId
        // parameter -- ProcessBuilder's argument must be derived from the validated UUID object,
        // not the raw tainted string, or the taint never actually clears.
        try {
            Process process = new ProcessBuilder(binary, "chat", "--delete-session", parsedSessionId.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                logger.warn("Timed out deleting kiro-cli session {}", sessionId);
                return false;
            }
            if (process.exitValue() != 0) {
                logger.warn("kiro-cli exited {} deleting session {}", process.exitValue(), sessionId);
                return false;
            }
            return true;
        } catch (IOException e) {
            logger.warn("Failed to delete kiro-cli session {}", sessionId, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static UUID parseSessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
