package com.ourgiant.kirocontrolpanel.testsupport;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JViewport;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

/**
 * Regression coverage for the class of bug behind issue #18: a
 * java.awt.FlowLayout-wrapped child (e.g. WorkspaceScopeBar) getting
 * silently clipped because its BorderLayout.NORTH parent under-allocated
 * height, since FlowLayout.preferredLayoutSize() always reports a
 * single-row size regardless of actual wrapping. Works entirely headlessly
 * -- LayoutManager.layoutContainer() is a pure Java object-graph
 * computation that doesn't need a realized peer; only Window subclasses
 * (JDialog/JFrame) fail to construct in this project's headless
 * CI/dev-container, not layout computation itself.
 * <p>
 * Uses a manual recursive {@code doLayout()} pass rather than
 * {@code Container.validate()}: empirically, in this fully headless setup
 * (no peer at all, not even a dummy one) {@code validate()}'s own
 * {@code isValid()}/peer-related bookkeeping never actually invokes
 * {@code layoutContainer()}, silently leaving descendants at (0,0,0,0) --
 * confirmed with a standalone diagnostic before writing this. Calling
 * {@code doLayout()} directly, one container level at a time, top-down,
 * sidesteps that entirely.
 */
public final class LayoutAssertions {

    private LayoutAssertions() {
    }

    /** @throws AssertionError if any visible descendant's bounds exceed its immediate parent's allocated bounds. */
    public static void assertNoClippedChildren(Component root, int width, int height) {
        root.setSize(width, height);
        layoutRecursively(root);

        List<String> violations = new ArrayList<>();
        collectViolations(root, violations);
        if (!violations.isEmpty()) {
            throw new AssertionError("Clipped children found at " + width + "x" + height + ":\n"
                + String.join("\n", violations));
        }
    }

    private static void layoutRecursively(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }

    private static void collectViolations(Component parent, List<String> violations) {
        if (!(parent instanceof Container container)) {
            return;
        }
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            // A JViewport's view is *supposed* to be larger than the viewport at
            // narrow widths -- that's scrolling working correctly, not the bug
            // this check targets. Still recurse into it so a real clipping bug
            // nested inside scrollable content is still caught.
            if (!(container instanceof JViewport) && exceedsParentBounds(child, container)) {
                violations.add(describe(child) + " bounds=" + child.getBounds()
                    + " exceeds parent " + describe(container)
                    + " bounds=(0,0," + container.getWidth() + "," + container.getHeight() + ")");
            }
            collectViolations(child, violations);
        }
    }

    private static boolean exceedsParentBounds(Component child, Container parent) {
        return child.getX() < 0 || child.getY() < 0
            || child.getX() + child.getWidth() > parent.getWidth()
            || child.getY() + child.getHeight() > parent.getHeight();
    }

    private static String describe(Component c) {
        String label = switch (c) {
            case JLabel l -> l.getText();
            case AbstractButton b -> b.getText();
            default -> null;
        };
        return c.getClass().getSimpleName() + (label != null && !label.isBlank() ? " \"" + label + "\"" : "");
    }
}
