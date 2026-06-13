package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositoryConnectorPort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * Gemeinsame Basis der Plattform-Connectoren (Bitbucket/GitHub/GitLab): klont das Repo per JGit mit
 * aufgelöstem Token in ein Wegwerf-Arbeitsverzeichnis und walkt es dann identisch zum lokalen Pfad
 * über {@link GitHistoryWalker}. Discovery bleibt plattformspezifisch (Subklasse).
 */
abstract class AbstractPlatformConnector implements RepositoryConnectorPort {

    protected final CredentialResolver credentials;

    protected AbstractPlatformConnector(CredentialResolver credentials) {
        this.credentials = credentials;
    }

    @Override
    public Stream<ScanUnit> walkHistory(RepositoryRef ref, HistoryMode mode) {
        if (mode == HistoryMode.SINCE_COMMIT || mode == HistoryMode.DIFF) {
            throw new UnsupportedOperationException("mode not yet supported: " + mode);
        }
        Path workDir = createWorkDir(ref);
        Git git = cloneRepo(ref, workDir);
        Repository repository = git.getRepository();
        try {
            List<ScanUnit> units = GitHistoryWalker.walk(ref, repository, Set.of());
            return units.stream().onClose(() -> {
                git.close();
                deleteRecursively(workDir);
            });
        } catch (IOException e) {
            git.close();
            deleteRecursively(workDir);
            throw new UncheckedIOException("failed to walk cloned repository " + ref.id(), e);
        } catch (RuntimeException e) {
            git.close();
            deleteRecursively(workDir);
            throw e;
        }
    }

    private Git cloneRepo(RepositoryRef ref, Path workDir) {
        var clone = Git.cloneRepository().setURI(ref.location()).setDirectory(workDir.toFile()).setCloneAllBranches(true);
        Optional<String> token = credentials.resolve(ref.tokenRef());
        token.ifPresent(t -> clone.setCredentialsProvider(credentialsFor(t)));
        try {
            return clone.call();
        } catch (Exception e) {
            deleteRecursively(workDir);
            throw new IllegalStateException("failed to clone " + ref.id() + ": " + e.getMessage(), e);
        }
    }

    /** Token → JGit-Credentials; je Plattform leicht unterschiedlich (Username-Konvention). */
    protected UsernamePasswordCredentialsProvider credentialsFor(String token) {
        return new UsernamePasswordCredentialsProvider(token, "");
    }

    private Path createWorkDir(RepositoryRef ref) {
        try {
            return Files.createTempDirectory("scanner-clone-");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create work dir for " + ref.id(), e);
        }
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Wegwerf-Verzeichnis: Best-Effort-Cleanup.
                }
            });
        } catch (IOException ignored) {
            // Best-Effort.
        }
    }

    /** {@code Authorization: Bearer <token>}-Header oder leer (anonym). */
    protected String bearer(Optional<String> token) {
        return token.map(t -> "Bearer " + t).orElse("");
    }
}
