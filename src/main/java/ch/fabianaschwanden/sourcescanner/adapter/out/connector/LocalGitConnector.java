package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositoryConnectorPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Liest lokale Git-Repositories über JGit (IR-01, FR-01): öffnet das Repo und delegiert den
 * Commit-/Datei-Walk an {@link GitHistoryWalker}. {@code FULL} und {@code INCREMENTAL} walken beide
 * alle Branches; das Überspringen bereits gescannter Commits (inkrementell) übernimmt der
 * Orchestrator über den Commit-Cache (FR-19, NFR-02).
 */
@ApplicationScoped
public class LocalGitConnector implements RepositoryConnectorPort {

    @Override
    public boolean supports(RepositoryRef ref) {
        return ref.type() == null || "localGit".equalsIgnoreCase(ref.type());
    }

    @Override
    public Stream<ScanUnit> walkHistory(RepositoryRef ref, HistoryMode mode) {
        if (mode == HistoryMode.SINCE_COMMIT || mode == HistoryMode.DIFF) {
            throw new UnsupportedOperationException("mode not yet supported: " + mode);
        }
        Repository repository = open(ref);
        try {
            // Lazy/gestreamt (History-Slicing); HEAD-Modus walkt nur den aktuellen Stand (keine History).
            return GitHistoryWalker.stream(ref, repository, Set.of(), mode == HistoryMode.HEAD)
                    .onClose(repository::close);
        } catch (RuntimeException e) {
            repository.close();
            throw e;
        }
    }

    private Repository open(RepositoryRef ref) {
        java.io.File path = new java.io.File(ref.location());
        if (!path.exists()) {
            throw new IllegalArgumentException("local path does not exist: " + ref.location()
                    + " (the server process must be able to read this path)");
        }
        FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .findGitDir(path)
                .setMustExist(true);
        if (builder.getGitDir() == null) {
            // findGitDir hat von hier aufwärts kein .git gefunden ⇒ klarer Fehler statt
            // JGits kryptischem "One of setGitDir or setWorkTree must be called.".
            throw new IllegalArgumentException("no git repository found at or above: " + ref.location()
                    + " (expected a .git directory; for localGit point to the working tree or its .git)");
        }
        try {
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to open git repository at " + ref.location(), e);
        }
    }
}
