package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.Locale;

/** Grobe Datei-Klassifikation für den Detektor-Vorfilter {@code supports(FileType)} (DR-04). */
public enum FileType {
    SOURCE,
    CONFIG,
    DOCUMENTATION,
    BINARY,
    OTHER;

    /** Leitet den Typ heuristisch aus der Dateiendung ab. */
    public static FileType ofPath(String path) {
        if (path == null) {
            return OTHER;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? lower.substring(dot + 1) : "";
        return switch (ext) {
            case "java", "kt", "ts", "js", "py", "go", "rb", "cs", "cpp", "c", "rs", "php", "scala" -> SOURCE;
            case "yaml", "yml", "json", "toml", "properties", "xml", "env", "ini", "conf" -> CONFIG;
            case "md", "txt", "adoc", "rst" -> DOCUMENTATION;
            case "png", "jpg", "jpeg", "gif", "pdf", "zip", "jar", "class", "ico", "woff", "woff2" -> BINARY;
            default -> OTHER;
        };
    }
}
