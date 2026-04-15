package cpubench.ui;

import java.awt.Color;
import java.awt.Font;

public final class UiPalette {
    public static final Color WINDOW = new Color(0x0B1220);
    public static final Color PANEL = new Color(0x101A2C);
    public static final Color PANEL_ALT = new Color(0x14213A);
    public static final Color SURFACE = new Color(0x18253F);
    public static final Color SURFACE_ALT = new Color(0x1D2D4C);
    public static final Color BORDER = new Color(0x294261);
    public static final Color TEXT = new Color(0xEAF2FF);
    public static final Color MUTED = new Color(0x92A7C4);
    public static final Color ACCENT = new Color(0x4DD0E1);
    public static final Color ACCENT_ALT = new Color(0x5EEAD4);
    public static final Color WARNING = new Color(0xF6C453);
    public static final Color SUCCESS = new Color(0x55D187);
    public static final Color DANGER = new Color(0xFF7B72);
    public static final Color INFO = new Color(0x67E8F9);
    public static final Font DISPLAY = new Font("Serif", Font.BOLD, 28);
    public static final Font SUBTITLE = new Font("Dialog", Font.PLAIN, 14);
    public static final Font BODY = new Font("Dialog", Font.PLAIN, 13);
    public static final Font LABEL = new Font("Dialog", Font.BOLD, 12);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private static final Color[] SERIES = {
        new Color(0x4DD0E1),
        new Color(0xF97316),
        new Color(0x55D187),
        new Color(0xF6C453),
        new Color(0xA78BFA),
        new Color(0xF472B6),
        new Color(0x60A5FA),
        new Color(0xF87171),
    };

    private UiPalette() {
    }

    public static Color seriesColor(int index) {
        return SERIES[Math.floorMod(index, SERIES.length)];
    }

    public static Color statusColor(String status) {
        return switch (status) {
            case "success" -> SUCCESS;
            case "partial_failure", "failed" -> DANGER;
            case "running", "launch" -> INFO;
            default -> MUTED;
        };
    }
}

