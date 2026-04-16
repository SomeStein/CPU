package cpubench.ui.shell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import cpubench.api.BackendClient;
import cpubench.model.ControllerState;
import cpubench.model.FilterState;
import cpubench.model.TableData;
import cpubench.ui.DarkTheme;
import cpubench.ui.IconFactory;
import cpubench.ui.ImplementationBarChartPanel;
import cpubench.ui.ProfileBuilderPanel;
import cpubench.ui.UiPalette;
import cpubench.ui.charts.InteractiveTrendChart;
import cpubench.ui.charts.InteractiveTrendChart.PointRecord;
import cpubench.ui.icons.LanguageCellRenderer;
import cpubench.ui.icons.LanguageListCellRenderer;
import cpubench.ui.shell.ActivityBar.Activity;

public final class ShellFrame {
    private static final List<String> RESULT_SELECTION_KEYS = List.of("run_id", "implementation", "case_id", "warmup", "repeat_index");
    private static final String HOME_KEY = "home";
    private static final String GLOBAL_KEY = "global";
    private static final String MONITOR_KEY = "monitor";
    private static final String BUILDER_KEY = "builder";
    private static final String RUN_OVERVIEW_KEY = "run";
    private static final String ARTIFACTS_KEY = "artifacts";
    private static final DateTimeFormatter LIVE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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
    private static final List<String> RUN_HEADERS = List.of(
        "run_id",
        "run_number",
        "started_at",
        "profile_id",
        "profile_name",
        "host_os",
        "host_arch",
        "status",
        "result_count",
        "implementation_count",
        "case_count",
        "best_metric_value",
        "best_metric_kind"
    );
    private static final List<String> RESULT_HEADERS = List.of(
        "run_id",
        "run_number",
        "started_at",
        "profile_id",
        "profile_name",
        "host_os",
        "host_arch",
        "implementation",
        "language",
        "variant",
        "case_id",
        "warmup",
        "repeat_index",
        "iterations",
        "parallel_chains",
        "loop_trip_count",
        "remainder",
        "timer_kind",
        "elapsed_ns",
        "ns_per_iteration",
        "ns_per_add",
        "legacy_cycles",
        "legacy_cycles_per_iteration",
        "legacy_cycles_per_add",
        "result_checksum",
        "requested_priority_mode",
        "requested_affinity_mode",
        "applied_priority_mode",
        "applied_affinity_mode",
        "scheduler_notes",
        "pid",
        "tid",
        "runtime_name",
        "runtime_source",
        "status",
        "log_file",
        "raw_file",
        "error_message",
        "platform_extras_json"
    );

    private final BackendClient backend;
    private final ControllerState state;
    private final JFrame frame;
    private final ActivityBar activityBar;
    private final SidePanel sidePanel;
    private final WorkspaceTabs workspaceTabs;
    private final StatusBar statusBar;
    private final Map<String, JComponent> tabsByKey = new LinkedHashMap<>();
    private final ProfileBuilderPanel profileBuilderPanel;

    private final JTextField runSearchField = new JTextField();
    private final JTextArea profilePreviewArea = buildTextArea();
    private final JTextArea selectedRunArea = buildTextArea();
    private final JTextArea runOverviewSummaryArea = buildTextArea();
    private final JTextArea liveStatusArea = buildTextArea();
    private final JTextArea monitorTailArea = buildTextArea();
    private final JTextArea globalMonitorArea = buildTextArea();
    private final JTextArea artifactSummaryArea = buildTextArea();
    private final JTextArea detailArea = buildTextArea();
    private final JTextArea rawLogArea = buildTextArea();
    private final JTextArea manifestArea = buildTextArea();
    private final JTextArea liveLogArea = buildTextArea();
    private final JTextArea homeGuideArea = buildTextArea();
    private final JTextArea homeRecentRunsArea = buildTextArea();
    private final JTextArea homeProfilesArea = buildTextArea();
    private final JTextArea homeBestArea = buildTextArea();

    private final JComboBox<String> runImplementationFilter = new JComboBox<>(new String[] {"All"});
    private final JComboBox<String> runCaseFilter = new JComboBox<>(new String[] {"All"});
    private final JComboBox<String> runStatusFilter = new JComboBox<>(new String[] {"All", "success", "partial_failure", "failed"});
    private final JCheckBox runMeasuredOnlyToggle = new JCheckBox("Hide Warmups", true);
    private final JComboBox<String> globalProfileFilter = new JComboBox<>(new String[] {"All"});
    private final JComboBox<String> globalImplementationFilter = new JComboBox<>(new String[] {"All"});
    private final JComboBox<String> globalCaseFilter = new JComboBox<>(new String[] {"All"});
    private final JComboBox<String> globalStatusFilter = new JComboBox<>(new String[] {"All", "success", "partial_failure", "failed"});
    private final JCheckBox globalMeasuredOnlyToggle = new JCheckBox("Hide Warmups", true);

    private final DefaultTableModel runsModel = new DefaultTableModel();
    private final DefaultTableModel resultsModel = new DefaultTableModel();
    private final DefaultTableModel eventsModel = new DefaultTableModel();
    private final DefaultTableModel globalResultsModel = new DefaultTableModel();
    private final JTable runsTable = new JTable(runsModel);
    private final JTable resultsTable = new JTable(resultsModel);
    private final JTable eventsTable = new JTable(eventsModel);
    private final JTable globalResultsTable = new JTable(globalResultsModel);

    private final InteractiveTrendChart runChart = new InteractiveTrendChart();
    private final ImplementationBarChartPanel runBarChart = new ImplementationBarChartPanel();
    private final InteractiveTrendChart liveChart = new InteractiveTrendChart();
    private final InteractiveTrendChart globalChart = new InteractiveTrendChart();
    private final ImplementationBarChartPanel globalBarChart = new ImplementationBarChartPanel();

    private String pendingRunSelection = "";
    private Process activeProcess;
    private List<String> runEventHeaders = List.of();
    private Activity activeActivity = Activity.RUNS;
    private final Timer idleRefreshTimer;
    private final Timer liveUpdateTimer;
    private boolean refreshInFlight;
    private boolean suppressFilterActions;
    private Map<String, Object> liveManifest = Map.of();
    private final Map<String, Color> liveSeriesColors = new LinkedHashMap<>();
    private final List<Map<String, String>> pendingLiveEvents = new ArrayList<>();
    private final List<Map<String, String>> liveRunRows = new ArrayList<>();
    private final List<Map<String, String>> liveGlobalRows = new ArrayList<>();
    private final List<Map<String, String>> liveEventRows = new ArrayList<>();
    private String liveRunStatus = "running";

    public ShellFrame(BackendClient backend) {
        this.backend = backend;
        this.state = new ControllerState();
        this.frame = new JFrame("CPU Lab");
        this.activityBar = new ActivityBar(this::handleActivitySelection);
        this.sidePanel = new SidePanel();
        this.workspaceTabs = new WorkspaceTabs();
        this.statusBar = new StatusBar();
        this.profileBuilderPanel = new ProfileBuilderPanel(backend, this::refreshAll, this::startRunProfileFile, this::selectProfile);
        this.idleRefreshTimer = new Timer(4000, event -> refreshAll(false));
        this.idleRefreshTimer.setRepeats(true);
        this.liveUpdateTimer = new Timer(90, event -> flushPendingLiveEvents());
        this.liveUpdateTimer.setRepeats(false);
        buildUi();
    }

