package cpubench.ui.charts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.Timer;

import cpubench.ui.UiPalette;
import cpubench.ui.icons.LanguageIconRegistry;

public final class InteractiveTrendChart extends JComponent {
    public record PointRecord(double x, double y, String implId, String caseId, int repeat) {
    }

    public record Series(String id, String implId, String caseId, Color color, List<PointRecord> points) {
    }

    private enum DragMode {
        NONE,
        PAN,
        SCALE_X,
        SCALE_Y,
    }

    private final Timer repaintTimer;
    private List<Series> series = List.of();
    private String title = "Interactive Trend";
    private String subtitle = "Drag to pan. Drag axes to scale. Wheel to zoom.";
    private String yAxisLabel = "ns/iter";
    private String xAxisLabel = "Sample";
    private HoverPoint hoverPoint;
    private DragMode dragMode = DragMode.NONE;
    private Point dragPoint;
    private double dragStartMinX;
    private double dragStartMaxX;
    private double dragStartMinY;
    private double dragStartMaxY;
    private double viewMinX = Double.NaN;
    private double viewMaxX = Double.NaN;
    private double viewMinY = Double.NaN;
    private double viewMaxY = Double.NaN;

    public InteractiveTrendChart() {
        setOpaque(true);
        setBackground(UiPalette.PANEL);
        setBorder(BorderFactory.createEmptyBorder(UiPalette.GAP_MD, UiPalette.GAP_MD, UiPalette.GAP_MD, UiPalette.GAP_MD));
        setPreferredSize(new Dimension(720, 320));
        repaintTimer = new Timer(40, event -> repaint());
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
                dragMode = dragModeFor(event.getPoint());
                dragPoint = event.getPoint();
                dragStartMinX = currentMinX();
                dragStartMaxX = currentMaxX();
                dragStartMinY = currentMinY();
                dragStartMaxY = currentMaxY();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                Rectangle plot = plotBounds();
                if (dragPoint == null || plot.isEmpty()) {
                    return;
                }
                switch (dragMode) {
                    case PAN -> panView(event, plot);
                    case SCALE_X -> scaleXByDrag(event, plot);
                    case SCALE_Y -> scaleYByDrag(event, plot);
                    case NONE -> {
                    }
                }
                hoverPoint = findNearest(event.getPoint());
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragMode = DragMode.NONE;
                dragPoint = null;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (yAxisBounds().contains(event.getPoint()) || event.isShiftDown()) {
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
        List<Series> normalized = new ArrayList<>(series.size());
        for (Series item : series) {
            normalized.add(new Series(item.id(), item.implId(), item.caseId(), item.color(), sortPoints(item.points())));
        }
        this.series = List.copyOf(normalized);
        resetView();
        repaint();
    }

    public void appendPoint(String seriesId, String implId, String caseId, Color color, PointRecord point) {
        List<Series> updated = new ArrayList<>(series);
        int index = -1;
        for (int i = 0; i < updated.size(); i += 1) {
            if (updated.get(i).id().equals(seriesId)) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            List<PointRecord> points = new ArrayList<>(updated.get(index).points());
            points.add(point);
            updated.set(index, new Series(seriesId, implId, caseId, color, sortPoints(points)));
        } else {
            updated.add(new Series(seriesId, implId, caseId, color, List.of(point)));
        }
        series = List.copyOf(updated);
        scheduleRepaint();
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
        scheduleRepaint();
    }

    public void setYWindow(double lo, double hi) {
        double domainMin = domainMinY();
        double domainMax = domainMaxY();
        if (Double.isNaN(domainMin) || Double.isNaN(domainMax)) {
            return;
        }
        double height = Math.max(1e-9, hi - lo);
        double clampedLo = Math.max(domainMin, Math.min(lo, domainMax - height));
        double clampedHi = Math.min(domainMax, clampedLo + height);
        viewMinY = clampedLo;
        viewMaxY = clampedHi;
        scheduleRepaint();
    }

    public void resetView() {
        viewMinX = Double.NaN;
        viewMaxX = Double.NaN;
        viewMinY = Double.NaN;
        viewMaxY = Double.NaN;
        scheduleRepaint();
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

        paintAxisHandles(g2, plot);

        if (series.isEmpty()) {
            g2.drawString("No metric data for the current selection.", plot.x, plot.y + plot.height / 2);
            g2.dispose();
            return;
        }

        double minX = currentMinX();
        double maxX = currentMaxX();
        double minY = currentMinY();
        double maxY = currentMaxY();
        if (maxY <= minY) {
            maxY = minY + 1.0;
        }

        g2.setFont(UiPalette.BODY);
        for (int index = 0; index <= 4; index += 1) {
            double value = maxY - ((maxY - minY) * index / 4.0);
            int y = plot.y + (int) Math.round(index * plot.height / 4.0);
            g2.setColor(UiPalette.MUTED);
            g2.drawString(String.format("%.3f", value), 10, y + 4);
        }

        for (Series item : series) {
            List<PointRecord> visible = visiblePoints(item.points(), minX, maxX);
            if (visible.isEmpty()) {
                continue;
            }
            g2.setColor(item.color());
            g2.setStroke(new BasicStroke(2.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Point previous = null;
            for (PointRecord point : visible) {
                Point mapped = mapPoint(point, plot, minX, maxX, minY, maxY);
                if (previous != null) {
                    g2.draw(new Line2D.Double(previous.x, previous.y, mapped.x, mapped.y));
                }
                g2.fill(new Ellipse2D.Double(mapped.x - 3.5, mapped.y - 3.5, 7.0, 7.0));
                previous = mapped;
            }
        }

        g2.setColor(UiPalette.MUTED);
        drawCentered(g2, xAxisLabel, plot.x + plot.width / 2, getHeight() - 16);
        g2.drawString(yAxisLabel, plot.x, plot.y + plot.height + 30);
        paintLegend(g2, plot);
        paintViewportIndicator(g2, plot);
        if (hoverPoint != null) {
            paintTooltip(g2, hoverPoint);
        }
        g2.dispose();
    }

    private void paintLegend(Graphics2D graphics, Rectangle plot) {
        Set<String> implementations = new LinkedHashSet<>();
        for (Series item : series) {
            implementations.add(item.implId());
        }
        int iconSize = 18;
        int spacing = 8;
        int x = plot.x + plot.width - Math.max(0, implementations.size() * (iconSize + spacing));
        int y = 16;
        for (String implementation : implementations) {
            Icon icon = LanguageIconRegistry.icon(implementation, iconSize);
            icon.paintIcon(this, graphics, x, y);
            x += iconSize + spacing;
        }
    }

    private void paintViewportIndicator(Graphics2D graphics, Rectangle plot) {
        double domainMin = domainMinX();
        double domainMax = domainMaxX();
        if (Double.isNaN(domainMin) || Double.isNaN(domainMax) || domainMax <= domainMin) {
            return;
        }
        int y = plot.y + plot.height + 18;
        graphics.setColor(UiPalette.PANEL_ALT);
        graphics.fillRoundRect(plot.x, y, plot.width, 4, 4, 4);
        int left = plot.x + (int) Math.round((currentMinX() - domainMin) / (domainMax - domainMin) * plot.width);
        int width = (int) Math.max(12, Math.round((currentMaxX() - currentMinX()) / (domainMax - domainMin) * plot.width));
        graphics.setColor(UiPalette.ACCENT);
        graphics.fillRoundRect(left, y, Math.min(width, plot.width), 4, 4, 4);
    }

    private void paintAxisHandles(Graphics2D graphics, Rectangle plot) {
        graphics.setColor(UiPalette.PANEL_ALT);
        graphics.fillRoundRect(plot.x, plot.y + plot.height + 4, plot.width, 10, 10, 10);
        graphics.fillRoundRect(plot.x - 14, plot.y, 10, plot.height, 10, 10);
        graphics.setColor(UiPalette.BORDER);
        graphics.drawRoundRect(plot.x, plot.y + plot.height + 4, plot.width, 10, 10, 10);
        graphics.drawRoundRect(plot.x - 14, plot.y, 10, plot.height, 10, 10);
    }

    private void paintTooltip(Graphics2D graphics, HoverPoint hover) {
        String caseLabel = hover.point.caseId().isBlank() ? "Case n/a" : hover.point.caseId();
        String sampleLabel = "Repeat " + hover.point.repeat();
        String metricLabel = String.format("%.3f %s", hover.point.y(), yAxisLabel);
        int width = 188;
        int height = 70;
        int x = Math.min(getWidth() - width - 12, hover.screenPoint.x + 14);
        int y = Math.max(12, hover.screenPoint.y - height - 10);

        graphics.setColor(new Color(UiPalette.PANEL_ALT.getRed(), UiPalette.PANEL_ALT.getGreen(), UiPalette.PANEL_ALT.getBlue(), 236));
        graphics.fill(new RoundRectangle2D.Double(x, y, width, height, 16, 16));
        graphics.setColor(UiPalette.BORDER);
        graphics.draw(new RoundRectangle2D.Double(x, y, width, height, 16, 16));

        LanguageIconRegistry.icon(hover.point.implId(), 24).paintIcon(this, graphics, x + 12, y + 12);
        graphics.setColor(UiPalette.TEXT);
        graphics.setFont(UiPalette.LABEL);
        graphics.drawString(caseLabel, x + 44, y + 26);
        graphics.setFont(UiPalette.BODY);
        graphics.drawString(sampleLabel, x + 44, y + 44);
        graphics.drawString(metricLabel, x + 44, y + 60);
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
        double bestDistance = 10.0;
        for (Series item : series) {
            for (PointRecord point : visiblePoints(item.points(), minX, maxX)) {
                Point screen = mapPoint(point, plot, minX, maxX, minY, maxY);
                double distance = mousePoint.distance(screen);
                if (distance <= bestDistance) {
                    best = new HoverPoint(point, screen);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private DragMode dragModeFor(Point point) {
        if (plotBounds().contains(point)) {
            return DragMode.PAN;
        }
        if (xAxisBounds().contains(point)) {
            return DragMode.SCALE_X;
        }
        if (yAxisBounds().contains(point)) {
            return DragMode.SCALE_Y;
        }
        return DragMode.NONE;
    }

    private void panView(MouseEvent event, Rectangle plot) {
        double domainX = Math.max(1e-6, dragStartMaxX - dragStartMinX);
        double domainY = Math.max(1e-9, dragStartMaxY - dragStartMinY);
        double deltaX = (event.getX() - dragPoint.x) / Math.max(1.0, plot.getWidth()) * domainX;
        double deltaY = (event.getY() - dragPoint.y) / Math.max(1.0, plot.getHeight()) * domainY;
        setXWindow(dragStartMinX - deltaX, dragStartMaxX - deltaX);
        setYWindow(dragStartMinY + deltaY, dragStartMaxY + deltaY);
    }

    private void scaleXByDrag(MouseEvent event, Rectangle plot) {
        double center = dragStartMinX + (dragStartMaxX - dragStartMinX) / 2.0;
        double factor = Math.exp((event.getX() - dragPoint.x) / Math.max(40.0, plot.getWidth()));
        double halfSpan = Math.max(1e-6, (dragStartMaxX - dragStartMinX) * factor / 2.0);
        setXWindow(center - halfSpan, center + halfSpan);
    }

    private void scaleYByDrag(MouseEvent event, Rectangle plot) {
        double center = dragStartMinY + (dragStartMaxY - dragStartMinY) / 2.0;
        double factor = Math.exp((dragPoint.y - event.getY()) / Math.max(40.0, plot.getHeight()));
        double halfSpan = Math.max(1e-9, (dragStartMaxY - dragStartMinY) * factor / 2.0);
        setYWindow(center - halfSpan, center + halfSpan);
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

    private Point mapPoint(PointRecord point, Rectangle plot, double minX, double maxX, double minY, double maxY) {
        double x = plot.x + ((point.x() - minX) / Math.max(1e-6, maxX - minX)) * plot.width;
        double y = plot.y + plot.height - ((point.y() - minY) / Math.max(1e-9, maxY - minY)) * plot.height;
        return new Point((int) Math.round(x), (int) Math.round(y));
    }

    private Rectangle plotBounds() {
        return new Rectangle(62, 70, Math.max(120, getWidth() - 92), Math.max(120, getHeight() - 132));
    }

    private Rectangle xAxisBounds() {
        Rectangle plot = plotBounds();
        return new Rectangle(plot.x, plot.y + plot.height + 2, plot.width, 16);
    }

    private Rectangle yAxisBounds() {
        Rectangle plot = plotBounds();
        return new Rectangle(Math.max(0, plot.x - 18), plot.y, 18, plot.height);
    }

    private void zoomX(Point mousePoint, double wheelRotation) {
        double minX = currentMinX();
        double maxX = currentMaxX();
        Rectangle plot = plotBounds();
        double mouseRatio = Math.max(0.0, Math.min(1.0, (mousePoint.x - plot.x) / Math.max(1.0, plot.getWidth())));
        double focusX = minX + (maxX - minX) * mouseRatio;
        double factor = wheelRotation > 0 ? 1.14 : 0.88;
        double nextMin = focusX - (focusX - minX) * factor;
        double nextMax = focusX + (maxX - focusX) * factor;
        setXWindow(nextMin, nextMax);
    }

    private void zoomY(double wheelRotation) {
        double minY = currentMinY();
        double maxY = currentMaxY();
        double center = minY + (maxY - minY) / 2.0;
        double factor = wheelRotation > 0 ? 1.12 : 0.88;
        double half = Math.max(1e-9, (maxY - minY) * factor / 2.0);
        setYWindow(center - half, center + half);
    }

    private List<PointRecord> sortPoints(List<PointRecord> points) {
        List<PointRecord> ordered = new ArrayList<>(points);
        ordered.sort(Comparator.comparingDouble(PointRecord::x).thenComparingInt(PointRecord::repeat));
        return List.copyOf(ordered);
    }

    private double currentMinX() {
        return Double.isNaN(viewMinX) ? domainMinX() : viewMinX;
    }

    private double currentMaxX() {
        return Double.isNaN(viewMaxX) ? domainMaxX() : viewMaxX;
    }

    private double currentMinY() {
        return Double.isNaN(viewMinY) ? domainMinY() : viewMinY;
    }

    private double currentMaxY() {
        return Double.isNaN(viewMaxY) ? domainMaxY() : viewMaxY;
    }

    private double domainMinX() {
        double min = Double.POSITIVE_INFINITY;
        for (Series item : series) {
            for (PointRecord point : item.points()) {
                min = Math.min(min, point.x());
            }
        }
        return min == Double.POSITIVE_INFINITY ? 0.0 : min;
    }

    private double domainMaxX() {
        double max = Double.NEGATIVE_INFINITY;
        for (Series item : series) {
            for (PointRecord point : item.points()) {
                max = Math.max(max, point.x());
            }
        }
        return max == Double.NEGATIVE_INFINITY ? 1.0 : Math.max(max, domainMinX() + 1.0);
    }

    private double domainMinY() {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Series item : series) {
            for (PointRecord point : item.points()) {
                min = Math.min(min, point.y());
                max = Math.max(max, point.y());
            }
        }
        if (min == Double.POSITIVE_INFINITY || max == Double.NEGATIVE_INFINITY) {
            return 0.0;
        }
        double padding = Math.max(1e-9, (max - min) * 0.08);
        return Math.max(0.0, min - padding);
    }

    private double domainMaxY() {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Series item : series) {
            for (PointRecord point : item.points()) {
                min = Math.min(min, point.y());
                max = Math.max(max, point.y());
            }
        }
        if (min == Double.POSITIVE_INFINITY || max == Double.NEGATIVE_INFINITY) {
            return 1.0;
        }
        double padding = Math.max(1e-9, (max - min) * 0.08);
        return max + padding;
    }

    private void scheduleRepaint() {
        if (!repaintTimer.isRunning()) {
            repaintTimer.start();
        }
    }

    private static void drawCentered(Graphics2D graphics, String text, int x, int y) {
        int width = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, x - width / 2, y);
    }

    private record HoverPoint(PointRecord point, Point screenPoint) {
    }
}
