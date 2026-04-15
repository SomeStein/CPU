package cpubench.ui.charts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.Timer;

import cpubench.ui.DarkTheme;
import cpubench.ui.UiPalette;
import cpubench.ui.icons.LanguageIconRegistry;

public final class InteractiveTrendChart extends JComponent {
    public record PointRecord(double x, double y, String implId, String caseId, int repeat) {
    }

    public record Series(String implId, Color color, List<PointRecord> points) {
    }

    private final Timer repaintTimer;
    private List<Series> series = List.of();
    private String title = "Interactive Trend";
    private String subtitle = "Pan with drag. Wheel to zoom.";
    private String yAxisLabel = "ns/iter";
    private String xAxisLabel = "Sample";
    private HoverPoint hoverPoint;
    private double viewMinX = Double.NaN;
    private double viewMaxX = Double.NaN;
    private double viewMinY = Double.NaN;
    private double viewMaxY = Double.NaN;
    private double dragStartViewMinX;
    private double dragStartViewMaxX;
    private java.awt.Point dragPoint;

    public InteractiveTrendChart() {
        setOpaque(true);
        setBackground(UiPalette.PANEL);
        setBorder(DarkTheme.panelBorder());
        setPreferredSize(new Dimension(720, 320));
        repaintTimer = new Timer(60, event -> repaint());
        repaintTimer.setRepeats(false);

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                hoverPoint = findNearest(event.getPoint());
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoverPoint = null;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                dragPoint = event.getPoint();
                dragStartViewMinX = currentMinX();
                dragStartViewMaxX = currentMaxX();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragPoint == null) {
                    return;
                }
                Rectangle plot = plotBounds();
                double domain = Math.max(1e-6, dragStartViewMaxX - dragStartViewMinX);
                double delta = (event.getX() - dragPoint.x) / Math.max(1.0, plot.getWidth()) * domain;
                setXWindow(dragStartViewMinX - delta, dragStartViewMaxX - delta);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragPoint = null;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (event.isShiftDown()) {
                    zoomY(event.getPreciseWheelRotation());
                } else {
                    zoomX(event.getPoint(), event.getPreciseWheelRotation());
                }
            }
        };
        addMouseMotionListener(mouseHandler);
        addMouseListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
    }

    public void setPresentation(String title, String subtitle, String yAxisLabel, String xAxisLabel) {
        this.title = title;
        this.subtitle = subtitle;
        this.yAxisLabel = yAxisLabel;
        this.xAxisLabel = xAxisLabel;
        repaint();
    }

    public void setSeries(List<Series> series) {
        this.series = List.copyOf(series);
        resetView();
        repaint();
    }

    public void appendPoint(String implId, PointRecord point) {
        Map<String, List<PointRecord>> grouped = new LinkedHashMap<>();
        Map<String, Color> colors = new LinkedHashMap<>();
        for (Series item : series) {
            grouped.put(item.implId(), new ArrayList<>(item.points()));
            colors.put(item.implId(), item.color());
        }
        grouped.computeIfAbsent(implId, ignored -> new ArrayList<>()).add(point);
        colors.putIfAbsent(implId, UiPalette.ACCENT);
        List<Series> updated = new ArrayList<>();
        for (Map.Entry<String, List<PointRecord>> entry : grouped.entrySet()) {
            updated.add(new Series(entry.getKey(), colors.get(entry.getKey()), entry.getValue()));
        }
        this.series = List.copyOf(updated);
        if (!repaintTimer.isRunning()) {
            repaintTimer.start();
        }
    }

    public void setXWindow(double lo, double hi) {
        double domainMin = domainMinX();
        double domainMax = domainMaxX();
        if (Double.isNaN(domainMin) || Double.isNaN(domainMax)) {
            return;
        }
        double width = Math.max(1e-6, hi - lo);
        double clampedLo = Math.max(domainMin, Math.min(lo, domainMax - width));
        double clampedHi = Math.min(domainMax, clampedLo + width);
        viewMinX = clampedLo;
        viewMaxX = clampedHi;
        repaint();
    }

    public void resetView() {
        viewMinX = Double.NaN;
        viewMaxX = Double.NaN;
        viewMinY = Double.NaN;
        viewMaxY = Double.NaN;
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

        Rectangle plot = plotBounds();
        g2.setColor(UiPalette.BORDER);
        for (int index = 0; index <= 4; index += 1) {
            int y = plot.y + (int) Math.round(index * plot.height / 4.0);
            g2.draw(new Line2D.Double(plot.x, y, plot.x + plot.width, y));
        }
        g2.draw(new Line2D.Double(plot.x, plot.y, plot.x, plot.y + plot.height));
        g2.draw(new Line2D.Double(plot.x, plot.y + plot.height, plot.x + plot.width, plot.y + plot.height));

        if (series.isEmpty()) {
            g2.drawString("No metric data for the current selection.", plot.x, plot.y + plot.height / 2);
            g2.dispose();
            return;
        }

        double minX = currentMinX();
        double maxX = currentMaxX();
        double minY = currentMinY();
        double maxY = currentMaxY();
        FontMetrics metrics = g2.getFontMetrics(UiPalette.BODY);
        g2.setFont(UiPalette.BODY);
        for (int index = 0; index <= 4; index += 1) {
            double value = maxY - ((maxY - minY) * index / 4.0);
            int y = plot.y + (int) Math.round(index * plot.height / 4.0);
            g2.drawString(String.format("%.3f", value), 12, y + 4);
        }

        for (Series item : series) {
            g2.setColor(item.color());
            g2.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.Point previous = null;
            for (PointRecord point : visiblePoints(item.points(), minX, maxX)) {
                java.awt.Point mapped = mapPoint(point, plot, minX, maxX, minY, maxY);
                if (previous != null) {
                    g2.draw(new Line2D.Double(previous.x, previous.y, mapped.x, mapped.y));
                }
                g2.fill(new Ellipse2D.Double(mapped.x - 3.5, mapped.y - 3.5, 7.0, 7.0));
                previous = mapped;
            }
        }

        g2.setColor(UiPalette.MUTED);
        g2.drawString(yAxisLabel, plot.x, plot.y + plot.height + metrics.getHeight() + 12);
        drawCentered(g2, xAxisLabel, plot.x + plot.width / 2, plot.y + plot.height + metrics.getHeight() * 2 + 8);
        paintLegend(g2, plot);
        paintViewportIndicator(g2, plot);
        if (hoverPoint != null) {
            paintTooltip(g2, hoverPoint);
        }
        g2.dispose();
    }

    private void paintLegend(Graphics2D graphics, Rectangle plot) {
        int x = Math.max(plot.x + 12, plot.x + plot.width - 220);
        int y = 16;
        graphics.setFont(UiPalette.BODY);
        int index = 0;
        for (Series item : series.subList(0, Math.min(series.size(), 6))) {
            Icon icon = LanguageIconRegistry.icon(item.implId(), 16);
            icon.paintIcon(this, graphics, x, y + index * 20);
            graphics.setColor(UiPalette.TEXT);
            graphics.drawString(LanguageIconRegistry.displayName(item.implId()), x + 22, y + index * 20 + 13);
            index += 1;
        }
    }

    private void paintViewportIndicator(Graphics2D graphics, Rectangle plot) {
        double domainMin = domainMinX();
        double domainMax = domainMaxX();
        if (Double.isNaN(domainMin) || Double.isNaN(domainMax) || domainMax <= domainMin) {
            return;
        }
        int y = plot.y + plot.height + 28;
        graphics.setColor(UiPalette.PANEL_ALT);
        graphics.fillRoundRect(plot.x, y, plot.width, 4, 4, 4);
        int left = plot.x + (int) Math.round((currentMinX() - domainMin) / (domainMax - domainMin) * plot.width);
        int width = (int) Math.max(12, Math.round((currentMaxX() - currentMinX()) / (domainMax - domainMin) * plot.width));
        graphics.setColor(UiPalette.ACCENT);
        graphics.fillRoundRect(left, y, Math.min(width, plot.width), 4, 4, 4);
    }

    private void paintTooltip(Graphics2D graphics, HoverPoint hover) {
        String label = LanguageIconRegistry.displayName(hover.point.implId()) + " · " + hover.point.caseId() + " · " + String.format("%.3f", hover.point.y()) + " " + yAxisLabel;
        int width = Math.max(160, graphics.getFontMetrics().stringWidth(label) + 48);
        int x = Math.min(getWidth() - width - 12, hover.screenPoint.x + 12);
        int y = Math.max(12, hover.screenPoint.y - 42);
        graphics.setColor(new Color(UiPalette.PANEL_ALT.getRed(), UiPalette.PANEL_ALT.getGreen(), UiPalette.PANEL_ALT.getBlue(), 236));
        graphics.fill(new RoundRectangle2D.Double(x, y, width, 32, 12, 12));
        graphics.setColor(UiPalette.BORDER);
        graphics.draw(new RoundRectangle2D.Double(x, y, width, 32, 12, 12));
        LanguageIconRegistry.icon(hover.point.implId(), 16).paintIcon(this, graphics, x + 10, y + 8);
        graphics.setColor(UiPalette.TEXT);
        graphics.drawString(label, x + 32, y + 21);
    }

    private HoverPoint findNearest(Point mousePoint) {
        Rectangle plot = plotBounds();
        if (!plot.contains(mousePoint)) {
            return null;
        }
        double minX = currentMinX();
        double maxX = currentMaxX();
        double minY = currentMinY();
        double maxY = currentMaxY();
        HoverPoint best = null;
        double bestDistance = 8.0;
        for (Series item : series) {
            for (PointRecord point : visiblePoints(item.points(), minX, maxX)) {
                java.awt.Point screen = mapPoint(point, plot, minX, maxX, minY, maxY);
                double distance = mousePoint.distance(screen);
                if (distance <= bestDistance) {
                    best = new HoverPoint(point, screen);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private List<PointRecord> visiblePoints(List<PointRecord> points, double minX, double maxX) {
        List<PointRecord> visible = new ArrayList<>();
        for (PointRecord point : points) {
            if (point.x() >= minX && point.x() <= maxX) {
                visible.add(point);
            }
        }
        return visible;
    }

    private java.awt.Point mapPoint(PointRecord point, Rectangle plot, double minX, double maxX, double minY, double maxY) {
        double x = plot.x + ((point.x() - minX) / Math.max(1e-6, maxX - minX)) * plot.width;
        double y = plot.y + plot.height - ((point.y() - minY) / Math.max(1e-6, maxY - minY)) * plot.height;
        return new java.awt.Point((int) Math.round(x), (int) Math.round(y));
    }

    private Rectangle plotBounds() {
        return new Rectangle(58, 70, Math.max(120, getWidth() - 86), Math.max(120, getHeight() - 134));
    }

    private void zoomX(Point mousePoint, double wheelRotation) {
        double minX = currentMinX();
        double maxX = currentMaxX();
        Rectangle plot = plotBounds();
        double mouseRatio = Math.max(0.0, Math.min(1.0, (mousePoint.x - plot.x) / Math.max(1.0, plot.getWidth())));
        double focusX = minX + (maxX - minX) * mouseRatio;
        double factor = wheelRotation > 0 ? 1.15 : 0.87;
        double nextMin = focusX - (focusX - minX) * factor;
        double nextMax = focusX + (maxX - focusX) * factor;
        setXWindow(nextMin, nextMax);
    }

    private void zoomY(double wheelRotation) {
        double minY = currentMinY();
        double maxY = currentMaxY();
        double center = minY + (maxY - minY) / 2.0;
        double factor = wheelRotation > 0 ? 1.12 : 0.88;
        double half = (maxY - minY) * factor / 2.0;
        viewMinY = center - half;
        viewMaxY = center + half;
        repaint();
    }

    private double currentMinX() {
        return Double.isNaN(viewMinX) ? domainMinX() : viewMinX;
    }

    private double currentMaxX() {
        return Double.isNaN(viewMaxX) ? domainMaxX() : viewMaxX;
    }

    private double currentMinY() {
        if (!Double.isNaN(viewMinY)) {
            return viewMinY;
        }
        double min = Double.POSITIVE_INFINITY;
        for (Series item : series) {
            for (PointRecord point : visiblePoints(item.points(), currentMinX(), currentMaxX())) {
                min = Math.min(min, point.y());
            }
        }
        return min == Double.POSITIVE_INFINITY ? 0.0 : min;
    }

    private double currentMaxY() {
        if (!Double.isNaN(viewMaxY)) {
            return viewMaxY;
        }
        double max = Double.NEGATIVE_INFINITY;
        for (Series item : series) {
            for (PointRecord point : visiblePoints(item.points(), currentMinX(), currentMaxX())) {
                max = Math.max(max, point.y());
            }
        }
        return max == Double.NEGATIVE_INFINITY ? 1.0 : max + Math.max(1e-6, max * 0.05);
    }

    private double domainMinX() {
        double min = Double.POSITIVE_INFINITY;
        for (Series item : series) {
            for (PointRecord point : item.points()) {
                min = Math.min(min, point.x());
            }
        }
        return min == Double.POSITIVE_INFINITY ? Double.NaN : min;
    }

    private double domainMaxX() {
        double max = Double.NEGATIVE_INFINITY;
        for (Series item : series) {
            for (PointRecord point : item.points()) {
                max = Math.max(max, point.x());
            }
        }
        return max == Double.NEGATIVE_INFINITY ? Double.NaN : Math.max(max, domainMinX() + 1.0);
    }

    private static void drawCentered(Graphics2D graphics, String text, int x, int y) {
        int width = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, x - width / 2, y);
    }

    private record HoverPoint(PointRecord point, java.awt.Point screenPoint) {
    }
}
