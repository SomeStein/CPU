package cpubench.model;

import java.util.Map;

public record ImplementationCatalogEntry(
    String implementationId,
    String language,
    String variant,
    String runtimeKind,
    String deliveryKind,
    String hostSupport,
    String defaultProfileScope,
    String description
) {
    public static ImplementationCatalogEntry fromRow(Map<String, String> row) {
        return new ImplementationCatalogEntry(
            row.getOrDefault("implementation_id", ""),
            row.getOrDefault("language", ""),
            row.getOrDefault("variant", ""),
            row.getOrDefault("runtime_kind", ""),
            row.getOrDefault("delivery_kind", ""),
            row.getOrDefault("host_support", ""),
            row.getOrDefault("default_profile_scope", ""),
            row.getOrDefault("description", "")
        );
    }

    public String displayName() {
        return implementationId + " · " + language + " / " + variant;
    }
}
