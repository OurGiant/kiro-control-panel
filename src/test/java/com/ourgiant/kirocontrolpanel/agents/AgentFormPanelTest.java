package com.ourgiant.kirocontrolpanel.agents;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Form/Raw JSON tab-sync logic that was previously untested —
 * AgentFormPanel is a plain JPanel (unlike AgentEditDialog, a JDialog),
 * which can be constructed in a headless test run; JOptionPane message
 * dialogs can't, so the invalid-JSON error-dialog path is exercised here
 * only up to the point of parse failure (tryParseRawJsonIntoForm returning
 * non-null), not the actual dialog/tab-revert — that needs a real display,
 * see .claude/skills/verify/SKILL.md.
 */
class AgentFormPanelTest {

    @Test
    void buildAgentReflectsScalarFormFields() throws Exception {
        AgentFormPanel panel = new AgentFormPanel();
        setText(panel, "descriptionField", "AWS and Rust expert");
        setText(panel, "promptArea", "You are an AWS and Rust expert.");
        setText(panel, "modelField", "claude-sonnet-4");
        setText(panel, "keyboardShortcutField", "ctrl+shift+r");
        setText(panel, "welcomeMessageArea", "Ready to help!");

        AgentConfig agent = panel.buildAgent();

        assertEquals("AWS and Rust expert", agent.getDescription());
        assertEquals("You are an AWS and Rust expert.", agent.getPrompt());
        assertEquals("claude-sonnet-4", agent.getModel());
        assertEquals("ctrl+shift+r", agent.getKeyboardShortcut());
        assertEquals("Ready to help!", agent.getWelcomeMessage());
    }

    @Test
    void buildAgentReflectsToolsListsAndCheckbox() throws Exception {
        AgentFormPanel panel = new AgentFormPanel();
        setText(panel, "toolsField", "read, write, shell");
        setText(panel, "allowedToolsField", "read");
        setChecked(panel, "includeMcpJsonCheckBox", true);

        AgentConfig agent = panel.buildAgent();

        assertEquals(List.of("read", "write", "shell"), agent.getTools());
        assertEquals(List.of("read"), agent.getAllowedTools());
        assertEquals(Boolean.TRUE, agent.getIncludeMcpJson());
    }

    @Test
    void populateFromThenBuildAgentRoundTripsIncludingExtraFields() throws Exception {
        // Parsed via real deserialization (not hand-built) so this exercises
        // the actual path AgentService.load() uses -- mcpServers/hooks have
        // no dedicated Form UI and must survive a Form-tab-only edit.
        String raw = """
            {
              "description": "kept as typed field",
              "mcpServers": {"fetch": {"command": "fetch-server", "args": []}},
              "hooks": {"agentSpawn": [{"command": "git status"}]}
            }
            """;
        AgentConfig original = com.ourgiant.kirocontrolpanel.util.JsonMapperFactory.createMapper()
            .readerForUpdating(new AgentConfig(null, null))
            .readValue(raw, AgentConfig.class);

        AgentFormPanel panel = new AgentFormPanel();
        panel.populateFrom(original);
        AgentConfig rebuilt = panel.buildAgent();

        assertEquals("kept as typed field", rebuilt.getDescription());
        assertTrue(rebuilt.getExtra().containsKey("mcpServers"), "unmodeled mcpServers should survive a Form-tab round trip");
        assertTrue(rebuilt.getExtra().containsKey("hooks"), "unmodeled hooks should survive a Form-tab round trip");
    }

    @Test
    void switchingFormToRawJsonTabSerializesCurrentFormState() throws Exception {
        AgentFormPanel panel = new AgentFormPanel();
        setText(panel, "descriptionField", "aws-rust-agent description");
        setText(panel, "modelField", "claude-sonnet-4");

        panel.selectRawJsonTab();

        String rawJson = panel.getRawJsonText();
        assertTrue(rawJson.contains("aws-rust-agent description"));
        assertTrue(rawJson.contains("claude-sonnet-4"));
    }

    @Test
    void editingValidRawJsonThenSwitchingBackUpdatesForm() throws Exception {
        AgentFormPanel panel = new AgentFormPanel();
        panel.selectRawJsonTab();

        panel.setRawJsonText("""
            {
              "description": "edited description",
              "model": "claude-sonnet-4"
            }
            """);
        panel.selectFormTab();

        assertEquals(0, panel.getSelectedTabIndex(), "valid JSON should let the tab switch succeed");
        AgentConfig agent = panel.buildAgent();
        assertEquals("edited description", agent.getDescription());
        assertEquals("claude-sonnet-4", agent.getModel());
    }

    @Test
    void tryParseRawJsonIntoFormDetectsInvalidJsonWithoutShowingADialog() {
        AgentFormPanel panel = new AgentFormPanel();
        panel.setRawJsonText("{ not valid json");

        String error = panel.tryParseRawJsonIntoForm();

        assertNotNull(error, "invalid JSON should be reported as a parse error");
    }

    @Test
    void tryParseRawJsonIntoFormAcceptsValidJson() {
        AgentFormPanel panel = new AgentFormPanel();
        panel.setRawJsonText("{ \"description\": \"x\" }");

        String error = panel.tryParseRawJsonIntoForm();

        assertNull(error);
        assertEquals("x", panel.buildAgent().getDescription());
    }

    @Test
    void repeatedTabSwitchingDoesNotCorruptState() throws Exception {
        AgentFormPanel panel = new AgentFormPanel();
        setText(panel, "descriptionField", "stable-description");

        panel.selectRawJsonTab();
        panel.selectFormTab();
        panel.selectRawJsonTab();
        panel.selectFormTab();

        assertEquals(0, panel.getSelectedTabIndex());
        assertEquals("stable-description", panel.buildAgent().getDescription());
    }

    private static void setText(AgentFormPanel panel, String fieldName, String text) throws Exception {
        var field = AgentFormPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object component = field.get(panel);
        component.getClass().getMethod("setText", String.class).invoke(component, text);
    }

    private static void setChecked(AgentFormPanel panel, String fieldName, boolean checked) throws Exception {
        var field = AgentFormPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        javax.swing.JCheckBox checkBox = (javax.swing.JCheckBox) field.get(panel);
        checkBox.setSelected(checked);
    }
}
