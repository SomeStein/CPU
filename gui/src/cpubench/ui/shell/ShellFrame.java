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
    private final List<Map<String, String>> liveMetricRows = new ArrayList<>();
    private Activity activeActivity = Activity.RUNS;

    public ShellFrame(BackendClient backend) {
        this.backend = backend;
        this.state = new ControllerState();
        this.frame = new JFrame("CPU Lab");
        this.activityBar = new ActivityBar(this::handleActivitySelection);
        this.sidePanel = new SidePanel();
        this.workspaceTabs = new WorkspaceTabs();
        this.statusBar = new StatusBar();
        this.profileBuilderPanel = new ProfileBuilderPanel(backend, this::refreshAll, this::startRunProfileFile, this::selectProfile);
        buildUi();
    }

    public void showWindow() {
        refreshAll();
        frame.setSize(1480, 920);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
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

        openScreen("global", "Global Overview", IconFactory.chartIcon(14, UiPalette.TEXT), buildGlobalOverviewScreen(), false);
        openScreen("monitor", "Live Monitor", IconFactory.radarIcon(14, UiPalette.TEXT), buildLiveMonitorScreen(), false);
        openScreen("builder", "Run Config", IconFactory.docsIcon(14, UiPalette.TEXT), profileBuilderPanel, false);
        workspaceTabs.setSelectedIndex(0);

        statusBar.runButton().addActionListener(event -> startRun());
        statusBar.refreshButton().addActionListener(event -> refreshAll());
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
        file.add(menuItem("Refresh", event -> refreshAll()));
        file.add(menuItem("Run Selected Profile", event -> startRun()));
        file.add(menuItem("Open Builder", event -> workspaceTabs.setSelectedComponent(tabsByKey.get("builder"))));
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
        runImplementationFilter.addActionListener(event -> applyRunFilters());
        runCaseFilter.addActionListener(event -> applyRunFilters());
        runStatusFilter.addActionListener(event -> applyRunFilters());
        runMeasuredOnlyToggle.addActionListener(event -> applyRunFilters());
        globalProfileFilter.addActionListener(event -> applyGlobalFilters());
        globalImplementationFilter.addActionListener(event -> applyGlobalFilters());
        globalCaseFilter.addActionListener(event -> applyGlobalFilters());
        globalStatusFilter.addActionListener(event -> applyGlobalFilters());
        globalMeasuredOnlyToggle.addActionListener(event -> applyGlobalFilters());
    }

    private void installWindowShortcuts() {
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F5"), "refresh");
        frame.getRootPane().getActionMap().put("refresh", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refreshAll();
            }
        });
    }

    private JComponent buildRunsSideView() {
        JPanel panel = sideCard("Runs");
        panel.add(labelledField("Search", runSearchField), BorderLayout.NORTH);
        panel.add(tableScroll(runsTable), BorderLayout.CENTER);
        panel.add(textSection("Selected Run", selectedRunArea, null), BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildAnalysisSideView() {
        JPanel shell = sideCard("Analysis Filters");
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
        panel.add(textSection("Live Status", liveStatusArea, new Dimension(0, 180)), BorderLayout.NORTH);
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
        JButton builderButton = button("Open Builder", UiPalette.ACCENT);
        builderButton.addActionListener(event -> workspaceTabs.setSelectedComponent(tabsByKey.get("builder")));
        panel.add(builderButton, BorderLayout.NORTH);
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
        setStatus("Refreshing data", UiPalette.INFO);
        setControlsEnabled(false);

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
                    applyRunsSearch();
                    populateGlobalFilters();
                    applyGlobalFilters();
                    if (!pendingRunSelection.isBlank()) {
                        selectRunRow(pendingRunSelection, true);
                        pendingRunSelection = "";
                    } else if (!state.currentRunId().isBlank()) {
                        selectRunRow(state.currentRunId(), false);
                    } else if (runsModel.getRowCount() > 0) {
                        runsTable.setRowSelectionInterval(0, 0);
                        loadSelectedRun(false);
                    } else {
                        clearRunWorkspace();
                    }
                    setStatus("CPU Lab ready", UiPalette.SUCCESS);
                } catch (Exception error) {
                    setStatus("Refresh failed", UiPalette.DANGER);
                    liveLogArea.append("[refresh-error] " + error.getMessage() + "\n");
                    statusBar.setHealth(UiPalette.DANGER);
                } finally {
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
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, String> row : state.runs().asMaps()) {
            if (needle.isBlank() || matchesRunSearch(row, needle)) {
                rows.add(row);
            }
        }
        applyTable(runsTable, runsModel, mapsToTableData(rows, state.runs().headers()));
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
                    applyTable(eventsTable, eventsModel, state.runEvents());
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
        applyTable(resultsTable, resultsModel, filtered);
        updateRunCharts(filtered.asMaps());
        renderSelectedRunSummary();
        renderLiveMonitorSnapshot(null);
        if (resultsModel.getRowCount() > 0) {
            resultsTable.setRowSelectionInterval(0, 0);
            inspectSelectedRunResult(false);
        }
    }

    private void applyGlobalFilters() {
        state.setGlobalFilter(currentGlobalFilter());
        TableData filtered = state.filteredGlobalResults();
        applyTable(globalResultsTable, globalResultsModel, filtered);
        updateGlobalCharts(filtered.asMaps());
        renderGlobalSummary(filtered.asMaps());
        if (globalResultsModel.getRowCount() > 0) {
            globalResultsTable.setRowSelectionInterval(0, 0);
            inspectSelectedGlobalResult(false);
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

        List<Map<String, String>> events = state.runEvents().asMaps();
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
            series.add(new InteractiveTrendChart.Series(implId, colors.get(entry.getKey()), entry.getValue()));
        }
        runChart.setPresentation(
            state.currentRunId().isBlank() ? "Run Metric Trend" : "Run Metric Trend · " + state.currentRunId(),
            rows.isEmpty() ? "No stored result rows match the current run filter." : "Drag to pan. Wheel to zoom.",
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

        List<Map<String, String>> orderedRuns = new ArrayList<>(state.runs().asMaps());
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
            series.add(new InteractiveTrendChart.Series(entry.getKey(), colors.get(entry.getKey()), entry.getValue()));
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
        liveMetricRows.clear();
        state.setRunEvents(TableData.empty());
        applyTable(eventsTable, eventsModel, TableData.empty());
        liveChart.resetView();
        liveChart.setPresentation("Live Metric Trend", "Finished samples will appear here as the profile runs.", "ns/iter", "Repeat");
        liveChart.setSeries(List.of());
        workspaceTabs.setSelectedComponent(tabsByKey.get("monitor"));
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
                for (Map<String, String> event : chunks) {
                    handleLiveEvent(event);
                }
            }

            @Override
            protected void done() {
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

    private void stopRun() {
        if (activeProcess != null) {
            activeProcess.destroy();
            statusBar.setLastEvent("Run stop requested");
        }
    }

    private void handleLiveEvent(Map<String, String> event) {
        appendLiveLog(event);
        updateProgress(event);
        renderLiveMonitorSnapshot(event);
        pushLiveEventTable(event);
        monitorTailArea.setText(monitorTailText(event));
        statusBar.setLastEvent(event.getOrDefault("message", event.getOrDefault("phase", "")));
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
            String implementation = event.getOrDefault("implementation", "");
            liveChart.appendPoint(
                implementation,
                new PointRecord(
                    parseInt(event.getOrDefault("repeat_index", "0")),
                    parseDouble(event.getOrDefault("metric", "0")),
                    implementation,
                    event.getOrDefault("case_id", ""),
                    parseInt(event.getOrDefault("repeat_index", "0"))
                )
            );
        }
        if ("completed".equals(event.get("phase"))) {
            pendingRunSelection = event.getOrDefault("run_id", "");
            state.setCurrentRunId(pendingRunSelection);
            setStatus("Run complete: " + pendingRunSelection, UiPalette.SUCCESS);
        }
    }

    private void appendLiveLog(Map<String, String> event) {
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
        liveLogArea.append(builder.append('\n').toString());
        liveLogArea.setCaretPosition(liveLogArea.getDocument().getLength());
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

    private void pushLiveEventTable(Map<String, String> event) {
        List<Map<String, String>> rows = new ArrayList<>(state.runEvents().asMaps());
        rows.add(event);
        state.setRunEvents(mapsToTableData(rows, LIVE_EVENT_HEADERS));
        applyTable(eventsTable, eventsModel, state.runEvents());
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
        openScreen("run", "Run Overview", IconFactory.chartIcon(14, UiPalette.TEXT), buildRunOverviewScreen(), true);
    }

    private void openArtifactsTab() {
        openScreen("artifacts", "Artifacts", IconFactory.docsIcon(14, UiPalette.TEXT), buildArtifactsScreen(), true);
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
        switch (activity) {
            case RUNS -> workspaceTabs.setSelectedComponent(tabsByKey.get("global"));
            case ANALYSIS -> workspaceTabs.setSelectedComponent(tabsByKey.get("global"));
            case MONITOR -> workspaceTabs.setSelectedComponent(tabsByKey.get("monitor"));
            case ARTIFACTS -> openArtifactsTab();
            case CONFIG -> workspaceTabs.setSelectedComponent(tabsByKey.get("builder"));
            case DOCS -> openDocumentTab("README", Path.of("README.md"));
        }
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

        JPanel panel = new JPanel(new BorderLayout(0, UiPalette.GAP_SM));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(DarkTheme.panelBorder());
        JLabel title = label(titleText);
        title.setFont(UiPalette.LABEL.deriveFont(13f));
        panel.add(title, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel sideCard(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, UiPalette.GAP_MD));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(DarkTheme.panelBorder());
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
