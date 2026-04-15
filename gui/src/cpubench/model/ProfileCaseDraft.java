package cpubench.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfileCaseDraft {
    private String caseId = "";
    private String iterations = "";
    private String parallelChains = "";
    private String priorityMode = "";
    private String affinityMode = "";
    private String timerMode = "";
    private final Map<String, ProfileOverrideDraft> overrides = new LinkedHashMap<>();

    public ProfileCaseDraft copy() {
        ProfileCaseDraft copy = new ProfileCaseDraft();
        copy.caseId = caseId;
        copy.iterations = iterations;
        copy.parallelChains = parallelChains;
        copy.priorityMode = priorityMode;
        copy.affinityMode = affinityMode;
        copy.timerMode = timerMode;
        for (Map.Entry<String, ProfileOverrideDraft> entry : overrides.entrySet()) {
            copy.overrides.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }

    public String caseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = normalize(caseId);
    }

    public String iterations() {
        return iterations;
    }

    public void setIterations(String iterations) {
        this.iterations = normalize(iterations);
    }

    public String parallelChains() {
        return parallelChains;
    }

    public void setParallelChains(String parallelChains) {
        this.parallelChains = normalize(parallelChains);
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

    public Map<String, ProfileOverrideDraft> overrides() {
        return overrides;
    }

    public ProfileOverrideDraft overrideFor(String implementationId) {
        return overrides.computeIfAbsent(implementationId, ProfileOverrideDraft::new);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
