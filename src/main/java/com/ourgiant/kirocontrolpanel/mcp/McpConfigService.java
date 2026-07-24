package com.ourgiant.kirocontrolpanel.mcp;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.util.GitAutoCommitter;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import com.ourgiant.kirocontrolpanel.util.SelfWriteTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes .kiro/settings/mcp.json — the exact file Kiro itself
 * reads and reloads automatically on save, so no extra signal is needed
 * after writing.
 */
public class McpConfigService {
    private static final Logger logger = LoggerFactory.getLogger(McpConfigService.class);

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();
    private final PrettyPrinter prettyPrinter = JsonMapperFactory.createPrettyPrinter();

    public McpConfigFile loadGlobal() {
        return load(KiroPaths.globalMcpConfig());
    }

    public McpConfigFile loadWorkspace(Path workspaceRoot) {
        return load(KiroPaths.workspaceMcpConfig(workspaceRoot));
    }

    public McpConfigFile load(Path path) {
        if (!Files.exists(path)) {
            return new McpConfigFile();
        }
        try {
            return mapper.readValue(path.toFile(), McpConfigFile.class);
        } catch (IOException e) {
            logger.error("Failed to read MCP config {}", path, e);
            return new McpConfigFile();
        }
    }

    public void saveGlobal(McpConfigFile config) throws IOException {
        save(KiroPaths.globalMcpConfig(), config);
    }

    public void saveWorkspace(Path workspaceRoot, McpConfigFile config) throws IOException {
        save(KiroPaths.workspaceMcpConfig(workspaceRoot), config);
    }

    public void save(Path path, McpConfigFile config) throws IOException {
        Files.createDirectories(path.getParent());
        SelfWriteTracker.markAboutToWrite(path);
        mapper.writer(prettyPrinter).writeValue(path.toFile(), config);
        logger.info("Saved MCP config {}", path);
        GitAutoCommitter.commitIfEnabled(path);
    }
}
