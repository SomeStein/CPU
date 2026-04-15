package cpubench.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import cpubench.api.BackendClient;
import cpubench.model.ImplementationCatalogEntry;
import cpubench.model.ProfileCaseDraft;
import cpubench.model.ProfileDraft;
import cpubench.model.ProfileOverrideDraft;
import cpubench.model.TableData;
import cpubench.ui.icons.LanguageIconRegistry;

public final class ProfileBuilderPanel extends JPanel {
    private final BackendClient backend;
    private final Runnable refreshDeck;
    private final Consumer<Path> runProfileFile;
    private final Consumer<String> selectProfile;
    private final JComboBox<ProfileRef> profilePicker = new JComboBox<>();
    private final JLabel sourceBadge = new JLabel("Custom");
    private final JLabel modeBadge = new JLabel("Editable");
    private final JTextField idField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JSpinner warmupsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JSpinner repeatsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JComboBox<String> priorityCombo = new JComboBox<>(new String[] {"high", "unchanged"});
    private final JComboBox<String> affinityCombo = new JComboBox<>(new String[] {"single_core", "unchanged"});
    private final JComboBox<String> timerCombo = new JComboBox<>(new String[] {"monotonic_ns"});
    private final DefaultTableModel casesModel = new DefaultTableModel(new Object[] {"case_id", "iterations", "parallel_chains", "priority_mode", "affinity_mode", "timer_mode"}, 0);
    private final DefaultTableModel overridesModel = new DefaultTableModel(new Object[] {"implementation", "iterations", "parallel_chains", "priority_mode", "affinity_mode", "timer_mode"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column > 0;
        }
    };
    private final JTable casesTable = new JTable(casesModel);
    private final JTable overridesTable = new JTable(overridesModel);
    private final JTextArea validationArea = buildTextArea();
    private final JTextArea implementationArea = buildTextArea();
    private final Map<String, JCheckBox> implementationBoxes = new LinkedHashMap<>();
    private final List<ImplementationCatalogEntry> catalog = new ArrayList<>();
    private final JPanel implementationChecklist = new JPanel();

    private ProfileDraft baselineDraft = ProfileDraft.blank();
    private ProfileDraft workingDraft = ProfileDraft.blank();
    private boolean applyingDraft;
    private int activeOverrideCaseIndex = -1;

    public ProfileBuilderPanel(
        BackendClient backend,
        Runnable refreshDeck,
        Consumer<Path> runProfileFile,
        Consumer<String> selectProfile
    ) {
        this.backend = backend;
        this.refreshDeck = refreshDeck;
        this.runProfileFile = runProfileFile;
        this.selectProfile = selectProfile;
        setOpaque(false);
        setLayout(new BorderLayout(0, 12));
        buildUi();
        applyDraft(ProfileDraft.blank(), "Create a custom profile, save it to testruns/custom, or run it directly as a temporary draft.");
    }

    public void reloadLookups(TableData profiles, TableData implementationCatalog) {
        catalog.clear();
        for (Map<String, String> row : implementationCatalog.asMaps()) {
            catalog.add(ImplementationCatalogEntry.fromRow(row));
        }
        catalog.sort(Comparator.comparing(ImplementationCatalogEntry::language).thenComparing(ImplementationCatalogEntry::implementationId));
        rebuildImplementationChecklist();
        for (Map.Entry<String, JCheckBox> entry : implementationBoxes.entrySet()) {
            entry.getValue().setSelected(workingDraft.implementations().contains(entry.getKey()));
        }

        ProfileRef selected = (ProfileRef) profilePicker.getSelectedItem();
        String preferredProfileId = workingDraft.id();
        profilePicker.removeAllItems();
        for (Map<String, String> row : profiles.asMaps()) {
            profilePicker.addItem(new ProfileRef(row.getOrDefault("profile_id", ""), row.getOrDefault("name", ""), row.getOrDefault("source", "")));
        }
        if (!preferredProfileId.isBlank()) {
            selectProfileRef(preferredProfileId);
        } else if (selected != null) {
            selectProfileRef(selected.profileId());
        }
        renderImplementationSummary();
    }

