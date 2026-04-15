package cpubench.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

public final class MetricLineChartPanel extends JPanel {
    public record Series(String name, Color color, List<Double> values) {
    }

    private String title = "Metric Trend";
    private String subtitle = "Pick a run to see repeat-level metrics.";
    private String yAxisLabel = "ns/iter";
    private List<Series> series = List.of();

    public MetricLineChartPanel() {
        setOpaque(true);
        setBackground(UiPalette.PANEL);
        setBorder(DarkTheme.panelBorder());
        setPreferredSize(new Dimension(680, 300));
    }

    public void setSeries(String title, String subtitle, String yAxisLabel, List<Series> series) {
        this.title = title;
        this.subtitle = subtitle;
        this.yAxisLabel = yAxisLabel;
        this.series = List.copyOf(series);
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

        int left = 56;
        int top = 68;
        int right = getWidth() - 18;
        int bottom = getHeight() - 44;
        int width = Math.max(80, right - left);
        int height = Math.max(80, bottom - top);

        g2.setColor(UiPalette.BORDER);
        for (int index = 0; index <= 4; index += 1) {
            int y = bottom - (height * index / 4);
            g2.draw(new Line2D.Double(left, y, right, y));
        }
        g2.draw(new Line2D.Double(left, top, left, bottom));
        g2.draw(new Line2D.Double(left, bottom, right, bottom));

        if (series.isEmpty()) {
            g2.setColor(UiPalette.MUTED);
            g2.drawString("No metric data for the current selection.", left, top + height / 2);
            g2.dispose();
            return;
        }

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int longest = 0;
        for (Series item : series) {
            longest = Math.max(longest, item.values().size());
            for (double value : item.values()) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (min == Double.MAX_VALUE || max == Double.MIN_VALUE) {
            min = 0.0;
            max = 1.0;
        }
        if (Math.abs(max - min) < 0.000001) {
            max = min + 1.0;
        }

        FontMetrics metrics = g2.getFontMetrics(UiPalette.BODY);
        g2.setFont(UiPalette.BODY);
        g2.setColor(UiPalette.MUTED);
        for (int index = 0; index <= 4; index += 1) {
            double value = min + ((max - min) * (4 - index) / 4.0);
            int y = top + (height * index / 4);
            String label = String.format("%.3f", value);
            g2.drawString(label, 12, y + 4);
        }

        for (int repeat = 0; repeat < longest; repeat += 1) {
            int x = left + (longest == 1 ? width / 2 : (width * repeat / (longest - 1)));
            g2.drawString(Integer.toString(repeat + 1), x - 3, bottom + metrics.getHeight() + 2);
        }
        g2.drawString(yAxisLabel, left, bottom + metrics.getHeight() * 2 + 4);

        for (Series item : series) {
            List<Double> values = item.values();
            g2.setColor(item.color());
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int index = 0; index < values.size(); index += 1) {
                int x = left + (values.size() == 1 ? width / 2 : (width * index / Math.max(1, longest - 1)));
                int y = bottom - (int) Math.round(((values.get(index) - min) / (max - min)) * height);
                if (index > 0) {
                    int previousX = left + (values.size() == 1 ? width / 2 : (width * (index - 1) / Math.max(1, longest - 1)));
                    int previousY = bottom - (int) Math.round(((values.get(index - 1) - min) / (max - min)) * height);
                    g2.draw(new Line2D.Double(previousX, previousY, x, y));
                }
                g2.fill(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
            }
        }

        paintLegend(g2, right - 220, 18);
        g2.dispose();
    }

    private void paintLegend(Graphics2D graphics, int x, int y) {
        List<Series> legendSeries = new ArrayList<>(series);
        int legendY = y;
        graphics.setFont(UiPalette.BODY);
        for (Series item : legendSeries.subList(0, Math.min(legendSeries.size(), 6))) {
            graphics.setColor(item.color());
            graphics.fillRoundRect(x, legendY, 12, 12, 6, 6);
            graphics.setColor(UiPalette.TEXT);
            graphics.drawString(item.name(), x + 18, legendY + 11);
            legendY += 18;
        }
    }
}

