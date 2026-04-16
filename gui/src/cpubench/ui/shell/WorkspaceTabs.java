package cpubench.ui.shell;

import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;

import cpubench.ui.DarkTheme;
import cpubench.ui.UiPalette;

public final class WorkspaceTabs extends JTabbedPane {
    public WorkspaceTabs() {
        DarkTheme.styleTabbedPane(this);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), "closeTab");
        getActionMap().put("closeTab", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                int index = getSelectedIndex();
                if (index >= 0 && isClosable(index)) {
                    removeTabAt(index);
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON2) {
                    return;
                }
                int index = indexAtLocation(event.getX(), event.getY());
                if (index >= 0 && isClosable(index)) {
                    removeTabAt(index);
                }
            }
        });
    }

    public void addClosableTab(String title, javax.swing.Icon icon, java.awt.Component component, boolean closable) {
        addTab(title, icon, component);
        int index = indexOfComponent(component);
        if (index >= 0) {
            setTabComponentAt(index, new TabHeader(this, title, closable));
            putClientProperty("tab.closable." + index, closable);
        }
    }

    private boolean isClosable(int index) {
        java.awt.Component tabComponent = getTabComponentAt(index);
        return !(tabComponent instanceof TabHeader header) || header.closable();
    }

    private static final class TabHeader extends JPanel {
        private final boolean closable;

        TabHeader(JTabbedPane tabs, String title, boolean closable) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            this.closable = closable;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            JLabel label = new JLabel(title);
            label.setForeground(UiPalette.TEXT);
            add(label);
            if (closable) {
                JButton close = new JButton("×");
                close.setFocusable(false);
                close.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
                close.setContentAreaFilled(false);
                close.setForeground(UiPalette.MUTED);
                close.addActionListener(event -> {
                    int index = tabs.indexOfTabComponent(this);
                    if (index >= 0) {
                        tabs.removeTabAt(index);
                    }
                });
                add(close);
            }
        }

        boolean closable() {
            return closable;
        }
    }
}
