package bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BenchCommon {
    static final long[][] SEEDS = {
        {1L, 1L},
        {3L, 5L},
        {8L, 13L},
        {21L, 34L},
        {55L, 89L},
        {144L, 233L},
        {377L, 610L},
        {987L, 1597L},
    };

    private BenchCommon() {
    }

    static CaseData loadCase(String[] args) throws IOException {
        if (args.length != 2 || !"--case-file".equals(args[0])) {
            throw new IllegalArgumentException("Usage: java <class> --case-file <path>");
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 0) {
                continue;
            }
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return new CaseData(
            values.getOrDefault("case_id", ""),
            Integer.parseInt(values.getOrDefault("iterations", "0")),
            Integer.parseInt(values.getOrDefault("parallel_chains", "1")),
            values.getOrDefault("priority_mode", "high"),
            values.getOrDefault("affinity_mode", "single_core"),
            "true".equals(values.getOrDefault("warmup", "false")),
            Integer.parseInt(values.getOrDefault("repeat_index", "1"))
        );
    }

    static Context prepareContext(CaseData caseData) {
        Thread current = Thread.currentThread();
        boolean isMacos = System.getProperty("os.name").toLowerCase().contains("mac");
        String appliedPriority = "unchanged";
        if ("high".equals(caseData.priorityMode())) {
            try {
                current.setPriority(Thread.MAX_PRIORITY);
                appliedPriority = isMacos ? "advisory_macos" : "high";
            } catch (IllegalArgumentException ignored) {
                appliedPriority = isMacos ? "advisory_macos" : "unsupported";
            }
        }
        String appliedAffinity = "single_core".equals(caseData.affinityMode()) ? (isMacos ? "advisory_macos" : "unsupported") : "unchanged";
        String notes = isMacos
            ? "Java benchmark uses controller-side best effort scheduling only; macOS affinity remains advisory"
            : "Java benchmark uses controller-side best effort scheduling only";
        return new Context(
            ProcessHandle.current().pid(),
            current.threadId(),
            caseData.priorityMode(),
            caseData.affinityMode(),
            appliedPriority,
            appliedAffinity,
            notes
        );
    }

    static long[][] extendSeedPairs(int count) {
        long[][] pairs = new long[count][2];
        for (int index = 0; index < count && index < SEEDS.length; index += 1) {
            pairs[index][0] = SEEDS[index][0];
            pairs[index][1] = SEEDS[index][1];
        }
        for (int index = SEEDS.length; index < count; index += 1) {
            pairs[index][0] = pairs[index - 1][0] + pairs[index - 1][1];
            pairs[index][1] = pairs[index][0] + pairs[index - 1][0];
        }
        return pairs;
    }

    static String toJson(ResultPayload payload) {
        StringBuilder builder = new StringBuilder(512);
        builder.append('{');
        appendField(builder, "schema_version", "1", false);
        appendField(builder, "implementation", payload.implementation(), true);
        appendField(builder, "language", "java", true);
        appendField(builder, "variant", payload.variant(), true);
        appendField(builder, "case_id", payload.caseData().caseId(), true);
        appendField(builder, "warmup", Boolean.toString(payload.caseData().warmup()), false);
        appendField(builder, "repeat_index", Integer.toString(payload.caseData().repeatIndex()), false);
        appendField(builder, "iterations", Integer.toString(payload.caseData().iterations()), false);
        appendField(builder, "parallel_chains", Integer.toString(payload.caseData().parallelChains()), false);
        appendField(builder, "loop_trip_count", Integer.toString(payload.loopTripCount()), false);
        appendField(builder, "remainder", Integer.toString(payload.remainder()), false);
        appendField(builder, "timer_kind", "system_nano_time", true);
        appendField(builder, "elapsed_ns", Long.toString(payload.elapsedNs()), false);
        appendField(builder, "ns_per_iteration", Double.toString(payload.nsPerIteration()), false);
        appendField(builder, "ns_per_add", Double.toString(payload.nsPerAdd()), false);
        appendField(builder, "result_checksum", Long.toUnsignedString(payload.checksum()), true);
        appendField(builder, "host_os", System.getProperty("os.name").toLowerCase().contains("mac") ? "macos" : "windows", true);
        appendField(builder, "host_arch", System.getProperty("os.arch").contains("aarch64") || System.getProperty("os.arch").contains("arm64") ? "arm64" : "x64", true);
        appendField(builder, "pid", Long.toString(payload.context().pid()), false);
        appendField(builder, "tid", Long.toString(payload.context().tid()), false);
        appendField(builder, "requested_priority_mode", payload.context().requestedPriorityMode(), true);
        appendField(builder, "requested_affinity_mode", payload.context().requestedAffinityMode(), true);
        appendField(builder, "applied_priority_mode", payload.context().appliedPriorityMode(), true);
        appendField(builder, "applied_affinity_mode", payload.context().appliedAffinityMode(), true);
        appendField(builder, "scheduler_notes", payload.context().schedulerNotes(), true);
        appendField(builder, "runtime_name", "java", true);
        builder.append(',');
        builder.append("\"platform_extras\":{}");
        builder.append('}');
        return builder.toString();
    }

    private static void appendField(StringBuilder builder, String key, String value, boolean stringValue) {
        if (builder.charAt(builder.length() - 1) != '{') {
            builder.append(',');
        }
        builder.append('"').append(escape(key)).append('"').append(':');
        if (stringValue) {
            builder.append('"').append(escape(value)).append('"');
        } else {
            builder.append(value);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record CaseData(
        String caseId,
        int iterations,
        int parallelChains,
        String priorityMode,
        String affinityMode,
        boolean warmup,
        int repeatIndex
    ) {
    }

    record Context(
        long pid,
        long tid,
        String requestedPriorityMode,
        String requestedAffinityMode,
        String appliedPriorityMode,
        String appliedAffinityMode,
        String schedulerNotes
    ) {
    }

    record ResultPayload(
        String implementation,
        String variant,
        CaseData caseData,
        Context context,
        int loopTripCount,
        int remainder,
        long elapsedNs,
        double nsPerIteration,
        double nsPerAdd,
        long checksum
    ) {
    }
}
