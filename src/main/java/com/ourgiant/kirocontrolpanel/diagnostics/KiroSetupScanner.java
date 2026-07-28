package com.ourgiant.kirocontrolpanel.diagnostics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.agents.AgentService;
import com.ourgiant.kirocontrolpanel.hooks.Hook;
import com.ourgiant.kirocontrolpanel.hooks.HookFile;
import com.ourgiant.kirocontrolpanel.hooks.HookService;
import com.ourgiant.kirocontrolpanel.mcp.McpConfigFile;
import com.ourgiant.kirocontrolpanel.mcp.McpServerConfig;
import com.ourgiant.kirocontrolpanel.skills.SkillService;
import com.ourgiant.kirocontrolpanel.steering.SteeringDoc;
import com.ourgiant.kirocontrolpanel.steering.SteeringService;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scans Kiro's on-disk config files for structural problems -- files that
 * are unparseable, or that parse fine but don't match the shape Kiro
 * expects. Deliberately does not judge content quality (missing
 * descriptions, disabled/autoApprove policy, etc.) -- see issue #78.
 * <p>
 * Every existing {@code *Service.list*()}/{@code load()} method silently
 * swallows parse/IO errors into a log line and omits the broken item, so a
 * corrupt file looks identical to "nothing configured." This scanner
 * bypasses that: MCP/Hooks are parsed directly via Jackson, and
 * Steering/Skills/Agents reuse each service's public single-file
 * {@code load(path, workspaceRoot)} method, which does throw.
 */
public class KiroSetupScanner {
    private static final Logger logger = LoggerFactory.getLogger(KiroSetupScanner.class);

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();
    private final SteeringService steeringService = new SteeringService();
    private final SkillService skillService = new SkillService();
    private final HookService hookService = new HookService();
    private final AgentService agentService = new AgentService();

    /** Scans every surface across every given scope (Hooks is skipped for Global scopes -- Kiro has no global hooks dir). */
    public List<Finding> scan(List<WorkspaceScope> scopes) {
        List<Finding> findings = new ArrayList<>();
        for (WorkspaceScope scope : scopes) {
            findings.addAll(scanMcp(scope));
            findings.addAll(scanSteering(scope));
            findings.addAll(scanSkills(scope));
            findings.addAll(scanHooks(scope));
            findings.addAll(scanAgents(scope));
        }
        return findings;
    }

