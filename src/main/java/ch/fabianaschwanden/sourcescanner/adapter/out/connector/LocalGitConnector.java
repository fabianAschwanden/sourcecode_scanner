package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositoryConnectorPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
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
            List<ScanUnit> units = GitHistoryWalker.walk(ref, repository, Set.of());
            return units.stream().onClose(repository::close);
        } catch (IOException e) {
            repository.close();
            throw new UncheckedIOException("failed to walk repository " + ref.location(), e);
        } catch (RuntimeException e) {
            repository.close();
            throw e;
        }
    }

    private Repository open(RepositoryRef ref) {
        try {
            return new FileRepositoryBuilder()
                    .findGitDir(new java.io.File(ref.location()))
                    .setMustExist(true)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("no git repository at " + ref.location(), e);
        }
    }
}
