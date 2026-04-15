package cpubench;

import java.nio.file.Path;
import javax.swing.SwingUtilities;

import cpubench.api.BackendClient;
import cpubench.ui.ControllerFrame;
import cpubench.ui.DarkTheme;

public final class ControllerApp {
    private ControllerApp() {
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: ControllerApp <repo-root> <python-exe> <api-script>");
        }

        SwingUtilities.invokeLater(() -> {
            try {
                DarkTheme.install();
                BackendClient client = new BackendClient(Path.of(args[0]), args[1], args[2]);
                new ControllerFrame(client).showWindow();
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                System.exit(1);
            }
        });
    }
}

