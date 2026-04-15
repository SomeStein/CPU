package cpubench.ui;

import java.awt.Color;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

public final class DarkTheme {
    private DarkTheme() {
    }

    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ignored) {
        }

        setColor("Panel.background", UiPalette.PANEL);
        setColor("Viewport.background", UiPalette.PANEL);
        setColor("TabbedPane.background", UiPalette.PANEL);
        setColor("TabbedPane.foreground", UiPalette.TEXT);
        setColor("TabbedPane.selected", UiPalette.SURFACE_ALT);
        setColor("TabbedPane.selectHighlight", UiPalette.SURFACE_ALT);
        setColor("TabbedPane.unselectedBackground", UiPalette.PANEL_ALT);
        setColor("TabbedPane.selectedBackground", UiPalette.SURFACE_ALT);
        setColor("TabbedPane.selectedForeground", UiPalette.TEXT);
        setColor("TabbedPane.unselectedForeground", UiPalette.MUTED);
        setColor("TabbedPane.contentAreaColor", UiPalette.PANEL);
        setColor("TabbedPane.focus", UiPalette.ACCENT);
        setColor("Label.foreground", UiPalette.TEXT);
        setColor("Button.background", UiPalette.SURFACE);
        setColor("Button.foreground", UiPalette.TEXT);
        setColor("Button.select", UiPalette.SURFACE_ALT);
        setColor("ComboBox.background", UiPalette.SURFACE);
        setColor("ComboBox.foreground", UiPalette.TEXT);
        setColor("ComboBox.selectionBackground", UiPalette.ACCENT);
        setColor("ComboBox.selectionForeground", UiPalette.WINDOW);
        setColor("TextArea.background", UiPalette.SURFACE);
        setColor("TextArea.foreground", UiPalette.TEXT);
        setColor("TextPane.background", UiPalette.SURFACE);
        setColor("TextPane.foreground", UiPalette.TEXT);
        setColor("Table.background", UiPalette.SURFACE);
        setColor("Table.foreground", UiPalette.TEXT);
        setColor("Table.selectionBackground", UiPalette.ACCENT);
        setColor("Table.selectionForeground", UiPalette.WINDOW);
        setColor("Table.gridColor", UiPalette.BORDER);
        setColor("TableHeader.background", UiPalette.PANEL_ALT);
        setColor("TableHeader.foreground", UiPalette.TEXT);
        setColor("Menu.background", UiPalette.PANEL_ALT);
        setColor("Menu.foreground", UiPalette.TEXT);
        setColor("MenuItem.background", UiPalette.PANEL_ALT);
        setColor("MenuItem.foreground", UiPalette.TEXT);
        setColor("MenuBar.background", UiPalette.PANEL_ALT);
        setColor("MenuBar.foreground", UiPalette.TEXT);
        setColor("ProgressBar.background", UiPalette.SURFACE);
        setColor("ProgressBar.foreground", UiPalette.ACCENT);
        setColor("ScrollBar.thumb", UiPalette.BORDER);
        setColor("ScrollBar.track", UiPalette.PANEL);
        setColor("SplitPane.background", UiPalette.WINDOW);
        setColor("SplitPaneDivider.background", UiPalette.WINDOW);
        setColor("ToolTip.background", UiPalette.SURFACE_ALT);
        setColor("ToolTip.foreground", UiPalette.TEXT);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Button.margin", new Insets(8, 12, 8, 12));
        UIManager.put("Button.arc", 16);
        UIManager.put("Component.arc", 18);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("Table.rowHeight", 26);
        UIManager.put("SplitPane.dividerSize", 10);
        UIManager.put("defaultFont", new FontUIResource(UiPalette.BODY));
    }

    private static void setColor(String key, Color value) {
        UIManager.put(key, new ColorUIResource(value));
    }

    public static Border panelBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiPalette.BORDER, 1, true),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        );
    }

    public static void styleTabbedPane(JTabbedPane pane) {
        pane.setUI(new DeckTabbedPaneUI());
        pane.setBackground(UiPalette.WINDOW);
        pane.setForeground(UiPalette.TEXT);
        pane.setOpaque(false);
        pane.setFocusable(false);
    }

    public static void styleSplitPane(JSplitPane pane) {
        pane.setOpaque(false);
        pane.setBackground(UiPalette.WINDOW);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setDividerSize(10);
    }
}
