package cpubench.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import cpubench.ui.icons.LanguageIconRegistry;

public final class IconFactory {
    private interface Painter {
        void paint(Graphics2D graphics, int size, Color color);
    }

    private IconFactory() {
    }

    public static BufferedImage appImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        graphics.setPaint(UiPalette.PANEL_ALT);
        graphics.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.32, size * 0.32));
        paintChart(graphics, size, UiPalette.ACCENT);
        graphics.dispose();
        return image;
    }

    public static Icon playIcon(int size, Color color) {
        return icon(size, color, IconFactory::paintPlay);
    }

    public static Icon refreshIcon(int size, Color color) {
        return icon(size, color, IconFactory::paintRefresh);
    }

    public static Icon docsIcon(int size, Color color) {
        return icon(size, color, IconFactory::paintDocs);
    }

    public static Icon chartIcon(int size, Color color) {
        return icon(size, color, IconFactory::paintChart);
    }

    public static Icon logsIcon(int size, Color color) {
        return icon(size, color, IconFactory::paintLogs);
    }

    public static Icon radarIcon(int size, Color color) {
        return icon(size, color, IconFactory::paintRadar);
    }

    public static Icon checklistIcon(int size, boolean selected) {
        return new ChecklistIcon(size, selected, null);
    }

    public static Icon checklistLanguageIcon(String implementationId, int size, boolean selected) {
        return new ChecklistIcon(size, selected, LanguageIconRegistry.icon(implementationId, size));
    }

    private static Icon icon(int size, Color color, Painter painter) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        painter.paint(graphics, size, color);
        graphics.dispose();
        return new ImageIcon(image);
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void paintPlay(Graphics2D graphics, int size, Color color) {
        graphics.setColor(color);
        GeneralPath triangle = new GeneralPath();
        triangle.moveTo(size * 0.28, size * 0.18);
        triangle.lineTo(size * 0.78, size * 0.5);
        triangle.lineTo(size * 0.28, size * 0.82);
        triangle.closePath();
        graphics.fill(triangle);
    }

    private static void paintRefresh(Graphics2D graphics, int size, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(2f, size * 0.11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.16, size * 0.16, size * 0.54, size * 0.54, 25, 260, Arc2D.OPEN));
        graphics.draw(new Arc2D.Double(size * 0.30, size * 0.30, size * 0.54, size * 0.54, 205, 250, Arc2D.OPEN));
        GeneralPath arrowOne = new GeneralPath();
        arrowOne.moveTo(size * 0.66, size * 0.16);
        arrowOne.lineTo(size * 0.86, size * 0.18);
        arrowOne.lineTo(size * 0.76, size * 0.34);
        arrowOne.closePath();
        graphics.fill(arrowOne);
        GeneralPath arrowTwo = new GeneralPath();
        arrowTwo.moveTo(size * 0.18, size * 0.70);
        arrowTwo.lineTo(size * 0.14, size * 0.90);
        arrowTwo.lineTo(size * 0.34, size * 0.82);
        arrowTwo.closePath();
        graphics.fill(arrowTwo);
    }

    private static void paintDocs(Graphics2D graphics, int size, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(2f, size * 0.08f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new RoundRectangle2D.Double(size * 0.20, size * 0.12, size * 0.58, size * 0.76, size * 0.14, size * 0.14));
        graphics.draw(new Line2D.Double(size * 0.32, size * 0.30, size * 0.64, size * 0.30));
        graphics.draw(new Line2D.Double(size * 0.32, size * 0.46, size * 0.64, size * 0.46));
        graphics.draw(new Line2D.Double(size * 0.32, size * 0.62, size * 0.56, size * 0.62));
    }

    private static void paintChart(Graphics2D graphics, int size, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(2f, size * 0.09f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Line2D.Double(size * 0.18, size * 0.78, size * 0.18, size * 0.24));
        graphics.draw(new Line2D.Double(size * 0.18, size * 0.78, size * 0.82, size * 0.78));
        Shape line = new Line2D.Double(size * 0.26, size * 0.66, size * 0.44, size * 0.44);
        graphics.draw(line);
        graphics.draw(new Line2D.Double(size * 0.44, size * 0.44, size * 0.58, size * 0.54));
        graphics.draw(new Line2D.Double(size * 0.58, size * 0.54, size * 0.76, size * 0.28));
    }

    private static void paintLogs(Graphics2D graphics, int size, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(2f, size * 0.08f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new RoundRectangle2D.Double(size * 0.16, size * 0.16, size * 0.68, size * 0.68, size * 0.12, size * 0.12));
        graphics.draw(new Line2D.Double(size * 0.30, size * 0.34, size * 0.70, size * 0.34));
        graphics.draw(new Line2D.Double(size * 0.30, size * 0.50, size * 0.70, size * 0.50));
        graphics.draw(new Line2D.Double(size * 0.30, size * 0.66, size * 0.58, size * 0.66));
    }

    private static void paintRadar(Graphics2D graphics, int size, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(2f, size * 0.08f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Arc2D.Double(size * 0.18, size * 0.18, size * 0.64, size * 0.64, 30, 300, Arc2D.OPEN));
        graphics.draw(new Arc2D.Double(size * 0.30, size * 0.30, size * 0.40, size * 0.40, 30, 300, Arc2D.OPEN));
        graphics.draw(new Line2D.Double(size * 0.50, size * 0.50, size * 0.78, size * 0.22));
        graphics.fill(new Arc2D.Double(size * 0.70, size * 0.14, size * 0.16, size * 0.16, 0, 360, Arc2D.CHORD));
    }

    private static final class ChecklistIcon implements Icon {
        private final int size;
        private final boolean selected;
        private final Icon languageIcon;

        private ChecklistIcon(int size, boolean selected, Icon languageIcon) {
            this.size = size;
            this.selected = selected;
            this.languageIcon = languageIcon;
        }

        @Override
        public void paintIcon(Component component, java.awt.Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                configure(g2);
                int boxSize = Math.max(12, size);
                int boxY = y + Math.max(0, (getIconHeight() - boxSize) / 2);
                Shape box = new RoundRectangle2D.Double(x, boxY, boxSize, boxSize, 6, 6);
                g2.setColor(selected ? UiPalette.ACCENT : UiPalette.SURFACE_ALT);
                g2.fill(box);
                g2.setColor(selected ? UiPalette.ACCENT : UiPalette.BORDER);
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(box);
                if (selected) {
                    g2.setColor(UiPalette.WINDOW);
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    GeneralPath check = new GeneralPath();
                    check.moveTo(x + boxSize * 0.22, boxY + boxSize * 0.54);
                    check.lineTo(x + boxSize * 0.42, boxY + boxSize * 0.74);
                    check.lineTo(x + boxSize * 0.78, boxY + boxSize * 0.28);
                    g2.draw(check);
                }
                if (languageIcon != null) {
                    int iconX = x + boxSize + 8;
                    int iconY = y + Math.max(0, (getIconHeight() - languageIcon.getIconHeight()) / 2);
                    languageIcon.paintIcon(component, g2, iconX, iconY);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            int languageWidth = languageIcon == null ? 0 : languageIcon.getIconWidth() + 8;
            return Math.max(12, size) + languageWidth;
        }

        @Override
        public int getIconHeight() {
            int baseHeight = Math.max(12, size);
            return languageIcon == null ? baseHeight : Math.max(baseHeight, languageIcon.getIconHeight());
        }
    }
}

