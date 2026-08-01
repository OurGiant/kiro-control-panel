package com.ourgiant.kirocontrolpanel.agents;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.changelog.ChangeKind;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogService;
import com.ourgiant.kirocontrolpanel.changelog.ChangeSource;
import com.ourgiant.kirocontrolpanel.util.GitAutoCommitter;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import com.ourgiant.kirocontrolpanel.util.SelfWriteTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads and writes .kiro/agents/*.json files -- one file per agent, unlike
 * mcp.json/hooks which hold a map/array of many entries in one file.
 */
public class AgentService {
    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();
    private final PrettyPrinter prettyPrinter = JsonMapperFactory.createPrettyPrinter();

    public List<AgentConfig> listGlobal() {
        return list(KiroPaths.globalAgentsDir(), null);
    }

    public List<AgentConfig> listWorkspace(Path workspaceRoot) {
        return list(KiroPaths.workspaceAgentsDir(workspaceRoot), workspaceRoot);
    }

    private List<AgentConfig> list(Path dir, Path workspaceRoot) {
        List<AgentConfig> agents = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return agents;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted()
                .forEach(p -> {
                    try {
                        agents.add(load(p, workspaceRoot));
                    } catch (IOException e) {
                        logger.warn("Failed to read agent config {}", p, e);
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to list agents directory {}", dir, e);
        }
        return agents;
    }

    public AgentConfig load(Path filePath, Path workspaceRoot) throws IOException {
        AgentConfig config = new AgentConfig(filePath, workspaceRoot);
        return mapper.readerForUpdating(config).readValue(filePath.toFile());
    }

    /**
     * Deep-copies {@code source}'s data (including unmodeled fields carried in {@code extra})
     * into a fresh config identified by {@code targetPath} -- for the Agents panel's "Duplicate"
     * action. A manual field-by-field copy would silently drop anything only reachable via the
     * Raw JSON tab.
     */
    public AgentConfig copy(AgentConfig source, Path targetPath, Path workspaceRoot) throws IOException {
        AgentConfig copy = new AgentConfig(targetPath, workspaceRoot);
        JsonNode tree = mapper.valueToTree(source);
        return mapper.readerForUpdating(copy).readValue(tree);
    }

    public void save(AgentConfig config) throws IOException {
        Path filePath = config.getFilePath();
        boolean existedBefore = Files.exists(filePath);
        SelfWriteTracker.markAboutToWrite(filePath.getParent());
        Files.createDirectories(filePath.getParent());
        SelfWriteTracker.markAboutToWrite(filePath);
        mapper.writer(prettyPrinter).writeValue(filePath.toFile(), config);
        logger.info("Saved agent config {}", filePath);
        GitAutoCommitter.commitIfEnabled(filePath);
        ChangeLogService.record(filePath, existedBefore ? ChangeKind.MODIFIED : ChangeKind.CREATED, ChangeSource.SELF);
    }

    public void delete(AgentConfig config) throws IOException {
        boolean existed = Files.deleteIfExists(config.getFilePath());
        if (existed) {
            logger.info("Deleted agent config {}", config.getFilePath());
            ChangeLogService.record(config.getFilePath(), ChangeKind.DELETED, ChangeSource.SELF);
        }
    }
}