    private void buildUi() {
        add(buildActionBar(), BorderLayout.NORTH);

        JSplitPaneLike horizontal = new JSplitPaneLike(
            buildLeftStack(),
            buildEditorStack(),
            0.30
        );
        add(horizontal, BorderLayout.CENTER);
    }

    private Component buildActionBar() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        left.add(sectionLabel("Profile Source"));
        profilePicker.setPreferredSize(new Dimension(230, 34));
        left.add(profilePicker);
        JButton loadButton = button("Load", UiPalette.SURFACE);
        JButton duplicateButton = button("Duplicate Built-In", UiPalette.SURFACE);
        JButton newButton = button("New", UiPalette.SURFACE);
        left.add(loadButton);
        left.add(duplicateButton);
        left.add(newButton);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        JButton saveButton = button("Save", UiPalette.ACCENT);
        JButton saveAsButton = button("Save As", UiPalette.SURFACE);
        JButton runNowButton = button("Run Now", UiPalette.ACCENT_ALT);
        JButton revertButton = button("Revert", UiPalette.SURFACE);
        right.add(saveButton);
        right.add(saveAsButton);
        right.add(runNowButton);
        right.add(revertButton);

        loadButton.addActionListener(event -> loadSelectedProfile());
        duplicateButton.addActionListener(event -> duplicateCurrentProfile());
        newButton.addActionListener(event -> applyDraft(ProfileDraft.blank(), "Started a new editable custom draft."));
        saveButton.addActionListener(event -> saveDraft(false));
        saveAsButton.addActionListener(event -> saveDraft(true));
        runNowButton.addActionListener(event -> runDraftNow());
        revertButton.addActionListener(event -> applyDraft(baselineDraft.copy(), "Reverted to the last loaded or saved draft."));

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private Component buildLeftStack() {
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(buildMetadataPanel());
        stack.add(gap());
        stack.add(buildDefaultsPanel());
        stack.add(gap());
        stack.add(buildImplementationPanel());
        stack.add(gap());
        stack.add(buildValidationPanel());
        JScrollPane scrollPane = new JScrollPane(stack);
        scrollPane.getViewport().setBackground(UiPalette.WINDOW);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private Component buildEditorStack() {
        JPanel shell = new JPanel(new BorderLayout(0, 12));
        shell.setOpaque(false);
        shell.add(buildCasesPanel(), BorderLayout.CENTER);
        shell.add(buildOverridesPanel(), BorderLayout.SOUTH);
        return shell;
    }

    private Component buildMetadataPanel() {
        JPanel panel = sectionPanel("Profile Metadata");
        JPanel grid = new JPanel(new GridLayout(4, 2, 10, 10));
        grid.setOpaque(false);
        grid.add(sectionLabel("Profile ID"));
        grid.add(idField);
        grid.add(sectionLabel("Display Name"));
        grid.add(nameField);
        grid.add(sectionLabel("Source"));
        grid.add(sourceBadge);
        grid.add(sectionLabel("Mode"));
        grid.add(modeBadge);
        styleBadge(sourceBadge);
        styleBadge(modeBadge);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private Component buildDefaultsPanel() {
        JPanel panel = sectionPanel("Defaults");
        JPanel grid = new JPanel(new GridLayout(3, 4, 10, 10));
        grid.setOpaque(false);
        grid.add(sectionLabel("Warmups"));
        grid.add(warmupsSpinner);
        grid.add(sectionLabel("Repeats"));
        grid.add(repeatsSpinner);
        grid.add(sectionLabel("Priority"));
        grid.add(priorityCombo);
        grid.add(sectionLabel("Affinity"));
        grid.add(affinityCombo);
        grid.add(sectionLabel("Timer"));
        grid.add(timerCombo);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private Component buildImplementationPanel() {
        JPanel panel = sectionPanel("Implementation Selection");
        implementationChecklist.setOpaque(false);
        implementationChecklist.setLayout(new BoxLayout(implementationChecklist, BoxLayout.Y_AXIS));
        JScrollPane checklistScroll = new JScrollPane(implementationChecklist);
        checklistScroll.getViewport().setBackground(UiPalette.SURFACE);
        checklistScroll.setBorder(BorderFactory.createLineBorder(UiPalette.BORDER, 1, true));
        checklistScroll.setPreferredSize(new Dimension(320, 260));
        implementationArea.setRows(6);
        panel.add(checklistScroll, BorderLayout.CENTER);
        panel.add(textScroll(implementationArea), BorderLayout.SOUTH);
        return panel;
    }

    private Component buildValidationPanel() {
        JPanel panel = sectionPanel("Validation / Activity");
        validationArea.setRows(8);
        validationArea.setText("Validation and save feedback will appear here.");
        panel.add(textScroll(validationArea), BorderLayout.CENTER);
        return panel;
    }

    private Component buildCasesPanel() {
        JPanel panel = sectionPanel("Case Matrix");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        JButton addButton = button("Add Case", UiPalette.SURFACE);
        JButton duplicateButton = button("Duplicate", UiPalette.SURFACE);
        JButton upButton = button("Move Up", UiPalette.SURFACE);
        JButton downButton = button("Move Down", UiPalette.SURFACE);
        JButton removeButton = button("Remove", UiPalette.SURFACE);
        buttons.add(addButton);
        buttons.add(duplicateButton);
        buttons.add(upButton);
        buttons.add(downButton);
        buttons.add(removeButton);

        panel.add(buttons, BorderLayout.NORTH);
        panel.add(tableScroll(casesTable), BorderLayout.CENTER);

        addButton.addActionListener(event -> addCase());
        duplicateButton.addActionListener(event -> duplicateCase());
        upButton.addActionListener(event -> moveCase(-1));
        downButton.addActionListener(event -> moveCase(1));
        removeButton.addActionListener(event -> removeCase());
        casesTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadOverridesForSelectedCase();
            }
        });
        return panel;
    }

