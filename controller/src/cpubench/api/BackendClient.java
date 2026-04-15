package cpubench.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cpubench.model.TableData;

public final class BackendClient {
    private final Path repoRoot;
    private final String pythonExe;
    private final String apiScript;

    public BackendClient(Path repoRoot, String pythonExe, String apiScript) {
        this.repoRoot = repoRoot;
        this.pythonExe = pythonExe;
        this.apiScript = apiScript;
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public TableData readTable(String... args) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command(args));
        builder.directory(repoRoot.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(splitTsv(line));
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Backend command failed: " + String.join(" ", command(args)));
        }
        if (rows.isEmpty()) {
            return TableData.empty();
        }
        return new TableData(rows.get(0), rows.subList(1, rows.size()));
    }

    public String readText(String... args) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command(args));
        builder.directory(repoRoot.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Backend command failed: " + String.join(" ", command(args)));
        }
        return output.toString();
    }

    public Process startRunProfile(String profileId) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command("run-profile", profileId));
        builder.directory(repoRoot.toFile());
        builder.redirectErrorStream(true);
        return builder.start();
    }

    private List<String> command(String... args) {
        List<String> command = new ArrayList<>();
        command.add(pythonExe);
        command.add(apiScript);
        command.addAll(Arrays.asList(args));
        return command;
    }

    public static List<String> splitTsv(String line) {
        return Arrays.asList(line.split("\t", -1));
    }
}

