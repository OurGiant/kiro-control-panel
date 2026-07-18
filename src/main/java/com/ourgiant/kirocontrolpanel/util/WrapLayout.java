package com.ourgiant.kirocontrolpanel.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} that reports a preferred size accounting for actual
 * wrapping, unlike the JDK's own FlowLayout: {@code FlowLayout.preferredLayoutSize()}
 * always computes a single-row size regardless of the container's actual
 * width, so a component using it inside e.g. {@code BorderLayout.NORTH}
 * silently clips every row past the first once the container is narrower
 * than the sum of its children's widths -- exactly what happened to
 * {@link WorkspaceScopeBar} at a narrow window width (issue #18). This
 * computes height by walking the container's current width and simulating
 * the same row-wrapping FlowLayout itself does at layout time.
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout() {
        super();
    }

    public WrapLayout(int align) {
        super(align);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            Container container = target;
            while (container.getSize().width == 0 && container.getParent() != null) {
                container = container.getParent();
            }
            targetWidth = container.getSize().width;
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxRowWidth = targetWidth - horizontalInsetsAndGap;

            Dimension result = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int componentCount = target.getComponentCount();
            for (int i = 0; i < componentCount; i++) {
                Component component = target.getComponent(i);
                if (!component.isVisible()) {
                    continue;
                }

                Dimension componentSize = preferred ? component.getPreferredSize() : component.getMinimumSize();
                if (rowWidth + componentSize.width > maxRowWidth && rowWidth > 0) {
                    result.width = Math.max(result.width, rowWidth);
                    result.height += rowHeight + vgap;
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) {
                    rowWidth += hgap;
                }
                rowWidth += componentSize.width;
                rowHeight = Math.max(rowHeight, componentSize.height);
            }
            result.width = Math.max(result.width, rowWidth);
            result.height += rowHeight;

            result.width += horizontalInsetsAndGap;
            result.height += insets.top + insets.bottom + (vgap * 2);
            return result;
        }
    }
}
