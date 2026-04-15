package cpubench.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import cpubench.api.BackendClient;
import cpubench.model.TableData;

public final class ControllerFrame {
    private final BackendClient backend;
    private final JFrame frame;
    private final JComboBox<String> profileSelector;
    private final JButton runButton;
    private final JButton refreshButton;
    private final JButton docsButton;
    private final JLabel statusPill;
    private final JProgressBar progressBar;
    private final JTextArea profileArea;
    private final JTextArea monitorArea;
    private final JTextArea detailArea;
    private final JTextArea rawLogArea;
    private final JTextArea manifestArea;
    private final JTextArea liveLogArea;
    private final JComboBox<String> implementationFilter;
    private final JComboBox<String> caseFilter;
    private final JComboBox<String> statusFilter;
    private final JCheckBox measuredOnlyToggle;
    private final JTable runsTable;
    private final JTable resultsTable;
    private final JTable eventsTable;
    private final DefaultTableModel runsModel;
    private final DefaultTableModel resultsModel;
    private final DefaultTableModel eventsModel;
    private final MetricLineChartPanel lineChart;
    private final ImplementationBarChartPanel barChart;

    private TableData profilesData = TableData.empty();
    private TableData runsData = TableData.empty();
    private TableData allResultsData = TableData.empty();
    private TableData displayedResultsData = TableData.empty();
    private TableData eventsData = TableData.empty();
    private TableData manifestData = TableData.empty();
    private String currentRunId = "";
    private String currentProfileId = "";
    private String pendingRunSelection = "";
    private Process activeProcess;
    private List<String> runEventHeaders = List.of();
    private final List<Map<String, String>> liveMetricRows = new ArrayList<>();

    public ControllerFrame(BackendClient backend) {
        this.backend = backend;
        this.frame = new JFrame("CPU Benchmark Launch Deck");
        this.profileSelector = new JComboBox<>();
        this.runButton = createButton("Run Profile", IconFactory.playIcon(16, UiPalette.WINDOW));
        this.refreshButton = createButton("Refresh", IconFactory.refreshIcon(16, UiPalette.TEXT));
        this.docsButton = createButton("Docs", IconFactory.docsIcon(16, UiPalette.TEXT));
        this.statusPill = buildStatusPill();
        this.progressBar = new JProgressBar();
        this.profileArea = buildTextArea();
        this.monitorArea = buildTextArea();
        this.detailArea = buildTextArea();
        this.rawLogArea = buildTextArea();
        this.manifestArea = buildTextArea();
        this.liveLogArea = buildTextArea();
        this.implementationFilter = new JComboBox<>(new String[] {"All"});
        this.caseFilter = new JComboBox<>(new String[] {"All"});
        this.statusFilter = new JComboBox<>(new String[] {"All", "success", "failed"});
        this.measuredOnlyToggle = new JCheckBox("Hide Warmups", true);
        this.runsModel = new DefaultTableModel();
        this.resultsModel = new DefaultTableModel();
        this.eventsModel = new DefaultTableModel();
        this.runsTable = new JTable(runsModel);
        this.resultsTable = new JTable(resultsModel);
        this.eventsTable = new JTable(eventsModel);
        this.lineChart = new MetricLineChartPanel();
        this.barChart = new ImplementationBarChartPanel();
        buildUi();
    }

