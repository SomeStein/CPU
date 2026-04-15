package cpubench.ui.icons;

import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

public final class LanguageListCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        String implementationId = String.valueOf(value);
        if (!implementationId.isBlank() && !"All".equals(implementationId)) {
            setIcon(LanguageIconRegistry.icon(implementationId, 16));
            setText(" " + LanguageIconRegistry.displayName(implementationId));
        }
        return this;
    }
}
