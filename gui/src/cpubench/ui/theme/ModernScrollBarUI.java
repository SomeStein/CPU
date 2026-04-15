package cpubench.ui.theme;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicScrollBarUI;

import cpubench.ui.UiPalette;

public final class ModernScrollBarUI extends BasicScrollBarUI {
    private boolean hovered;

    @Override
    protected void installListeners() {
        super.installListeners();
        scrollbar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                scrollbar.repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                scrollbar.repaint();
            }
        });
    }

    @Override
    protected void configureScrollBarColors() {
        thumbColor = UiPalette.BORDER;
        trackColor = UiPalette.PANEL;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zeroButton();
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle trackBounds) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(UiPalette.PANEL);
        g2.fillRoundRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height, 8, 8);
        g2.dispose();
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle thumbBounds) {
        if (!scrollbar.isEnabled() || thumbBounds.width <= 0 || thumbBounds.height <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color color = new Color(
            UiPalette.ACCENT.getRed(),
            UiPalette.ACCENT.getGreen(),
            UiPalette.ACCENT.getBlue(),
            hovered ? 180 : 96
        );
        g2.setColor(color);
        int inset = scrollbar.getOrientation() == JScrollBar.VERTICAL ? 2 : 1;
        Rectangle thumb = new Rectangle(thumbBounds);
        if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
            thumb.x += inset;
            thumb.width = Math.max(6, thumb.width - inset * 2);
        } else {
            thumb.y += inset;
            thumb.height = Math.max(6, thumb.height - inset * 2);
        }
        g2.fill(new RoundRectangle2D.Double(thumb.x, thumb.y, thumb.width, thumb.height, 8, 8));
        g2.dispose();
    }

    private static JButton zeroButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        return button;
    }
}
