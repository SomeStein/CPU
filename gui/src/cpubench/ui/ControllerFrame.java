package cpubench.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import cpubench.api.BackendClient;
import cpubench.model.ControllerState;
import cpubench.model.FilterState;
import cpubench.model.TableData;

public final class ControllerFrame {
    private static final List<String> LIVE_EVENT_HEADERS = List.of(
        "event_type",
        "phase",
        "run_id",
        "profile_id",
        "implementation",
        "case_id",
        "repeat_index",
        "warmup",
        "status",
        "metric",
        "metric_kind",
        "elapsed_ns",
        "timer_kind",
        "step_index",
        "step_total",
        "message"
    );

    private final BackendClient backend;
    private final ControllerState state;
    private final JFrame frame;
    private final GraphicsDevice graphicsDevice;
    private final JTabbedPane workspaceTabs;
    private final JComboBox<String> profileSelector;
    private final JButton runButton;
    private final JButton refreshButton;
    private final JButton docsButton;
    private final JLabel statusPill;
    private final JProgressBar progressBar;
    private final JTextArea profileArea;
    private final JTextArea runSummaryArea;
    private final JTextArea liveStatusArea;
    private final JTextArea globalMonitorArea;
    private final JTextArea detailArea;
    private final JTextArea rawLogArea;
    private final JTextArea manifestArea;
    private final JTextArea liveLogArea;
    private final JComboBox<String> runImplementationFilter;
    private final JComboBox<String> runCaseFilter;
    private final JComboBox<String> runStatusFilter;
    private final JCheckBox runMeasuredOnlyToggle;
    private final JComboBox<String> globalProfileFilter;
    private final JComboBox<String> globalImplementationFilter;
    private final JComboBox<String> globalCaseFilter;
    private final JComboBox<String> globalStatusFilter;
    private final JCheckBox globalMeasuredOnlyToggle;
    private final JTable runsTable;
    private final JTable resultsTable;
    private final JTable eventsTable;
    private final JTable globalResultsTable;
    private final DefaultTableModel runsModel;
    private final DefaultTableModel resultsModel;
    private final DefaultTableModel eventsModel;
    private final DefaultTableModel globalResultsModel;
    private final MetricLineChartPanel runLineChart;
    private final ImplementationBarChartPanel runBarChart;
    private final MetricLineChartPanel globalLineChart;
    private final ImplementationBarChartPanel globalBarChart;
    private final ProfileBuilderPanel profileBuilderPanel;

    private String pendingRunSelection = "";
    private Process activeProcess;
    private List<String> runEventHeaders = List.of();
    private final List<Map<String, String>> liveMetricRows = new ArrayList<>();
    private Rectangle windowedBounds;
    private boolean fullscreen;

    public ControllerFrame(BackendClient backend) {
        this.backend = backend;
        this.state = new ControllerState();
        this.frame = new JFrame("CPU Benchmark Launch Deck");
        this.graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        this.workspaceTabs = createTabbedPane();
        this.profileSelector = new JComboBox<>();
        this.runButton = createButton("Run Profile", IconFactory.playIcon(16, UiPalette.WINDOW));
        this.refreshButton = createButton("Refresh", IconFactory.refreshIcon(16, UiPalette.TEXT));
        this.docsButton = createButton("Docs", IconFactory.docsIcon(16, UiPalette.TEXT));
        this.statusPill = buildStatusPill();
        this.progressBar = new JProgressBar();
        this.profileArea = buildTextArea();
        this.runSummaryArea = buildTextArea();
        this.liveStatusArea = buildTextArea();
        this.globalMonitorArea = buildTextArea();
        this.detailArea = buildTextArea();
        this.rawLogArea = buildTextArea();
        this.manifestArea = buildTextArea();
        this.liveLogArea = buildTextArea();
        this.runImplementationFilter = new JComboBox<>(new String[] {"All"});
        this.runCaseFilter = new JComboBox<>(new String[] {"All"});
        this.runStatusFilter = new JComboBox<>(new String[] {"All", "success", "partial_failure", "failed"});
        this.runMeasuredOnlyToggle = new JCheckBox("Hide Warmups", true);
        this.globalProfileFilter = new JComboBox<>(new String[] {"All"});
        this.globalImplementationFilter = new JComboBox<>(new String[] {"All"});
        this.globalCaseFilter = new JComboBox<>(new String[] {"All"});
        this.globalStatusFilter = new JComboBox<>(new String[] {"All", "success", "partial_failure", "failed"});
        this.globalMeasuredOnlyToggle = new JCheckBox("Hide Warmups", true);
        this.runsModel = new DefaultTableModel();
        this.resultsModel = new DefaultTableModel();
        this.eventsModel = new DefaultTableModel();
        this.globalResultsModel = new DefaultTableModel();
        this.runsTable = new JTable(runsModel);
        this.resultsTable = new JTable(resultsModel);
        this.eventsTable = new JTable(eventsModel);
        this.globalResultsTable = new JTable(globalResultsModel);
        this.runLineChart = new MetricLineChartPanel();
        this.runBarChart = new ImplementationBarChartPanel();
        this.globalLineChart = new MetricLineChartPanel();
        this.globalBarChart = new ImplementationBarChartPanel();
        this.profileBuilderPanel = new ProfileBuilderPanel(backend, this::refreshAll, this::startRunProfileFile, this::selectProfile);
        buildUi();
    }