    private Component buildOverridesPanel() {
        JPanel panel = sectionPanel("Per-Implementation Overrides");
        panel.setPreferredSize(new Dimension(0, 280));
        panel.add(tableScroll(overridesTable), BorderLayout.CENTER);
        return panel;
    }

    private void rebuildImplementationChecklist() {
        implementationBoxes.clear();
        implementationChecklist.removeAll();
        String currentLanguage = "";
        for (ImplementationCatalogEntry entry : catalog) {
            if (!Objects.equals(currentLanguage, entry.language())) {
                JLabel title = sectionLabel(entry.language().toUpperCase());
                title.setForeground(UiPalette.ACCENT_ALT);
                implementationChecklist.add(title);
                currentLanguage = entry.language();
            }
            JCheckBox box = new JCheckBox(entry.displayName());
            box.setOpaque(false);
            box.setForeground(UiPalette.TEXT);
            box.setToolTipText(entry.description());
            box.setIcon(LanguageIconRegistry.icon(entry.implementationId(), 16));
            box.addActionListener(event -> {
                if (!applyingDraft) {
                    renderImplementationSummary();
                    reloadOverrideRows();
                }
            });
            implementationBoxes.put(entry.implementationId(), box);
            implementationChecklist.add(box);
        }
        implementationChecklist.revalidate();
        implementationChecklist.repaint();
    }

    private void loadSelectedProfile() {
        ProfileRef selected = (ProfileRef) profilePicker.getSelectedItem();
        if (selected == null || selected.profileId().isBlank()) {
            return;
        }
        validationArea.setText("Loading profile " + selected.profileId() + " …");
        new SwingWorker<ProfileDraft, Void>() {
            @Override
            protected ProfileDraft doInBackground() throws Exception {
                TableData details = backend.readTable("profile", selected.profileId());
                TableData overrides = backend.readTable("profile-overrides", selected.profileId());
                return ProfileDraft.fromTables(details, overrides);
            }

            @Override
            protected void done() {
                try {
                    applyDraft(get(), "Loaded profile " + selected.profileId() + " into the builder.");
                } catch (Exception error) {
                    validationArea.setText("Unable to load profile.\n" + error.getMessage());
                }
            }
        }.execute();
    }

