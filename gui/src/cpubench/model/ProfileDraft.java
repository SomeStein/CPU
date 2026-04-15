package cpubench.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProfileDraft {
    private String id = "";
    private String name = "";
    private String source = "custom";
    private boolean editable = true;
    private String warmups = "1";
    private String repeats = "1";
    private String priorityMode = "high";
    private String affinityMode = "single_core";
    private String timerMode = "monotonic_ns";
    private final LinkedHashSet<String> implementations = new LinkedHashSet<>();
    private final List<ProfileCaseDraft> cases = new ArrayList<>();

    public static ProfileDraft blank() {
        ProfileDraft draft = new ProfileDraft();
        ProfileCaseDraft sample = new ProfileCaseDraft();
        sample.setCaseId("custom_case");
        sample.setIterations("20000");
        sample.setParallelChains("1");
        draft.cases.add(sample);
        return draft;
    }

    public static ProfileDraft fromTables(TableData details, TableData overrides) {
        if (details.rows().isEmpty()) {
            return blank();
        }
        ProfileDraft draft = new ProfileDraft();
        Map<String, String> first = details.rowAsMap(0);
        draft.id = first.getOrDefault("profile_id", "");
        draft.name = first.getOrDefault("profile_name", "");
        draft.source = first.getOrDefault("source", "builtin");
        draft.editable = "true".equals(first.getOrDefault("editable", "false"));
        draft.warmups = first.getOrDefault("warmups", "1");
        draft.repeats = first.getOrDefault("repeats", "1");
        draft.priorityMode = first.getOrDefault("priority_mode", "high");
        draft.affinityMode = first.getOrDefault("affinity_mode", "single_core");
        draft.timerMode = first.getOrDefault("timer_mode", "monotonic_ns");
        for (String implementation : splitCsv(first.getOrDefault("implementations", ""))) {
            draft.implementations.add(implementation);
        }
        Map<String, ProfileCaseDraft> casesById = new LinkedHashMap<>();
        for (Map<String, String> row : details.asMaps()) {
            ProfileCaseDraft caseDraft = new ProfileCaseDraft();
            caseDraft.setCaseId(row.getOrDefault("case_id", ""));
            caseDraft.setIterations(row.getOrDefault("iterations", ""));
            caseDraft.setParallelChains(row.getOrDefault("parallel_chains", ""));
            caseDraft.setPriorityMode(row.getOrDefault("case_priority_mode", ""));
            caseDraft.setAffinityMode(row.getOrDefault("case_affinity_mode", ""));
            caseDraft.setTimerMode(row.getOrDefault("case_timer_mode", ""));
            draft.cases.add(caseDraft);
            casesById.put(caseDraft.caseId(), caseDraft);
        }
        for (Map<String, String> row : overrides.asMaps()) {
            ProfileCaseDraft caseDraft = casesById.getOrDefault(row.getOrDefault("case_id", ""), null);
            if (caseDraft == null) {
                continue;
            }
            String implementationId = row.getOrDefault("implementation", "");
            ProfileOverrideDraft overrideDraft = caseDraft.overrideFor(implementationId);
            overrideDraft.setIterations(row.getOrDefault("iterations", ""));
            overrideDraft.setParallelChains(row.getOrDefault("parallel_chains", ""));
            overrideDraft.setPriorityMode(row.getOrDefault("priority_mode", ""));
            overrideDraft.setAffinityMode(row.getOrDefault("affinity_mode", ""));
            overrideDraft.setTimerMode(row.getOrDefault("timer_mode", ""));
        }
        return draft;
    }

    public ProfileDraft copy() {
        ProfileDraft copy = new ProfileDraft();
        copy.id = id;
        copy.name = name;
        copy.source = source;
        copy.editable = editable;
        copy.warmups = warmups;
        copy.repeats = repeats;
        copy.priorityMode = priorityMode;
        copy.affinityMode = affinityMode;
        copy.timerMode = timerMode;
        copy.implementations.addAll(implementations);
        for (ProfileCaseDraft caseDraft : cases) {
            copy.cases.add(caseDraft.copy());
        }
        return copy;
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = normalize(id);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = normalize(name);
    }

    public String source() {
        return source;
    }

    public void setSource(String source) {
        this.source = normalize(source);
    }

    public boolean editable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public String warmups() {
        return warmups;
    }

    public void setWarmups(String warmups) {
        this.warmups = normalize(warmups);
    }

    public String repeats() {
        return repeats;
    }

    public void setRepeats(String repeats) {
        this.repeats = normalize(repeats);
    }

    public String priorityMode() {
        return priorityMode;
    }

    public void setPriorityMode(String priorityMode) {
        this.priorityMode = normalize(priorityMode);
    }

    public String affinityMode() {
        return affinityMode;
    }

    public void setAffinityMode(String affinityMode) {
        this.affinityMode = normalize(affinityMode);
    }

    public String timerMode() {
        return timerMode;
    }

    public void setTimerMode(String timerMode) {
        this.timerMode = normalize(timerMode);
    }

    public Set<String> implementations() {
        return implementations;
    }

    public List<ProfileCaseDraft> cases() {
        return cases;
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder(2048);
        builder.append("{\n");
        builder.append("  \"schema_version\": 1,\n");
        builder.append("  \"id\": \"").append(escape(id)).append("\",\n");
        builder.append("  \"name\": \"").append(escape(name)).append("\",\n");
        builder.append("  \"implementations\": [");
        int index = 0;
        for (String implementation : implementations) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append("\"").append(escape(implementation)).append("\"");
            index += 1;
        }
        builder.append("],\n");
        builder.append("  \"defaults\": {\n");
        builder.append("    \"affinity_mode\": \"").append(escape(affinityMode)).append("\",\n");
        builder.append("    \"priority_mode\": \"").append(escape(priorityMode)).append("\",\n");
        builder.append("    \"repeats\": ").append(numberOrZero(repeats)).append(",\n");
        builder.append("    \"timer_mode\": \"").append(escape(timerMode)).append("\",\n");
        builder.append("    \"warmups\": ").append(numberOrZero(warmups)).append("\n");
        builder.append("  },\n");
        builder.append("  \"matrix\": [\n");
        for (int caseIndex = 0; caseIndex < cases.size(); caseIndex += 1) {
            ProfileCaseDraft caseDraft = cases.get(caseIndex);
            builder.append("    {\n");
            builder.append("      \"case_id\": \"").append(escape(caseDraft.caseId())).append("\",\n");
            builder.append("      \"iterations\": ").append(numberOrZero(caseDraft.iterations())).append(",\n");
            builder.append("      \"parallel_chains\": ").append(numberOrZero(caseDraft.parallelChains()));
            appendOptionalString(builder, "priority_mode", caseDraft.priorityMode(), 6);
            appendOptionalString(builder, "affinity_mode", caseDraft.affinityMode(), 6);
            appendOptionalString(builder, "timer_mode", caseDraft.timerMode(), 6);
            appendOverrides(builder, caseDraft);
            builder.append('\n').append("    }");
            if (caseIndex < cases.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static void appendOptionalString(StringBuilder builder, String key, String value, int indent) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append(",\n").append(" ".repeat(indent)).append("\"").append(escape(key)).append("\": \"").append(escape(value)).append("\"");
    }

    private static void appendOverrides(StringBuilder builder, ProfileCaseDraft caseDraft) {
        List<ProfileOverrideDraft> activeOverrides = new ArrayList<>();
        for (ProfileOverrideDraft override : caseDraft.overrides().values()) {
            if (!override.isEmpty()) {
                activeOverrides.add(override);
            }
        }
        if (activeOverrides.isEmpty()) {
            return;
        }
        builder.append(",\n      \"overrides\": {\n");
        for (int index = 0; index < activeOverrides.size(); index += 1) {
            ProfileOverrideDraft override = activeOverrides.get(index);
            builder.append("        \"").append(escape(override.implementationId())).append("\": {");
            boolean wroteField = false;
            wroteField = appendOptionalNumber(builder, "iterations", override.iterations(), wroteField);
            wroteField = appendOptionalNumber(builder, "parallel_chains", override.parallelChains(), wroteField);
            wroteField = appendOptionalStringInline(builder, "priority_mode", override.priorityMode(), wroteField);
            wroteField = appendOptionalStringInline(builder, "affinity_mode", override.affinityMode(), wroteField);
            appendOptionalStringInline(builder, "timer_mode", override.timerMode(), wroteField);
            builder.append("}");
            if (index < activeOverrides.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("      }");
    }

    private static boolean appendOptionalNumber(StringBuilder builder, String key, String value, boolean wroteField) {
        if (value == null || value.isBlank()) {
            return wroteField;
        }
        if (wroteField) {
            builder.append(", ");
        }
        builder.append("\"").append(escape(key)).append("\": ").append(numberOrZero(value));
        return true;
    }

    private static boolean appendOptionalStringInline(StringBuilder builder, String key, String value, boolean wroteField) {
        if (value == null || value.isBlank()) {
            return wroteField;
        }
        if (wroteField) {
            builder.append(", ");
        }
        builder.append("\"").append(escape(key)).append("\": \"").append(escape(value)).append("\"");
        return true;
    }

    private static int numberOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException error) {
            return 0;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> splitCsv(String raw) {
        List<String> values = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