    List<Finding> scanMcp(WorkspaceScope scope) {
        Path path = scope.isGlobal() ? KiroPaths.globalMcpConfig() : KiroPaths.workspaceMcpConfig(scope.workspaceRoot());
        if (!Files.exists(path)) {
            return List.of();
        }
        McpConfigFile config;
        try {
            config = mapper.readValue(path.toFile(), McpConfigFile.class);
        } catch (IOException e) {
            return List.of(invalid(KiroSurface.MCP, scope, path, "mcp.json is not valid JSON: " + e.getMessage()));
        }
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, McpServerConfig> entry : config.getMcpServers().entrySet()) {
            McpServerConfig server = entry.getValue();
            if (isBlank(server.getCommand()) && !server.isRemote()) {
                findings.add(structural(KiroSurface.MCP, scope, path,
                    "MCP server \"" + entry.getKey() + "\" has neither a command nor a url", null));
            }
        }
        return findings;
    }

    List<Finding> scanSteering(WorkspaceScope scope) {
        Path dir = scope.isGlobal() ? KiroPaths.globalSteeringDir() : KiroPaths.workspaceSteeringDir(scope.workspaceRoot());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".md"))
                .sorted()
                .forEach(path -> findings.addAll(scanSteeringFile(scope, path)));
        } catch (IOException e) {
            logger.warn("Failed to list steering directory {}", dir, e);
        }
        return findings;
    }

    private List<Finding> scanSteeringFile(WorkspaceScope scope, Path path) {
        SteeringDoc doc;
        try {
            doc = steeringService.load(path, scope.workspaceRoot());
        } catch (IOException | RuntimeException e) {
            return List.of(invalid(KiroSurface.STEERING, scope, path, "Not valid front matter/Markdown: " + e.getMessage()));
        }
        String inclusion = doc.getInclusion();
        if ("fileMatch".equals(inclusion) && doc.getFileMatchPatterns().isEmpty()) {
            return List.of(structural(KiroSurface.STEERING, scope, path,
                "inclusion: fileMatch but no fileMatchPattern is set", null));
        }
        if ("auto".equals(inclusion) && (isBlank(doc.getName()) || isBlank(doc.getDescription()))) {
            return List.of(structural(KiroSurface.STEERING, scope, path,
                "inclusion: auto requires both name and description", null));
        }
        return List.of();
    }

    List<Finding> scanSkills(WorkspaceScope scope) {
        Path dir = scope.isGlobal() ? KiroPaths.globalSkillsDir() : KiroPaths.workspaceSkillsDir(scope.workspaceRoot());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.sorted().forEach(entry -> findings.addAll(scanSkillEntry(scope, entry)));
        } catch (IOException e) {
            logger.warn("Failed to list skills directory {}", dir, e);
        }
        return findings;
    }

    private List<Finding> scanSkillEntry(WorkspaceScope scope, Path entry) {
        if (Files.isRegularFile(entry) && entry.getFileName().toString().endsWith(".md")) {
            return List.of(structural(KiroSurface.SKILLS, scope, entry,
                "\"" + entry.getFileName() + "\" is a loose file in skills/ -- Kiro requires SKILL.md inside its own subfolder",
                new SkillFileRelocationFix(entry)));
        }
        if (Files.isDirectory(entry)) {
            Path skillMd = entry.resolve("SKILL.md");
            if (Files.isRegularFile(skillMd)) {
                try {
                    skillService.load(entry, scope.workspaceRoot());
                } catch (IOException | RuntimeException e) {
                    return List.of(invalid(KiroSurface.SKILLS, scope, skillMd,
                        "SKILL.md is not valid front matter/Markdown: " + e.getMessage()));
                }
            }
        }
        return List.of();
    }

    List<Finding> scanHooks(WorkspaceScope scope) {
        if (scope.isGlobal()) {
            return List.of();
        }
        Path dir = KiroPaths.workspaceHooksDir(scope.workspaceRoot());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted()
                .forEach(path -> findings.addAll(scanHookFile(scope, path)));
        } catch (IOException e) {
            logger.warn("Failed to list hooks directory {}", dir, e);
        }
        return findings;
    }

    private List<Finding> scanHookFile(WorkspaceScope scope, Path path) {
        JsonNode root;
        try {
            root = mapper.readTree(path.toFile());
        } catch (IOException e) {
            return List.of(invalid(KiroSurface.HOOKS, scope, path, "Not valid JSON: " + e.getMessage()));
        }
        if (root.isArray()) {
            try {
                List<Hook> hooks = mapper.convertValue(root, new TypeReference<List<Hook>>() { });
                return List.of(structural(KiroSurface.HOOKS, scope, path,
                    "Hooks file is a bare array; Kiro expects {\"version\":\"v1\",\"hooks\":[...]}",
                    new HookArrayWrapFix(path, hooks, hookService)));
            } catch (IllegalArgumentException e) {
                return List.of(invalid(KiroSurface.HOOKS, scope, path, "Not a recognized hooks shape"));
            }
        }
        if (!root.isObject()) {
            return List.of(invalid(KiroSurface.HOOKS, scope, path, "Not a recognized hooks shape"));
        }
        HookFile hookFile;
        try {
            hookFile = mapper.treeToValue(root, HookFile.class);
        } catch (IOException e) {
            return List.of(invalid(KiroSurface.HOOKS, scope, path, "Does not match the expected hooks schema: " + e.getMessage()));
        }
        List<Finding> findings = new ArrayList<>();
        for (Hook hook : hookFile.getHooks()) {
            if (isBlank(hook.getName()) || isBlank(hook.getTrigger()) || hook.getAction() == null) {
                findings.add(structural(KiroSurface.HOOKS, scope, path,
                    "A hook entry is missing name, trigger, or action", null));
            }
        }
        return findings;
    }

    List<Finding> scanAgents(WorkspaceScope scope) {
        Path dir = scope.isGlobal() ? KiroPaths.globalAgentsDir() : KiroPaths.workspaceAgentsDir(scope.workspaceRoot());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted()
                .forEach(path -> {
                    try {
                        agentService.load(path, scope.workspaceRoot());
                    } catch (IOException e) {
                        findings.add(invalid(KiroSurface.AGENTS, scope, path, "Not valid JSON: " + e.getMessage()));
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to list agents directory {}", dir, e);
        }
        return findings;
    }

    private static Finding invalid(KiroSurface surface, WorkspaceScope scope, Path path, String message) {
        return new Finding(surface, scope, FindingSeverity.INVALID, path, message, null);
    }

    private static Finding structural(KiroSurface surface, WorkspaceScope scope, Path path, String message, Fix fix) {
        return new Finding(surface, scope, FindingSeverity.STRUCTURAL, path, message, fix);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
