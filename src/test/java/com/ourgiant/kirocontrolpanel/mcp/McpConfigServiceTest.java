package com.ourgiant.kirocontrolpanel.mcp;

import com.ourgiant.kirocontrolpanel.changelog.ChangeKind;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpConfigServiceTest {

    private final McpConfigService service = new McpConfigService();

    @TempDir
    Path tempDir;

    private Path configPath() {
        return tempDir.resolve("mcp.json");
    }

    @Test
    void localServerRoundTrips() throws IOException {
        McpConfigFile config = new McpConfigFile();
        McpServerConfig server = new McpServerConfig();
        server.setCommand("uvx");
        server.setArgs(List.of("mcp-server-fetch"));
        server.setEnv(Map.of("API_KEY", "${API_KEY}"));
        server.setAutoApprove(List.of("fetch"));
        server.setDisabledTools(List.of("dangerous_tool"));
        server.setDisabled(false);
        config.getMcpServers().put("fetch", server);

        service.save(configPath(), config);
        McpConfigFile reloaded = service.load(configPath());

        McpServerConfig reloadedServer = reloaded.getMcpServers().get("fetch");
        assertFalse(reloadedServer.isRemote());
        assertEquals("uvx", reloadedServer.getCommand());
        assertEquals(List.of("mcp-server-fetch"), reloadedServer.getArgs());
        assertEquals("${API_KEY}", reloadedServer.getEnv().get("API_KEY"));
        assertEquals(List.of("fetch"), reloadedServer.getAutoApprove());
        assertEquals(List.of("dangerous_tool"), reloadedServer.getDisabledTools());
        assertFalse(reloadedServer.isDisabled());
    }

    @Test
    void remoteServerRoundTrips() throws IOException {
        McpConfigFile config = new McpConfigFile();
        McpServerConfig server = new McpServerConfig();
        server.setUrl("https://mcp.example.com/endpoint");
        server.setHeaders(Map.of("Authorization", "Bearer xyz"));
        Map<String, Object> oauth = new LinkedHashMap<>();
        oauth.put("clientId", "abc123");
        server.setOauth(oauth);
        server.setOauthScopes(List.of("read", "write"));
        server.setDisabled(true);
        config.getMcpServers().put("remote-tool", server);

        service.save(configPath(), config);
        McpConfigFile reloaded = service.load(configPath());

        McpServerConfig reloadedServer = reloaded.getMcpServers().get("remote-tool");
        assertTrue(reloadedServer.isRemote());
        assertEquals("https://mcp.example.com/endpoint", reloadedServer.getUrl());
        assertEquals("Bearer xyz", reloadedServer.getHeaders().get("Authorization"));
        assertEquals("abc123", reloadedServer.getOauth().get("clientId"));
        assertEquals(List.of("read", "write"), reloadedServer.getOauthScopes());
        assertTrue(reloadedServer.isDisabled());
    }

    @Test
    void preservesServerInsertionOrder() throws IOException {
        McpConfigFile config = new McpConfigFile();
        config.getMcpServers().put("charlie", new McpServerConfig());
        config.getMcpServers().put("alpha", new McpServerConfig());
        config.getMcpServers().put("bravo", new McpServerConfig());

        service.save(configPath(), config);
        McpConfigFile reloaded = service.load(configPath());

        assertEquals(List.of("charlie", "alpha", "bravo"), List.copyOf(reloaded.getMcpServers().keySet()));
    }

    @Test
    void preservesUnknownFieldsOnServerAndTopLevel() throws IOException {
        String raw = """
            {
              "mcpServers": {
                "future-server": {
                  "command": "foo",
                  "someFutureField": "keep-me"
                }
              },
              "someTopLevelFutureField": "keep-me-too"
            }
            """;
        Files.writeString(configPath(), raw);

        McpConfigFile config = service.load(configPath());
        service.save(configPath(), config);
        String rewritten = Files.readString(configPath());

        assertTrue(rewritten.contains("keep-me-too"));
        assertTrue(rewritten.contains("keep-me"));
    }

    @Test
    void serializedOutputDoesNotLeakTheDerivedIsRemoteProperty() throws IOException {
        // isRemote() is a derived convenience method, not real config state --
        // without @JsonIgnore, Jackson's bean-property introspection treats
        // any isXxx() getter as a serializable "remote" field, polluting
        // mcp.json with something Kiro's own schema doesn't define.
        McpConfigFile config = new McpConfigFile();
        McpServerConfig server = new McpServerConfig();
        server.setUrl("https://mcp.example.com/endpoint");
        config.getMcpServers().put("url-based-tool", server);

        service.save(configPath(), config);
        String written = Files.readString(configPath());

        assertFalse(written.contains("\"remote\""), "serialized mcp.json should not contain a \"remote\" field: " + written);
    }

    @Test
    void loadOfMissingFileReturnsEmptyConfig() {
        McpConfigFile config = service.load(tempDir.resolve("does-not-exist.json"));

        assertTrue(config.getMcpServers().isEmpty());
    }

    @Test
    void savingRecordsChangeLogEntries() throws IOException {
        // Needs a real .kiro/settings/ path -- configPath() (bare tempDir/mcp.json) has no
        // .kiro segment, so KiroSurface.classify would (correctly) skip it entirely.
        Path path = tempDir.resolve(".kiro").resolve("settings").resolve("mcp.json");
        McpConfigFile config = new McpConfigFile();

        service.save(path, config);
        assertEquals(List.of(ChangeKind.CREATED), kindsFor(path));

        service.save(path, config);
        assertEquals(List.of(ChangeKind.CREATED, ChangeKind.MODIFIED), kindsFor(path));
    }

    /** Filters the (shared, whole-test-suite) change log down to entries for one specific path, in recorded order. */
    private static List<ChangeKind> kindsFor(Path path) {
        return ChangeLogService.loadSince(Instant.EPOCH).reversed().stream()
            .filter(e -> e.path().equals(path.toAbsolutePath().normalize()))
            .map(e -> e.kindEnum())
            .toList();
    }
}
