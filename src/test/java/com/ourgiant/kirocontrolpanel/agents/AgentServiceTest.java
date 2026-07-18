package com.ourgiant.kirocontrolpanel.agents;

import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceTest {

    @TempDir
    Path workspaceRoot;

    private final AgentService service = new AgentService();

    private Path agentsDir;

    @BeforeEach
    void setUp() throws IOException {
        agentsDir = workspaceRoot.resolve(".kiro").resolve("agents");
        Files.createDirectories(agentsDir);
    }

    @Test
    void savedAgentRoundTripsAllTypedFields() throws IOException {
        AgentConfig config = new AgentConfig(agentsDir.resolve("aws-rust-agent.json"), workspaceRoot);
        config.setDescription("Specialized agent for AWS and Rust development");
        config.setPrompt("You are an AWS and Rust expert.");
        config.setModel("claude-sonnet-4");
        config.setKeyboardShortcut("ctrl+shift+r");
        config.setWelcomeMessage("Ready to help!");
        config.setTools(List.of("read", "write", "shell"));
        config.setAllowedTools(List.of("read"));
        config.setIncludeMcpJson(true);

        service.save(config);
        AgentConfig reloaded = service.load(agentsDir.resolve("aws-rust-agent.json"), workspaceRoot);

        assertEquals("Specialized agent for AWS and Rust development", reloaded.getDescription());
        assertEquals("You are an AWS and Rust expert.", reloaded.getPrompt());
        assertEquals("claude-sonnet-4", reloaded.getModel());
        assertEquals("ctrl+shift+r", reloaded.getKeyboardShortcut());
        assertEquals("Ready to help!", reloaded.getWelcomeMessage());
        assertEquals(List.of("read", "write", "shell"), reloaded.getTools());
        assertEquals(List.of("read"), reloaded.getAllowedTools());
        assertEquals(Boolean.TRUE, reloaded.getIncludeMcpJson());
        assertFalse(reloaded.isGlobal());
        assertEquals("aws-rust-agent.json", reloaded.getFileName());
    }

    @Test
    void unmodeledFieldsRoundTripThroughExtra() throws IOException {
        // mcpServers/toolAliases/toolsSettings/resources/hooks/name have no
        // dedicated Form UI (see AgentFormPanel) -- this guards the exact
        // class of bug (a Jackson catch-all/@JsonIgnore gap) that the MCP
        // catalog feature's end-to-end harness caught and unit tests missed.
        String raw = """
            {
              "name": "aws-rust-agent",
              "description": "kept as a typed field",
              "mcpServers": {"fetch": {"command": "fetch-server", "args": []}},
              "toolAliases": {"@git/git_status": "status"},
              "hooks": {"agentSpawn": [{"command": "git status"}]}
            }
            """;
        Path filePath = agentsDir.resolve("aws-rust-agent.json");
        Files.writeString(filePath, raw);

        AgentConfig loaded = service.load(filePath, workspaceRoot);
        service.save(loaded);
        String rewritten = Files.readString(filePath);

        assertEquals("kept as a typed field", loaded.getDescription());
        assertEquals("aws-rust-agent", loaded.getExtra().get("name"));
        assertTrue(loaded.getExtra().containsKey("mcpServers"));
        assertTrue(loaded.getExtra().containsKey("toolAliases"));
        assertTrue(loaded.getExtra().containsKey("hooks"));
        assertTrue(rewritten.contains("\"fetch-server\""));
        assertTrue(rewritten.contains("\"git status\""));
        assertTrue(rewritten.contains("\"status\""));
    }

    @Test
    void listWorkspaceReturnsAllJsonFilesSorted() throws IOException {
        JsonMapperFactory.createMapper().writeValue(agentsDir.resolve("charlie.json").toFile(), new AgentConfig(null, null));
        JsonMapperFactory.createMapper().writeValue(agentsDir.resolve("alpha.json").toFile(), new AgentConfig(null, null));
        JsonMapperFactory.createMapper().writeValue(agentsDir.resolve("bravo.json").toFile(), new AgentConfig(null, null));
        Files.writeString(agentsDir.resolve("ignored.txt"), "not json");

        List<AgentConfig> agents = service.listWorkspace(workspaceRoot);

        assertEquals(3, agents.size());
        assertEquals(List.of("alpha.json", "bravo.json", "charlie.json"),
            agents.stream().map(AgentConfig::getFileName).toList());
    }

    @Test
    void deleteRemovesFileFromDisk() throws IOException {
        AgentConfig config = new AgentConfig(agentsDir.resolve("aws-rust-agent.json"), workspaceRoot);
        service.save(config);
        assertTrue(Files.exists(config.getFilePath()));

        service.delete(config);

        assertFalse(Files.exists(config.getFilePath()));
    }

    @Test
    void listWorkspaceReturnsEmptyWhenAgentsDirMissing() throws IOException {
        Files.delete(agentsDir);

        List<AgentConfig> agents = service.listWorkspace(workspaceRoot);

        assertTrue(agents.isEmpty());
    }

    @Test
    void loadOfGlobalAgentReportsIsGlobal() throws IOException {
        Path globalAgentsDir = Files.createDirectories(workspaceRoot.resolve("global-home").resolve("agents"));
        AgentConfig config = new AgentConfig(globalAgentsDir.resolve("global-agent.json"), null);
        service.save(config);

        AgentConfig reloaded = service.load(globalAgentsDir.resolve("global-agent.json"), null);

        assertTrue(reloaded.isGlobal());
    }
}
