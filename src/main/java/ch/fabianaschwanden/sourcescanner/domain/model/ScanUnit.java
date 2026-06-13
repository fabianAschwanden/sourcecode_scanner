package ch.fabianaschwanden.sourcescanner.domain.model;

import java.time.Instant;

/**
 * Kleinste Scan-Einheit: eine Datei in einem Commit-Stand. Wird vom Connector erzeugt und
 * den Detektoren übergeben. {@code diffHunk} ist {@code null} bei einem Full-File-Scan.
 */
public record ScanUnit(
        String repoId,
        String path,
        String commitId,
        String author,
        Instant timestamp,
        String content,
        DiffHunk diffHunk) {

    public ScanUnit {
        if (repoId == null || repoId.isBlank()) {
            throw new IllegalArgumentException("repoId must not be blank");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }

    /** Klassifiziert die Einheit für den Detektor-Vorfilter. */
    public FileType fileType() {
        return FileType.ofPath(path);
    }
}
