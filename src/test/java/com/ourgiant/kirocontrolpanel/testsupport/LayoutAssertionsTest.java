package com.ourgiant.kirocontrolpanel.testsupport;

import com.ourgiant.kirocontrolpanel.util.WrapLayout;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Proves {@link LayoutAssertions} actually detects the bug class behind
 * issue #18, rather than being a check that coincidentally never fires --
 * the negative control (plain FlowLayout) must throw, or nothing downstream
 * that relies on this utility is trustworthy.
 */
class LayoutAssertionsTest {

    private static Component fixedSize(int width, int height) {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(width, height));
        return panel;
    }

    /** Same shape as the real bug: a wrapping bar in BorderLayout.NORTH, at a width that forces wrapping. */
    private static JPanel narrowBarInBorderLayoutNorth(java.awt.LayoutManager barLayout) {
        JPanel bar = new JPanel(barLayout);
        bar.add(fixedSize(100, 20));
        bar.add(fixedSize(100, 20));
        bar.add(fixedSize(100, 20));

        JPanel root = new JPanel(new BorderLayout());
        root.add(bar, BorderLayout.NORTH);
        root.add(new JScrollPane(new JTextArea()), BorderLayout.CENTER);
        return root;
    }

    @Test
    void passesWhenWrapLayoutCorrectlyReportsWrappedHeight() {
        JPanel root = narrowBarInBorderLayoutNorth(new WrapLayout(FlowLayout.LEFT, 6, 4));

        assertDoesNotThrow(() -> LayoutAssertions.assertNoClippedChildren(root, 150, 400));
    }

    @Test
    void detectsClippingWithPlainFlowLayout() {
        // The actual historical bug: plain FlowLayout.preferredLayoutSize() always
        // reports single-row height, so BorderLayout.NORTH under-allocates height
        // once the bar wraps -- this must be caught, or the utility is useless.
        JPanel root = narrowBarInBorderLayoutNorth(new FlowLayout(FlowLayout.LEFT, 6, 4));

        AssertionError error = assertThrows(AssertionError.class,
            () -> LayoutAssertions.assertNoClippedChildren(root, 150, 400));
        assertTrueMessageMentionsClipping(error);
    }

    private static void assertTrueMessageMentionsClipping(AssertionError error) {
        if (!error.getMessage().contains("exceeds parent")) {
            throw new AssertionError("Expected a clipping violation message, got: " + error.getMessage());
        }
    }

    @Test
    void doesNotFlagAnOversizedScrollableViewAsClipping() {
        JTextArea bigTextArea = new JTextArea();
        bigTextArea.setPreferredSize(new Dimension(2000, 2000));
        JScrollPane scrollPane = new JScrollPane(bigTextArea);

        JPanel root = new JPanel(new BorderLayout());
        root.add(scrollPane, BorderLayout.CENTER);

        assertDoesNotThrow(() -> LayoutAssertions.assertNoClippedChildren(root, 200, 200));
    }
}
