package cpubench;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;

import cpubench.api.BackendClient;
import cpubench.ui.DarkTheme;
import cpubench.ui.shell.ShellFrame;

public final class ControllerApp {
    private ControllerApp() {
    }

    public static void main(String[] args) {
        LaunchContext context = resolveLaunchContext(args);

        SwingUtilities.invokeLater(() -> {
            try {
                DarkTheme.install();
                BackendClient client = new BackendClient(context.repoRoot(), context.pythonExe(), context.apiScript());
                new ShellFrame(client).showWindow();
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                System.exit(1);
            }
        });
    }

    private static LaunchContext resolveLaunchContext(String[] args) {
        if (args.length == 3) {
            return new LaunchContext(Path.of(args[0]), args[1], args[2]);
        }
        if (args.length == 0) {
            Path appDir = resolveAppDirectory();
            Path repoRoot = appDir.resolve("controller");
            Path apiScript = repoRoot.resolve("scripts").resolve("controller_api.py");
            return new LaunchContext(repoRoot, resolveBundledPython(repoRoot), apiScript.toString());
        }
        throw new IllegalArgumentException("Usage: ControllerApp <repo-root> <python-exe> <api-script>");
    }

    private static Path resolveAppDirectory() {
        try {
            Path location = Path.of(ControllerApp.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            return location.getParent();
        } catch (URISyntaxException error) {
            throw new IllegalStateException("Unable to resolve packaged application directory.", error);
        }
    }

    private static String resolveBundledPython(Path repoRoot) {
        String hostKey = hostOs() + "-" + hostArch();
        List<Path> candidates = List.of(
            repoRoot.resolve("tools").resolve("runtime").resolve(hostKey).resolve("python").resolve("python.exe"),
            repoRoot.resolve("tools").resolve("runtime").resolve(hostKey).resolve("python").resolve("bin").resolve("python3"),
            repoRoot.resolve("tools").resolve("runtime").resolve(hostKey).resolve("python").resolve("bin").resolve("python")
        );
        for (Path candidate : candidates) {
            if (candidate.toFile().exists()) {
                return candidate.toString();
            }
        }
        return hostOs().equals("windows") ? "python.exe" : "python3";
    }

    private static String hostOs() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            return "macos";
        }
        if (osName.contains("win")) {
            return "windows";
        }
        return osName;
    }

    private static String hostArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        }
        if (arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64")) {
            return "x64";
        }
        return arch;
    }

    private record LaunchContext(Path repoRoot, String pythonExe, String apiScript) {
    }
}
