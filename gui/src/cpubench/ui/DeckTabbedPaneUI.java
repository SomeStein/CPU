package cpubench.ui;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

public final class DeckTabbedPaneUI extends BasicTabbedPaneUI {
    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabAreaInsets = new Insets(6, 8, 0, 8);
        contentBorderInsets = new Insets(8, 0, 0, 0);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabInsets = new Insets(10, 16, 10, 16);
    }

    @Override
    protected Insets getTabInsets(int tabPlacement, int tabIndex) {
        return new Insets(10, 16, 10, 16);
    }

    @Override
    protected void paintTabBackground(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isSelected ? UiPalette.SURFACE_ALT : UiPalette.PANEL_ALT);
        g2.fill(new RoundRectangle2D.Double(x + 1.0, y + 2.0, w - 2.0, h + 8.0, 16.0, 16.0));
        g2.setColor(isSelected ? UiPalette.ACCENT : UiPalette.BORDER);
        g2.setStroke(new BasicStroke(isSelected ? 1.6f : 1.0f));
        g2.draw(new RoundRectangle2D.Double(x + 1.0, y + 2.0, w - 2.0, h + 8.0, 16.0, 16.0));
        g2.dispose();
    }

    @Override
    protected void paintText(Graphics graphics, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title, Rectangle textRect, boolean isSelected) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setFont(font.deriveFont(Font.BOLD, 14f));
        g2.setColor(isSelected ? UiPalette.TEXT : UiPalette.MUTED);
        BasicGraphicsUtils.drawStringUnderlineCharAt(tabPane, g2, title, tabPane.getDisplayedMnemonicIndexAt(tabIndex), textRect.x, textRect.y + metrics.getAscent());
        g2.dispose();
    }

    @Override
    protected void paintFocusIndicator(Graphics graphics, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
    }

    @Override
    protected void paintTabBorder(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    }

    @Override
    protected void paintContentBorder(Graphics graphics, int tabPlacement, int selectedIndex) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int x = 0;
        int y = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        int width = tabPane.getWidth() - 1;
        int height = tabPane.getHeight() - y - 1;
        g2.setColor(UiPalette.BORDER);
        g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, width - 1.0, height - 1.0, 18.0, 18.0));
        g2.dispose();
    }

    @Override
    public void paint(Graphics graphics, JComponent component) {
        component.setBackground(UiPalette.WINDOW);
        super.paint(graphics, component);
    }
}
