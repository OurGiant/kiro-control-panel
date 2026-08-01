package com.ourgiant.kirocontrolpanel.diagnostics;

import com.ourgiant.kirocontrolpanel.util.InAppFileEditorLauncher;
import com.ourgiant.kirocontrolpanel.util.SwingLayoutUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
 * Plain-JPanel content of the setup scan dialog -- split out from
 * {@link KiroSetupScanDialog} for the same reason {@code HookFormPanel} etc.
 * are split from their dialogs: {@code JDialog} throws
 * {@code HeadlessException} in this project's headless build container, but
 * a plain {@code JPanel} constructs fine, so this stays unit-testable.
 * <p>
 * Owns no scanning logic itself -- {@link #setFindings} is handed an
 * already-computed list by the caller.
 */
public class KiroSetupFindingsPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(KiroSetupFindingsPanel.class);

    private final DefaultListModel<Finding> findingListModel = new DefaultListModel<>();
    private final JList<Finding> findingList = new JList<>(findingListModel);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton openFileButton;
    private final JButton fixButton;
    private final JButton fixAllButton;
    private final JButton rescanButton;

    public KiroSetupFindingsPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(statusLabel, BorderLayout.NORTH);

        findingList.setCellRenderer(new FindingRenderer());
        findingList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonState();
            }
        });
        add(new JScrollPane(findingList), BorderLayout.CENTER);

        openFileButton = new JButton("Edit...");
        openFileButton.addActionListener(e -> onOpenFile());

        fixButton = new JButton("Fix...");
        fixButton.addActionListener(e -> onFix());

        fixAllButton = new JButton("Fix All...");
        fixAllButton.addActionListener(e -> onFixAll());

        rescanButton = new JButton("Rescan");

        add(SwingLayoutUtils.createVerticalButtonPanel(openFileButton, fixButton, fixAllButton, rescanButton), BorderLayout.EAST);

        setFindings(List.of());
    }

    public void setFindings(List<Finding> findings) {
        findingListModel.clear();
        for (Finding finding : findings) {
            findingListModel.addElement(finding);
        }
        updateStatusText();
        updateButtonState();
    }

    /** Fires whenever "Rescan" is clicked -- the caller re-runs the scan and calls {@link #setFindings}. */
    public void addRescanListener(Runnable listener) {
        rescanButton.addActionListener(e -> listener.run());
    }

    /** Package-private, for tests: the panel's current finding list. */
    List<Finding> getFindings() {
        return java.util.Collections.list(findingListModel.elements());
    }

    /** Package-private, for tests. */
    JButton getOpenFileButton() {
        return openFileButton;
    }

    /** Package-private, for tests. */
    JButton getFixButton() {
        return fixButton;
    }

    /** Package-private, for tests. */
    JButton getFixAllButton() {
        return fixAllButton;
    }

    /** Package-private, for tests. */
    JButton getRescanButton() {
        return rescanButton;
    }

    /** Package-private, for tests. */
    void selectFinding(Finding finding) {
        findingList.setSelectedValue(finding, true);
    }

    private void updateStatusText() {
        int count = findingListModel.size();
        statusLabel.setText(count == 0
            ? "No structural issues found"
            : count + " issue" + (count == 1 ? "" : "s") + " found");
    }

    private void updateButtonState() {
        Finding selected = findingList.getSelectedValue();
        openFileButton.setEnabled(selected != null);
        fixButton.setEnabled(selected != null && selected.isFixable());
        fixAllButton.setEnabled(hasAnyFixable());
    }

    private boolean hasAnyFixable() {
        for (int i = 0; i < findingListModel.size(); i++) {
            if (findingListModel.get(i).isFixable()) {
                return true;
            }
        }
        return false;
    }

    private void onOpenFile() {
        Finding selected = findingList.getSelectedValue();
        if (selected != null) {
            InAppFileEditorLauncher.open(InAppFileEditorLauncher.resolveFrame(this),
                selected.surface(), selected.scope(), selected.path());
        }
    }

    private void onFix() {
        Finding selected = findingList.getSelectedValue();
        if (selected == null || !selected.isFixable()) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, selected.fix().previewText(),
            "Apply Fix?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            applyFix(selected);
        }
    }

    /**
     * Dialog-free apply, split out from {@link #onFix()} specifically so it's testable
     * headlessly -- {@code JOptionPane.showConfirmDialog} throws {@code HeadlessException}
     * in this project's build container, same reason the invalid-JSON confirm dialogs
     * elsewhere in this app aren't unit tested. Package-private, for tests.
     */
    boolean applyFix(Finding finding) {
        try {
            finding.fix().apply();
            findingListModel.removeElement(finding);
            updateStatusText();
            updateButtonState();
            return true;
        } catch (IOException e) {
            logger.warn("Failed to apply fix for {}", finding.path(), e);
            JOptionPane.showMessageDialog(this,
                "Failed to apply fix: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void onFixAll() {
        List<Finding> fixable = getFindings().stream().filter(Finding::isFixable).toList();
        if (fixable.isEmpty()) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Apply " + fixable.size() + " fix" + (fixable.size() == 1 ? "" : "es") + "?",
            "Apply All Fixes?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            applyAllFixes(fixable);
        }
    }

    /**
     * Dialog-free batch apply, split out from {@link #onFixAll()} for the same headless-testing
     * reason as {@link #applyFix}. Package-private, for tests. Applies every entry, collecting
     * failures rather than stopping at the first one -- one bad file shouldn't block fixing the
     * rest of an otherwise-healthy tree.
     */
    int applyAllFixes(List<Finding> fixable) {
        int failed = 0;
        for (Finding finding : fixable) {
            try {
                finding.fix().apply();
                findingListModel.removeElement(finding);
            } catch (IOException e) {
                logger.warn("Failed to apply fix for {}", finding.path(), e);
                failed++;
            }
        }
        updateStatusText();
        updateButtonState();
        if (failed > 0) {
            JOptionPane.showMessageDialog(this,
                failed + " fix" + (failed == 1 ? "" : "es") + " failed to apply. See logs for details.",
                "Some Fixes Failed", JOptionPane.WARNING_MESSAGE);
        }
        return failed;
    }

    private static final class FindingRenderer extends JPanel implements ListCellRenderer<Finding> {
        private final JLabel titleLabel = new JLabel();
        private final JLabel detailLabel = new JLabel();

        FindingRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
            detailLabel.setForeground(Color.GRAY);
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(titleLabel);
            add(detailLabel);
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Finding> list, Finding value, int index,
                                                        boolean isSelected, boolean cellHasFocus) {
            String scopeLabel = value.scope().isGlobal() ? "Global" : value.scope().label();
            titleLabel.setText(value.surface() + " (" + scopeLabel + ") — " + value.path().getFileName());
            titleLabel.setForeground(value.severity() == FindingSeverity.INVALID
                ? new Color(178, 34, 34) : new Color(184, 134, 11));
            detailLabel.setText(value.message());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            return this;
        }
    }
}
