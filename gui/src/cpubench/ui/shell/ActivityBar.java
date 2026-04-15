package cpubench.ui.shell;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import cpubench.ui.IconFactory;
import cpubench.ui.UiPalette;

public final class ActivityBar extends JPanel {
    public enum Activity {
        RUNS,
        ANALYSIS,
        MONITOR,
        ARTIFACTS,
        CONFIG,
        DOCS,
    }

    private final Map<Activity, JToggleButton> buttons = new EnumMap<>(Activity.class);
    private final Consumer<Activity> callback;
    private Activity selected = Activity.RUNS;

    public ActivityBar(Consumer<Activity> callback) {
        super(new BorderLayout(0, UiPalette.GAP_MD));
        this.callback = callback;
        setBackground(UiPalette.PANEL_ALT);
        setBorder(BorderFactory.createEmptyBorder(UiPalette.GAP_LG, UiPalette.GAP_SM, UiPalette.GAP_LG, UiPalette.GAP_SM));
        setPreferredSize(new Dimension(56, 0));

        JPanel stack = new JPanel(new GridLayout(0, 1, 0, UiPalette.GAP_SM));
        stack.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        addButton(stack, group, Activity.RUNS, IconFactory.logsIcon(18, UiPalette.TEXT));
        addButton(stack, group, Activity.ANALYSIS, IconFactory.chartIcon(18, UiPalette.TEXT));
        addButton(stack, group, Activity.MONITOR, IconFactory.radarIcon(18, UiPalette.TEXT));
        addButton(stack, group, Activity.ARTIFACTS, IconFactory.docsIcon(18, UiPalette.TEXT));
        addButton(stack, group, Activity.CONFIG, IconFactory.refreshIcon(18, UiPalette.TEXT));
        addButton(stack, group, Activity.DOCS, IconFactory.docsIcon(18, UiPalette.ACCENT_ALT));
        add(stack, BorderLayout.NORTH);
    }

    public void setSelected(Activity activity) {
        selected = activity;
        JToggleButton button = buttons.get(activity);
        if (button != null) {
            button.setSelected(true);
        }
    }

    public Activity selected() {
        return selected;
    }

    private void addButton(JPanel parent, ButtonGroup group, Activity activity, Icon icon) {
        JToggleButton button = new JToggleButton(icon);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        button.setBackground(UiPalette.PANEL_ALT);
        button.setForeground(UiPalette.TEXT);
        button.setFocusPainted(false);
        button.setToolTipText(activity.name());
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.addActionListener(event -> {
            if (selected == activity && button.isSelected()) {
                callback.accept(activity);
                return;
            }
            selected = activity;
            callback.accept(activity);
        });
        group.add(button);
        buttons.put(activity, button);
        parent.add(button);
        if (activity == Activity.RUNS) {
            button.setSelected(true);
        }
    }
}