    private void applyDraft(ProfileDraft draft, String message) {
        baselineDraft = draft.copy();
        workingDraft = draft.copy();
        applyingDraft = true;
        try {
            idField.setText(workingDraft.id());
            nameField.setText(workingDraft.name());
            warmupsSpinner.setValue(parseInt(workingDraft.warmups(), 1));
            repeatsSpinner.setValue(parseInt(workingDraft.repeats(), 1));
            priorityCombo.setSelectedItem(workingDraft.priorityMode());
            affinityCombo.setSelectedItem(workingDraft.affinityMode());
            timerCombo.setSelectedItem(workingDraft.timerMode());
            sourceBadge.setText(workingDraft.source());
            modeBadge.setText(workingDraft.editable() ? "Editable" : "Read-only template");
            styleBadge(sourceBadge);
            styleBadge(modeBadge);
            for (Map.Entry<String, JCheckBox> entry : implementationBoxes.entrySet()) {
                entry.getValue().setSelected(workingDraft.implementations().contains(entry.getKey()));
            }
            casesModel.setRowCount(0);
            for (ProfileCaseDraft caseDraft : workingDraft.cases()) {
                casesModel.addRow(new Object[] {
                    caseDraft.caseId(),
                    caseDraft.iterations(),
                    caseDraft.parallelChains(),
                    caseDraft.priorityMode(),
                    caseDraft.affinityMode(),
                    caseDraft.timerMode(),
                });
            }
            if (casesModel.getRowCount() > 0) {
                casesTable.setRowSelectionInterval(0, 0);
            }
            activeOverrideCaseIndex = -1;
            reloadOverrideRows();
            renderImplementationSummary();
            validationArea.setText(message);
            if (profileExists(workingDraft.id())) {
                selectProfile.accept(workingDraft.id());
            }
        } finally {
            applyingDraft = false;
        }
    }

    private void syncDraftFromUi() {
        if (applyingDraft) {
            return;
        }
        storeOverridesForActiveCase();
        workingDraft.setId(idField.getText());
        workingDraft.setName(nameField.getText());
        workingDraft.setWarmups(String.valueOf(warmupsSpinner.getValue()));
        workingDraft.setRepeats(String.valueOf(repeatsSpinner.getValue()));
        workingDraft.setPriorityMode(String.valueOf(priorityCombo.getSelectedItem()));
        workingDraft.setAffinityMode(String.valueOf(affinityCombo.getSelectedItem()));
        workingDraft.setTimerMode(String.valueOf(timerCombo.getSelectedItem()));

        LinkedHashSet<String> selectedImplementations = new LinkedHashSet<>();
        for (Map.Entry<String, JCheckBox> entry : implementationBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedImplementations.add(entry.getKey());
            }
        }
        workingDraft.implementations().clear();
        workingDraft.implementations().addAll(selectedImplementations);

