package cpubench.model;

public final class ProfileOverrideDraft {
    private final String implementationId;
    private String iterations = "";
    private String parallelChains = "";
    private String priorityMode = "";
    private String affinityMode = "";
    private String timerMode = "";

    public ProfileOverrideDraft(String implementationId) {
        this.implementationId = implementationId;
    }

    public ProfileOverrideDraft copy() {
        ProfileOverrideDraft copy = new ProfileOverrideDraft(implementationId);
        copy.iterations = iterations;
        copy.parallelChains = parallelChains;
        copy.priorityMode = priorityMode;
        copy.affinityMode = affinityMode;
        copy.timerMode = timerMode;
        return copy;
    }

    public String implementationId() {
        return implementationId;
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

    public boolean isEmpty() {
        return iterations.isBlank() && parallelChains.isBlank() && priorityMode.isBlank() && affinityMode.isBlank() && timerMode.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
