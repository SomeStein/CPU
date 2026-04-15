package cpubench.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JPanel;

public final class ImplementationBarChartPanel extends JPanel {
    public record Bar(String label, Color color, double value) {
    }

    private String title = "Best By Implementation";
    private String subtitle = "Best measured metric in the current view.";
    private String unit = "ns/iter";
    private List<Bar> bars = List.of();

    public ImplementationBarChartPanel() {
        setOpaque(true);
        setBackground(UiPalette.PANEL);
        setBorder(DarkTheme.panelBorder());
        setPreferredSize(new Dimension(360, 300));
    }

    public void setBars(String title, String subtitle, String unit, List<Bar> bars) {
        this.title = title;
        this.subtitle = subtitle;
        this.unit = unit;
        this.bars = List.copyOf(bars);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(UiPalette.TEXT);
        g2.setFont(UiPalette.LABEL.deriveFont(14f));
        g2.drawString(title, 18, 26);
        g2.setColor(UiPalette.MUTED);
        g2.setFont(UiPalette.SUBTITLE);
        g2.drawString(subtitle, 18, 46);

        if (bars.isEmpty()) {
            g2.drawString("No implementation comparison available.", 18, 84);
            g2.dispose();
            return;
        }

        double max = 0.0;
        for (Bar bar : bars) {
            max = Math.max(max, bar.value());
        }
        if (max <= 0.0) {
            max = 1.0;
        }

        int chartTop = 74;
        int chartLeft = 18;
        int chartRight = getWidth() - 18;
        int chartBottom = getHeight() - 48;
        int chartHeight = chartBottom - chartTop;
        int barWidth = Math.max(24, (chartRight - chartLeft - 12 * (bars.size() - 1)) / Math.max(1, bars.size()));
        FontMetrics metrics = g2.getFontMetrics(UiPalette.BODY);

        for (int index = 0; index < bars.size(); index += 1) {
            Bar bar = bars.get(index);
            int x = chartLeft + index * (barWidth + 12);
            int height = (int) Math.round((bar.value() / max) * (chartHeight - 28));
            int y = chartBottom - height;
            g2.setColor(bar.color());
            g2.fill(new RoundRectangle2D.Double(x, y, barWidth, height, 14, 14));
            g2.setColor(UiPalette.TEXT);
            g2.drawString(String.format("%.2f", bar.value()), x, y - 6);
            g2.setColor(UiPalette.MUTED);
            drawCentered(g2, abbreviate(bar.label(), 16), x + barWidth / 2, chartBottom + metrics.getHeight());
        }
        g2.setColor(UiPalette.MUTED);
        g2.drawString(unit, 18, getHeight() - 16);
        g2.dispose();
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static void drawCentered(Graphics2D graphics, String text, int x, int y) {
        int width = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, x - width / 2, y);
    }
}
