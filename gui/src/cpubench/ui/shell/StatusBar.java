package cpubench.ui.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import cpubench.ui.DarkTheme;
import cpubench.ui.UiPalette;

public final class StatusBar extends JPanel {
    private final JComboBox<String> profileSelector = new JComboBox<>();
    private final JButton runButton = new JButton("Run");
    private final JButton stopButton = new JButton("Stop");
    private final JButton refreshButton = new JButton("Refresh");
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel lastEventLabel = new JLabel("Controller ready");
    private final JLabel healthDot = new JLabel("\u25CF");

    public StatusBar() {
        super(new BorderLayout(UiPalette.GAP_MD, 0));
        setBackground(UiPalette.PANEL_ALT);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UiPalette.BORDER),
            BorderFactory.createEmptyBorder(UiPalette.GAP_SM, UiPalette.GAP_MD, UiPalette.GAP_SM, UiPalette.GAP_MD)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, UiPalette.GAP_SM, 0));
        left.setOpaque(false);
        left.add(new JLabel("Profile"));
        profileSelector.setPreferredSize(new Dimension(180, 30));
        left.add(profileSelector);
        styleButton(runButton, UiPalette.ACCENT);
        styleButton(stopButton, UiPalette.SURFACE_ALT);
        styleButton(refreshButton, UiPalette.SURFACE);
        stopButton.setEnabled(false);
        left.add(runButton);
        left.add(stopButton);
        left.add(refreshButton);

        JPanel center = new JPanel(new BorderLayout(UiPalette.GAP_MD, 0));
        center.setOpaque(false);
        progressBar.setStringPainted(true);
        progressBar.setString("0 / 0");
        center.add(progressBar, BorderLayout.CENTER);
        center.add(lastEventLabel, BorderLayout.SOUTH);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiPalette.GAP_SM, 0));
        right.setOpaque(false);
        healthDot.setForeground(UiPalette.SUCCESS);
        right.add(new JLabel("Backend"));
        right.add(healthDot);

        add(left, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    public JComboBox<String> profileSelector() {
        return profileSelector;
    }

    public JButton runButton() {
        return runButton;
    }

    public JButton stopButton() {
        return stopButton;
    }

    public JButton refreshButton() {
        return refreshButton;
    }

    public JProgressBar progressBar() {
        return progressBar;
    }

    public void setLastEvent(String text) {
        lastEventLabel.setText(text);
    }

    public void setHealth(Color color) {
        healthDot.setForeground(color);
    }

    private static void styleButton(JButton button, Color background) {
        button.setBackground(background);
        button.setForeground(pickForeground(background));
        button.setFocusPainted(false);
        button.setBorder(DarkTheme.panelBorder());
    }

    private static Color pickForeground(Color background) {
        double luminance = (0.2126 * background.getRed() + 0.7152 * background.getGreen() + 0.0722 * background.getBlue()) / 255.0;
        return luminance > 0.55 ? UiPalette.WINDOW : UiPalette.TEXT;
    }
}
