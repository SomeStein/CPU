package cpubench.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

public final class HeroPanel extends JPanel {
    public HeroPanel() {
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, UiPalette.PANEL_ALT, getWidth(), getHeight(), UiPalette.SURFACE_ALT));
        g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 28, 28));
        g2.setColor(UiPalette.BORDER);
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, 28, 28));
        g2.dispose();
        super.paintComponent(graphics);
    }
}