    public void showWindow() {
        refreshAll();
        fitToVisibleScreen();
        frame.setVisible(true);
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1220, 760));
        frame.getContentPane().setBackground(UiPalette.WINDOW);
        frame.setLayout(new BorderLayout(14, 14));
        frame.setJMenuBar(buildMenuBar());
        frame.setIconImage(IconFactory.appImage(256));

        HeroPanel header = new HeroPanel();
        header.setLayout(new BorderLayout(18, 0));
        header.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        header.add(buildHeroCopy(), BorderLayout.WEST);
        header.add(buildToolbar(), BorderLayout.EAST);
        frame.add(header, BorderLayout.NORTH);

        workspaceTabs.addTab("Run Browser", IconFactory.logsIcon(14, UiPalette.TEXT), buildRunBrowserView());
        workspaceTabs.addTab("Custom Run Builder", IconFactory.docsIcon(14, UiPalette.TEXT), profileBuilderPanel);
        workspaceTabs.addTab("Run Analysis", IconFactory.chartIcon(14, UiPalette.TEXT), buildRunAnalysisView());
        workspaceTabs.addTab("Live Monitor", IconFactory.radarIcon(14, UiPalette.TEXT), buildLiveMonitorView());
        workspaceTabs.addTab("Global Analysis", IconFactory.chartIcon(14, UiPalette.TEXT), buildGlobalAnalysisView());
        workspaceTabs.addTab("Artifacts", IconFactory.docsIcon(14, UiPalette.TEXT), buildArtifactsView());

        JSplitPane shell = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSidebar(), workspaceTabs);
        shell.setResizeWeight(0.22);
        DarkTheme.styleSplitPane(shell);
        shell.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
        frame.add(shell, BorderLayout.CENTER);

        runsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedRun();
            }
        });
        resultsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                inspectSelectedRunResult();
            }
        });
        globalResultsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                inspectSelectedGlobalResult();
            }
        });

        installWindowControls();
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                if (activeProcess != null) {
                    activeProcess.destroy();
                }
                if (fullscreen) {
                    graphicsDevice.setFullScreenWindow(null);
                }
            }
        });
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(menuItem("Refresh Data", IconFactory.refreshIcon(14, UiPalette.TEXT), event -> refreshAll()));
        file.add(menuItem("Toggle Fullscreen", IconFactory.chartIcon(14, UiPalette.TEXT), event -> toggleFullscreen()));
        file.add(menuItem("Exit Fullscreen", IconFactory.logsIcon(14, UiPalette.TEXT), event -> exitFullscreen()));
        file.add(menuItem("Exit", IconFactory.logsIcon(14, UiPalette.TEXT), event -> frame.dispose()));

        JMenu run = new JMenu("Run");
        run.add(menuItem("Run Selected Profile", IconFactory.playIcon(14, UiPalette.ACCENT), event -> startRun()));
        run.add(menuItem("Show Smoke Profile", IconFactory.chartIcon(14, UiPalette.TEXT), event -> selectProfile("smoke")));
        run.add(menuItem("Show Balanced Profile", IconFactory.chartIcon(14, UiPalette.TEXT), event -> selectProfile("balanced")));
        run.add(menuItem("Show Stress Profile", IconFactory.chartIcon(14, UiPalette.TEXT), event -> selectProfile("stress")));
        run.add(menuItem("Show Polyglot Smoke", IconFactory.chartIcon(14, UiPalette.TEXT), event -> selectProfile("polyglot_smoke")));
        run.add(menuItem("Open Custom Builder", IconFactory.docsIcon(14, UiPalette.TEXT), event -> workspaceTabs.setSelectedIndex(1)));

        JMenu help = new JMenu("Help");
        help.add(menuItem("Repository Overview", IconFactory.docsIcon(14, UiPalette.TEXT), event -> showDocument("README.md", Path.of("README.md"))));
        help.add(menuItem("Architecture", IconFactory.chartIcon(14, UiPalette.TEXT), event -> showDocument("Architecture", Path.of("docs", "ARCHITECTURE.md"))));
        help.add(menuItem("Timing Isolation", IconFactory.radarIcon(14, UiPalette.TEXT), event -> showDocument("Timing Isolation", Path.of("docs", "TIMING_ISOLATION.md"))));
        help.add(menuItem("Agent Hints", IconFactory.docsIcon(14, UiPalette.TEXT), event -> showDocument("AGENTS.md", Path.of("AGENTS.md"))));

        bar.add(file);
        bar.add(run);
        bar.add(help);
        return bar;
    }

    private JMenuItem menuItem(String title, javax.swing.Icon icon, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(title);
        item.setIcon(icon);
        item.addActionListener(action);
        return item;
    }

    private Component buildHeroCopy() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel("Benchmark Launch Deck");
        title.setForeground(UiPalette.TEXT);
        title.setFont(UiPalette.DISPLAY);
        JLabel subtitle = new JLabel("Fullscreen-ready command center for runs, global analysis, live telemetry, and artifacts.");
        subtitle.setForeground(UiPalette.MUTED);
        subtitle.setFont(UiPalette.SUBTITLE);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.CENTER);
        return panel;
    }

    private Component buildToolbar() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 8));
        panel.setOpaque(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        profileSelector.setPreferredSize(new Dimension(180, 34));
        actions.add(label("Profile"));
        actions.add(profileSelector);
        actions.add(runButton);
        actions.add(refreshButton);
        actions.add(docsButton);

        JPanel statusRow = new JPanel(new BorderLayout(10, 0));
        statusRow.setOpaque(false);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(360, 16));
        progressBar.setValue(0);
        progressBar.setString("F11 fullscreen • Esc exit");
        statusRow.add(progressBar, BorderLayout.CENTER);
        statusRow.add(statusPill, BorderLayout.EAST);

        panel.add(actions);
        panel.add(statusRow);

        runButton.addActionListener(event -> startRun());
        refreshButton.addActionListener(event -> refreshAll());
        docsButton.addActionListener(event -> showDocument("README.md", Path.of("README.md")));
        profileSelector.addActionListener(event -> {
            String profileId = (String) profileSelector.getSelectedItem();
            if (profileId != null && !Objects.equals(profileId, state.currentProfileId())) {
                state.setCurrentProfileId(profileId);
                loadProfilePreview(profileId);
            }
        });
        return panel;
    }

    private Component buildSidebar() {
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.add(textSection("Profile Blueprint", profileArea, new Dimension(320, 220)));
        stack.add(javax.swing.Box.createVerticalStrut(12));
        stack.add(textSection("Selected Run", runSummaryArea, new Dimension(320, 220)));
        stack.add(javax.swing.Box.createVerticalStrut(12));
        stack.add(buildRunFilterSection());
        stack.add(javax.swing.Box.createVerticalStrut(12));
        stack.add(buildGlobalFilterSection());

        JScrollPane scrollPane = new JScrollPane(stack);
        scrollPane.getViewport().setBackground(UiPalette.WINDOW);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private Component buildRunFilterSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(DarkTheme.panelBorder());
        JLabel title = label("Run Filters");
        title.setFont(UiPalette.LABEL.deriveFont(13f));
        panel.add(title, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridLayout(4, 2, 8, 8));
        controls.setOpaque(false);
        controls.add(label("Implementation"));
        controls.add(runImplementationFilter);
        controls.add(label("Case"));
        controls.add(runCaseFilter);
        controls.add(label("Status"));
        controls.add(runStatusFilter);
        controls.add(new JLabel());
        controls.add(runMeasuredOnlyToggle);
        panel.add(controls, BorderLayout.CENTER);

        runImplementationFilter.addActionListener(event -> applyRunFilters());
        runCaseFilter.addActionListener(event -> applyRunFilters());
        runStatusFilter.addActionListener(event -> applyRunFilters());
        runMeasuredOnlyToggle.addActionListener(event -> applyRunFilters());
        return panel;
    }

    private Component buildGlobalFilterSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(DarkTheme.panelBorder());
        JLabel title = label("Global Filters");
        title.setFont(UiPalette.LABEL.deriveFont(13f));
        panel.add(title, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridLayout(5, 2, 8, 8));
        controls.setOpaque(false);
        controls.add(label("Profile"));
        controls.add(globalProfileFilter);
        controls.add(label("Implementation"));
        controls.add(globalImplementationFilter);
        controls.add(label("Case"));
        controls.add(globalCaseFilter);
        controls.add(label("Status"));
        controls.add(globalStatusFilter);
        controls.add(new JLabel());
        controls.add(globalMeasuredOnlyToggle);
        panel.add(controls, BorderLayout.CENTER);

        globalProfileFilter.addActionListener(event -> applyGlobalFilters());
        globalImplementationFilter.addActionListener(event -> applyGlobalFilters());
        globalCaseFilter.addActionListener(event -> applyGlobalFilters());
        globalStatusFilter.addActionListener(event -> applyGlobalFilters());
        globalMeasuredOnlyToggle.addActionListener(event -> applyGlobalFilters());
        return panel;
    }

    private Component buildRunBrowserView() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.add(tableScroll(runsTable), BorderLayout.CENTER);
        return panel;
    }

    private Component buildRunAnalysisView() {
        JTabbedPane chartTabs = createTabbedPane();
        chartTabs.addTab("Metric Trend", IconFactory.chartIcon(14, UiPalette.TEXT), runLineChart);
        chartTabs.addTab("Implementation Ladder", IconFactory.chartIcon(14, UiPalette.TEXT), runBarChart);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartTabs, tableScroll(resultsTable));
        split.setResizeWeight(0.58);
        DarkTheme.styleSplitPane(split);
        return split;
    }

    private Component buildLiveMonitorView() {
        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll(eventsTable), textScroll(liveLogArea));
        bottom.setResizeWeight(0.58);
        DarkTheme.styleSplitPane(bottom);

        JSplitPane shell = new JSplitPane(JSplitPane.VERTICAL_SPLIT, textScroll(liveStatusArea), bottom);
        shell.setResizeWeight(0.24);
        DarkTheme.styleSplitPane(shell);
        return shell;
    }

    private Component buildGlobalAnalysisView() {
        JTabbedPane globalCharts = createTabbedPane();
        globalCharts.addTab("Cross-Run Trend", IconFactory.chartIcon(14, UiPalette.TEXT), globalLineChart);
        globalCharts.addTab("Implementation Scoreboard", IconFactory.chartIcon(14, UiPalette.TEXT), globalBarChart);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(textSection("Global Monitor", globalMonitorArea, null), BorderLayout.NORTH);
        top.add(globalCharts, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, tableScroll(globalResultsTable));
        split.setResizeWeight(0.58);
        DarkTheme.styleSplitPane(split);
        return split;
    }

    private Component buildArtifactsView() {
        JTabbedPane inspectorTabs = createTabbedPane();
        inspectorTabs.addTab("Inspector", IconFactory.docsIcon(14, UiPalette.TEXT), textScroll(detailArea));
        inspectorTabs.addTab("Raw Log", IconFactory.logsIcon(14, UiPalette.TEXT), textScroll(rawLogArea));
        inspectorTabs.addTab("Manifest", IconFactory.docsIcon(14, UiPalette.TEXT), textScroll(manifestArea));
        inspectorTabs.addTab("Live Feed", IconFactory.radarIcon(14, UiPalette.TEXT), textScroll(liveLogArea));
        return inspectorTabs;
    }

    private JTabbedPane createTabbedPane() {
        JTabbedPane pane = new JTabbedPane();
        DarkTheme.styleTabbedPane(pane);
        return pane;
    }

    private JScrollPane tableScroll(JTable table) {
        configureTable(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(UiPalette.SURFACE);
        scrollPane.setBorder(DarkTheme.panelBorder());
        return scrollPane;
    }

    private JScrollPane textScroll(JTextArea area) {
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.getViewport().setBackground(UiPalette.SURFACE);
        scrollPane.setBorder(DarkTheme.panelBorder());
        return scrollPane;
    }

    private JPanel textSection(String titleText, JTextArea area, Dimension size) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.getViewport().setBackground(UiPalette.SURFACE);
        if (size != null) {
            scroll.setPreferredSize(size);
        }
        scroll.setBorder(BorderFactory.createLineBorder(UiPalette.BORDER, 1, true));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(DarkTheme.panelBorder());
        JLabel title = label(titleText);
        title.setFont(UiPalette.LABEL.deriveFont(13f));
        panel.add(title, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JButton createButton(String text, javax.swing.Icon icon) {
        JButton button = new JButton(text, icon);
        button.setFocusPainted(false);
        button.setMargin(new Insets(8, 14, 8, 14));
        if ("Run Profile".equals(text)) {
            button.setBackground(UiPalette.ACCENT);
            button.setForeground(UiPalette.WINDOW);
        } else {
            button.setBackground(UiPalette.SURFACE);
            button.setForeground(UiPalette.TEXT);
        }
        return button;
    }

    private JLabel buildStatusPill() {
        JLabel label = new JLabel("Ready", SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(UiPalette.SURFACE_ALT);
        label.setForeground(UiPalette.TEXT);
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiPalette.BORDER, 1, true),
            BorderFactory.createEmptyBorder(7, 12, 7, 12)
        ));
        return label;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiPalette.MUTED);
        label.setFont(UiPalette.LABEL);
        return label;
    }

    private JTextArea buildTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(UiPalette.SURFACE);
        area.setForeground(UiPalette.TEXT);
        area.setCaretColor(UiPalette.ACCENT);
        area.setFont(UiPalette.MONO);
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return area;
    }

    private void configureTable(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setSelectionBackground(UiPalette.ACCENT);
        table.setSelectionForeground(UiPalette.WINDOW);
        table.setBackground(UiPalette.SURFACE);
        table.setForeground(UiPalette.TEXT);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                JTable localTable,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
            ) {
                Component component = super.getTableCellRendererComponent(localTable, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                if (isSelected) {
                    component.setBackground(UiPalette.ACCENT);
                    component.setForeground(UiPalette.WINDOW);
                    return component;
                }
                component.setBackground(row % 2 == 0 ? UiPalette.SURFACE : UiPalette.SURFACE_ALT);
                component.setForeground(UiPalette.TEXT);
                String columnName = localTable.getColumnName(column);
                if ("status".equals(columnName)) {
                    component.setForeground(UiPalette.statusColor(String.valueOf(value)));
                }
                return component;
            }
        });
    }

    private void installWindowControls() {
        JRootPane root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "toggleFullscreen");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitFullscreen");
        root.getActionMap().put("toggleFullscreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                toggleFullscreen();
            }
        });
        root.getActionMap().put("exitFullscreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                exitFullscreen();
            }
        });
    }

    private void fitToVisibleScreen() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        windowedBounds = new Rectangle(bounds);
        frame.setBounds(bounds);
        frame.validate();
    }

    private void toggleFullscreen() {
        if (fullscreen) {
            exitFullscreen();
            return;
        }
        windowedBounds = frame.getBounds();
        frame.dispose();
        frame.setUndecorated(true);
        frame.setVisible(true);
        graphicsDevice.setFullScreenWindow(frame);
        fullscreen = true;
    }

    private void exitFullscreen() {
        if (!fullscreen) {
            fitToVisibleScreen();
            return;
        }
        graphicsDevice.setFullScreenWindow(null);
        frame.dispose();
        frame.setUndecorated(false);
        frame.setVisible(true);
        fullscreen = false;
        if (windowedBounds != null) {
            frame.setBounds(windowedBounds);
        } else {
            fitToVisibleScreen();
        }
    }

    private void refreshAll() {
        setStatus("Refreshing data", UiPalette.INFO);
        runButton.setEnabled(false);
        refreshButton.setEnabled(false);
        docsButton.setEnabled(false);

        new SwingWorker<Void, Void>() {
            private TableData loadedProfiles = TableData.empty();
            private TableData loadedCatalog = TableData.empty();
            private TableData loadedRuns = TableData.empty();
            private TableData loadedGlobalResults = TableData.empty();

            @Override
            protected Void doInBackground() throws Exception {
                loadedProfiles = backend.readTable("profiles");
                loadedCatalog = backend.readTable("implementation-catalog");
                loadedRuns = backend.readTable("runs");
                loadedGlobalResults = backend.readTable("global-results");
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    state.setProfiles(loadedProfiles);
                    state.setImplementationCatalog(loadedCatalog);
                    state.setRuns(loadedRuns);
                    state.setGlobalResults(loadedGlobalResults);
                    populateProfileSelector();
                    profileBuilderPanel.reloadLookups(state.profiles(), state.implementationCatalog());
                    applyTable(runsModel, state.runs());
                    populateGlobalFilters();
                    applyGlobalFilters();
                    if (!pendingRunSelection.isBlank()) {
                        selectRunRow(pendingRunSelection);
                        pendingRunSelection = "";
                    } else if (!state.currentRunId().isBlank()) {
                        selectRunRow(state.currentRunId());
                    } else if (runsModel.getRowCount() > 0) {
                        runsTable.setRowSelectionInterval(0, 0);
                        loadSelectedRun();
                    } else {
                        clearRunWorkspace();
                        setStatus("Deck ready", UiPalette.SUCCESS);
                    }
                } catch (Exception error) {
                    setStatus("Refresh failed", UiPalette.DANGER);
                    liveLogArea.append("[refresh-error] " + error.getMessage() + "\n");
                } finally {
                    runButton.setEnabled(profileSelector.getItemCount() > 0);
                    refreshButton.setEnabled(true);
                    docsButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void clearRunWorkspace() {
        state.setRunResults(TableData.empty());
        state.setRunEvents(TableData.empty());
        state.setRunManifest(TableData.empty());
        applyTable(resultsModel, TableData.empty());
        applyTable(eventsModel, TableData.empty());
        manifestArea.setText("");
        detailArea.setText("Select a run to inspect stored measurements.");
        rawLogArea.setText("");
        runSummaryArea.setText("No run selected.");
        liveStatusArea.setText("No run selected.\nStart a profile or load a stored run.");
        runLineChart.setSeries("Metric Trend", "Select a run to render repeat-level metrics.", "ns/iter", "Repeat", List.of(), List.of());
        runBarChart.setBars("Best By Implementation", "Select a run to compare implementations.", "ns/iter", List.of());
    }

    private void populateProfileSelector() {
        String currentSelection = (String) profileSelector.getSelectedItem();
        profileSelector.removeAllItems();
        for (Map<String, String> row : state.profiles().asMaps()) {
            profileSelector.addItem(row.getOrDefault("profile_id", ""));
        }
        if (currentSelection != null) {
            profileSelector.setSelectedItem(currentSelection);
        }
        if (profileSelector.getSelectedItem() == null && profileSelector.getItemCount() > 0) {
            profileSelector.setSelectedIndex(0);
        }
        state.setCurrentProfileId((String) profileSelector.getSelectedItem());
        if (state.currentProfileId() != null && !state.currentProfileId().isBlank()) {
            loadProfilePreview(state.currentProfileId());
        }
    }

    private void selectProfile(String profileId) {
        profileSelector.setSelectedItem(profileId);
        state.setCurrentProfileId(profileId);
        loadProfilePreview(profileId);
    }

    private void loadProfilePreview(String profileId) {
        new SwingWorker<TableData, Void>() {
            @Override
            protected TableData doInBackground() throws Exception {
                return backend.readTable("profile", profileId);
            }

            @Override
            protected void done() {
                try {
                    TableData profileData = get();
                    StringBuilder builder = new StringBuilder();
                    for (Map<String, String> row : profileData.asMaps()) {
                        builder.append(row.getOrDefault("case_id", "")).append('\n');
                        builder.append("  iterations: ").append(row.getOrDefault("iterations", "")).append('\n');
                        builder.append("  chains: ").append(row.getOrDefault("parallel_chains", "")).append('\n');
                        builder.append("  warmups/repeats: ")
                            .append(row.getOrDefault("warmups", ""))
                            .append('/')
                            .append(row.getOrDefault("repeats", ""))
                            .append('\n');
                        builder.append("  scheduler: ")
                            .append(row.getOrDefault("priority_mode", ""))
                            .append(" / ")
                            .append(row.getOrDefault("affinity_mode", ""))
                            .append('\n');
                        builder.append("  implementations: ").append(row.getOrDefault("implementations", "")).append('\n').append('\n');
                    }
                    profileArea.setText(builder.toString().stripTrailing());
                } catch (Exception error) {
                    profileArea.setText("Unable to load profile details.\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void loadSelectedRun() {
        int row = runsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = runsTable.convertRowIndexToModel(row);
        String runId = String.valueOf(runsModel.getValueAt(modelRow, runsModel.findColumn("run_id")));
        if (runId.isBlank()) {
            return;
        }
        state.setCurrentRunId(runId);
        loadRun(runId);
    }

    private void loadRun(String runId) {
        setStatus("Loading " + runId, UiPalette.INFO);
        new SwingWorker<Void, Void>() {
            private TableData loadedResults = TableData.empty();
            private TableData loadedEvents = TableData.empty();
            private TableData loadedManifest = TableData.empty();

            @Override
            protected Void doInBackground() throws Exception {
                loadedResults = backend.readTable("results", runId);
                loadedEvents = backend.readTable("events", runId);
                loadedManifest = backend.readTable("manifest", runId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    state.setRunResults(loadedResults);
                    state.setRunEvents(loadedEvents);
                    state.setRunManifest(loadedManifest);
                    applyTable(eventsModel, state.runEvents());
                    manifestArea.setText(formatKeyValueTable(state.runManifest()));
                    populateRunFilters();
                    applyRunFilters();
                    renderSelectedRunSummary();
                    renderLiveMonitorSnapshot(null);
                    workspaceTabs.setSelectedIndex(2);
                    setStatus("Loaded " + runId, UiPalette.SUCCESS);
                } catch (Exception error) {
                    setStatus("Run load failed", UiPalette.DANGER);
                    detailArea.setText("Could not load run " + runId + "\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void populateRunFilters() {
        repopulateCombo(runImplementationFilter, state.runResults().distinctValues("implementation"));
        repopulateCombo(runCaseFilter, state.runResults().distinctValues("case_id"));
    }

    private void populateGlobalFilters() {
        repopulateCombo(globalProfileFilter, state.globalResults().distinctValues("profile_id"));
        repopulateCombo(globalImplementationFilter, state.globalResults().distinctValues("implementation"));
        repopulateCombo(globalCaseFilter, state.globalResults().distinctValues("case_id"));
    }

    private void repopulateCombo(JComboBox<String> combo, Set<String> values) {
        String previous = (String) combo.getSelectedItem();
        combo.removeAllItems();
        combo.addItem("All");
        for (String value : values) {
            combo.addItem(value);
        }
        if (previous != null) {
            combo.setSelectedItem(previous);
        }
        if (combo.getSelectedItem() == null) {
            combo.setSelectedIndex(0);
        }
    }

    private void applyRunFilters() {
        state.setRunFilter(currentRunFilter());
        TableData filtered = state.filteredRunResults();
        applyTable(resultsModel, filtered);
        updateRunCharts(filtered.asMaps());
        renderSelectedRunSummary();
        renderLiveMonitorSnapshot(null);
        if (resultsModel.getRowCount() > 0) {
            resultsTable.setRowSelectionInterval(0, 0);
            inspectSelectedRunResult();
        } else {
            detailArea.setText("No run results match the current filters.");
            rawLogArea.setText("");
        }
    }

    private void applyGlobalFilters() {
        state.setGlobalFilter(currentGlobalFilter());
        TableData filtered = state.filteredGlobalResults();
        applyTable(globalResultsModel, filtered);
        updateGlobalCharts(filtered.asMaps());
        renderGlobalSummary(filtered.asMaps());
    }

    private FilterState currentRunFilter() {
        return new FilterState(
            (String) runImplementationFilter.getSelectedItem(),
            (String) runCaseFilter.getSelectedItem(),
            (String) runStatusFilter.getSelectedItem(),
            "All",
            runMeasuredOnlyToggle.isSelected()
        );
    }

    private FilterState currentGlobalFilter() {
        return new FilterState(
            (String) globalImplementationFilter.getSelectedItem(),
            (String) globalCaseFilter.getSelectedItem(),
            (String) globalStatusFilter.getSelectedItem(),
            (String) globalProfileFilter.getSelectedItem(),
            globalMeasuredOnlyToggle.isSelected()
        );
    }

    private void renderSelectedRunSummary() {
        StringBuilder builder = new StringBuilder();
        if (state.currentRunId().isBlank()) {
            runSummaryArea.setText("No run selected.");
            return;
        }

        Map<String, String> run = findRow(state.runs().asMaps(), "run_id", state.currentRunId());
        TableData filteredData = state.filteredRunResults();
        List<Map<String, String>> filtered = filteredData.asMaps();
        builder.append("Run: ").append(state.currentRunId()).append('\n');
        if (!run.isEmpty()) {
            builder.append("Profile: ").append(run.getOrDefault("profile_name", run.getOrDefault("profile_id", ""))).append('\n');
            builder.append("Host: ").append(run.getOrDefault("host_os", "")).append(" / ").append(run.getOrDefault("host_arch", "")).append('\n');
            builder.append("Status: ").append(run.getOrDefault("status", "")).append('\n');
            builder.append("Started: ").append(run.getOrDefault("started_at", "")).append('\n');
        }
        builder.append("Visible samples: ").append(filtered.size()).append('\n');

        MetricSample best = bestMetric(filtered);
        if (best != null) {
            builder.append("Best visible: ").append(String.format("%.6f", best.value())).append(' ').append(best.kind()).append('\n');
            builder.append("Fastest sample: ").append(best.row().getOrDefault("implementation", "")).append(" / ")
                .append(best.row().getOrDefault("case_id", "")).append('\n');
        }

        builder.append("Filters: ")
            .append(runImplementationFilter.getSelectedItem()).append(" / ")
            .append(runCaseFilter.getSelectedItem()).append(" / ")
            .append(runStatusFilter.getSelectedItem()).append('\n');
        builder.append("Visible implementations: ").append(joinDistinct(filtered, "implementation")).append('\n');
        builder.append("Visible cases: ").append(joinDistinct(filtered, "case_id")).append('\n');
        builder.append("Timers: ").append(joinDistinct(filtered, "timer_kind")).append('\n');
        runSummaryArea.setText(builder.toString());
    }

    private void renderLiveMonitorSnapshot(Map<String, String> liveEvent) {
        if (liveEvent != null) {
            StringBuilder builder = new StringBuilder();
            int stepIndex = parseInt(liveEvent.get("step_index"));
            int stepTotal = Math.max(1, parseInt(liveEvent.get("step_total")));
            builder.append("Active profile: ").append(liveEvent.getOrDefault("profile_id", state.currentProfileId())).append('\n');
            builder.append("Progress: ").append(stepIndex).append(" / ").append(stepTotal).append('\n');
            builder.append("Phase: ").append(liveEvent.getOrDefault("phase", "")).append('\n');
            if (!liveEvent.getOrDefault("implementation", "").isBlank()) {
                builder.append("Implementation: ").append(liveEvent.get("implementation")).append('\n');
            }
            if (!liveEvent.getOrDefault("case_id", "").isBlank()) {
                builder.append("Case: ").append(liveEvent.get("case_id")).append('\n');
            }
            if (!liveEvent.getOrDefault("timer_kind", "").isBlank()) {
                builder.append("Timer: ").append(liveEvent.get("timer_kind")).append('\n');
            }
            if (!liveEvent.getOrDefault("metric", "").isBlank()) {
                builder.append("Latest metric: ").append(liveEvent.get("metric")).append(' ').append(liveEvent.getOrDefault("metric_kind", "")).append('\n');
            }
            builder.append("Event stream: phase-boundary only").append('\n');
            liveStatusArea.setText(builder.toString());
            return;
        }

        if (state.currentRunId().isBlank()) {
            liveStatusArea.setText("No run selected.\nStart a profile or pick a stored run.");
            return;
        }

        List<Map<String, String>> events = state.runEvents().asMaps();
        StringBuilder builder = new StringBuilder();
        builder.append("Loaded run: ").append(state.currentRunId()).append('\n');
        builder.append("Recorded events: ").append(events.size()).append('\n');
        builder.append("Finished samples: ").append(countBy(events, "phase", "finished")).append('\n');
        builder.append("Launch events: ").append(countBy(events, "phase", "launch")).append('\n');
        if (!events.isEmpty()) {
            Map<String, String> last = events.get(events.size() - 1);
            builder.append("Last phase: ").append(last.getOrDefault("phase", "")).append('\n');
            if (!last.getOrDefault("message", "").isBlank()) {
                builder.append("Last message: ").append(last.get("message")).append('\n');
            }
        }
        builder.append("Mode: controller-side monitoring only\n");
        builder.append("No measured-loop UI callbacks or logging\n");
        liveStatusArea.setText(builder.toString());
    }

    private void renderGlobalSummary(List<Map<String, String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("Tracked runs: ").append(state.runs().rows().size()).append('\n');
        builder.append("Visible samples: ").append(rows.size()).append('\n');
        builder.append("Visible profiles: ").append(joinDistinct(rows, "profile_id")).append('\n');
        builder.append("Visible implementations: ").append(joinDistinct(rows, "implementation")).append('\n');
        builder.append("Visible cases: ").append(joinDistinct(rows, "case_id")).append('\n');

        MetricSample best = bestMetric(rows);
        if (best != null) {
            builder.append("Fastest visible sample: ").append(String.format("%.6f", best.value())).append(' ').append(best.kind()).append('\n');
            builder.append("Location: ").append(best.row().getOrDefault("run_id", "")).append(" / ")
                .append(best.row().getOrDefault("implementation", "")).append(" / ")
                .append(best.row().getOrDefault("case_id", "")).append('\n');
        }

        Map<String, String> latest = firstNonBlank(state.runs().asMaps());
        if (!latest.isEmpty()) {
            builder.append("Latest tracked run: ").append(latest.getOrDefault("run_id", "")).append('\n');
        }
        builder.append("Scope: cross-run stored metrics only\n");
        globalMonitorArea.setText(builder.toString());
    }

    private void updateRunCharts(List<Map<String, String>> rows) {
        Map<String, List<Double>> seriesMap = new LinkedHashMap<>();
        Map<String, Double> barValues = new LinkedHashMap<>();
        String unit = "ns/iter";
        int longest = 0;

        for (Map<String, String> row : rows) {
            Double metric = metric(row);
            if (metric == null) {
                continue;
            }
            unit = metricKind(row);
            String key = row.getOrDefault("implementation", "") + " · " + row.getOrDefault("case_id", "");
            List<Double> values = seriesMap.computeIfAbsent(key, ignored -> new ArrayList<>());
            values.add(metric);
            longest = Math.max(longest, values.size());
            barValues.merge(row.getOrDefault("implementation", ""), metric, Math::min);
        }

        List<MetricLineChartPanel.Series> series = new ArrayList<>();
        int colorIndex = 0;
        for (Map.Entry<String, List<Double>> entry : seriesMap.entrySet()) {
            series.add(new MetricLineChartPanel.Series(entry.getKey(), UiPalette.seriesColor(colorIndex), entry.getValue()));
            colorIndex += 1;
        }

        List<String> xLabels = new ArrayList<>();
        for (int index = 1; index <= Math.max(1, longest); index += 1) {
            xLabels.add("r" + index);
        }
        runLineChart.setSeries(
            state.currentRunId().isBlank() ? "Run Metric Trend" : "Run Metric Trend · " + state.currentRunId(),
            rows.isEmpty() ? "No stored result rows match the current run filter." : "Repeat-level metrics from the loaded run only.",
            unit,
            "Repeat",
            xLabels,
            series
        );

        List<ImplementationBarChartPanel.Bar> bars = new ArrayList<>();
        colorIndex = 0;
        for (Map.Entry<String, Double> entry : sortDoubleEntries(barValues)) {
            bars.add(new ImplementationBarChartPanel.Bar(entry.getKey(), UiPalette.seriesColor(colorIndex), entry.getValue()));
            colorIndex += 1;
        }
        runBarChart.setBars("Run Best By Implementation", "Fastest visible metric for the loaded run.", unit, bars);
    }

    private void updateGlobalCharts(List<Map<String, String>> rows) {
        Map<String, Double> bestByRun = new LinkedHashMap<>();
        Map<String, List<Double>> implementationMetrics = new LinkedHashMap<>();
        String unit = "ns/iter";

        for (Map<String, String> row : rows) {
            Double metric = metric(row);
            if (metric == null) {
                continue;
            }
            unit = metricKind(row);
            bestByRun.merge(row.getOrDefault("run_id", ""), metric, Math::min);
            implementationMetrics.computeIfAbsent(row.getOrDefault("implementation", ""), ignored -> new ArrayList<>()).add(metric);
        }

        List<Map<String, String>> orderedRuns = new ArrayList<>(state.runs().asMaps());
        orderedRuns.sort(Comparator.comparingInt(row -> parseInt(row.get("run_number"))));
        List<Double> runTrend = new ArrayList<>();
        List<String> runLabels = new ArrayList<>();
        for (Map<String, String> run : orderedRuns) {
            String runId = run.getOrDefault("run_id", "");
            if (!bestByRun.containsKey(runId)) {
                continue;
            }
            runTrend.add(bestByRun.get(runId));
            runLabels.add(shortRunLabel(run));
        }

        List<MetricLineChartPanel.Series> series = runTrend.isEmpty()
            ? List.of()
            : List.of(new MetricLineChartPanel.Series("Visible run best", UiPalette.ACCENT, runTrend));
        globalLineChart.setSeries(
            "Cross-Run Best Metric",
            rows.isEmpty() ? "No stored metrics match the current global filter." : "One point per visible run using the fastest stored sample.",
            unit,
            "Run",
            runLabels,
            series
        );

        List<ImplementationBarChartPanel.Bar> bars = new ArrayList<>();
        int colorIndex = 0;
        List<Map.Entry<String, Double>> averages = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : implementationMetrics.entrySet()) {
            double sum = 0.0;
            for (double value : entry.getValue()) {
                sum += value;
            }
            averages.add(Map.entry(entry.getKey(), sum / Math.max(1, entry.getValue().size())));
        }
        averages.sort(Map.Entry.comparingByValue());
        for (Map.Entry<String, Double> entry : averages) {
            bars.add(new ImplementationBarChartPanel.Bar(entry.getKey(), UiPalette.seriesColor(colorIndex), entry.getValue()));
            colorIndex += 1;
        }
        globalBarChart.setBars("Cross-Run Average By Implementation", "Average visible metric across the current global filter.", unit, bars);
    }

    private void inspectSelectedRunResult() {
        int row = resultsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = resultsTable.convertRowIndexToModel(row);
        inspectResult(state.filteredRunResults().rowAsMap(modelRow));
    }

    private void inspectSelectedGlobalResult() {
        int row = globalResultsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = globalResultsTable.convertRowIndexToModel(row);
        inspectResult(state.filteredGlobalResults().rowAsMap(modelRow));
    }

    private void inspectResult(Map<String, String> values) {
        if (values.isEmpty()) {
            return;
        }
        detailArea.setText(formatDetail(values));
        loadManifestForRun(values.getOrDefault("run_id", ""));

        String rawFile = values.getOrDefault("raw_file", values.getOrDefault("log_file", ""));
        if (rawFile.isBlank()) {
            rawLogArea.setText("No raw log recorded for this row.");
            return;
        }

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return backend.readText("raw", rawFile);
            }

            @Override
            protected void done() {
                try {
                    rawLogArea.setText(get());
                    rawLogArea.setCaretPosition(0);
                } catch (Exception error) {
                    rawLogArea.setText("Unable to load raw log.\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void loadManifestForRun(String runId) {
        if (runId.isBlank()) {
            return;
        }
        if (Objects.equals(runId, state.currentRunId()) && !state.runManifest().rows().isEmpty()) {
            manifestArea.setText(formatKeyValueTable(state.runManifest()));
            return;
        }
        new SwingWorker<TableData, Void>() {
            @Override
            protected TableData doInBackground() throws Exception {
                return backend.readTable("manifest", runId);
            }

            @Override
            protected void done() {
                try {
                    manifestArea.setText(formatKeyValueTable(get()));
                } catch (Exception error) {
                    manifestArea.setText("Unable to load manifest for " + runId + "\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void startRun() {
        String profileId = (String) profileSelector.getSelectedItem();
        if (profileId == null || profileId.isBlank()) {
            return;
        }
        startRunProfile(profileId);
    }

    private void startRunProfile(String profileId) {
        launchRun(profileId, () -> backend.startRunProfile(profileId));
    }

    private void startRunProfileFile(Path profilePath) {
        String profileLabel = profilePath.getFileName().toString().replace(".testrun.json", "");
        launchRun(profileLabel, () -> backend.startRunProfileFile(profilePath));
    }

    private void launchRun(String profileLabel, ProcessStarter starter) {
        runButton.setEnabled(false);
        refreshButton.setEnabled(false);
        docsButton.setEnabled(false);
        workspaceTabs.setSelectedIndex(3);
        setStatus("Running " + profileLabel, UiPalette.INFO);
        progressBar.setValue(0);
        progressBar.setMaximum(100);
        progressBar.setString("0 / 0");
        liveLogArea.setText("=== Launching " + profileLabel + " ===\n");
        liveStatusArea.setText("Waiting for controller events…\n");
        runEventHeaders = List.of();
        liveMetricRows.clear();
        state.setRunEvents(TableData.empty());
        applyTable(eventsModel, TableData.empty());
        runLineChart.setSeries("Live Metric Trend", "Finished samples will appear here as the profile runs.", "ns/iter", "Repeat", List.of(), List.of());
        runBarChart.setBars("Live Best By Implementation", "Controller-side view during the active run.", "ns/iter", List.of());

        new SwingWorker<Integer, Map<String, String>>() {
            @Override
            protected Integer doInBackground() throws Exception {
                activeProcess = starter.start();
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(activeProcess.getInputStream(), StandardCharsets.UTF_8)
                )) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        List<String> columns = BackendClient.splitTsv(line);
                        if (!columns.isEmpty() && "event_type".equals(columns.get(0))) {
                            runEventHeaders = columns;
                            continue;
                        }
                        if (!runEventHeaders.isEmpty()) {
                            publish(tsvToMap(runEventHeaders, columns));
                        }
                    }
                }
                return activeProcess.waitFor();
            }

            @Override
            protected void process(List<Map<String, String>> chunks) {
                for (Map<String, String> event : chunks) {
                    handleLiveEvent(event);
                }
            }

            @Override
            protected void done() {
                activeProcess = null;
                int exitCode = 1;
                try {
                    exitCode = get();
                } catch (Exception error) {
                    liveLogArea.append("[run-error] " + error.getMessage() + "\n");
                } finally {
                    runButton.setEnabled(true);
                    refreshButton.setEnabled(true);
                    docsButton.setEnabled(true);
                    if (!pendingRunSelection.isBlank()) {
                        refreshAll();
                    } else if (exitCode == 0) {
                        setStatus("Run finished", UiPalette.SUCCESS);
                    } else {
                        setStatus("Run finished with issues", UiPalette.WARNING);
                    }
                }
            }
        }.execute();
    }

    private void handleLiveEvent(Map<String, String> event) {
        appendLiveLog(event);
        updateProgress(event);
        renderLiveMonitorSnapshot(event);
        pushLiveEventTable(event);
        if ("finished".equals(event.get("phase"))) {
            Map<String, String> sample = new LinkedHashMap<>();
            sample.put("run_id", event.getOrDefault("run_id", ""));
            sample.put("profile_id", event.getOrDefault("profile_id", ""));
            sample.put("implementation", event.getOrDefault("implementation", ""));
            sample.put("case_id", event.getOrDefault("case_id", ""));
            sample.put("repeat_index", event.getOrDefault("repeat_index", ""));
            sample.put("warmup", event.getOrDefault("warmup", ""));
            sample.put("ns_per_iteration", "ns/iter".equals(event.getOrDefault("metric_kind", "")) ? event.getOrDefault("metric", "") : "");
            sample.put("legacy_cycles_per_iteration", "cycles/iter".equals(event.getOrDefault("metric_kind", "")) ? event.getOrDefault("metric", "") : "");
            sample.put("status", event.getOrDefault("status", ""));
            sample.put("timer_kind", event.getOrDefault("timer_kind", ""));
            sample.put("elapsed_ns", event.getOrDefault("elapsed_ns", ""));
            liveMetricRows.add(sample);
            updateRunCharts(liveMetricRows);
        }
        if ("completed".equals(event.get("phase"))) {
            pendingRunSelection = event.getOrDefault("run_id", "");
            state.setCurrentRunId(pendingRunSelection);
            setStatus("Run complete: " + pendingRunSelection, UiPalette.SUCCESS);
        }
    }

    private void appendLiveLog(Map<String, String> event) {
        String phase = event.getOrDefault("phase", "");
        String implementation = event.getOrDefault("implementation", "");
        String caseId = event.getOrDefault("case_id", "");
        String metric = event.getOrDefault("metric", "");
        String metricKind = event.getOrDefault("metric_kind", "");
        String message = event.getOrDefault("message", "");
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(phase).append("] ").append(message);
        if (!implementation.isBlank()) {
            builder.append(" | ").append(implementation);
        }
        if (!caseId.isBlank()) {
            builder.append(" | ").append(caseId);
        }
        if (!metric.isBlank()) {
            builder.append(" | ").append(metric).append(' ').append(metricKind);
        }
        liveLogArea.append(builder.append('\n').toString());
        liveLogArea.setCaretPosition(liveLogArea.getDocument().getLength());
    }

    private void updateProgress(Map<String, String> event) {
        int stepIndex = parseInt(event.get("step_index"));
        int stepTotal = Math.max(1, parseInt(event.get("step_total")));
        int progress = (int) Math.round((stepIndex * 100.0) / stepTotal);
        progressBar.setValue(progress);
        progressBar.setString(stepIndex + " / " + stepTotal);
    }

    private void pushLiveEventTable(Map<String, String> event) {
        List<Map<String, String>> rows = new ArrayList<>(state.runEvents().asMaps());
        rows.add(event);
        state.setRunEvents(mapsToTableData(rows, LIVE_EVENT_HEADERS));
        applyTable(eventsModel, state.runEvents());
    }

    private void setStatus(String text, Color color) {
        statusPill.setText(text);
        statusPill.setBackground(color);
        statusPill.setForeground(brightBackground(color) ? UiPalette.WINDOW : UiPalette.TEXT);
    }

    private void showDocument(String title, Path relativePath) {
        try {
            String text = java.nio.file.Files.readString(backend.repoRoot().resolve(relativePath), StandardCharsets.UTF_8);
            JTextArea area = buildTextArea();
            area.setText(text);
            area.setCaretPosition(0);
            JScrollPane scrollPane = new JScrollPane(area);
            scrollPane.getViewport().setBackground(UiPalette.SURFACE);
            JDialog dialog = new JDialog(frame, title, false);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.add(scrollPane);
            dialog.setSize(Math.max(900, frame.getWidth() - 240), Math.max(720, frame.getHeight() - 180));
            dialog.setLocationRelativeTo(frame);
            dialog.setVisible(true);
        } catch (IOException error) {
            JOptionPane.showMessageDialog(frame, "Unable to open " + relativePath + "\n" + error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static boolean brightBackground(Color color) {
        double luminance = (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255.0;
        return luminance > 0.55;
    }

    private static void applyTable(DefaultTableModel model, TableData data) {
        Vector<String> headers = new Vector<>(data.headers());
        Vector<Vector<String>> rows = new Vector<>();
        for (List<String> row : data.rows()) {
            rows.add(new Vector<>(row));
        }
        model.setDataVector(rows, headers);
    }

    private static TableData mapsToTableData(List<Map<String, String>> rows, List<String> preferredHeaders) {
        if (preferredHeaders.isEmpty()) {
            return TableData.empty();
        }
        List<List<String>> values = new ArrayList<>();
        for (Map<String, String> row : rows) {
            List<String> line = new ArrayList<>(preferredHeaders.size());
            for (String header : preferredHeaders) {
                line.add(row.getOrDefault(header, ""));
            }
            values.add(line);
        }
        return new TableData(preferredHeaders, values);
    }

    private static Map<String, String> tsvToMap(List<String> headers, List<String> row) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index += 1) {
            values.put(headers.get(index), index < row.size() ? row.get(index) : "");
        }
        return values;
    }

    private void selectRunRow(String runId) {
        int runIdColumn = runsModel.findColumn("run_id");
        if (runIdColumn < 0) {
            return;
        }
        for (int row = 0; row < runsModel.getRowCount(); row += 1) {
            if (Objects.equals(runId, runsModel.getValueAt(row, runIdColumn))) {
                runsTable.setRowSelectionInterval(row, row);
                loadSelectedRun();
                return;
            }
        }
    }

    private static String formatKeyValueTable(TableData data) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, String> row : data.asMaps()) {
            builder.append(row.getOrDefault("field", "")).append(": ").append(row.getOrDefault("value", "")).append('\n');
        }
        return builder.toString();
    }

    private static String formatDetail(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        builder.append(values.getOrDefault("run_id", "")).append('\n');
        builder.append(values.getOrDefault("implementation", "")).append(" / ").append(values.getOrDefault("case_id", "")).append('\n');
        builder.append("status: ").append(values.getOrDefault("status", "")).append('\n');
        builder.append("warmup: ").append(values.getOrDefault("warmup", "")).append('\n');
        builder.append("repeat: ").append(values.getOrDefault("repeat_index", "")).append('\n');
        builder.append("iterations: ").append(values.getOrDefault("iterations", "")).append('\n');
        builder.append("parallel_chains: ").append(values.getOrDefault("parallel_chains", "")).append('\n');
        builder.append("timer_kind: ").append(values.getOrDefault("timer_kind", "")).append('\n');
        builder.append("elapsed_ns: ").append(values.getOrDefault("elapsed_ns", "")).append('\n');
        builder.append("ns_per_iteration: ").append(values.getOrDefault("ns_per_iteration", "")).append('\n');
        builder.append("ns_per_add: ").append(values.getOrDefault("ns_per_add", "")).append('\n');
        builder.append("legacy_cycles: ").append(values.getOrDefault("legacy_cycles", "")).append('\n');
        builder.append("legacy_cycles_per_iteration: ").append(values.getOrDefault("legacy_cycles_per_iteration", "")).append('\n');
        builder.append("legacy_cycles_per_add: ").append(values.getOrDefault("legacy_cycles_per_add", "")).append('\n');
        builder.append("runtime: ").append(values.getOrDefault("runtime_name", "")).append(" (").append(values.getOrDefault("runtime_source", "")).append(")\n");
        builder.append("pid/tid: ").append(values.getOrDefault("pid", "")).append(" / ").append(values.getOrDefault("tid", "")).append('\n');
        builder.append("scheduler: ")
            .append(values.getOrDefault("requested_priority_mode", "")).append(" -> ")
            .append(values.getOrDefault("applied_priority_mode", "")).append(" | ")
            .append(values.getOrDefault("requested_affinity_mode", "")).append(" -> ")
            .append(values.getOrDefault("applied_affinity_mode", "")).append('\n');
        builder.append("notes: ").append(values.getOrDefault("scheduler_notes", "")).append('\n');
        builder.append("checksum: ").append(values.getOrDefault("result_checksum", "")).append('\n');
        builder.append("platform_extras_json: ").append(values.getOrDefault("platform_extras_json", "")).append('\n');
        return builder.toString();
    }

    private static MetricSample bestMetric(List<Map<String, String>> rows) {
        MetricSample best = null;
        for (Map<String, String> row : rows) {
            Double value = metric(row);
            if (value == null) {
                continue;
            }
            MetricSample sample = new MetricSample(row, value, metricKind(row));
            if (best == null || value < best.value()) {
                best = sample;
            }
        }
        return best;
    }

    private static String joinDistinct(List<Map<String, String>> rows, String key) {
        Set<String> values = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String value = row.getOrDefault(key, "");
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return "n/a";
        }
        return String.join(", ", values);
    }

    private static int countBy(List<Map<String, String>> rows, String key, String value) {
        int count = 0;
        for (Map<String, String> row : rows) {
            if (Objects.equals(value, row.get(key))) {
                count += 1;
            }
        }
        return count;
    }

    private static Map<String, String> findRow(List<Map<String, String>> rows, String key, String value) {
        for (Map<String, String> row : rows) {
            if (Objects.equals(value, row.get(key))) {
                return row;
            }
        }
        return Map.of();
    }

    private static Map<String, String> firstNonBlank(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (!row.isEmpty()) {
                return row;
            }
        }
        return Map.of();
    }

    private static String shortRunLabel(Map<String, String> row) {
        String runNumber = row.getOrDefault("run_number", "");
        String profile = row.getOrDefault("profile_id", "");
        if (!runNumber.isBlank()) {
            return "r" + runNumber + " " + profile;
        }
        return row.getOrDefault("run_id", "");
    }

    private static List<Map.Entry<String, Double>> sortDoubleEntries(Map<String, Double> values) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        return entries;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private static Double metric(Map<String, String> row) {
        String value = row.getOrDefault("ns_per_iteration", "");
        if (!value.isBlank()) {
            return Double.parseDouble(value);
        }
        value = row.getOrDefault("legacy_cycles_per_iteration", "");
        if (!value.isBlank()) {
            return Double.parseDouble(value);
        }
        return null;
    }

    private static String metricKind(Map<String, String> row) {
        return !row.getOrDefault("ns_per_iteration", "").isBlank() ? "ns/iter" : "cycles/iter";
    }

    private record MetricSample(Map<String, String> row, double value, String kind) {
    }

    @FunctionalInterface
    private interface ProcessStarter {
        Process start() throws IOException;
    }
}
