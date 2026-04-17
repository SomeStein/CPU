package cpubench.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record FilterState(
    Set<String> implementations,
    Set<String> caseIds,
    Set<String> statuses,
    Set<String> profileIds,
    boolean hideWarmups
) {
    public FilterState {
        implementations = implementations == null ? null : Set.copyOf(implementations);
        caseIds = caseIds == null ? null : Set.copyOf(caseIds);
        statuses = statuses == null ? null : Set.copyOf(statuses);
        profileIds = profileIds == null ? null : Set.copyOf(profileIds);
    }

    public static FilterState defaults() {
        return new FilterState(null, null, null, null, true);
    }

    public boolean matches(Map<String, String> row) {
        if (!matchesValues(implementations, row.get("implementation"))) {
            return false;
        }
        if (!matchesValues(caseIds, row.get("case_id"))) {
            return false;
        }
        if (!matchesValues(statuses, row.get("status"))) {
            return false;
        }
        if (row.containsKey("profile_id") && !matchesValues(profileIds, row.get("profile_id"))) {
            return false;
        }
        return !(hideWarmups && Objects.equals("true", row.getOrDefault("warmup", "false")));
    }

    private static boolean matchesValues(Set<String> allowedValues, String value) {
        if (allowedValues == null) {
            return true;
        }
        return allowedValues.contains(value);
    }
}
