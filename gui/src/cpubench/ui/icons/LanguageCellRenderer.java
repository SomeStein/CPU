package cpubench.ui.icons;

import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import cpubench.ui.UiPalette;

public final class LanguageCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table,
        Object value,
        boolean isSelected,
        boolean hasFocus,
        int row,
        int column
    ) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        String implementationId = String.valueOf(value);
        setIcon(LanguageIconRegistry.icon(implementationId, 16));
        setText("");
        setHorizontalAlignment(CENTER);
        setToolTipText(LanguageIconRegistry.displayName(implementationId));
        if (!isSelected) {
            setBackground(row % 2 == 0 ? UiPalette.SURFACE : UiPalette.SURFACE_ALT);
            setForeground(UiPalette.TEXT);
        } else {
            setBackground(UiPalette.ACCENT);
            setForeground(UiPalette.WINDOW);
        }
        return this;
    }
}
