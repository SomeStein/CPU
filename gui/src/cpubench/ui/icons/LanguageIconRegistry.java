package cpubench.ui.icons;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import cpubench.ui.IconFactory;

public final class LanguageIconRegistry {
    private static final Map<String, Map<Integer, Icon>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> DISPLAY_NAMES = Map.of(
        "c", "C",
        "cpp", "C++",
        "rust", "Rust",
        "go", "Go",
        "java", "Java",
        "node", "Node.js",
        "python", "Python",
        "ruby", "Ruby",
        "perl", "Perl"
    );

    private LanguageIconRegistry() {
    }

    public static Icon icon(String implementationId, int size) {
        return CACHE.computeIfAbsent(implementationId, ignored -> new ConcurrentHashMap<>())
            .computeIfAbsent(size, ignored -> loadIcon(implementationId, size));
    }

    public static String displayName(String implementationId) {
        return DISPLAY_NAMES.getOrDefault(implementationId, implementationId);
    }

    private static Icon loadIcon(String implementationId, int size) {
        String resource = "/cpubench/ui/assets/lang/" + implementationId + ".svg";
        try (InputStream stream = LanguageIconRegistry.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return IconFactory.logsIcon(size, cpubench.ui.UiPalette.TEXT);
            }
            return SimpleSvgIcon.load(stream, size);
        } catch (IOException error) {
            return new ImageIcon(IconFactory.appImage(size));
        }
    }
}
