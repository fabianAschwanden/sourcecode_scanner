package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Eine konkrete, anwendbare Datei-Änderung eines Fix-Vorschlags (docs/07 §2.4). Ersetzt die Zeilen
 * {@code startLine..endLine} (1-basiert, inklusive) durch {@code newContent}; bei {@code startLine ==
 * endLine + 1} eine reine Einfügung. {@code newContent} enthält nie einen Klartext-Treffer (FR-18).
 */
public record FileEdit(String path, int startLine, int endLine, String newContent) {

    public FileEdit {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("file edit path must not be blank");
        }
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be >= 1");
        }
        if (endLine < startLine - 1) {
            throw new IllegalArgumentException("endLine must be >= startLine - 1");
        }
        newContent = newContent == null ? "" : newContent;
    }
}