    public void showWindow() {
        refreshAll();
        frame.setVisible(true);
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1560, 960));
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

        JSplitPane shell = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSidebar(), buildWorkspace());
        shell.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
        shell.setResizeWeight(0.24);
        shell.setOpaque(false);
        frame.add(shell, BorderLayout.CENTER);

        runsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadSelectedRun();
            }
        });
        resultsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                inspectSelectedResult();
            }
        });
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                if (activeProcess != null) {
                    activeProcess.destroy();
                }
            }
        });
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(menuItem("Refresh Data", IconFactory.refreshIcon(14, UiPalette.TEXT), event -> refreshAll()));
        file.add(menuItem("Exit", IconFactory.logsIcon(14, UiPalette.TEXT), event -> frame.dispose()));

        JMenu run = new JMenu("Run");
        run.add(menuItem("Run Selected Profile", IconFactory.playIcon(14, UiPalette.ACCENT), event -> startRun()));
        run.add(menuItem("Show Smoke Profile", IconFactory.chartIcon(14, UiPalette.TEXT), event -> selectProfile("smoke")));
        run.add(menuItem("Show Balanced Profile", IconFactory.chartIcon(14, UiPalette.TEXT), event -> selectProfile("balanced")));
        run.add(menuItem("Show Stress Profile", IconFactory.chartIcon(14, UiPalette.TEXT), event -> selectProfile("stress")));

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
        JLabel subtitle = new JLabel("Dark-mode mission control for runs, history, live telemetry, graphs, and raw inspection.");
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
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(320, 16));
        progressBar.setValue(0);
        statusRow.add(progressBar, BorderLayout.CENTER);
        statusRow.add(statusPill, BorderLayout.EAST);

        panel.add(actions);
        panel.add(statusRow);

        runButton.addActionListener(event -> startRun());
        refreshButton.addActionListener(event -> refreshAll());
        docsButton.addActionListener(event -> showDocument("README.md", Path.of("README.md")));
        profileSelector.addActionListener(event -> {
            String profileId = (String) profileSelector.getSelectedItem();
            if (profileId != null && !Objects.equals(profileId, currentProfileId)) {
                currentProfileId = profileId;
                loadProfilePreview(profileId);
            }
        });
        return panel;
    }

    private Component buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 12));
        sidebar.setOpaque(false);

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.add(section("Profile Blueprint", profileArea, new Dimension(320, 210)));
        stack.add(javax.swing.Box.createVerticalStrut(12));
        stack.add(section("Live Monitor", monitorArea, new Dimension(320, 200)));
        stack.add(javax.swing.Box.createVerticalStrut(12));
        stack.add(buildFilterSection());

        sidebar.add(new JScrollPane(stack), BorderLayout.CENTER);
        return sidebar;
    }

    private Component buildFilterSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(DarkTheme.panelBorder());
        JLabel title = label("Interactive Filters");
        title.setFont(UiPalette.LABEL.deriveFont(13f));
        panel.add(title, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridLayout(4, 2, 8, 8));
        controls.setOpaque(false);
        controls.add(label("Implementation"));
        controls.add(implementationFilter);
        controls.add(label("Case"));
        controls.add(caseFilter);
        controls.add(label("Status"));
        controls.add(statusFilter);
        controls.add(new JLabel());
        controls.add(measuredOnlyToggle);
        panel.add(controls, BorderLayout.CENTER);

        implementationFilter.addActionListener(event -> applyResultFilters());
        caseFilter.addActionListener(event -> applyResultFilters());
        statusFilter.addActionListener(event -> applyResultFilters());
        measuredOnlyToggle.addActionListener(event -> applyResultFilters());
        return panel;
    }

    private Component buildWorkspace() {
        JTabbedPane analyticsTabs = new JTabbedPane();
        analyticsTabs.addTab("Performance", IconFactory.chartIcon(14, UiPalette.TEXT), buildPerformanceView());
        analyticsTabs.addTab("Monitoring", IconFactory.radarIcon(14, UiPalette.TEXT), buildMonitoringView());

        JTabbedPane dataTabs = new JTabbedPane();
        dataTabs.addTab("Runs", IconFactory.logsIcon(14, UiPalette.TEXT), tableScroll(runsTable));
        dataTabs.addTab("Results", IconFactory.chartIcon(14, UiPalette.TEXT), tableScroll(resultsTable));
        dataTabs.addTab("Events", IconFactory.radarIcon(14, UiPalette.TEXT), tableScroll(eventsTable));

        JTabbedPane inspectorTabs = new JTabbedPane();
        inspectorTabs.addTab("Inspector", IconFactory.docsIcon(14, UiPalette.TEXT), textScroll(detailArea));
        inspectorTabs.addTab("Raw Log", IconFactory.logsIcon(14, UiPalette.TEXT), textScroll(rawLogArea));
        inspectorTabs.addTab("Manifest", IconFactory.docsIcon(14, UiPalette.TEXT), textScroll(manifestArea));
        inspectorTabs.addTab("Live Feed", IconFactory.radarIcon(14, UiPalette.TEXT), textScroll(liveLogArea));

        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dataTabs, inspectorTabs);
        bottom.setResizeWeight(0.62);
        bottom.setOpaque(false);

        JSplitPane center = new JSplitPane(JSplitPane.VERTICAL_SPLIT, analyticsTabs, bottom);
        center.setResizeWeight(0.44);
        center.setOpaque(false);
        return center;
    }

    private Component buildPerformanceView() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, lineChart, barChart);
        split.setResizeWeight(0.68);
        split.setOpaque(false);
        return split;
    }

    private Component buildMonitoringView() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll(eventsTable), textScroll(liveLogArea));
        split.setResizeWeight(0.6);
        split.setOpaque(false);
        return split;
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

    private JPanel section(String titleText, JTextArea area, Dimension size) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(DarkTheme.panelBorder());
        JLabel title = label(titleText);
        title.setFont(UiPalette.LABEL.deriveFont(13f));
        panel.add(title, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(area);
        scroll.getViewport().setBackground(UiPalette.SURFACE);
        scroll.setPreferredSize(size);
        scroll.setBorder(BorderFactory.createLineBorder(UiPalette.BORDER, 1, true));
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
                if (!isSelected) {
                    component.setBackground(row % 2 == 0 ? UiPalette.SURFACE : UiPalette.SURFACE_ALT);
                    component.setForeground(UiPalette.TEXT);
                    String columnName = localTable.getColumnName(column);
                    if ("status".equals(columnName)) {
                        component.setForeground(UiPalette.statusColor(String.valueOf(value)));
                    }
                }
                return component;
            }
        });
    }

    private void refreshAll() {
        setStatus("Refreshing data", UiPalette.INFO);
        runButton.setEnabled(false);
        refreshButton.setEnabled(false);

        new SwingWorker<Void, Void>() {
            private TableData loadedProfiles = TableData.empty();
            private TableData loadedRuns = TableData.empty();

            @Override
            protected Void doInBackground() throws Exception {
                loadedProfiles = backend.readTable("profiles");
                loadedRuns = backend.readTable("runs");
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    profilesData = loadedProfiles;
                    runsData = loadedRuns;
                    populateProfileSelector();
                    applyTable(runsModel, runsData);
                    if (!pendingRunSelection.isBlank()) {
                        selectRunRow(pendingRunSelection);
                        pendingRunSelection = "";
                    } else if (runsModel.getRowCount() > 0) {
                        runsTable.setRowSelectionInterval(0, 0);
                        loadSelectedRun();
                    } else {
                        applyTable(resultsModel, TableData.empty());
                        applyTable(eventsModel, TableData.empty());
                    }
                    setStatus("Deck ready", UiPalette.SUCCESS);
                } catch (Exception error) {
                    setStatus("Refresh failed", UiPalette.DANGER);
                    liveLogArea.append("[refresh-error] " + error.getMessage() + "\n");
                } finally {
                    runButton.setEnabled(profileSelector.getItemCount() > 0);
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void populateProfileSelector() {
        String currentSelection = (String) profileSelector.getSelectedItem();
        profileSelector.removeAllItems();
        for (Map<String, String> row : profilesData.asMaps()) {
            profileSelector.addItem(row.getOrDefault("profile_id", ""));
        }
        if (currentSelection != null) {
            profileSelector.setSelectedItem(currentSelection);
        }
        if (profileSelector.getSelectedItem() == null && profileSelector.getItemCount() > 0) {
            profileSelector.setSelectedIndex(0);
        }
        currentProfileId = (String) profileSelector.getSelectedItem();
        if (currentProfileId != null && !currentProfileId.isBlank()) {
            loadProfilePreview(currentProfileId);
        }
    }

    private void selectProfile(String profileId) {
        profileSelector.setSelectedItem(profileId);
        currentProfileId = profileId;
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
                            .append('\n')
                            .append('\n');
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
        currentRunId = runId;
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
                    allResultsData = loadedResults;
                    eventsData = loadedEvents;
                    manifestData = loadedManifest;
                    applyTable(eventsModel, eventsData);
                    manifestArea.setText(formatKeyValueTable(manifestData));
                    populateFilters();
                    applyResultFilters();
                    setStatus("Loaded " + runId, UiPalette.SUCCESS);
                } catch (Exception error) {
                    setStatus("Run load failed", UiPalette.DANGER);
                    detailArea.setText("Could not load run " + runId + "\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void populateFilters() {
        repopulateCombo(implementationFilter, allResultsData.distinctValues("implementation"));
        repopulateCombo(caseFilter, allResultsData.distinctValues("case_id"));
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

    private void applyResultFilters() {
        List<Map<String, String>> filtered = new ArrayList<>();
        String implementation = (String) implementationFilter.getSelectedItem();
        String caseId = (String) caseFilter.getSelectedItem();
        String status = (String) statusFilter.getSelectedItem();
        boolean measuredOnly = measuredOnlyToggle.isSelected();

        for (Map<String, String> row : allResultsData.asMaps()) {
            if (!"All".equals(implementation) && !Objects.equals(implementation, row.get("implementation"))) {
                continue;
            }
            if (!"All".equals(caseId) && !Objects.equals(caseId, row.get("case_id"))) {
                continue;
            }
            if (!"All".equals(status) && !Objects.equals(status, row.get("status"))) {
                continue;
            }
            if (measuredOnly && "true".equals(row.get("warmup"))) {
                continue;
            }
            filtered.add(row);
        }

        displayedResultsData = mapsToTableData(filtered, allResultsData.headers());
        applyTable(resultsModel, displayedResultsData);
        if (resultsModel.getRowCount() > 0) {
            resultsTable.setRowSelectionInterval(0, 0);
            inspectSelectedResult();
        } else {
            detailArea.setText("No results match the current filters.");
            rawLogArea.setText("");
        }
        updateCharts(filtered);
        updateMonitorSummary(filtered);
    }

    private void updateMonitorSummary(List<Map<String, String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("Selected run: ").append(currentRunId.isBlank() ? "none" : currentRunId).append('\n');
        builder.append("Visible samples: ").append(rows.size()).append('\n');
        builder.append("Filters: ")
            .append(implementationFilter.getSelectedItem()).append(" / ")
            .append(caseFilter.getSelectedItem()).append(" / ")
            .append(statusFilter.getSelectedItem()).append('\n');
        if (!rows.isEmpty()) {
            Map<String, String> first = rows.get(0);
            builder.append("Timer: ").append(metricKind(first)).append('\n');
        }
        monitorArea.setText(builder.toString());
    }

    private void updateCharts(List<Map<String, String>> rows) {
        Map<String, List<Double>> seriesMap = new LinkedHashMap<>();
        Map<String, String> units = new LinkedHashMap<>();
        Map<String, Double> barValues = new LinkedHashMap<>();

        for (Map<String, String> row : rows) {
            Double metric = metric(row);
            if (metric == null) {
                continue;
            }
            String unit = metricKind(row);
            String key = row.getOrDefault("implementation", "") + " · " + row.getOrDefault("case_id", "");
            seriesMap.computeIfAbsent(key, ignored -> new ArrayList<>()).add(metric);
            units.put(key, unit);
            barValues.merge(row.getOrDefault("implementation", ""), metric, Math::min);
        }

        List<MetricLineChartPanel.Series> series = new ArrayList<>();
        int colorIndex = 0;
        for (Map.Entry<String, List<Double>> entry : seriesMap.entrySet()) {
            series.add(new MetricLineChartPanel.Series(entry.getKey(), UiPalette.seriesColor(colorIndex), entry.getValue()));
            colorIndex += 1;
        }
        String unit = seriesMap.isEmpty() ? "ns/iter" : units.values().iterator().next();
        lineChart.setSeries(
            currentRunId.isBlank() ? "Metric Trend" : "Metric Trend · " + currentRunId,
            rows.isEmpty() ? "No data in the current filter." : "Repeat-level metrics rendered from stored result rows only.",
            unit,
            series
        );

        List<ImplementationBarChartPanel.Bar> bars = new ArrayList<>();
        int implementationColor = 0;
        for (Map.Entry<String, Double> entry : barValues.entrySet()) {
            bars.add(new ImplementationBarChartPanel.Bar(entry.getKey(), UiPalette.seriesColor(implementationColor), entry.getValue()));
            implementationColor += 1;
        }
        barChart.setBars("Best By Implementation", "Minimum visible metric for the current selection.", unit, bars);
    }

    private void inspectSelectedResult() {
        int row = resultsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = resultsTable.convertRowIndexToModel(row);
        Map<String, String> values = displayedResultsData.rowAsMap(modelRow);
        detailArea.setText(formatDetail(values));

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

    private void startRun() {
        String profileId = (String) profileSelector.getSelectedItem();
        if (profileId == null || profileId.isBlank()) {
            return;
        }

        runButton.setEnabled(false);
        refreshButton.setEnabled(false);
        docsButton.setEnabled(false);
        setStatus("Running " + profileId, UiPalette.INFO);
        progressBar.setValue(0);
        progressBar.setMaximum(100);
        liveLogArea.setText("=== Launching " + profileId + " ===\n");
        monitorArea.setText("Run in progress…\n");
        runEventHeaders = List.of();
        liveMetricRows.clear();
        eventsData = TableData.empty();
        pendingRunSelection = "";
        applyTable(eventsModel, TableData.empty());

        new SwingWorker<Integer, Map<String, String>>() {
            @Override
            protected Integer doInBackground() throws Exception {
                activeProcess = backend.startRunProfile(profileId);
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
                try {
                    get();
                } catch (Exception error) {
                    liveLogArea.append("[run-error] " + error.getMessage() + "\n");
                } finally {
                    runButton.setEnabled(true);
                    refreshButton.setEnabled(true);
                    docsButton.setEnabled(true);
                    if (!pendingRunSelection.isBlank()) {
                        refreshAll();
                    } else {
                        setStatus("Run finished", UiPalette.SUCCESS);
                    }
                }
            }
        }.execute();
    }

    private void handleLiveEvent(Map<String, String> event) {
        appendLiveLog(event);
        updateProgress(event);
        pushLiveEventTable(event);
        if ("finished".equals(event.get("phase"))) {
            liveMetricRows.add(new LinkedHashMap<>(Map.of(
                "implementation", event.getOrDefault("implementation", ""),
                "case_id", event.getOrDefault("case_id", ""),
                "repeat_index", event.getOrDefault("repeat_index", ""),
                "warmup", event.getOrDefault("warmup", ""),
                "ns_per_iteration", "ns/iter".equals(event.getOrDefault("metric_kind", "")) ? event.getOrDefault("metric", "") : "",
                "legacy_cycles_per_iteration", "cycles/iter".equals(event.getOrDefault("metric_kind", "")) ? event.getOrDefault("metric", "") : "",
                "status", event.getOrDefault("status", ""),
                "timer_kind", event.getOrDefault("timer_kind", ""),
                "elapsed_ns", event.getOrDefault("elapsed_ns", "")
            )));
            updateCharts(liveMetricRows);
        }
        if ("completed".equals(event.get("phase"))) {
            pendingRunSelection = event.getOrDefault("run_id", "");
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
        progressBar.setStringPainted(true);
        StringBuilder builder = new StringBuilder();
        builder.append("Active profile: ").append(event.getOrDefault("profile_id", currentProfileId)).append('\n');
        builder.append("Progress: ").append(stepIndex).append(" / ").append(stepTotal).append('\n');
        builder.append("Phase: ").append(event.getOrDefault("phase", "")).append('\n');
        if (!event.getOrDefault("implementation", "").isBlank()) {
            builder.append("Implementation: ").append(event.get("implementation")).append('\n');
        }
        if (!event.getOrDefault("case_id", "").isBlank()) {
            builder.append("Case: ").append(event.get("case_id")).append('\n');
        }
        if (!event.getOrDefault("timer_kind", "").isBlank()) {
            builder.append("Timer: ").append(event.get("timer_kind")).append('\n');
        }
        if (!event.getOrDefault("metric", "").isBlank()) {
            builder.append("Latest metric: ").append(event.get("metric")).append(' ').append(event.getOrDefault("metric_kind", "")).append('\n');
        }
        monitorArea.setText(builder.toString());
    }

    private void pushLiveEventTable(Map<String, String> event) {
        List<Map<String, String>> rows = eventsData.asMaps().isEmpty() ? new ArrayList<>() : new ArrayList<>(eventsData.asMaps());
        rows.add(event);
        eventsData = mapsToTableData(rows, List.of(
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
        ));
        applyTable(eventsModel, eventsData);
    }

    private void setStatus(String text, java.awt.Color color) {
        statusPill.setText(text);
        statusPill.setBackground(color);
        statusPill.setForeground(color.equals(UiPalette.ACCENT) || color.equals(UiPalette.WARNING) ? UiPalette.WINDOW : UiPalette.WINDOW);
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
            dialog.setSize(900, 720);
            dialog.setLocationRelativeTo(frame);
            dialog.setVisible(true);
        } catch (IOException error) {
            JOptionPane.showMessageDialog(frame, "Unable to open " + relativePath + "\n" + error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
        }
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

}
