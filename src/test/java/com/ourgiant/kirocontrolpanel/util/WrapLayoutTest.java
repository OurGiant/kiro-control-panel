package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for issue #18: plain java.awt.FlowLayout.preferredLayoutSize()
 * always reports a single-row size regardless of the container's actual
 * width, so WorkspaceScopeBar's content silently clipped past the first row
 * once the window got narrow enough to force wrapping. Uses fixed-size
 * placeholder components (not real buttons/labels) so the expected pixel
 * math is deterministic and doesn't depend on font metrics/look-and-feel.
 */
class WrapLayoutTest {

    private static Component fixedSize(int width, int height) {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(width, height));
        return panel;
    }

    @Test
    void reportsSingleRowHeightWhenEverythingFitsOnOneRow() {
        JPanel container = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        container.add(fixedSize(50, 20));
        container.add(fixedSize(60, 25));
        container.setSize(1000, 1);

        Dimension preferred = container.getLayout().preferredLayoutSize(container);

        // one row: max child height (25) + top/bottom vgap (4*2)
        assertEquals(25 + 4 * 2, preferred.height);
    }

    @Test
    void reportsMultiRowHeightWhenContainerIsNarrowerThanContentWidth() {
        JPanel container = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        container.add(fixedSize(100, 20)); // row 1
        container.add(fixedSize(100, 20)); // row 2 (doesn't fit alongside row 1 at width 150)
        container.add(fixedSize(100, 20)); // row 3
        container.setSize(150, 1);

        Dimension preferred = container.getLayout().preferredLayoutSize(container);

        // 3 rows of height 20, plus vgap between each row (2 gaps) and top/bottom vgap
        int expectedHeight = (20 * 3) + (4 * 2) + (4 * 2);
        assertEquals(expectedHeight, preferred.height,
            "preferred height must account for wrapped rows, not just the first row "
                + "(this is exactly the bug that clipped WorkspaceScopeBar at a narrow window width)");
    }

    @Test
    void matchesPlainFlowLayoutWhenNoWrappingIsNeeded() {
        // Sanity check: WrapLayout shouldn't change behavior in the common,
        // non-wrapping case -- only fix the wrapping-height calculation.
        JPanel wrapContainer = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        JPanel flowContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (JPanel c : new JPanel[] {wrapContainer, flowContainer}) {
            c.add(fixedSize(50, 20));
            c.add(fixedSize(60, 25));
            c.setSize(1000, 1);
        }

        assertEquals(flowContainer.getPreferredSize(), wrapContainer.getPreferredSize());
    }
}
