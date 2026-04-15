package cpubench.ui.icons;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class SimpleSvgIcon implements Icon {
    private final int size;
    private final double viewWidth;
    private final double viewHeight;
    private final List<Primitive> primitives;

    private SimpleSvgIcon(int size, double viewWidth, double viewHeight, List<Primitive> primitives) {
        this.size = size;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.primitives = List.copyOf(primitives);
    }

    public static SimpleSvgIcon load(InputStream stream, int size) throws IOException {
        byte[] bytes = stream.readAllBytes();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            Element root = document.getDocumentElement();
            String[] viewBox = root.getAttribute("viewBox").split("\\s+");
            double viewWidth = viewBox.length >= 4 ? Double.parseDouble(viewBox[2]) : size;
            double viewHeight = viewBox.length >= 4 ? Double.parseDouble(viewBox[3]) : size;
            List<Primitive> primitives = new ArrayList<>();
            for (int index = 0; index < root.getChildNodes().getLength(); index += 1) {
                Node node = root.getChildNodes().item(index);
                if (node instanceof Element child) {
                    switch (child.getTagName()) {
                        case "rect" -> primitives.add(RectPrimitive.from(child));
                        case "circle" -> primitives.add(CirclePrimitive.from(child));
                        case "text" -> primitives.add(TextPrimitive.from(child));
                        default -> {
                        }
                    }
                }
            }
            return new SimpleSvgIcon(size, viewWidth, viewHeight, primitives);
        } catch (Exception error) {
            String fallback = new String(bytes, StandardCharsets.UTF_8);
            throw new IOException("Unable to parse SVG icon\n" + fallback, error);
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.translate(x, y);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.scale(size / viewWidth, size / viewHeight);
        for (Primitive primitive : primitives) {
            primitive.paint(g2);
        }
        g2.dispose();
    }

    private interface Primitive {
        void paint(Graphics2D graphics);
    }

    private record RectPrimitive(
        double x,
        double y,
        double width,
        double height,
        double radius,
        Color fill,
        Color stroke,
        float strokeWidth
    ) implements Primitive {
        static RectPrimitive from(Element element) {
            return new RectPrimitive(
                number(element, "x"),
                number(element, "y"),
                number(element, "width"),
                number(element, "height"),
                number(element, "rx"),
                color(element.getAttribute("fill")),
                color(element.getAttribute("stroke")),
                (float) number(element, "stroke-width")
            );
        }

        @Override
        public void paint(Graphics2D graphics) {
            RoundRectangle2D.Double shape = new RoundRectangle2D.Double(x, y, width, height, radius * 2.0, radius * 2.0);
            if (fill != null) {
                graphics.setColor(fill);
                graphics.fill(shape);
            }
            if (stroke != null && strokeWidth > 0f) {
                graphics.setColor(stroke);
                graphics.setStroke(new BasicStroke(strokeWidth));
                graphics.draw(shape);
            }
        }
    }

    private record CirclePrimitive(double cx, double cy, double radius, Color fill) implements Primitive {
        static CirclePrimitive from(Element element) {
            return new CirclePrimitive(
                number(element, "cx"),
                number(element, "cy"),
                number(element, "r"),
                color(element.getAttribute("fill"))
            );
        }

        @Override
        public void paint(Graphics2D graphics) {
            if (fill == null) {
                return;
            }
            graphics.setColor(fill);
            graphics.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0));
        }
    }

    private record TextPrimitive(double x, double y, String anchor, float size, Font font, Color fill, String text) implements Primitive {
        static TextPrimitive from(Element element) {
            String family = element.getAttribute("font-family");
            int style = "700".equals(element.getAttribute("font-weight")) ? Font.BOLD : Font.PLAIN;
            return new TextPrimitive(
                number(element, "x"),
                number(element, "y"),
                element.getAttribute("text-anchor"),
                (float) number(element, "font-size"),
                new Font(family.isBlank() ? Font.SANS_SERIF : family, style, Math.max(1, (int) number(element, "font-size"))),
                color(element.getAttribute("fill")),
                element.getTextContent()
            );
        }

        @Override
        public void paint(Graphics2D graphics) {
            graphics.setFont(font.deriveFont(size));
            graphics.setColor(fill == null ? Color.WHITE : fill);
            int drawX = (int) Math.round(x);
            if ("middle".equals(anchor)) {
                drawX -= graphics.getFontMetrics().stringWidth(text) / 2;
            }
            graphics.drawString(text, drawX, (int) Math.round(y));
        }
    }

    private static double number(Element element, String attribute) {
        String raw = element.getAttribute(attribute);
        if (raw == null || raw.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(raw);
    }

    private static Color color(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw)) {
            return null;
        }
        return Color.decode(raw);
    }
}
