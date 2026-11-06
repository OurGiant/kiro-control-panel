package com.ourgiant.kirocontrolpanel.migration;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.snapshot.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Walks the user through moving from kiro-cli V2 to V3 (issue #147), one step at a time, with
 * each step's kiro-cli output shown in a log pane:
 * <ol>
 *   <li>Back up {@code ~/.kiro} (the app's own snapshot zip)</li>
 *   <li>Update kiro-cli, if it's too old to have {@code agent upgrade}</li>
 *   <li>Convert V2 agent configs ({@code kiro-cli agent upgrade}, with a dry-run preview)</li>
 *   <li>Make V3 the saved default ({@code chat.agentEngine v3})</li>
 *   <li>Sessions: nothing to run, just what happens to them</li>
 * </ol>
 * "Switch Back to V2" undoes step 4. Non-modal, like the setup scan dialog, so the user can
 * look at the Agents or Sessions tab while it's open.
 */
public class KiroCliV3MigrationDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(KiroCliV3MigrationDialog.class);
    private static final String DONE = "✔";
    private static final String TODO = "○";
    private static final String INFO = "ℹ";
    private static final int TEXT_WIDTH_PX = 430;

    private final AppPreferences preferences;
    private final KiroCliV3MigrationService service = new KiroCliV3MigrationService();

    private final JLabel statusLabel = new JLabel("Checking kiro-cli...");
    private final JTextArea logArea = new JTextArea();

    private final Step backupStep = new Step("Back up your Kiro settings");
    private final Step updateStep = new Step("Update kiro-cli");
    private final Step agentsStep = new Step("Convert agent configs");
    private final Step engineStep = new Step("Make V3 the default");
    private final Step sessionsStep = new Step("Your sessions");

    private final JButton backupButton = new JButton("Back Up Now");
    private final JButton updateButton = new JButton("Update kiro-cli");
    private final JButton previewAgentsButton = new JButton("Preview");
    private final JButton upgradeAgentsButton = new JButton("Convert");
    private final JButton switchToV3Button = new JButton("Switch to V3");
    private final JButton switchToV2Button = new JButton("Switch Back to V2");
    private final JButton recheckButton = new JButton("Re-check");

    private volatile KiroCliV3Status status;
    private Path backupFile;
    private boolean busy;

    public KiroCliV3MigrationDialog(Frame parent, AppPreferences preferences) {
        super(parent, "Upgrade to Kiro CLI V3", false);
        this.preferences = preferences;

        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel intro = new JLabel(html(
            "<b>Kiro CLI V3</b> is the new default engine and V2 no longer gets feature updates. "
                + "V3 changes how tool permissions, hooks and sessions are stored. These steps move you "
                + "over without losing settings or sessions: every change is made by kiro-cli itself, "
                + "which keeps a backup of anything it rewrites."));
        JPanel north = new JPanel(new BorderLayout(4, 6));
        north.add(intro, BorderLayout.NORTH);
        north.add(statusLabel, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel steps = new JPanel(new GridBagLayout());
        addStep(steps, 0, backupStep, backupButton);
        addStep(steps, 1, updateStep, updateButton);
        addStep(steps, 2, agentsStep, previewAgentsButton, upgradeAgentsButton);
        addStep(steps, 3, engineStep, switchToV3Button);
        addStep(steps, 4, sessionsStep);
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = 5;
        filler.weighty = 1;
        steps.add(new JPanel(), filler);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, logArea.getFont().getSize()));
        logArea.setRows(8);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("kiro-cli output"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(steps), logScroll);
        split.setResizeWeight(0.7);
        add(split, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        JPanel south = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(switchToV2Button);
        left.add(recheckButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(closeButton);
        south.add(left, BorderLayout.WEST);
        south.add(right, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        backupButton.addActionListener(e -> onBackup());
        updateButton.addActionListener(e -> onUpdateCli());
        previewAgentsButton.addActionListener(e -> onUpgradeAgents(true));
        upgradeAgentsButton.addActionListener(e -> onUpgradeAgents(false));
        switchToV3Button.addActionListener(e -> onSwitchToV3());
        switchToV2Button.addActionListener(e -> onSwitchToV2());
        recheckButton.addActionListener(e -> recheck());
        switchToV2Button.setToolTipText("Sets chat.agentEngine back to v2. Converted agents still work on V2.");

        setPreferredSize(new Dimension(760, 720));
        pack();
        setLocationRelativeTo(parent);

        refreshSteps();
        recheck();
    }

    /** Every agents folder the app manages: global plus each pinned workspace. */
    public static List<Path> managedAgentDirs(AppPreferences preferences) {
        List<Path> dirs = new ArrayList<>();
        dirs.add(KiroPaths.globalAgentsDir());
        for (WorkspaceScope scope : WorkspaceScope.pinnedWorkspaces(preferences)) {
            dirs.add(KiroPaths.workspaceAgentsDir(scope.workspaceRoot()));
        }
        return dirs;
    }

    public static KiroCliV3Status detect(KiroCliV3MigrationService service, AppPreferences preferences) {
        return service.detect(managedAgentDirs(preferences),
            KiroPaths.globalSessionsCliDir(), KiroPaths.globalSessionsDir());
    }

    // -- actions --

    private void recheck() {
        runInBackground("Checking kiro-cli...", () -> {
            status = detect(service, preferences);
            return null;
        }, false);
    }

    private void onBackup() {
        Path destination = snapshotDestination();
        runInBackground("Backing up ~/.kiro...", () -> {
            backupFile = SnapshotService.createSnapshot(KiroPaths.globalKiroHome(), destination);
            appendLog("Backed up ~/.kiro to " + backupFile + "\n"
                + "(Sessions aren't in the zip. The upgrade doesn't change them.)\n");
            return null;
        }, false);
    }

    private void onUpdateCli() {
        runInBackground("Updating kiro-cli (this can take a few minutes)...", () -> {
            log(service.updateCli());
            return null;
        }, true);
    }

    private void onUpgradeAgents(boolean dryRun) {
        if (status == null) {
            return;
        }
        List<Path> dirs = new ArrayList<>(status.legacyAgentDirs());
        runInBackground(dryRun ? "Previewing agent conversion..." : "Converting agent configs...", () -> {
            for (Path dir : dirs) {
                log(service.upgradeAgents(dir, dryRun));
            }
            if (dryRun) {
                appendLog("Preview only, nothing was changed. Click Convert to apply.\n");
            }
            return null;
        }, !dryRun);
    }

    private void onSwitchToV3() {
        if (status != null && !status.legacyAgentFiles().isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                status.legacyAgentFiles().size() + " agent config(s) haven't been converted yet.\n"
                    + "V3 skips them until they are (kiro-cli may also convert them automatically\n"
                    + "the first time V3 starts, keeping a .bak copy).\n\nSwitch to V3 anyway?",
                "Agents Not Converted", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
        }
        runInBackground("Making V3 the default...", () -> {
            log(service.setDefaultEngine("v3"));
            return null;
        }, true);
    }

    private void onSwitchToV2() {
        runInBackground("Switching the default back to V2...", () -> {
            log(service.setDefaultEngine("v2"));
            appendLog("V2 is the default again. Sessions you started on V3 stay in the V3 store; "
                + "V2 can't open them, but the Sessions tab still lists them.\n");
            return null;
        }, true);
    }

    /**
     * Runs {@code work} off the EDT with every action button disabled, then refreshes the step
     * list. {@code recheckAfter} re-reads kiro-cli's state when the work may have changed it.
     */
    private void runInBackground(String message, Callable<Void> work, boolean recheckAfter) {
        if (busy) {
            return;
        }
        busy = true;
        statusLabel.setText(message);
        refreshSteps();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                work.call();
                if (recheckAfter) {
                    status = detect(service, preferences);
                }
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    logger.warn("V3 migration step failed", cause);
                    appendLog("Failed: " + cause.getMessage() + "\n");
                }
                refreshSteps();
            }
        }.execute();
    }

    private void log(KiroCliV3MigrationService.CommandResult result) {
        StringBuilder text = new StringBuilder("$ ").append(String.join(" ", result.command())).append('\n');
        if (!result.output().isBlank()) {
            text.append(result.output().strip()).append('\n');
        }
        text.append(result.succeeded() ? "(done)" : "(exit code " + result.exitCode() + ")").append("\n\n");
        appendLog(text.toString());
    }

    private void appendLog(String text) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            logArea.append(text);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private Path snapshotDestination() {
        String configured = preferences.getSnapshotDestination();
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured);
        }
        return Paths.get(System.getProperty("user.home"), ".kiro-control-panel", "snapshots");
    }

    // -- rendering --

    private void refreshSteps() {
        KiroCliV3Status s = status;
        if (s == null) {
            statusLabel.setText(busy ? statusLabel.getText() : "Checking kiro-cli...");
            for (JButton b : List.of(backupButton, updateButton, previewAgentsButton, upgradeAgentsButton,
                    switchToV3Button, switchToV2Button, recheckButton)) {
                b.setEnabled(false);
            }
            return;
        }
        if (!busy) {
            statusLabel.setText(statusLine(s));
        }

        backupStep.set(backupFile != null ? DONE : TODO, backupFile != null
            ? "Saved to " + escape(backupFile.toString()) + "."
            : "Saves a zip of ~/.kiro (settings, agents, steering, skills, MCP servers) to "
                + escape(snapshotDestination().toString()) + ". Sessions aren't included; nothing here changes them.");
        backupButton.setEnabled(!busy);

        if (!s.cliFound()) {
            updateStep.set(TODO, "kiro-cli wasn't found on your PATH. Install it from kiro.dev, then click Re-check.");
        } else if (s.agentUpgradeAvailable()) {
            updateStep.set(DONE, "kiro-cli " + escape(orUnknown(s.cliVersion())) + " supports V3 and can convert agents.");
        } else if (s.legacyAgentFiles().isEmpty()) {
            // Builds without `agent upgrade` already bundle the V3 engine; the update only
            // matters for converting agents, and there are none to convert.
            updateStep.set(INFO, "kiro-cli " + escape(orUnknown(s.cliVersion()))
                + " can already run V3. Updating is optional since no agents need converting, "
                + "but gets you the latest V3 fixes.");
        } else {
            updateStep.set(TODO, "kiro-cli " + escape(orUnknown(s.cliVersion()))
                + " is too old to convert agent configs. Update it to the latest release. If the update "
                + "fails here, run <code>kiro-cli update</code> in a terminal, then click Re-check.");
        }
        updateButton.setEnabled(!busy && s.cliFound() && !s.agentUpgradeAvailable());

        if (s.legacyAgentFiles().isEmpty()) {
            agentsStep.set(DONE, "No agent configs need converting.");
        } else {
            String names = s.legacyAgentFiles().stream()
                .map(p -> escape(p.toString()))
                .collect(Collectors.joining("<br>"));
            agentsStep.set(TODO, s.legacyAgentFiles().size() + " agent config(s) still use V2's "
                + "<code>allowedTools</code>/<code>toolsSettings</code>, and V3 skips them until they're converted. "
                + "kiro-cli keeps each original as <code>&lt;name&gt;.json.bak</code> and leaves the V2 fields in "
                + "place, so V2 can still read them.<br>" + names);
        }
        boolean canConvert = !busy && s.agentUpgradeAvailable() && !s.legacyAgentFiles().isEmpty();
        previewAgentsButton.setEnabled(canConvert);
        upgradeAgentsButton.setEnabled(canConvert);

        engineStep.set(s.v3IsDefault() ? DONE : TODO, s.v3IsDefault()
            ? "V3 is your default. Plain <code>kiro-cli</code> starts V3. Use Switch Back to V2 below to undo."
            : "Saves <code>chat.agentEngine = v3</code>, so plain <code>kiro-cli</code> starts V3. "
                + "You can still run <code>kiro-cli --v2</code> for one session, or switch back below.");
        switchToV3Button.setEnabled(!busy && s.cliFound() && !s.v3IsDefault());
        switchToV2Button.setEnabled(!busy && s.cliFound() && s.v3IsDefault());
        recheckButton.setEnabled(!busy);

        sessionsStep.set(INFO, "Nothing to run. Your " + s.v2SessionCount() + " V2 session(s) stay where they are and "
            + "keep showing in the Sessions tab. To carry one on in V3, select it there and click "
            + "<b>Continue in V3...</b>: kiro-cli makes a V3 copy and keeps the original. V2 can't open V3 "
            + "sessions" + (s.v3SessionCount() > 0 ? " (you already have " + s.v3SessionCount() + ")" : "") + ".");
    }

    private static String statusLine(KiroCliV3Status s) {
        if (!s.cliFound()) {
            return "kiro-cli not found.";
        }
        return "kiro-cli " + orUnknown(s.cliVersion())
            + "  •  default engine: " + (s.savedEngine() != null ? s.savedEngine() : "not chosen yet")
            + "  •  sessions: " + s.v2SessionCount() + " V2, " + s.v3SessionCount() + " V3";
    }

    private static String orUnknown(String value) {
        return value != null ? value : "(unknown version)";
    }

    private static void addStep(JPanel panel, int row, Step step, JButton... buttons) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.insets = new Insets(6, 4, 6, 8);

        c.gridx = 0;
        panel.add(step.marker, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        JPanel text = new JPanel(new BorderLayout(0, 2));
        JLabel title = new JLabel((row + 1) + ". " + step.title);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        text.add(title, BorderLayout.NORTH);
        text.add(step.detail, BorderLayout.CENTER);
        panel.add(text, c);

        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        for (JButton button : buttons) {
            buttonPanel.add(button);
        }
        panel.add(buttonPanel, c);
    }

    private static String html(String body) {
        return "<html><div style='width:" + TEXT_WIDTH_PX + "px'>" + body + "</div></html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** One row's marker and description, updated in place by {@link #refreshSteps()}. */
    private static final class Step {
        final String title;
        final JLabel marker = new JLabel(TODO);
        final JLabel detail = new JLabel();

        Step(String title) {
            this.title = title;
            marker.setFont(marker.getFont().deriveFont(Font.BOLD, marker.getFont().getSize2D() + 2f));
        }

        void set(String markerText, String detailHtml) {
            marker.setText(markerText);
            detail.setText(html(detailHtml));
        }
    }
}
