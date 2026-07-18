package com.ourgiant.kirocontrolpanel.hooks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Form/Raw JSON tab-sync logic that was previously untested —
 * HookFormPanel is a plain JPanel (unlike HookEditDialog, a JDialog), which
 * can be constructed in a headless test run; JOptionPane message dialogs
 * can't, so the invalid-JSON error-dialog path is exercised here only up to
 * the point of parse failure (tryParseRawJsonIntoForm returning non-null),
 * not the actual dialog/tab-revert — that needs a real display, see
 * .claude/skills/verify/SKILL.md.
 */
class HookFormPanelTest {

    @Test
    void buildHookReflectsCommandFormFields() throws Exception {
        HookFormPanel panel = new HookFormPanel();
        setText(panel, "nameField", "lint-on-save");
        selectItem(panel, "triggerCombo", "file_save");
        setText(panel, "matcherField", "**/*.java");
        setText(panel, "commandField", "mvn -q checkstyle:check");

        Hook hook = panel.buildHook();

        assertEquals("lint-on-save", hook.getName());
        assertEquals("file_save", hook.getTrigger());
        assertEquals("**/*.java", hook.getMatcher());
        assertEquals(HookAction.TYPE_COMMAND, hook.getAction().getType());
        assertEquals("mvn -q checkstyle:check", hook.getAction().getCommand());
    }

    @Test
    void buildHookReflectsAgentPromptFormFields() throws Exception {
        HookFormPanel panel = new HookFormPanel();
        setText(panel, "nameField", "summarize");
        selectItem(panel, "actionTypeCombo", "Agent Prompt");
        setText(panel, "promptArea", "Summarize what changed this session.");

        Hook hook = panel.buildHook();

        assertEquals(HookAction.TYPE_AGENT, hook.getAction().getType());
        assertEquals("Summarize what changed this session.", hook.getAction().getPrompt());
    }

    @Test
    void populateFromThenBuildHookRoundTrips() {
        HookFormPanel panel = new HookFormPanel();
        Hook original = new Hook();
        original.setName("only-hook");
        original.setTrigger("prompt_submit");
        original.setEnabled(false);
        original.setTimeout(30);
        HookAction action = new HookAction();
        action.setType(HookAction.TYPE_COMMAND);
        action.setCommand("echo hi");
        original.setAction(action);

        panel.populateFrom(original);
        Hook rebuilt = panel.buildHook();

        assertEquals("only-hook", rebuilt.getName());
        assertEquals("prompt_submit", rebuilt.getTrigger());
        assertTrue(!rebuilt.isEnabled());
        assertEquals(30, rebuilt.getTimeout());
        assertEquals("echo hi", rebuilt.getAction().getCommand());
    }

    @Test
    void switchingFormToRawJsonTabSerializesCurrentFormState() throws Exception {
        HookFormPanel panel = new HookFormPanel();
        setText(panel, "nameField", "lint-on-save");
        setText(panel, "commandField", "mvn -q checkstyle:check");

        panel.selectRawJsonTab();

        String rawJson = panel.getRawJsonText();
        assertTrue(rawJson.contains("lint-on-save"));
        assertTrue(rawJson.contains("mvn -q checkstyle:check"));
    }

    @Test
    void editingValidRawJsonThenSwitchingBackUpdatesForm() throws Exception {
        HookFormPanel panel = new HookFormPanel();
        panel.selectRawJsonTab();

        panel.setRawJsonText("""
            {
              "name": "edited-hook",
              "trigger": "agent_stop",
              "enabled": true,
              "action": { "type": "command", "command": "echo edited" }
            }
            """);
        panel.selectFormTab();

        assertEquals(0, panel.getSelectedTabIndex(), "valid JSON should let the tab switch succeed");
        Hook hook = panel.buildHook();
        assertEquals("edited-hook", hook.getName());
        assertEquals("agent_stop", hook.getTrigger());
        assertEquals("echo edited", hook.getAction().getCommand());
    }

    @Test
    void tryParseRawJsonIntoFormDetectsInvalidJsonWithoutShowingADialog() {
        HookFormPanel panel = new HookFormPanel();
        panel.setRawJsonText("{ not valid json");

        String error = panel.tryParseRawJsonIntoForm();

        assertNotNull(error, "invalid JSON should be reported as a parse error");
    }

    @Test
    void tryParseRawJsonIntoFormAcceptsValidJson() {
        HookFormPanel panel = new HookFormPanel();
        panel.setRawJsonText("{ \"name\": \"x\", \"trigger\": \"file_save\" }");

        String error = panel.tryParseRawJsonIntoForm();

        assertNull(error);
        assertEquals("x", panel.buildHook().getName());
    }

    @Test
    void repeatedTabSwitchingDoesNotCorruptState() throws Exception {
        HookFormPanel panel = new HookFormPanel();
        setText(panel, "nameField", "stable-hook");

        panel.selectRawJsonTab();
        panel.selectFormTab();
        panel.selectRawJsonTab();
        panel.selectFormTab();

        assertEquals(0, panel.getSelectedTabIndex());
        assertEquals("stable-hook", panel.buildHook().getName());
    }

    private static void setText(HookFormPanel panel, String fieldName, String text) throws Exception {
        var field = HookFormPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object component = field.get(panel);
        component.getClass().getMethod("setText", String.class).invoke(component, text);
    }

    private static void selectItem(HookFormPanel panel, String fieldName, String value) throws Exception {
        var field = HookFormPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) field.get(panel);
        combo.setSelectedItem(value);
    }
}
