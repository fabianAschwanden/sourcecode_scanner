package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Geänderter Zeilenbereich eines Commits (für den Diff-Modus). In Phase 1 (Full-Scan)
 * ist {@code diffHunk} einer {@link ScanUnit} stets {@code null}.
 */
public record DiffHunk(int startLine, int endLine) {

    public DiffHunk {
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("invalid hunk range: " + startLine + ".." + endLine);
        }
    }
}
