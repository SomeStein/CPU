package cpubench.ui;

import java.awt.Color;
import java.awt.Font;

public final class UiPalette {
    public static final Color WINDOW = new Color(0x08111D);
    public static final Color PANEL = new Color(0x101B2A);
    public static final Color PANEL_ALT = new Color(0x142234);
    public static final Color SURFACE = new Color(0x1A293C);
    public static final Color SURFACE_ALT = new Color(0x21354D);
    public static final Color BORDER = new Color(0x294865);
    public static final Color TEXT = new Color(0xE8F1FF);
    public static final Color MUTED = new Color(0x93A8C3);
    public static final Color ACCENT = new Color(0x4ED3C3);
    public static final Color ACCENT_ALT = new Color(0xF59E0B);
    public static final Color WARNING = new Color(0xF6C453);
    public static final Color SUCCESS = new Color(0x55D187);
    public static final Color DANGER = new Color(0xFF7B72);
    public static final Color INFO = new Color(0x67E8F9);
    public static final int GAP_SM = 8;
    public static final int GAP_MD = 12;
    public static final int GAP_LG = 16;
    public static final Font DISPLAY = new Font("SansSerif", Font.BOLD, 28);
    public static final Font SUBTITLE = new Font("SansSerif", Font.PLAIN, 14);
    public static final Font BODY = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font LABEL = new Font("SansSerif", Font.BOLD, 12);
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