    public void showWindow() {
        refreshAll();
        frame.setSize(1480, 920);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        idleRefreshTimer.start();
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1240, 760));
        frame.getContentPane().setBackground(UiPalette.WINDOW);
        frame.setLayout(new BorderLayout(UiPalette.GAP_MD, UiPalette.GAP_MD));
        frame.setJMenuBar(buildMenuBar());
        frame.setIconImage(IconFactory.appImage(256));

        frame.add(activityBar, BorderLayout.WEST);

        JPanel shell = new JPanel(new BorderLayout(UiPalette.GAP_MD, UiPalette.GAP_MD));
        shell.setOpaque(false);
        shell.setBorder(BorderFactory.createEmptyBorder(UiPalette.GAP_MD, 0, 0, UiPalette.GAP_MD));
        shell.add(sidePanel, BorderLayout.WEST);
        shell.add(workspaceTabs, BorderLayout.CENTER);
        shell.add(statusBar, BorderLayout.SOUTH);
        frame.add(shell, BorderLayout.CENTER);

        configureCombo(runImplementationFilter);
        configureCombo(runCaseFilter);
        configureCombo(runStatusFilter);
        configureCombo(globalProfileFilter);
        configureCombo(globalImplementationFilter);
        configureCombo(globalCaseFilter);
        configureCombo(globalStatusFilter);

        sidePanel.addPanel(Activity.RUNS, buildRunsSideView());
        sidePanel.addPanel(Activity.ANALYSIS, buildAnalysisSideView());
        sidePanel.addPanel(Activity.MONITOR, buildMonitorSideView());
        sidePanel.addPanel(Activity.ARTIFACTS, buildArtifactsSideView());
        sidePanel.addPanel(Activity.CONFIG, buildConfigSideView());
        sidePanel.addPanel(Activity.DOCS, buildDocsSideView());
        sidePanel.showPanel(Activity.RUNS);

        openScreen(HOME_KEY, "Home", IconFactory.logsIcon(14, UiPalette.ACCENT), buildHomeScreen(), false);
        workspaceTabs.setSelectedIndex(0);

        statusBar.runButton().addActionListener(event -> startRun());
        statusBar.refreshButton().addActionListener(event -> refreshAll(true));
        statusBar.stopButton().addActionListener(event -> stopRun());
        statusBar.profileSelector().addActionListener(event -> {
            if (statusBar.profileSelector().getSelectedItem() != null) {
                String profileId = String.valueOf(statusBar.profileSelector().getSelectedItem());
                if (!Objects.equals(profileId, state.currentProfileId())) {
                    state.setCurrentProfileId(profileId);
                    loadProfilePreview(profileId);
                }
            }
        });

        installRunsInteractions();
        installResultInteractions();
        installFilterInteractions();
        installWindowShortcuts();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(menuItem("Refresh", event -> refreshAll(true)));
        file.add(menuItem("Run Selected Profile", event -> startRun()));
        file.add(menuItem("Open Builder", event -> openBuilderTab()));
        file.add(menuItem("Open Global Overview", event -> openGlobalOverviewScreen()));
        file.add(menuItem("Open Live Monitor", event -> openLiveMonitorTab()));
        file.add(menuItem("Exit", event -> frame.dispose()));

        JMenu help = new JMenu("Help");
        help.add(menuItem("README", event -> openDocumentTab("README", Path.of("README.md"))));
        help.add(menuItem("Architecture", event -> openDocumentTab("Architecture", Path.of("docs", "ARCHITECTURE.md"))));
        help.add(menuItem("Timing Isolation", event -> openDocumentTab("Timing Isolation", Path.of("docs", "TIMING_ISOLATION.md"))));
        help.add(menuItem("Agent Hints", event -> openDocumentTab("AGENTS.md", Path.of("AGENTS.md"))));

        bar.add(file);
        bar.add(help);
        return bar;
    }

    private JMenuItem menuItem(String title, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(action);
        return item;
    }

    private void installRunsInteractions() {
        runsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedRun(false);
            }
        });
        runsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    loadSelectedRun(true);
                }
            }
        });
        runSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                applyRunsSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                applyRunsSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                applyRunsSearch();
            }
        });
    }

    private void installResultInteractions() {
        resultsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                inspectSelectedRunResult(false);
            }
        });
        globalResultsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                inspectSelectedGlobalResult(false);
            }
        });
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    inspectSelectedRunResult(true);
                }
            }
        });
        globalResultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    inspectSelectedGlobalResult(true);
                }
            }
        });
    }

    private void installFilterInteractions() {
        runImplementationFilter.setRenderer(new LanguageListCellRenderer());
        globalImplementationFilter.setRenderer(new LanguageListCellRenderer());
        runImplementationFilter.addActionListener(event -> {
            if (!suppressFilterActions) {
                applyRunFilters();
            }
        });
        runCaseFilter.addActionListener(event -> {
            if (!suppressFilterActions) {
                applyRunFilters();
            }
        });
        runStatusFilter.addActionListener(event -> {
            if (!suppressFilterActions) {
                applyRunFilters();
            }
        });
        runMeasuredOnlyToggle.addActionListener(event -> {
            if (!suppressFilterActions) {
                applyRunFilters();
            }
        });
        globalProfileFilter.addActionListener(event -> {
            if (!suppressFilterActions) {
                applyGlobalFilters();
            }
        });
        globalImplementationFilter.addActionListener(event -> {
            if (!suppressFilterActions) {
                applyGlobalFilters();
            }
        });
        globalCaseFilter.addActionListener(event -> {
            if (!suppressFilterActions) {
                applyGlobalFilters();
            }
        });
        globalStatusFilter.addActionListener(event -> {
            if (!suppressFilterActions) {
                applyGlobalFilters();
            }
        });
        globalMeasuredOnlyToggle.addActionListener(event -> {
            if (!suppressFilterActions) {
                applyGlobalFilters();
            }
        });
    }

    private void installWindowShortcuts() {
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F5"), "refresh");
        frame.getRootPane().getActionMap().put("refresh", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refreshAll(true);
            }
        });
    }

    private JComponent buildRunsSideView() {
        JPanel panel = sideCard("Runs");
        JPanel top = new JPanel(new BorderLayout(0, UiPalette.GAP_SM));
        top.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, UiPalette.GAP_SM, 0));
        buttons.setOpaque(false);
        JButton openRun = button("Open Run", UiPalette.SURFACE);
        JButton openArtifacts = button("Artifacts", UiPalette.SURFACE_ALT);
        JButton deleteRun = button("Delete Run", UiPalette.WARNING);
        openRun.addActionListener(event -> {
            if (!state.currentRunId().isBlank()) {
                openRunOverviewTab();
            }
        });
        openArtifacts.addActionListener(event -> {
            if (!state.currentRunId().isBlank()) {
                openArtifactsTab();
            }
        });
        deleteRun.addActionListener(event -> deleteSelectedRun());
        buttons.add(openRun);
        buttons.add(openArtifacts);
        buttons.add(deleteRun);
        top.add(labelledField("Search", runSearchField), BorderLayout.NORTH);
        top.add(buttons, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(tableScroll(runsTable), BorderLayout.CENTER);
        panel.add(textSection("Selected Run", selectedRunArea, null), BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildAnalysisSideView() {
        JPanel shell = sideCard("Analysis Filters");
        JButton openGlobal = button("Open Global Overview", UiPalette.SURFACE);
        openGlobal.addActionListener(event -> openGlobalOverviewScreen());
        shell.add(openGlobal, BorderLayout.NORTH);
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.add(filterSection("Run View", runImplementationFilter, runCaseFilter, runStatusFilter, runMeasuredOnlyToggle));
        stack.add(javax.swing.Box.createVerticalStrut(UiPalette.GAP_MD));
        stack.add(filterSection("Global View", globalImplementationFilter, globalCaseFilter, globalStatusFilter, globalMeasuredOnlyToggle, globalProfileFilter));
        shell.add(stack, BorderLayout.CENTER);
        return shell;
    }

    private JComponent buildMonitorSideView() {
        JPanel panel = sideCard("Monitor");
        JPanel top = new JPanel(new BorderLayout(0, UiPalette.GAP_SM));
        top.setOpaque(false);
        JButton openMonitor = button("Open Live Monitor", UiPalette.SURFACE);
        openMonitor.addActionListener(event -> openLiveMonitorTab());
        top.add(openMonitor, BorderLayout.NORTH);
        top.add(textSection("Live Status", liveStatusArea, new Dimension(0, 180)), BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        panel.add(textSection("Event Tail", monitorTailArea, null), BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildArtifactsSideView() {
        JPanel panel = sideCard("Artifacts");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, UiPalette.GAP_SM, 0));
        buttons.setOpaque(false);
        JButton openRun = button("Run Overview", UiPalette.SURFACE);
        JButton openArtifacts = button("Inspector", UiPalette.SURFACE_ALT);
        buttons.add(openRun);
        buttons.add(openArtifacts);
        openRun.addActionListener(event -> {
            if (!state.currentRunId().isBlank()) {
                openRunOverviewTab();
            }
        });
        openArtifacts.addActionListener(event -> openArtifactsTab());
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(textSection("Selection", artifactSummaryArea, null), BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildConfigSideView() {
        JPanel panel = sideCard("Profile");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, UiPalette.GAP_SM, 0));
        buttons.setOpaque(false);
        JButton builderButton = button("Open Builder", UiPalette.ACCENT);
        JButton deleteButton = button("Delete Custom", UiPalette.WARNING);
        builderButton.addActionListener(event -> openBuilderTab());
        deleteButton.addActionListener(event -> deleteSelectedCustomProfile());
        buttons.add(builderButton);
        buttons.add(deleteButton);
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(textSection("Profile Preview", profilePreviewArea, null), BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildDocsSideView() {
        JPanel panel = sideCard("Docs");
        JPanel buttons = new JPanel(new java.awt.GridLayout(0, 1, 0, UiPalette.GAP_SM));
        buttons.setOpaque(false);
        buttons.add(docButton("README", Path.of("README.md")));
        buttons.add(docButton("Architecture", Path.of("docs", "ARCHITECTURE.md")));
        buttons.add(docButton("Timing Isolation", Path.of("docs", "TIMING_ISOLATION.md")));
        buttons.add(docButton("AGENTS.md", Path.of("AGENTS.md")));
        panel.add(buttons, BorderLayout.NORTH);
        return panel;
    }

    private JComponent buildHomeScreen() {
        homeGuideArea.setText(
            "CPU Lab\n\n"
                + "1. Pick or build a profile.\n"
                + "2. Start a run from the status bar.\n"
                + "3. Open Run Overview, Global Overview, or Artifacts only when you need them.\n"
                + "4. Live Monitor will auto-focus during active runs.\n"
                + "5. Use the side panels to inspect, compare, and clean up tracked data."
        );

        JPanel docsButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, UiPalette.GAP_SM, 0));
        docsButtons.setOpaque(false);
        docsButtons.add(docButton("README", Path.of("README.md")));
        docsButtons.add(docButton("Architecture", Path.of("docs", "ARCHITECTURE.md")));
        docsButtons.add(docButton("Timing", Path.of("docs", "TIMING_ISOLATION.md")));

        JPanel left = new JPanel(new BorderLayout(0, UiPalette.GAP_MD));
        left.setOpaque(false);
        left.add(textSection("How To Use", homeGuideArea, null), BorderLayout.CENTER);
        left.add(docsButtons, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(0, UiPalette.GAP_MD));
        right.setOpaque(false);
        right.add(textSection("Recent Runs", homeRecentRunsArea, new Dimension(0, 220)), BorderLayout.NORTH);
        right.add(textSection("Profiles", homeProfilesArea, new Dimension(0, 180)), BorderLayout.CENTER);
        right.add(textSection("Best Recent Snapshot", homeBestArea, new Dimension(0, 140)), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.56);
        DarkTheme.styleSplitPane(split);
        return split;
    }

    private JComponent buildGlobalOverviewScreen() {
        JSplitPane charts = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, globalChart, globalBarChart);
        charts.setResizeWeight(0.62);
        DarkTheme.styleSplitPane(charts);

        JPanel summary = textSection("Global Summary", globalMonitorArea, new Dimension(0, 140));
        JPanel top = new JPanel(new BorderLayout(0, UiPalette.GAP_MD));
        top.setOpaque(false);
        top.add(summary, BorderLayout.NORTH);
        top.add(charts, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, tableScroll(globalResultsTable));
        split.setResizeWeight(0.56);
        DarkTheme.styleSplitPane(split);
        return split;
    }

    private JComponent buildLiveMonitorScreen() {
        liveChart.setPresentation("Live Metric Trend", "Finished samples stream here after phase boundaries.", "ns/iter", "Repeat");
        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll(eventsTable), textScroll(liveLogArea));
        bottom.setResizeWeight(0.56);
        DarkTheme.styleSplitPane(bottom);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, liveChart, bottom);
        split.setResizeWeight(0.52);
        DarkTheme.styleSplitPane(split);
        return split;
    }

    private JComponent buildRunOverviewScreen() {
        runChart.setPresentation("Run Metric Trend", "Per-run repeat metrics for the selected results.", "ns/iter", "Repeat");
        JSplitPane charts = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, runChart, runBarChart);
        charts.setResizeWeight(0.64);
        DarkTheme.styleSplitPane(charts);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, charts, tableScroll(resultsTable));
        split.setResizeWeight(0.52);
        DarkTheme.styleSplitPane(split);

        JPanel panel = new JPanel(new BorderLayout(0, UiPalette.GAP_MD));
        panel.setOpaque(false);
        panel.add(textSection("Run Summary", runOverviewSummaryArea, new Dimension(0, 150)), BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildArtifactsScreen() {
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, textScroll(rawLogArea), textScroll(manifestArea));
        right.setResizeWeight(0.58);
        DarkTheme.styleSplitPane(right);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, textScroll(detailArea), right);
        split.setResizeWeight(0.42);
        DarkTheme.styleSplitPane(split);
        return split;
    }

    private void refreshAll() {
        refreshAll(true);
    }

    private void refreshAll(boolean announce) {
        if (refreshInFlight || (!announce && activeProcess != null)) {
            return;
        }
        refreshInFlight = true;
        if (announce) {
            setStatus("Refreshing data", UiPalette.INFO);
            setControlsEnabled(false);
        }

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
                    updateHomeOverview();
                    applyRunsSearch();
                    populateGlobalFilters();
                    applyGlobalFilters();
                    if (!pendingRunSelection.isBlank()) {
                        if (Objects.equals(state.liveRunId(), pendingRunSelection)) {
                            state.clearLiveRun();
                            liveManifest = Map.of();
                            applyRunsSearch();
                            populateGlobalFilters();
                            applyGlobalFilters();
                        }
                        selectRunRow(pendingRunSelection, true);
                        pendingRunSelection = "";
                    } else if (!state.currentRunId().isBlank() && hasDisplayedRun(state.currentRunId())) {
                        selectRunRow(state.currentRunId(), false);
                    } else if (!state.currentRunId().isBlank()) {
                        state.setCurrentRunId("");
                        clearRunWorkspace();
                        workspaceTabs.setSelectedComponent(tabsByKey.get(HOME_KEY));
                    } else {
                        clearRunWorkspace();
                    }
                    if (announce) {
                        setStatus("CPU Lab ready", UiPalette.SUCCESS);
                    }
                } catch (Exception error) {
                    setStatus("Refresh failed", UiPalette.DANGER);
                    liveLogArea.append("[refresh-error] " + error.getMessage() + "\n");
                    statusBar.setHealth(UiPalette.DANGER);
                } finally {
                    refreshInFlight = false;
                    setControlsEnabled(true);
                }
            }
        }.execute();
    }

    private void populateProfileSelector() {
        String currentSelection = (String) statusBar.profileSelector().getSelectedItem();
        statusBar.profileSelector().removeAllItems();
        for (Map<String, String> row : state.profiles().asMaps()) {
            statusBar.profileSelector().addItem(row.getOrDefault("profile_id", ""));
        }
        if (currentSelection != null) {
            statusBar.profileSelector().setSelectedItem(currentSelection);
        }
        if (statusBar.profileSelector().getSelectedItem() == null && statusBar.profileSelector().getItemCount() > 0) {
            statusBar.profileSelector().setSelectedIndex(0);
        }
        state.setCurrentProfileId(String.valueOf(statusBar.profileSelector().getSelectedItem()));
        if (state.currentProfileId() != null && !state.currentProfileId().isBlank()) {
            loadProfilePreview(state.currentProfileId());
        }
    }

    private void selectProfile(String profileId) {
        statusBar.profileSelector().setSelectedItem(profileId);
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
                        builder.append("  warmups/repeats: ").append(row.getOrDefault("warmups", "")).append('/').append(row.getOrDefault("repeats", "")).append('\n');
                        builder.append("  scheduler: ").append(row.getOrDefault("priority_mode", "")).append(" / ").append(row.getOrDefault("affinity_mode", "")).append('\n');
                        builder.append("  implementations: ").append(row.getOrDefault("implementations", "")).append("\n\n");
                    }
                    profilePreviewArea.setText(builder.toString().stripTrailing());
                } catch (Exception error) {
                    profilePreviewArea.setText("Unable to load profile details.\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void applyRunsSearch() {
        String needle = runSearchField.getText() == null ? "" : runSearchField.getText().trim().toLowerCase();
        TableData displayedRuns = state.displayedRuns();
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, String> row : displayedRuns.asMaps()) {
            if (needle.isBlank() || matchesRunSearch(row, needle)) {
                rows.add(row);
            }
        }
        rows.sort((left, right) -> {
            int byNumber = Integer.compare(parseInt(right.get("run_number")), parseInt(left.get("run_number")));
            if (byNumber != 0) {
                return byNumber;
            }
            return right.getOrDefault("run_id", "").compareTo(left.getOrDefault("run_id", ""));
        });
        applyTable(runsTable, runsModel, mapsToTableData(rows, displayedRuns.headers()));
        if (!state.currentRunId().isBlank()) {
            if (!restoreSelectionByKey(runsTable, runsModel, "run_id", state.currentRunId())) {
                runsTable.clearSelection();
            }
        } else {
            runsTable.clearSelection();
        }
    }

    private static boolean matchesRunSearch(Map<String, String> row, String needle) {
        return row.getOrDefault("run_id", "").toLowerCase().contains(needle)
            || row.getOrDefault("profile_id", "").toLowerCase().contains(needle)
            || row.getOrDefault("profile_name", "").toLowerCase().contains(needle);
    }

    private void loadSelectedRun(boolean openTab) {
        int row = runsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = runsTable.convertRowIndexToModel(row);
        int runIdColumn = runsModel.findColumn("run_id");
        if (runIdColumn < 0) {
            return;
        }
        String runId = String.valueOf(runsModel.getValueAt(modelRow, runIdColumn));
        if (runId.isBlank()) {
            return;
        }
        state.setCurrentRunId(runId);
        loadRun(runId, openTab);
    }

    private void loadRun(String runId, boolean openTab) {
        if (Objects.equals(runId, state.liveRunId())) {
            state.setCurrentRunId(runId);
            state.setRunResults(TableData.empty());
            state.setRunEvents(TableData.empty());
            applyTable(eventsTable, eventsModel, state.displayedRunEvents());
            populateRunFilters();
            applyRunFilters();
            renderLiveMonitorSnapshot(null);
            if (openTab) {
                openRunOverviewTab();
            }
            statusBar.setLastEvent("Loaded live " + runId);
            setStatus("Loaded " + runId, UiPalette.SUCCESS);
            return;
        }
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
                    applyTable(eventsTable, eventsModel, state.displayedRunEvents());
                    populateRunFilters();
                    applyRunFilters();
                    renderLiveMonitorSnapshot(null);
                    if (openTab) {
                        openRunOverviewTab();
                    }
                    statusBar.setLastEvent("Loaded " + runId);
                    setStatus("Loaded " + runId, UiPalette.SUCCESS);
                } catch (Exception error) {
                    setStatus("Run load failed", UiPalette.DANGER);
                    detailArea.setText("Could not load run " + runId + "\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void populateRunFilters() {
        repopulateCombo(runImplementationFilter, state.displayedRunResults().distinctValues("implementation"));
        repopulateCombo(runCaseFilter, state.displayedRunResults().distinctValues("case_id"));
    }

    private void populateGlobalFilters() {
        repopulateCombo(globalProfileFilter, state.displayedGlobalResults().distinctValues("profile_id"));
        repopulateCombo(globalImplementationFilter, state.displayedGlobalResults().distinctValues("implementation"));
        repopulateCombo(globalCaseFilter, state.displayedGlobalResults().distinctValues("case_id"));
    }

    private void repopulateCombo(JComboBox<String> combo, Set<String> values) {
        String previous = (String) combo.getSelectedItem();
        suppressFilterActions = true;
        try {
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
        } finally {
            suppressFilterActions = false;
        }
    }

    private void applyRunFilters() {
        String selectedKey = selectedCompositeKey(resultsTable, resultsModel, RESULT_SELECTION_KEYS);
        state.setRunFilter(currentRunFilter());
        TableData filtered = state.filteredRunResults();
        applyTable(resultsTable, resultsModel, filtered);
        updateRunCharts(filtered.asMaps());
        renderSelectedRunSummary();
        renderLiveMonitorSnapshot(null);
        if (resultsModel.getRowCount() > 0) {
            if (!restoreSelectionByCompositeKey(resultsTable, resultsModel, RESULT_SELECTION_KEYS, selectedKey)) {
                resultsTable.setRowSelectionInterval(0, 0);
            }
        } else {
            artifactSummaryArea.setText("Select a result row to inspect.");
            detailArea.setText("No result row matches the current run filter.");
            rawLogArea.setText("");
        }
    }

    private void applyGlobalFilters() {
        String selectedKey = selectedCompositeKey(globalResultsTable, globalResultsModel, RESULT_SELECTION_KEYS);
        state.setGlobalFilter(currentGlobalFilter());
        TableData filtered = state.filteredGlobalResults();
        applyTable(globalResultsTable, globalResultsModel, filtered);
        updateGlobalCharts(filtered.asMaps());
        renderGlobalSummary(filtered.asMaps());
        if (globalResultsModel.getRowCount() > 0) {
            if (!restoreSelectionByCompositeKey(globalResultsTable, globalResultsModel, RESULT_SELECTION_KEYS, selectedKey)) {
                globalResultsTable.setRowSelectionInterval(0, 0);
            }
        } else {
            artifactSummaryArea.setText("Select a result row to inspect.");
            detailArea.setText("No result row matches the current global filter.");
            rawLogArea.setText("");
        }
    }

    private FilterState currentRunFilter() {
        return new FilterState(
            String.valueOf(runImplementationFilter.getSelectedItem()),
            String.valueOf(runCaseFilter.getSelectedItem()),
            String.valueOf(runStatusFilter.getSelectedItem()),
            "All",
            runMeasuredOnlyToggle.isSelected()
        );
    }

    private FilterState currentGlobalFilter() {
        return new FilterState(
            String.valueOf(globalImplementationFilter.getSelectedItem()),
            String.valueOf(globalCaseFilter.getSelectedItem()),
            String.valueOf(globalStatusFilter.getSelectedItem()),
            String.valueOf(globalProfileFilter.getSelectedItem()),
            globalMeasuredOnlyToggle.isSelected()
        );
    }

    private void renderSelectedRunSummary() {
        StringBuilder builder = new StringBuilder();
        if (state.currentRunId().isBlank()) {
            selectedRunArea.setText("No run selected.");
            runOverviewSummaryArea.setText("No run selected.");
            return;
        }
        Map<String, String> run = findRow(state.displayedRuns().asMaps(), "run_id", state.currentRunId());
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
            builder.append("Fastest sample: ").append(best.row().getOrDefault("implementation", "")).append(" / ").append(best.row().getOrDefault("case_id", "")).append('\n');
        }
        builder.append("Visible implementations: ").append(joinDistinct(filtered, "implementation")).append('\n');
        builder.append("Visible cases: ").append(joinDistinct(filtered, "case_id")).append('\n');
        String text = builder.toString();
        selectedRunArea.setText(text);
        runOverviewSummaryArea.setText(text);
    }

    private void renderLiveMonitorSnapshot(Map<String, String> liveEvent) {
        StringBuilder builder = new StringBuilder();
        if (liveEvent != null) {
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
            if (!liveEvent.getOrDefault("metric", "").isBlank()) {
                builder.append("Latest metric: ").append(liveEvent.get("metric")).append(' ').append(liveEvent.getOrDefault("metric_kind", "")).append('\n');
            }
            builder.append("Event stream: phase-boundary only");
            liveStatusArea.setText(builder.toString());
            return;
        }

        if (state.currentRunId().isBlank()) {
            liveStatusArea.setText("No run selected.\nStart a profile or pick a stored run.");
            return;
        }

        List<Map<String, String>> events = state.displayedRunEvents().asMaps();
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
        builder.append("Tracked runs: ").append(state.displayedRuns().rows().size()).append('\n');
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
        globalMonitorArea.setText(builder.toString());
    }

    private void updateRunCharts(List<Map<String, String>> rows) {
        Map<String, List<PointRecord>> grouped = new LinkedHashMap<>();
        Map<String, Color> colors = new LinkedHashMap<>();
        Map<String, Double> bestByImplementation = new LinkedHashMap<>();
        int colorIndex = 0;
        for (Map<String, String> row : rows) {
            Double metric = metric(row);
            if (metric == null) {
                continue;
            }
            String implementation = row.getOrDefault("implementation", "");
            String key = implementation + "::" + row.getOrDefault("case_id", "");
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(
                new PointRecord(parseInt(row.getOrDefault("repeat_index", "0")), metric, implementation, row.getOrDefault("case_id", ""), parseInt(row.getOrDefault("repeat_index", "0")))
            );
            colors.putIfAbsent(key, UiPalette.seriesColor(colorIndex++));
            bestByImplementation.merge(implementation, metric, Math::min);
        }
        List<InteractiveTrendChart.Series> series = new ArrayList<>();
        for (Map.Entry<String, List<PointRecord>> entry : grouped.entrySet()) {
            String implId = entry.getValue().isEmpty() ? "" : entry.getValue().get(0).implId();
            String caseId = entry.getValue().isEmpty() ? "" : entry.getValue().get(0).caseId();
            series.add(new InteractiveTrendChart.Series(entry.getKey(), implId, caseId, colors.get(entry.getKey()), entry.getValue()));
        }
        runChart.setPresentation(
            state.currentRunId().isBlank() ? "Run Metric Trend" : "Run Metric Trend · " + state.currentRunId(),
            rows.isEmpty() ? "No stored result rows match the current run filter." : "Drag plot to pan. Drag axes to scale. Wheel to zoom.",
            "ns/iter",
            "Repeat"
        );
        runChart.setSeries(series);

        List<ImplementationBarChartPanel.Bar> bars = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Double> entry : sortDoubleEntries(bestByImplementation)) {
            bars.add(new ImplementationBarChartPanel.Bar(entry.getKey(), UiPalette.seriesColor(index++), entry.getValue()));
        }
        runBarChart.setBars("Run Best By Implementation", "Fastest visible metric for the loaded run.", "ns/iter", bars);
    }

    private void updateGlobalCharts(List<Map<String, String>> rows) {
        Map<String, List<PointRecord>> grouped = new LinkedHashMap<>();
        Map<String, List<Double>> implementationMetrics = new LinkedHashMap<>();
        int colorIndex = 0;
        Map<String, Color> colors = new LinkedHashMap<>();

        List<Map<String, String>> orderedRuns = new ArrayList<>(state.displayedRuns().asMaps());
        orderedRuns.sort(Comparator.comparingInt(row -> parseInt(row.get("run_number"))));
        Map<String, Integer> runIndex = new LinkedHashMap<>();
        int index = 1;
        for (Map<String, String> row : orderedRuns) {
            runIndex.put(row.getOrDefault("run_id", ""), index++);
        }

        Map<String, Map<String, Double>> bestByRunAndImplementation = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            Double metric = metric(row);
            if (metric == null) {
                continue;
            }
            String implementation = row.getOrDefault("implementation", "");
            String runId = row.getOrDefault("run_id", "");
            bestByRunAndImplementation.computeIfAbsent(implementation, ignored -> new LinkedHashMap<>()).merge(runId, metric, Math::min);
            implementationMetrics.computeIfAbsent(implementation, ignored -> new ArrayList<>()).add(metric);
        }
        for (Map.Entry<String, Map<String, Double>> entry : bestByRunAndImplementation.entrySet()) {
            List<PointRecord> points = new ArrayList<>();
            for (Map.Entry<String, Double> runEntry : entry.getValue().entrySet()) {
                points.add(new PointRecord(runIndex.getOrDefault(runEntry.getKey(), 0), runEntry.getValue(), entry.getKey(), runEntry.getKey(), runIndex.getOrDefault(runEntry.getKey(), 0)));
            }
            colors.put(entry.getKey(), UiPalette.seriesColor(colorIndex++));
            grouped.put(entry.getKey(), points);
        }

        List<InteractiveTrendChart.Series> series = new ArrayList<>();
        for (Map.Entry<String, List<PointRecord>> entry : grouped.entrySet()) {
            series.add(new InteractiveTrendChart.Series(entry.getKey(), entry.getKey(), "", colors.get(entry.getKey()), entry.getValue()));
        }
        globalChart.setPresentation(
            "Cross-Run Trend",
            rows.isEmpty() ? "No stored metrics match the current global filter." : "Best visible sample per implementation across runs.",
            "ns/iter",
            "Run"
        );
        globalChart.setSeries(series);

        List<ImplementationBarChartPanel.Bar> bars = new ArrayList<>();
        colorIndex = 0;
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
            bars.add(new ImplementationBarChartPanel.Bar(entry.getKey(), UiPalette.seriesColor(colorIndex++), entry.getValue()));
        }
        globalBarChart.setBars("Cross-Run Average By Implementation", "Average visible metric across the current global filter.", "ns/iter", bars);
    }

    private void startRun() {
        String profileId = (String) statusBar.profileSelector().getSelectedItem();
        if (profileId == null || profileId.isBlank()) {
            return;
        }
        launchRun(profileId, () -> backend.startRunProfile(profileId));
    }

    private void startRunProfileFile(Path profilePath) {
        String profileLabel = profilePath.getFileName().toString().replace(".testrun.json", "");
        launchRun(profileLabel, () -> backend.startRunProfileFile(profilePath));
    }

    private void launchRun(String profileLabel, ProcessStarter starter) {
        setControlsEnabled(false);
        statusBar.stopButton().setEnabled(true);
        statusBar.progressBar().setValue(0);
        statusBar.progressBar().setMaximum(100);
        statusBar.progressBar().setString("0 / 0");
        statusBar.setLastEvent("Launching " + profileLabel);
        liveLogArea.setText("=== Launching " + profileLabel + " ===\n");
        monitorTailArea.setText("");
        liveStatusArea.setText("Waiting for controller events…\n");
        runEventHeaders = List.of();
        liveSeriesColors.clear();
        pendingLiveEvents.clear();
        liveRunRows.clear();
        liveGlobalRows.clear();
        liveEventRows.clear();
        liveRunStatus = "running";
        liveUpdateTimer.stop();
        liveManifest = Map.of();
        state.clearLiveRun();
        state.setRunEvents(TableData.empty());
        applyTable(eventsTable, eventsModel, TableData.empty());
        liveChart.resetView();
        liveChart.setPresentation("Live Metric Trend", "Finished samples will appear here as the profile runs.", "ns/iter", "Repeat");
        liveChart.setSeries(List.of());
        openLiveMonitorTab();
        setStatus("Running " + profileLabel, UiPalette.INFO);

        new SwingWorker<Integer, Map<String, String>>() {
            @Override
            protected Integer doInBackground() throws Exception {
                activeProcess = starter.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream(), StandardCharsets.UTF_8))) {
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
                if (chunks.isEmpty()) {
                    return;
                }
                pendingLiveEvents.addAll(chunks);
                if (!liveUpdateTimer.isRunning()) {
                    liveUpdateTimer.start();
                }
            }

            @Override
            protected void done() {
                flushPendingLiveEvents();
                activeProcess = null;
                statusBar.stopButton().setEnabled(false);
                int exitCode = 1;
                try {
                    exitCode = get();
                } catch (Exception error) {
                    liveLogArea.append("[run-error] " + error.getMessage() + "\n");
                } finally {
                    setControlsEnabled(true);
                    if (!pendingRunSelection.isBlank()) {
                        refreshAll(true);
                    } else if (exitCode == 0) {
                        setStatus("Run finished", UiPalette.SUCCESS);
                    } else {
                        setStatus("Run finished with issues", UiPalette.WARNING);
                    }
                }
            }
        }.execute();
    }

    private void stopRun() {
        if (activeProcess != null) {
            activeProcess.destroy();
            statusBar.setLastEvent("Run stop requested");
        }
    }

    private void flushPendingLiveEvents() {
        if (pendingLiveEvents.isEmpty()) {
            return;
        }
        List<Map<String, String>> batch = new ArrayList<>(pendingLiveEvents);
        pendingLiveEvents.clear();

        boolean runFiltersChanged = false;
        boolean globalFiltersChanged = false;
        boolean runsChanged = false;
        boolean eventsChanged = false;
        StringBuilder logBatch = new StringBuilder();
        Map<String, String> lastEvent = batch.get(batch.size() - 1);

        for (Map<String, String> event : batch) {
            String phase = event.getOrDefault("phase", "");
            if ("started".equals(phase)) {
                beginLiveRunState(event);
                runsChanged = true;
                runFiltersChanged = true;
                globalFiltersChanged = true;
            } else if ("finished".equals(phase)) {
                appendLiveResultState(event);
                runsChanged = true;
                runFiltersChanged = true;
                globalFiltersChanged = true;
            } else if ("completed".equals(phase)) {
                pendingRunSelection = event.getOrDefault("run_id", "");
                state.setCurrentRunId(pendingRunSelection);
                liveRunStatus = event.getOrDefault("status", "success");
                runsChanged = true;
                setStatus(
                    "Run complete: " + pendingRunSelection,
                    Objects.equals("success", liveRunStatus) ? UiPalette.SUCCESS : UiPalette.WARNING
                );
            }
            appendLiveLog(logBatch, event);
            liveEventRows.add(event);
            eventsChanged = true;
        }

        if (logBatch.length() > 0) {
            liveLogArea.append(logBatch.toString());
            liveLogArea.setCaretPosition(liveLogArea.getDocument().getLength());
        }

        updateProgress(lastEvent);
        renderLiveMonitorSnapshot(lastEvent);
        monitorTailArea.setText(monitorTailText(lastEvent));
        statusBar.setLastEvent(lastEvent.getOrDefault("message", lastEvent.getOrDefault("phase", "")));

        if (eventsChanged) {
            state.setLiveRunEvents(mapsToTableData(liveEventRows, runEventHeaders.isEmpty() ? LIVE_EVENT_HEADERS : runEventHeaders));
            applyTable(eventsTable, eventsModel, state.displayedRunEvents());
        }
        if (runFiltersChanged) {
            state.setLiveRunResults(mapsToTableData(liveRunRows, RESULT_HEADERS));
            if (!state.liveRunId().isBlank()) {
                state.setLiveRuns(mapsToTableData(List.of(buildLiveRunSummary(liveRunStatus)), state.runs().headers().isEmpty() ? RUN_HEADERS : state.runs().headers()));
            }
        }
        if (globalFiltersChanged) {
            state.setLiveGlobalResults(mapsToTableData(liveGlobalRows, RESULT_HEADERS));
        }
        if (runsChanged) {
            applyRunsSearch();
            updateHomeOverview();
        }
        if (runFiltersChanged) {
            populateRunFilters();
            if (Objects.equals(state.currentRunId(), state.liveRunId())) {
                applyRunFilters();
            }
        }
        if (globalFiltersChanged) {
            populateGlobalFilters();
            applyGlobalFilters();
        }
    }

    private void beginLiveRunState(Map<String, String> event) {
        String runId = event.getOrDefault("run_id", "");
        String profileId = event.getOrDefault("profile_id", state.currentProfileId());
        Map<String, String> profileRow = findRow(state.profiles().asMaps(), "profile_id", profileId);
        liveRunRows.clear();
        liveGlobalRows.clear();
        liveEventRows.clear();
        liveManifest = new LinkedHashMap<>();
        liveManifest.put("run_id", runId);
        liveManifest.put("profile_id", profileId);
        liveManifest.put("profile_name", profileRow.getOrDefault("name", profileId));
        liveManifest.put("started_at", LocalDateTime.now().format(LIVE_TIME));
        liveRunStatus = "running";

        state.setLiveRunId(runId);
        state.setCurrentRunId(runId);
    }

    private void appendLiveResultState(Map<String, String> event) {
        Map<String, String> row = resultRowFromEvent(event);
        liveRunRows.add(row);
        liveGlobalRows.add(row);

        String implementation = row.getOrDefault("implementation", "");
        String caseId = row.getOrDefault("case_id", "");
        String seriesId = implementation + "::" + caseId;
        Color color = liveSeriesColors.computeIfAbsent(seriesId, ignored -> UiPalette.seriesColor(liveSeriesColors.size()));
        Double metric = metric(row);
        if (metric != null) {
            liveChart.appendPoint(
                seriesId,
                implementation,
                caseId,
                color,
                new PointRecord(
                    parseInt(row.getOrDefault("repeat_index", "0")),
                    metric,
                    implementation,
                    caseId,
                    parseInt(row.getOrDefault("repeat_index", "0"))
                )
            );
        }

        liveRunStatus = hasLiveFailures() ? "partial_failure" : "running";
    }

    private boolean hasLiveFailures() {
        for (Map<String, String> row : liveRunRows) {
            if (!Objects.equals("success", row.getOrDefault("status", ""))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> buildLiveRunSummary(String status) {
        MetricSample best = bestMetric(liveRunRows);
        Set<String> implementations = new LinkedHashSet<>();
        Set<String> cases = new LinkedHashSet<>();
        String hostOs = "";
        String hostArch = "";
        for (Map<String, String> row : liveRunRows) {
            if (!Objects.equals("true", row.getOrDefault("warmup", "false"))) {
                implementations.add(row.getOrDefault("implementation", ""));
                cases.add(row.getOrDefault("case_id", ""));
            }
            if (hostOs.isBlank()) {
                hostOs = row.getOrDefault("host_os", "");
            }
            if (hostArch.isBlank()) {
                hostArch = row.getOrDefault("host_arch", "");
            }
        }

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("run_id", String.valueOf(liveManifest.getOrDefault("run_id", "")));
        summary.put("run_number", Integer.toString(runNumberFromRunId(String.valueOf(liveManifest.getOrDefault("run_id", "")))));
        summary.put("started_at", String.valueOf(liveManifest.getOrDefault("started_at", "")));
        summary.put("profile_id", String.valueOf(liveManifest.getOrDefault("profile_id", "")));
        summary.put("profile_name", String.valueOf(liveManifest.getOrDefault("profile_name", "")));
        summary.put("host_os", hostOs);
        summary.put("host_arch", hostArch);
        summary.put("status", status);
        summary.put("result_count", Integer.toString((int) liveRunRows.stream().filter(row -> !"true".equals(row.getOrDefault("warmup", "false"))).count()));
        summary.put("implementation_count", Integer.toString((int) implementations.stream().filter(value -> !value.isBlank()).count()));
        summary.put("case_count", Integer.toString((int) cases.stream().filter(value -> !value.isBlank()).count()));
        summary.put("best_metric_value", best == null ? "" : String.format("%.6f", best.value()));
        summary.put("best_metric_kind", best == null ? "" : best.kind());
        return summary;
    }

    private Map<String, String> resultRowFromEvent(Map<String, String> event) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String header : RESULT_HEADERS) {
            row.put(header, event.getOrDefault(header, ""));
        }
        return row;
    }

    private void appendLiveLog(StringBuilder output, Map<String, String> event) {
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(event.getOrDefault("phase", "")).append("] ").append(event.getOrDefault("message", ""));
        if (!event.getOrDefault("implementation", "").isBlank()) {
            builder.append(" | ").append(event.get("implementation"));
        }
        if (!event.getOrDefault("case_id", "").isBlank()) {
            builder.append(" | ").append(event.get("case_id"));
        }
        if (!event.getOrDefault("metric", "").isBlank()) {
            builder.append(" | ").append(event.get("metric")).append(' ').append(event.getOrDefault("metric_kind", ""));
        }
        output.append(builder).append('\n');
    }

    private String monitorTailText(Map<String, String> event) {
        return "phase=" + event.getOrDefault("phase", "") + '\n'
            + "implementation=" + event.getOrDefault("implementation", "") + '\n'
            + "case=" + event.getOrDefault("case_id", "") + '\n'
            + "status=" + event.getOrDefault("status", "") + '\n'
            + "message=" + event.getOrDefault("message", "");
    }

    private void updateProgress(Map<String, String> event) {
        int stepIndex = parseInt(event.get("step_index"));
        int stepTotal = Math.max(1, parseInt(event.get("step_total")));
        int progress = (int) Math.round((stepIndex * 100.0) / stepTotal);
        statusBar.progressBar().setValue(progress);
        statusBar.progressBar().setString(stepIndex + " / " + stepTotal);
    }

    private void inspectSelectedRunResult(boolean openTab) {
        int row = resultsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = resultsTable.convertRowIndexToModel(row);
        inspectResult(state.filteredRunResults().rowAsMap(modelRow), openTab);
    }

    private void inspectSelectedGlobalResult(boolean openTab) {
        int row = globalResultsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = globalResultsTable.convertRowIndexToModel(row);
        inspectResult(state.filteredGlobalResults().rowAsMap(modelRow), openTab);
    }

    private void inspectResult(Map<String, String> values, boolean openTab) {
        if (values.isEmpty()) {
            return;
        }
        String detailText = formatDetail(values);
        artifactSummaryArea.setText(detailText);
        detailArea.setText(detailText);
        loadManifestForRun(values.getOrDefault("run_id", ""));

        String rawFile = values.getOrDefault("raw_file", values.getOrDefault("log_file", ""));
        if (rawFile.isBlank()) {
            rawLogArea.setText("No raw log recorded for this row.");
            if (openTab) {
                openArtifactsTab();
            }
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
                    if (openTab) {
                        openArtifactsTab();
                    }
                } catch (Exception error) {
                    rawLogArea.setText("Unable to load raw log.\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void loadManifestForRun(String runId) {
        if (runId.isBlank()) {
            manifestArea.setText("");
            return;
        }
        if (Objects.equals(runId, state.liveRunId()) && !liveManifest.isEmpty()) {
            manifestArea.setText(formatLiveManifest(liveManifest));
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

    private void openRunOverviewTab() {
        openScreen(RUN_OVERVIEW_KEY, "Run Overview", IconFactory.chartIcon(14, UiPalette.TEXT), buildRunOverviewScreen(), true);
    }

    private void openArtifactsTab() {
        openScreen(ARTIFACTS_KEY, "Artifacts", IconFactory.docsIcon(14, UiPalette.TEXT), buildArtifactsScreen(), true);
    }

    private void openGlobalOverviewScreen() {
        openScreen(GLOBAL_KEY, "Global Overview", IconFactory.chartIcon(14, UiPalette.TEXT), buildGlobalOverviewScreen(), true);
    }

    private void openLiveMonitorTab() {
        openScreen(MONITOR_KEY, "Live Monitor", IconFactory.radarIcon(14, UiPalette.TEXT), buildLiveMonitorScreen(), true);
    }

    private void openBuilderTab() {
        openScreen(BUILDER_KEY, "Run Config", IconFactory.docsIcon(14, UiPalette.TEXT), profileBuilderPanel, true);
    }

    private void openDocumentTab(String title, Path relativePath) {
        String key = "doc:" + relativePath;
        if (tabsByKey.containsKey(key)) {
            workspaceTabs.setSelectedComponent(tabsByKey.get(key));
            return;
        }
        JTextArea area = buildTextArea();
        area.setText("Loading " + relativePath + " …");
        JComponent component = textScroll(area);
        openScreen(key, title, IconFactory.docsIcon(14, UiPalette.TEXT), component, true);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return java.nio.file.Files.readString(backend.repoRoot().resolve(relativePath), StandardCharsets.UTF_8);
            }

            @Override
            protected void done() {
                try {
                    area.setText(get());
                    area.setCaretPosition(0);
                } catch (Exception error) {
                    area.setText("Unable to open " + relativePath + "\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void openScreen(String key, String title, javax.swing.Icon icon, JComponent component, boolean closable) {
        JComponent existing = tabsByKey.get(key);
        if (existing != null && workspaceTabs.indexOfComponent(existing) >= 0) {
            workspaceTabs.setSelectedComponent(existing);
            return;
        }
        tabsByKey.remove(key);
        tabsByKey.put(key, component);
        workspaceTabs.addClosableTab(title, icon, component, closable);
        workspaceTabs.setSelectedComponent(component);
    }

    private void handleActivitySelection(Activity activity) {
        if (activeActivity == activity && !sidePanel.isCollapsed()) {
            sidePanel.setCollapsed(true);
            return;
        }
        activeActivity = activity;
        activityBar.setSelected(activity);
        sidePanel.setCollapsed(false);
        sidePanel.showPanel(activity);
    }

    private void clearRunWorkspace() {
        state.setRunResults(TableData.empty());
        state.setRunEvents(TableData.empty());
        state.setRunManifest(TableData.empty());
        applyTable(resultsTable, resultsModel, TableData.empty());
        applyTable(eventsTable, eventsModel, TableData.empty());
        manifestArea.setText("");
        detailArea.setText("Select a run to inspect stored measurements.");
        rawLogArea.setText("");
        selectedRunArea.setText("No run selected.");
        runOverviewSummaryArea.setText("No run selected.");
        liveStatusArea.setText("No run selected.\nStart a profile or load a stored run.");
        artifactSummaryArea.setText("Select a result row to inspect.");
        runChart.setSeries(List.of());
        runBarChart.setBars("Best By Implementation", "Select a run to compare implementations.", "ns/iter", List.of());
        liveChart.setSeries(List.of());
    }

    private void configureTable(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(UiPalette.ACCENT);
        table.setSelectionForeground(UiPalette.WINDOW);
        table.setBackground(UiPalette.SURFACE);
        table.setForeground(UiPalette.TEXT);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable localTable, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                java.awt.Component component = super.getTableCellRendererComponent(localTable, value, isSelected, hasFocus, row, column);
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

    private JScrollPane tableScroll(JTable table) {
        configureTable(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(UiPalette.SURFACE);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private JScrollPane textScroll(JTextArea area) {
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.getViewport().setBackground(UiPalette.SURFACE);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private JPanel textSection(String titleText, JTextArea area, Dimension size) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.getViewport().setBackground(UiPalette.SURFACE);
        if (size != null) {
            scroll.setPreferredSize(size);
        }
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel panel = new JPanel(new BorderLayout(0, UiPalette.GAP_SM));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(moduleBorder());
        JLabel title = label(titleText);
        title.setFont(UiPalette.LABEL.deriveFont(13f));
        panel.add(title, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel sideCard(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, UiPalette.GAP_MD));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(moduleBorder());
        panel.add(label(title), BorderLayout.NORTH);
        return panel;
    }

    private JPanel filterSection(String title, JComboBox<String> implementation, JComboBox<String> caseFilter, JComboBox<String> status, JCheckBox measuredOnly) {
        return filterSection(title, implementation, caseFilter, status, measuredOnly, null);
    }

    private JPanel filterSection(String title, JComboBox<String> implementation, JComboBox<String> caseFilter, JComboBox<String> status, JCheckBox measuredOnly, JComboBox<String> profile) {
        JPanel panel = new JPanel(new BorderLayout(0, UiPalette.GAP_SM));
        panel.setOpaque(false);
        panel.add(label(title), BorderLayout.NORTH);
        JPanel grid = new JPanel(new java.awt.GridLayout(profile == null ? 4 : 5, 2, UiPalette.GAP_SM, UiPalette.GAP_SM));
        grid.setOpaque(false);
        if (profile != null) {
            grid.add(label("Profile"));
            grid.add(profile);
        }
        grid.add(label("Implementation"));
        grid.add(implementation);
        grid.add(label("Case"));
        grid.add(caseFilter);
        grid.add(label("Status"));
        grid.add(status);
        grid.add(new JLabel());
        grid.add(measuredOnly);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel labelledField(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(0, UiPalette.GAP_SM));
        panel.setOpaque(false);
        panel.add(label(title), BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JButton docButton(String title, Path path) {
        JButton button = button(title, UiPalette.SURFACE);
        button.addActionListener(event -> openDocumentTab(title, path));
        return button;
    }

    private JButton button(String text, Color background) {
        JButton button = new JButton(text);
        button.setBackground(background);
        button.setForeground(pickForeground(background));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        return button;
    }

    private static Color pickForeground(Color background) {
        double luminance = (0.2126 * background.getRed() + 0.7152 * background.getGreen() + 0.0722 * background.getBlue()) / 255.0;
        return luminance > 0.55 ? UiPalette.WINDOW : UiPalette.TEXT;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiPalette.MUTED);
        label.setFont(UiPalette.LABEL);
        return label;
    }

    private static javax.swing.border.Border moduleBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiPalette.BORDER, 1),
            BorderFactory.createEmptyBorder(UiPalette.GAP_MD, UiPalette.GAP_MD, UiPalette.GAP_MD, UiPalette.GAP_MD)
        );
    }

    private void updateHomeOverview() {
        StringBuilder runsBuilder = new StringBuilder();
        int shown = 0;
        for (Map<String, String> row : sortedRuns(state.displayedRuns().asMaps())) {
            runsBuilder.append(row.getOrDefault("run_id", ""))
                .append(" · ")
                .append(row.getOrDefault("profile_id", ""))
                .append(" · ")
                .append(row.getOrDefault("status", ""))
                .append('\n');
            String metric = row.getOrDefault("best_metric_value", "");
            if (!metric.isBlank()) {
                runsBuilder.append("  best: ").append(metric).append(' ').append(row.getOrDefault("best_metric_kind", "")).append('\n');
            }
            runsBuilder.append('\n');
            shown += 1;
            if (shown >= 6) {
                break;
            }
        }
        homeRecentRunsArea.setText(runsBuilder.length() == 0 ? "No tracked runs yet." : runsBuilder.toString().stripTrailing());

        int builtinProfiles = 0;
        int customProfiles = 0;
        StringBuilder profilesBuilder = new StringBuilder();
        for (Map<String, String> row : state.profiles().asMaps()) {
            if (Objects.equals("custom", row.getOrDefault("source", ""))) {
                customProfiles += 1;
            } else {
                builtinProfiles += 1;
            }
            profilesBuilder.append(row.getOrDefault("profile_id", ""))
                .append(" · ")
                .append(row.getOrDefault("source", ""))
                .append('\n');
        }
        profilesBuilder.append('\n')
            .append("Built-in: ").append(builtinProfiles).append('\n')
            .append("Custom: ").append(customProfiles);
        homeProfilesArea.setText(profilesBuilder.toString().stripTrailing());

        MetricSample best = bestMetric(state.displayedGlobalResults().asMaps());
        if (best == null) {
            homeBestArea.setText("No measured data yet.");
        } else {
            homeBestArea.setText(
                best.row().getOrDefault("implementation", "") + " · " + best.row().getOrDefault("case_id", "") + '\n'
                    + String.format("%.6f %s", best.value(), best.kind()) + '\n'
                    + best.row().getOrDefault("run_id", "")
            );
        }
    }

    private boolean hasDisplayedRun(String runId) {
        return !findRow(state.displayedRuns().asMaps(), "run_id", runId).isEmpty();
    }

    private void deleteSelectedRun() {
        String runId = state.currentRunId();
        if (runId.isBlank()) {
            return;
        }
        if (activeProcess != null && Objects.equals(runId, state.liveRunId())) {
            JOptionPane.showMessageDialog(frame, "Stop the active run before deleting it.", "Run Active", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(frame, "Delete tracked run " + runId + "?", "Delete Run", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            backend.deleteRun(runId);
            if (Objects.equals(state.currentRunId(), runId)) {
                state.setCurrentRunId("");
                clearRunWorkspace();
                workspaceTabs.setSelectedComponent(tabsByKey.get(HOME_KEY));
            }
            refreshAll(true);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(frame, "Unable to delete run.\n" + error.getMessage(), "Delete Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedCustomProfile() {
        String profileId = String.valueOf(statusBar.profileSelector().getSelectedItem());
        if (profileId == null || profileId.isBlank()) {
            return;
        }
        Map<String, String> profileRow = findRow(state.profiles().asMaps(), "profile_id", profileId);
        if (!Objects.equals("custom", profileRow.getOrDefault("source", ""))) {
            JOptionPane.showMessageDialog(frame, "Built-in profiles stay read-only.", "Read-Only Profile", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(frame, "Delete custom profile " + profileId + "?", "Delete Profile", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            backend.deleteCustomProfile(profileId);
            refreshAll(true);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(frame, "Unable to delete custom profile.\n" + error.getMessage(), "Delete Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String formatLiveManifest(Map<String, Object> manifest) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : manifest.entrySet()) {
            builder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        return builder.toString().stripTrailing();
    }

    private static int runNumberFromRunId(String runId) {
        if (runId == null || !runId.startsWith("run")) {
            return 0;
        }
        int underscore = runId.indexOf('_');
        String number = underscore >= 0 ? runId.substring(3, underscore) : runId.substring(3);
        return parseInt(number);
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

    private void configureCombo(JComboBox<String> combo) {
        combo.setBackground(UiPalette.SURFACE);
        combo.setForeground(UiPalette.TEXT);
    }

    private void setStatus(String text, Color color) {
        statusBar.setLastEvent(text);
        statusBar.setHealth(color);
    }

    private void setControlsEnabled(boolean enabled) {
        statusBar.runButton().setEnabled(enabled && statusBar.profileSelector().getItemCount() > 0);
        statusBar.refreshButton().setEnabled(enabled);
        statusBar.profileSelector().setEnabled(enabled);
    }

    private void selectRunRow(String runId, boolean openTab) {
        int runIdColumn = runsModel.findColumn("run_id");
        if (runIdColumn < 0) {
            return;
        }
        for (int row = 0; row < runsModel.getRowCount(); row += 1) {
            if (Objects.equals(runId, runsModel.getValueAt(row, runIdColumn))) {
                runsTable.setRowSelectionInterval(row, row);
                loadRun(runId, openTab);
                return;
            }
        }
    }

    private static List<Map<String, String>> sortedRuns(List<Map<String, String>> rows) {
        List<Map<String, String>> ordered = new ArrayList<>(rows);
        ordered.sort((left, right) -> {
            int byNumber = Integer.compare(parseInt(right.get("run_number")), parseInt(left.get("run_number")));
            if (byNumber != 0) {
                return byNumber;
            }
            return right.getOrDefault("run_id", "").compareTo(left.getOrDefault("run_id", ""));
        });
        return ordered;
    }

    private static boolean restoreSelectionByKey(JTable table, DefaultTableModel model, String columnName, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        int column = model.findColumn(columnName);
        if (column < 0) {
            return false;
        }
        for (int row = 0; row < model.getRowCount(); row += 1) {
            if (Objects.equals(value, String.valueOf(model.getValueAt(row, column)))) {
                table.setRowSelectionInterval(row, row);
                return true;
            }
        }
        return false;
    }

    private static String selectedCompositeKey(JTable table, DefaultTableModel model, List<String> columns) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return "";
        }
        int modelRow = table.convertRowIndexToModel(row);
        Map<String, String> values = new LinkedHashMap<>();
        for (String column : columns) {
            int columnIndex = model.findColumn(column);
            values.put(column, columnIndex >= 0 ? String.valueOf(model.getValueAt(modelRow, columnIndex)) : "");
        }
        return compositeKey(values, columns);
    }

    private static boolean restoreSelectionByCompositeKey(JTable table, DefaultTableModel model, List<String> columns, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        for (int row = 0; row < model.getRowCount(); row += 1) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String column : columns) {
                int columnIndex = model.findColumn(column);
                values.put(column, columnIndex >= 0 ? String.valueOf(model.getValueAt(row, columnIndex)) : "");
            }
            if (Objects.equals(key, compositeKey(values, columns))) {
                table.setRowSelectionInterval(row, row);
                return true;
            }
        }
        return false;
    }

    private static String compositeKey(Map<String, String> values, List<String> columns) {
        StringBuilder builder = new StringBuilder();
        for (String column : columns) {
            builder.append(values.getOrDefault(column, "")).append('\u001f');
        }
        return builder.toString();
    }

    private static void applyTable(JTable table, DefaultTableModel model, TableData data) {
        Vector<String> headers = new Vector<>(data.headers());
        Vector<Vector<String>> rows = new Vector<>();
        for (List<String> row : data.rows()) {
            rows.add(new Vector<>(row));
        }
        model.setDataVector(rows, headers);
        int implementationColumn = headers.indexOf("implementation");
        if (implementationColumn >= 0) {
            table.getColumnModel().getColumn(implementationColumn).setCellRenderer(new LanguageCellRenderer());
        }
    }

    private static TableData mapsToTableData(List<Map<String, String>> rows, List<String> preferredHeaders) {
        if (preferredHeaders == null || preferredHeaders.isEmpty()) {
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
        builder.append("runtime: ").append(values.getOrDefault("runtime_name", "")).append(" (").append(values.getOrDefault("runtime_source", "")).append(")\n");
        builder.append("pid/tid: ").append(values.getOrDefault("pid", "")).append(" / ").append(values.getOrDefault("tid", "")).append('\n');
        builder.append("scheduler: ")
            .append(values.getOrDefault("requested_priority_mode", "")).append(" -> ")
            .append(values.getOrDefault("applied_priority_mode", "")).append(" | ")
            .append(values.getOrDefault("requested_affinity_mode", "")).append(" -> ")
            .append(values.getOrDefault("applied_affinity_mode", "")).append('\n');
        builder.append("notes: ").append(values.getOrDefault("scheduler_notes", "")).append('\n');
        builder.append("checksum: ").append(values.getOrDefault("result_checksum", "")).append('\n');
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
        return values.isEmpty() ? "n/a" : String.join(", ", values);
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

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException error) {
            return 0.0;
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
