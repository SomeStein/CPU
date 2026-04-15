package cpubench.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ControllerState {
    private TableData profiles = TableData.empty();
    private TableData runs = TableData.empty();
    private TableData globalResults = TableData.empty();
    private TableData runResults = TableData.empty();
    private TableData runEvents = TableData.empty();
    private TableData runManifest = TableData.empty();
    private String currentRunId = "";
    private String currentProfileId = "";
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

    public TableData filteredRunResults() {
        return filter(runResults, runFilter);
    }

    public TableData filteredGlobalResults() {
        return filter(globalResults, globalFilter);
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
}
