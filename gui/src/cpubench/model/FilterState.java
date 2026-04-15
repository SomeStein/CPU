package cpubench.model;

import java.util.Map;
import java.util.Objects;

public record FilterState(
    String implementation,
    String caseId,
    String status,
    String profileId,
    boolean hideWarmups
) {
    public static FilterState defaults() {
        return new FilterState("All", "All", "All", "All", true);
    }

    public boolean matches(Map<String, String> row) {
        if (!matchesValue(implementation, row.get("implementation"))) {
            return false;
        }
        if (!matchesValue(caseId, row.get("case_id"))) {
            return false;
        }
        if (!matchesValue(status, row.get("status"))) {
            return false;
        }
        if (row.containsKey("profile_id") && !matchesValue(profileId, row.get("profile_id"))) {
            return false;
        }
        return !(hideWarmups && Objects.equals("true", row.getOrDefault("warmup", "false")));
    }

    private static boolean matchesValue(String filter, String value) {
        if (filter == null || filter.isBlank() || "All".equals(filter)) {
            return true;
        }
        return Objects.equals(filter, value);
    }
}
