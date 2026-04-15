package cpubench.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TableData(List<String> headers, List<List<String>> rows) {
    public TableData {
        headers = List.copyOf(headers);
        List<List<String>> normalizedRows = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            normalizedRows.add(List.copyOf(row));
        }
        rows = List.copyOf(normalizedRows);
    }

    public static TableData empty() {
        return new TableData(List.of(), List.of());
    }

    public int columnIndex(String name) {
        return headers.indexOf(name);
    }

    public String valueAt(int rowIndex, String columnName) {
        int columnIndex = columnIndex(columnName);
        if (rowIndex < 0 || rowIndex >= rows.size() || columnIndex < 0 || columnIndex >= rows.get(rowIndex).size()) {
            return "";
        }
        return rows.get(rowIndex).get(columnIndex);
    }

    public Map<String, String> rowAsMap(int rowIndex) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return values;
        }
        List<String> row = rows.get(rowIndex);
        for (int index = 0; index < headers.size(); index += 1) {
            values.put(headers.get(index), index < row.size() ? row.get(index) : "");
        }
        return values;
    }

    public List<Map<String, String>> asMaps() {
        List<Map<String, String>> values = new ArrayList<>(rows.size());
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex += 1) {
            values.add(rowAsMap(rowIndex));
        }
        return values;
    }

    public Set<String> distinctValues(String columnName) {
        Set<String> values = new LinkedHashSet<>();
        int columnIndex = columnIndex(columnName);
        if (columnIndex < 0) {
            return values;
        }
        for (List<String> row : rows) {
            if (columnIndex < row.size() && !row.get(columnIndex).isBlank()) {
                values.add(row.get(columnIndex));
            }
        }
        return values;
    }
}

