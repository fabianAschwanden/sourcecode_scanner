package ch.fabianaschwanden.sourcescanner.adapter.out.vcsplatform;

import ch.fabianaschwanden.sourcescanner.domain.model.FileEdit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * Klont ein Repo in ein Wegwerf-Verzeichnis, legt einen Fix-Branch an, wendet {@link FileEdit}s an,
 * committet (redigierte Message) und pusht <b>nur den Fix-Branch</b> — nie den Basis-Branch (RMR-11).
 * Eigene, minimale {@code env:}-Token-Auflösung, um keine Abhängigkeit auf den Connector-Adapter zu
 * erzeugen (Layering); der Token verlässt diese Klasse nur für Clone/Push und wird nie geloggt.
 */
final class FixBranchPusher {

    private FixBranchPusher() {
    }

    /** Führt Clone → Branch → Edits → Commit → Push aus und gibt das Arbeitsverzeichnis (zum Cleanup) zurück. */
    static void cloneBranchCommitPush(String repoUrl, String baseBranch, String fixBranch,
                                      String commitMessage, List<FileEdit> edits, String tokenRef) {
        Path workDir = createWorkDir();
        try {
            var clone = Git.cloneRepository().setURI(repoUrl).setDirectory(workDir.toFile())
                    .setBranch(baseBranch);
            Optional<String> token = resolveToken(tokenRef);
            token.ifPresent(t -> clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(t, "")));
            try (Git git = clone.call()) {
                git.checkout().setCreateBranch(true).setName(fixBranch).call();
                applyEdits(workDir, edits);
                git.add().addFilepattern(".").call();
                git.commit().setMessage(commitMessage).setAuthor("sourcecode-scanner", "scanner@example.com").call();
                var push = git.push().setRemote("origin")
                        .setRefSpecs(new org.eclipse.jgit.transport.RefSpec(
                                "refs/heads/" + fixBranch + ":refs/heads/" + fixBranch));
                token.ifPresent(t -> push.setCredentialsProvider(new UsernamePasswordCredentialsProvider(t, "")));
                push.call();
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to create fix branch: " + e.getMessage(), e);
        } finally {
            deleteRecursively(workDir);
        }
    }

    /** Vom Remediator gesetzter Marker: den Rest an die bestehende Zeile anhängen statt ersetzen. */
    static final String APPEND_MARKER = "<<APPEND>>";

    private static void applyEdits(Path workDir, List<FileEdit> edits) throws IOException {
        for (FileEdit edit : edits) {
            Path file = workDir.resolve(edit.path());
            if (!Files.isRegularFile(file)) {
                continue;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int from = edit.startLine() - 1;
            if (from < 0 || from > lines.size()) {
                continue;
            }
            if (edit.newContent().startsWith(APPEND_MARKER)) {
                // ANNOTATE-by-append: Direktive an die bestehende Zeile hängen (idempotent).
                String suffix = edit.newContent().substring(APPEND_MARKER.length()).trim();
                if (from < lines.size() && !lines.get(from).contains("scanner:ignore-")) {
                    lines.set(from, lines.get(from) + " " + suffix);
                }
            } else {
                // Ersetze [startLine..endLine] durch newContent (eine Zeile).
                int to = Math.min(edit.endLine(), lines.size());
                for (int i = to - 1; i >= from && i < lines.size(); i--) {
                    lines.remove(i);
                }
                lines.add(Math.min(from, lines.size()), edit.newContent());
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        }
    }

    private static Optional<String> resolveToken(String tokenRef) {
        if (tokenRef == null || tokenRef.isBlank()) {
            return Optional.empty();
        }
        if (tokenRef.startsWith("env:")) {
            String value = System.getenv(tokenRef.substring("env:".length()));
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("environment variable not set for tokenRef: " + tokenRef);
            }
            return Optional.of(value);
        }
        throw new IllegalArgumentException("unsupported tokenRef for fix push (use env:): " + tokenRef);
    }

    private static Path createWorkDir() {
        try {
            return Files.createTempDirectory("scanner-fix-");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create fix work dir", e);
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-Effort-Cleanup des Wegwerf-Verzeichnisses.
                }
            });
        } catch (IOException ignored) {
            // Best-Effort.
        }
    }
}