        List<ProfileCaseDraft> preservedCases = new ArrayList<>();
        for (ProfileCaseDraft caseDraft : workingDraft.cases()) {
            preservedCases.add(caseDraft.copy());
        }
        workingDraft.cases().clear();
        for (int row = 0; row < casesModel.getRowCount(); row += 1) {
            ProfileCaseDraft caseDraft = row < preservedCases.size() ? preservedCases.get(row) : new ProfileCaseDraft();
            caseDraft.setCaseId(String.valueOf(casesModel.getValueAt(row, 0)));
            caseDraft.setIterations(String.valueOf(casesModel.getValueAt(row, 1)));
            caseDraft.setParallelChains(String.valueOf(casesModel.getValueAt(row, 2)));
            caseDraft.setPriorityMode(String.valueOf(casesModel.getValueAt(row, 3)));
            caseDraft.setAffinityMode(String.valueOf(casesModel.getValueAt(row, 4)));
            caseDraft.setTimerMode(String.valueOf(casesModel.getValueAt(row, 5)));
            workingDraft.cases().add(caseDraft);
        }
    }

    private void renderImplementationSummary() {
        syncDraftFromUi();
        Map<String, Integer> languageCounts = new LinkedHashMap<>();
        for (ImplementationCatalogEntry entry : catalog) {
            if (workingDraft.implementations().contains(entry.implementationId())) {
                languageCounts.merge(entry.language(), 1, Integer::sum);
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Selected implementations: ").append(workingDraft.implementations().size()).append('\n');
        if (workingDraft.implementations().isEmpty()) {
            builder.append("No implementations selected yet.\n");
        } else {
            for (Map.Entry<String, Integer> entry : languageCounts.entrySet()) {
                builder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
            }
        }
        builder.append('\n').append("Current source: ").append(workingDraft.source()).append('\n');
        builder.append("Cases: ").append(casesModel.getRowCount()).append('\n');
        implementationArea.setText(builder.toString());
    }

    private void reloadOverrideRows() {
        if (applyingDraft) {
            return;
        }
        storeOverridesForActiveCase();
        overridesModel.setRowCount(0);
        int row = casesTable.getSelectedRow();
        if (row < 0 || row >= workingDraft.cases().size()) {
            activeOverrideCaseIndex = -1;
            return;
        }
        activeOverrideCaseIndex = row;
        ProfileCaseDraft caseDraft = workingDraft.cases().get(row);
        for (String implementationId : workingDraft.implementations()) {
            ProfileOverrideDraft override = caseDraft.overrides().getOrDefault(implementationId, new ProfileOverrideDraft(implementationId));
            overridesModel.addRow(new Object[] {
                implementationId,
                override.iterations(),
                override.parallelChains(),
                override.priorityMode(),
                override.affinityMode(),
                override.timerMode(),
            });
        }
    }

    private void loadOverridesForSelectedCase() {
        if (applyingDraft) {
            return;
        }
        syncDraftFromUi();
        reloadOverrideRows();
    }

    private void storeOverridesForActiveCase() {
        if (activeOverrideCaseIndex < 0 || activeOverrideCaseIndex >= workingDraft.cases().size()) {
            return;
        }
        ProfileCaseDraft caseDraft = workingDraft.cases().get(activeOverrideCaseIndex);
        caseDraft.overrides().clear();
        for (int row = 0; row < overridesModel.getRowCount(); row += 1) {
            ProfileOverrideDraft override = new ProfileOverrideDraft(String.valueOf(overridesModel.getValueAt(row, 0)));
            override.setIterations(String.valueOf(overridesModel.getValueAt(row, 1)));
            override.setParallelChains(String.valueOf(overridesModel.getValueAt(row, 2)));
            override.setPriorityMode(String.valueOf(overridesModel.getValueAt(row, 3)));
            override.setAffinityMode(String.valueOf(overridesModel.getValueAt(row, 4)));
            override.setTimerMode(String.valueOf(overridesModel.getValueAt(row, 5)));
            if (!override.isEmpty()) {
                caseDraft.overrides().put(override.implementationId(), override);
            }
        }
    }

    private void addCase() {
        syncDraftFromUi();
        ProfileCaseDraft newCase = new ProfileCaseDraft();
        newCase.setCaseId("case_" + (workingDraft.cases().size() + 1));
        newCase.setIterations("20000");
        newCase.setParallelChains("1");
        workingDraft.cases().add(newCase);
        applyDraft(workingDraft, "Added a new case row.");
    }

    private void duplicateCase() {
        syncDraftFromUi();
        int row = casesTable.getSelectedRow();
        if (row < 0 || row >= workingDraft.cases().size()) {
            return;
        }
        ProfileCaseDraft copy = workingDraft.cases().get(row).copy();
        copy.setCaseId(copy.caseId() + "_copy");
        workingDraft.cases().add(row + 1, copy);
        applyDraft(workingDraft, "Duplicated the selected case.");
    }

    private void moveCase(int delta) {
        syncDraftFromUi();
        int row = casesTable.getSelectedRow();
        int target = row + delta;
        if (row < 0 || target < 0 || target >= workingDraft.cases().size()) {
            return;
        }
        ProfileCaseDraft draft = workingDraft.cases().remove(row);
        workingDraft.cases().add(target, draft);
        applyDraft(workingDraft, "Reordered the case matrix.");
        if (target < casesModel.getRowCount()) {
            casesTable.setRowSelectionInterval(target, target);
        }
    }

    private void removeCase() {
        syncDraftFromUi();
        int row = casesTable.getSelectedRow();
        if (row < 0 || row >= workingDraft.cases().size()) {
            return;
        }
        workingDraft.cases().remove(row);
        if (workingDraft.cases().isEmpty()) {
            workingDraft.cases().add(new ProfileCaseDraft());
        }
        applyDraft(workingDraft, "Removed the selected case.");
    }

    private void duplicateCurrentProfile() {
        syncDraftFromUi();
        if (workingDraft.id().isBlank()) {
            return;
        }
        String suggestion = suggestCustomProfileId(workingDraft.id());
        ProfileDraft copy = workingDraft.copy();
        copy.setSource("custom");
        copy.setEditable(true);
        copy.setId(suggestion);
        if (!copy.name().isBlank()) {
            copy.setName(copy.name() + " Copy");
        }
        applyDraft(copy, "Duplicated the current profile into an editable custom draft.");
    }

    private void saveDraft(boolean forceSaveAs) {
        syncDraftFromUi();
        ProfileDraft draftToSave = workingDraft.copy();
        draftToSave.setSource("custom");
        draftToSave.setEditable(true);
        if (forceSaveAs || !workingDraft.editable() || "builtin".equals(workingDraft.source())) {
            promptForProfileIdentity(draftToSave);
        }
        if (draftToSave.id().isBlank()) {
            validationArea.setText("Save cancelled. Provide a profile id first.");
            return;
        }
        try {
            Path tempPath = writeDraftToTemp(draftToSave, "save");
            TableData validation = backend.readTable("validate-profile-file", tempPath.toString());
            if (containsValidationErrors(validation)) {
                validationArea.setText(formatValidation(validation));
                return;
            }
            backend.readTable("save-profile-file", tempPath.toString());
            applyDraft(draftToSave, "Saved " + draftToSave.id() + " to testruns/custom.");
            selectProfile.accept(draftToSave.id());
            refreshDeck.run();
        } catch (Exception error) {
            validationArea.setText("Save failed.\n" + error.getMessage());
        }
    }

    private void runDraftNow() {
        syncDraftFromUi();
        try {
            Path tempPath = writeDraftToTemp(workingDraft, "run");
            TableData validation = backend.readTable("validate-profile-file", tempPath.toString());
            if (containsValidationErrors(validation)) {
                validationArea.setText(formatValidation(validation));
                return;
            }
            validationArea.setText("Launching draft profile through the main run controller.\nNo measured-loop logging will be injected.");
            runProfileFile.accept(tempPath);
        } catch (Exception error) {
            validationArea.setText("Run-now failed.\n" + error.getMessage());
        }
    }

    private void promptForProfileIdentity(ProfileDraft draft) {
        JTextField idPrompt = new JTextField(draft.id().isBlank() ? suggestCustomProfileId("custom_profile") : draft.id());
        JTextField namePrompt = new JTextField(draft.name());
        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8));
        panel.add(new JLabel("Profile ID"));
        panel.add(idPrompt);
        panel.add(new JLabel("Display Name"));
        panel.add(namePrompt);
        int result = JOptionPane.showConfirmDialog(this, panel, "Save Custom Profile", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            draft.setId(idPrompt.getText());
            draft.setName(namePrompt.getText());
        } else {
            draft.setId("");
        }
    }

    private Path writeDraftToTemp(ProfileDraft draft, String label) throws IOException {
        Path tempDir = backend.repoRoot().resolve("build").resolve("tmp");
        Files.createDirectories(tempDir);
        Path tempFile = tempDir.resolve(label + "-" + System.currentTimeMillis() + ".testrun.json");
        Files.writeString(tempFile, draft.toJson(), StandardCharsets.UTF_8);
        return tempFile;
    }

    private boolean containsValidationErrors(TableData validation) {
        for (Map<String, String> row : validation.asMaps()) {
            if ("error".equals(row.getOrDefault("status", ""))) {
                return true;
            }
        }
        return false;
    }

    private String formatValidation(TableData validation) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, String> row : validation.asMaps()) {
            builder.append(row.getOrDefault("status", "")).append(": ").append(row.getOrDefault("message", "")).append('\n');
        }
        return builder.toString();
    }

    private String suggestCustomProfileId(String base) {
        String candidate = base.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
        if (candidate.isBlank()) {
            candidate = "custom_profile";
        }
        Set<String> existingIds = new LinkedHashSet<>();
        for (int index = 0; index < profilePicker.getItemCount(); index += 1) {
            ProfileRef ref = profilePicker.getItemAt(index);
            existingIds.add(ref.profileId());
        }
        if (!existingIds.contains(candidate)) {
            return candidate;
        }
        int suffix = 2;
        while (existingIds.contains(candidate + "_" + suffix)) {
            suffix += 1;
        }
        return candidate + "_" + suffix;
    }

    private void selectProfileRef(String profileId) {
        for (int index = 0; index < profilePicker.getItemCount(); index += 1) {
            ProfileRef item = profilePicker.getItemAt(index);
            if (Objects.equals(profileId, item.profileId())) {
                profilePicker.setSelectedIndex(index);
                return;
            }
        }
    }

    private boolean profileExists(String profileId) {
        for (int index = 0; index < profilePicker.getItemCount(); index += 1) {
            if (Objects.equals(profileId, profilePicker.getItemAt(index).profileId())) {
                return true;
            }
        }
        return false;
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

    private Component gap() {
        JPanel gap = new JPanel();
        gap.setOpaque(false);
        gap.setPreferredSize(new Dimension(10, 12));
        return gap;
    }

    private JPanel sectionPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UiPalette.PANEL);
        panel.setBorder(DarkTheme.panelBorder());
        panel.add(sectionLabel(title), BorderLayout.NORTH);
        return panel;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiPalette.MUTED);
        label.setFont(UiPalette.LABEL.deriveFont(13f));
        return label;
    }

    private JButton button(String text, Color background) {
        JButton button = new JButton(text);
        button.setBackground(background);
        button.setForeground(DarkThemeForeground.pick(background));
        button.setFocusPainted(false);
        return button;
    }

    private void styleBadge(JLabel label) {
        label.setOpaque(true);
        label.setBackground("builtin".equals(label.getText()) ? UiPalette.WARNING : UiPalette.SURFACE_ALT);
        label.setForeground(DarkThemeForeground.pick(label.getBackground()));
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiPalette.BORDER, 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
    }

    private JScrollPane tableScroll(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(28);
        table.setBackground(UiPalette.SURFACE);
        table.setForeground(UiPalette.TEXT);
        table.setSelectionBackground(UiPalette.ACCENT);
        table.setSelectionForeground(UiPalette.WINDOW);
        table.getTableHeader().setBackground(UiPalette.PANEL_ALT);
        table.getTableHeader().setForeground(UiPalette.TEXT);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(UiPalette.SURFACE);
        scrollPane.setBorder(BorderFactory.createLineBorder(UiPalette.BORDER, 1, true));
        return scrollPane;
    }

    private JScrollPane textScroll(JTextArea area) {
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.getViewport().setBackground(UiPalette.SURFACE);
        scrollPane.setBorder(BorderFactory.createLineBorder(UiPalette.BORDER, 1, true));
        return scrollPane;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private record ProfileRef(String profileId, String name, String source) {
        @Override
        public String toString() {
            return profileId + " · " + source + (name.isBlank() ? "" : " · " + name);
        }
    }

    private static final class JSplitPaneLike extends JPanel {
        JSplitPaneLike(Component left, Component right, double resizeWeight) {
            super(new BorderLayout(12, 0));
            setOpaque(false);
            javax.swing.JSplitPane splitPane = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, left, right);
            splitPane.setResizeWeight(resizeWeight);
            DarkTheme.styleSplitPane(splitPane);
            add(splitPane, BorderLayout.CENTER);
        }
    }

    private static final class DarkThemeForeground {
        private DarkThemeForeground() {
        }

        static Color pick(Color background) {
            double luminance = (0.2126 * background.getRed() + 0.7152 * background.getGreen() + 0.0722 * background.getBlue()) / 255.0;
            return luminance > 0.55 ? UiPalette.WINDOW : UiPalette.TEXT;
        }
    }
}
