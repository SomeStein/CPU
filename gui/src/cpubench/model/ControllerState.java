package cpubench.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ControllerState {
    private TableData profiles = TableData.empty();
    private TableData implementationCatalog = TableData.empty();
    private TableData runs = TableData.empty();
    private TableData globalResults = TableData.empty();
    private TableData runResults = TableData.empty();
    private TableData runEvents = TableData.empty();
    private TableData runManifest = TableData.empty();
    private TableData liveRuns = TableData.empty();
    private TableData liveGlobalResults = TableData.empty();
    private TableData liveRunResults = TableData.empty();
    private TableData liveRunEvents = TableData.empty();
    private String currentRunId = "";
    private String currentProfileId = "";
    private String liveRunId = "";
    private FilterState runFilter = FilterState.defaults();
    private FilterState globalFilter = FilterState.defaults();

    public TableData profiles() {
        return profiles;
    }

    public void setProfiles(TableData profiles) {
        this.profiles = profiles;
    }

    public TableData runs() {
        return runs;
    }

    public TableData implementationCatalog() {
        return implementationCatalog;
    }

    public void setImplementationCatalog(TableData implementationCatalog) {
        this.implementationCatalog = implementationCatalog;
    }

    public void setRuns(TableData runs) {
        this.runs = runs;
    }

    public TableData globalResults() {
        return globalResults;
    }

    public void setGlobalResults(TableData globalResults) {
        this.globalResults = globalResults;
    }

    public TableData runResults() {
        return runResults;
    }

    public void setRunResults(TableData runResults) {
        this.runResults = runResults;
    }

    public TableData runEvents() {
        return runEvents;
    }

    public void setRunEvents(TableData runEvents) {
        this.runEvents = runEvents;
    }

    public TableData runManifest() {
        return runManifest;
    }

    public void setRunManifest(TableData runManifest) {
        this.runManifest = runManifest;
    }

    public TableData liveRuns() {
        return liveRuns;
    }

    public void setLiveRuns(TableData liveRuns) {
        this.liveRuns = liveRuns;
    }

    public TableData liveGlobalResults() {
        return liveGlobalResults;
    }

    public void setLiveGlobalResults(TableData liveGlobalResults) {
        this.liveGlobalResults = liveGlobalResults;
    }

    public TableData liveRunResults() {
        return liveRunResults;
    }

    public void setLiveRunResults(TableData liveRunResults) {
        this.liveRunResults = liveRunResults;
    }

    public TableData liveRunEvents() {
        return liveRunEvents;
    }

    public void setLiveRunEvents(TableData liveRunEvents) {
        this.liveRunEvents = liveRunEvents;
    }

    public String currentRunId() {
        return currentRunId;
    }

    public void setCurrentRunId(String currentRunId) {
        this.currentRunId = currentRunId;
    }

    public String currentProfileId() {
        return currentProfileId;
    }

    public void setCurrentProfileId(String currentProfileId) {
        this.currentProfileId = currentProfileId;
    }

    public String liveRunId() {
        return liveRunId;
    }

    public void setLiveRunId(String liveRunId) {
        this.liveRunId = liveRunId;
    }

    public FilterState runFilter() {
        return runFilter;
    }

    public void setRunFilter(FilterState runFilter) {
        this.runFilter = runFilter;
    }

    public FilterState globalFilter() {
        return globalFilter;
    }

    public void setGlobalFilter(FilterState globalFilter) {
        this.globalFilter = globalFilter;
    }

    public TableData displayedRuns() {
        return merge(runs, liveRuns, List.of("run_id"));
    }

    public TableData displayedGlobalResults() {
        return merge(globalResults, liveGlobalResults, List.of("run_id", "implementation", "case_id", "warmup", "repeat_index"));
    }

    public TableData displayedRunResults() {
        if (!liveRunId.isBlank() && liveRunId.equals(currentRunId)) {
            return merge(runResults, liveRunResults, List.of("run_id", "implementation", "case_id", "warmup", "repeat_index"));
        }
        return runResults;
    }

    public TableData displayedRunEvents() {
        if (!liveRunId.isBlank() && liveRunId.equals(currentRunId)) {
            return merge(runEvents, liveRunEvents, List.of("phase", "step_index", "implementation", "case_id", "repeat_index"));
        }
        return runEvents;
    }

    public TableData filteredRunResults() {
        return filter(displayedRunResults(), runFilter);
    }

    public TableData filteredGlobalResults() {
        return filter(displayedGlobalResults(), globalFilter);
    }

    public void clearLiveRun() {
        liveRunId = "";
        liveRuns = TableData.empty();
        liveGlobalResults = TableData.empty();
        liveRunResults = TableData.empty();
        liveRunEvents = TableData.empty();
    }

    private static TableData filter(TableData source, FilterState filter) {
        if (source.headers().isEmpty()) {
            return TableData.empty();
        }
        List<Map<String, String>> maps = new ArrayList<>();
        for (Map<String, String> row : source.asMaps()) {
            if (filter.matches(row)) {
                maps.add(row);
            }
        }
        return mapsToTableData(maps, source.headers());
    }

    private static TableData mapsToTableData(List<Map<String, String>> rows, List<String> preferredHeaders) {
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

    private static TableData merge(TableData base, TableData overlay, List<String> keyColumns) {
        if (base.headers().isEmpty()) {
            return overlay;
        }
        if (overlay.headers().isEmpty()) {
            return base;
        }
        List<String> headers = base.headers().equals(overlay.headers()) ? base.headers() : base.headers();
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();
        for (Map<String, String> row : base.asMaps()) {
            merged.put(key(row, keyColumns), row);
        }
        for (Map<String, String> row : overlay.asMaps()) {
            merged.put(key(row, keyColumns), row);
        }
        return mapsToTableData(new ArrayList<>(merged.values()), headers);
    }

    private static String key(Map<String, String> row, List<String> keyColumns) {
        StringBuilder builder = new StringBuilder();
        for (String key : keyColumns) {
            builder.append(row.getOrDefault(key, "")).append('\u001f');
        }
        return builder.toString();
    }
}
