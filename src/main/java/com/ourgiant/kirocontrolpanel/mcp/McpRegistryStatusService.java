package com.ourgiant.kirocontrolpanel.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether kiro-cli is currently blocked -- fully or partially -- from an org-configured
 * MCP registry. Enterprise governance (set by a Kiro admin in the org's AWS account) can gate
 * which MCP servers are allowed to run against a registry URL. Two distinct states, both with
 * nothing in this app's own UI to explain why until now:
 * <ul>
 *   <li><b>Registry unreachable</b> (issue #138): kiro-cli disables <em>every</em> MCP server
 *       regardless of local {@code mcp.json} config.</li>
 *   <li><b>Registry reachable but only partially approving</b> (issue #139): kiro-cli still runs,
 *       but silently drops any locally-configured server whose name isn't recognized by the
 *       registry -- {@link McpRegistryStatus#ignoredServerNames()} carries exactly which ones.</li>
 * </ul>
 * There is no documented API for any of this -- everything here is inferred from hand-testing
 * against real enterprise-governed accounts (kiro-cli 2.12.1/2.16.2):
 * <ul>
 *   <li>{@code kiro-cli whoami} is non-interactive and reports "Logged in with IAM Identity
 *       Center" for an org-managed identity -- the prerequisite for registry governance existing
 *       at all. A personal/Builder-ID login never hits this path, so this check is skipped and no
 *       {@code mcp list} call is made for the common case.</li>
 *   <li>{@code kiro-cli mcp list} (no scope argument) is the one variant confirmed to reliably
 *       surface a loud, parseable failure when the registry fetch fails:
 *       {@code "Failed to fetch registry data: Failed to fetch MCP registry: HTTP 403 Forbidden"}.
 *       Other scopes behave inconsistently (e.g. {@code mcp list global} silently reports zero
 *       servers instead, with no error text at all) -- deliberately not relied on here.</li>
 *   <li>When the registry <em>is</em> reachable, that same {@code mcp list} output instead prints
 *       {@code "✓ <name>"} for each registry-approved server and, per scope/agent block, a
 *       {@code "Ignored (not in registry):"} subsection listing each locally-configured server
 *       that isn't -- {@code "⚠ <name> <command> (disabled)"}. Matching is by exact server name;
 *       a locally-configured name that's semantically the same server as a differently-named
 *       registry entry (e.g. {@code awspricing} locally vs. a registry entry named
 *       {@code awslabs.aws-pricing-mcp-server}) still shows as ignored.</li>
 *   <li><b>Exit code is always 0</b> in both the failure and partial-approval cases -- confirmed
 *       by hand across every scope variant. Detection is entirely stdout/stderr text parsing,
 *       never exit code.</li>
 * </ul>
 * Since this is screen-scraping an undocumented CLI's plain-text output (not a stable API), every
 * failure mode -- including an unrecognized future message format -- degrades to
 * {@link McpRegistryStatus#OK}: the whole point is to warn on a confidently-detected problem,
 * never to invent a false alarm from a parse miss.
 */
public final class McpRegistryStatusService {
    private static final Logger logger = LoggerFactory.getLogger(McpRegistryStatusService.class);
    private static final long TIMEOUT_SECONDS = 20;
    private static final String ENTERPRISE_MARKER = "IAM Identity Center";
    private static final String REGISTRY_FAILURE_MARKER = "Failed to fetch registry data";
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("HTTP\\s+(\\d{3})");
    // Matches a line like "⚠ awspricing   uvx (disabled) [legacy]" under kiro-cli's own
    // "Ignored (not in registry):" subheading -- captures just the server name (issue #139).
    // Confirmed by hand against a real reachable, partially-approving registry (kiro-cli 2.16.2).
    private static final Pattern IGNORED_SERVER_PATTERN = Pattern.compile("(?m)^\\s*⚠\\s+(\\S+)");

    private McpRegistryStatusService() {
    }

    public static McpRegistryStatus checkStatus() {
        return checkStatus("kiro-cli");
    }

    /** Package-private seam for tests: lets a nonexistent binary name exercise the degrade-quietly path. */
    static McpRegistryStatus checkStatus(String binary) {
        String whoamiOutput = run(binary, "whoami");
        if (whoamiOutput == null || !whoamiOutput.contains(ENTERPRISE_MARKER)) {
            return McpRegistryStatus.OK;
        }
        String listOutput = run(binary, "mcp", "list");
        if (listOutput == null) {
            return McpRegistryStatus.OK;
        }
        return parseListOutput(listOutput);
    }

    /** Package-private for tests. */
    static McpRegistryStatus parseListOutput(String mcpListOutput) {
        if (mcpListOutput.contains(REGISTRY_FAILURE_MARKER)) {
            Matcher matcher = HTTP_STATUS_PATTERN.matcher(mcpListOutput);
            Integer httpStatusCode = matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
            return McpRegistryStatus.unreachable(httpStatusCode);
        }
        return McpRegistryStatus.reachable(extractIgnoredServerNames(mcpListOutput));
    }

    /**
     * Union of every "⚠ name" entry across every scope/agent block in the output, rather than
     * trying to line this app's own Global/per-workspace scope model up with kiro-cli's own
     * per-agent grouping (default/workspace/global sections, each containing one block per named
     * agent) -- that mapping isn't confirmed, and a name ignored in any block means it's a real
     * name the registry doesn't recognize, not something scoped narrowly enough to ignore.
     */
    private static Set<String> extractIgnoredServerNames(String mcpListOutput) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = IGNORED_SERVER_PATTERN.matcher(mcpListOutput);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** Never throws; returns {@code null} on any failure (binary missing, timeout, interrupted). */
    private static String run(String binary, String... args) {
        List<String> command = new ArrayList<>();
        command.add(binary);
        command.addAll(List.of(args));
        try {
            // Merge stderr into stdout: kiro-cli's error text hasn't been confirmed to land on
            // one stream specifically, so both are captured rather than risking a silent miss.
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                logger.warn("Timed out running {} {}", binary, String.join(" ", args));
                return null;
            }
            return output;
        } catch (IOException e) {
            logger.warn("Failed to run {} {}: {}", binary, String.join(" ", args), e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
