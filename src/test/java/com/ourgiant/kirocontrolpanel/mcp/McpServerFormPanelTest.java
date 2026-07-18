package com.ourgiant.kirocontrolpanel.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Form/Raw JSON tab-sync logic that was previously untested —
 * McpServerFormPanel is a plain JPanel (unlike McpServerEditDialog, a
 * JDialog), which can be constructed in a headless test run; JOptionPane
 * message dialogs can't, so the invalid-JSON error-dialog path is exercised
 * here only up to the point of parse failure (tryParseRawJsonIntoForm
 * returning non-null), not the actual dialog/tab-revert — that needs a real
 * display, see .claude/skills/verify/SKILL.md.
 */
class McpServerFormPanelTest {

    @Test
    void buildServerReflectsLocalFormFields() throws Exception {
        McpServerFormPanel panel = new McpServerFormPanel();
        panel.setServerName("fetch");
        panel.setDefaultsForNewServer();
        setText(panel, "commandField", "uvx");
        setText(panel, "argsArea", "mcp-server-fetch");
        setText(panel, "envArea", "API_KEY=${API_KEY}");
        setText(panel, "autoApproveField", "fetch");

        McpServerConfig server = panel.buildServer();

        assertEquals("uvx", server.getCommand());
        assertEquals(List.of("mcp-server-fetch"), server.getArgs());
        assertEquals("${API_KEY}", server.getEnv().get("API_KEY"));
        assertEquals(List.of("fetch"), server.getAutoApprove());
        assertTrue(!server.isRemote());
    }

    @Test
    void buildServerReflectsRemoteFormFields() throws Exception {
        McpServerFormPanel panel = new McpServerFormPanel();
        panel.setServerName("remote-tool");
        selectType(panel, "Remote (url)");
        setText(panel, "urlField", "https://mcp.example.com/endpoint");
        setText(panel, "headersArea", "Authorization=Bearer xyz");
        setText(panel, "oauthClientIdField", "abc123");

        McpServerConfig server = panel.buildServer();

        assertTrue(server.isRemote());
        assertEquals("https://mcp.example.com/endpoint", server.getUrl());
        assertEquals("Bearer xyz", server.getHeaders().get("Authorization"));
        assertEquals("abc123", server.getOauth().get("clientId"));
    }

    @Test
    void populateFromThenBuildServerRoundTrips() {
        McpServerFormPanel panel = new McpServerFormPanel();
        McpServerConfig original = new McpServerConfig();
        original.setCommand("npx");
        original.setArgs(List.of("-y", "some-server"));
        original.setDisabled(true);

        panel.populateFrom(original);
        McpServerConfig rebuilt = panel.buildServer();

        assertEquals("npx", rebuilt.getCommand());
        assertEquals(List.of("-y", "some-server"), rebuilt.getArgs());
        assertTrue(rebuilt.isDisabled());
    }

    @Test
    void switchingFormToRawJsonTabSerializesCurrentFormState() throws Exception {
        McpServerFormPanel panel = new McpServerFormPanel();
        panel.setDefaultsForNewServer();
        setText(panel, "commandField", "uvx");
        setText(panel, "argsArea", "mcp-server-fetch");

        panel.selectRawJsonTab();

        String rawJson = panel.getRawJsonText();
        assertTrue(rawJson.contains("\"command\""), "raw JSON should reflect the form's command field");
        assertTrue(rawJson.contains("uvx"));
        assertTrue(rawJson.contains("mcp-server-fetch"));
    }

    @Test
    void editingValidRawJsonThenSwitchingBackUpdatesForm() throws Exception {
        McpServerFormPanel panel = new McpServerFormPanel();
        panel.setDefaultsForNewServer();
        panel.selectRawJsonTab();

        panel.setRawJsonText("""
            {
              "command": "npx",
              "args": ["-y", "edited-server"]
            }
            """);
        panel.selectFormTab();

        assertEquals(0, panel.getSelectedTabIndex(), "valid JSON should let the tab switch succeed");
        McpServerConfig server = panel.buildServer();
        assertEquals("npx", server.getCommand());
        assertEquals(List.of("-y", "edited-server"), server.getArgs());
    }

    @Test
    void tryParseRawJsonIntoFormDetectsInvalidJsonWithoutShowingADialog() {
        McpServerFormPanel panel = new McpServerFormPanel();
        panel.setRawJsonText("{ not valid json");

        String error = panel.tryParseRawJsonIntoForm();

        assertNotNull(error, "invalid JSON should be reported as a parse error");
    }

    @Test
    void tryParseRawJsonIntoFormAcceptsValidJson() {
        McpServerFormPanel panel = new McpServerFormPanel();
        panel.setRawJsonText("{ \"command\": \"echo\" }");

        String error = panel.tryParseRawJsonIntoForm();

        assertNull(error);
        assertEquals("echo", panel.buildServer().getCommand());
    }

    @Test
    void repeatedTabSwitchingDoesNotCorruptState() throws Exception {
        McpServerFormPanel panel = new McpServerFormPanel();
        panel.setDefaultsForNewServer();
        setText(panel, "commandField", "uvx");

        panel.selectRawJsonTab();
        panel.selectFormTab();
        panel.selectRawJsonTab();
        panel.selectFormTab();

        assertEquals(0, panel.getSelectedTabIndex());
        assertEquals("uvx", panel.buildServer().getCommand());
    }

    private static void setText(McpServerFormPanel panel, String fieldName, String text) throws Exception {
        var field = McpServerFormPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object component = field.get(panel);
        component.getClass().getMethod("setText", String.class).invoke(component, text);
    }

    private static void selectType(McpServerFormPanel panel, String type) throws Exception {
        var field = McpServerFormPanel.class.getDeclaredField("typeCombo");
        field.setAccessible(true);
        javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) field.get(panel);
        combo.setSelectedItem(type);
    }
}
