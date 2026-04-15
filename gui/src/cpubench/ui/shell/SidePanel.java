package cpubench.ui.shell;

import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

import cpubench.ui.DarkTheme;
import cpubench.ui.UiPalette;
import cpubench.ui.shell.ActivityBar.Activity;

public final class SidePanel extends JPanel {
    private final CardLayout cards = new CardLayout();
    private boolean collapsed;

    public SidePanel() {
        super();
        setLayout(cards);
        setBackground(UiPalette.PANEL);
        setBorder(BorderFactory.createCompoundBorder(DarkTheme.panelBorder(), BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        setPreferredSize(new Dimension(300, 0));
    }

    public void addPanel(Activity activity, JComponent component) {
        add(component, activity.name());
    }

    public void showPanel(Activity activity) {
        cards.show(this, activity.name());
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        setVisible(!collapsed);
    }

    public boolean isCollapsed() {
        return collapsed;
    }
}
