package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.DiscoverySpec;
import ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.util.List;
import java.util.stream.Stream;

/**
 * Einheitlicher Zugriff auf Quellsysteme (docs/01 §3.1): lokales Git über JGit (IR-01) sowie
 * Bitbucket/GitHub/GitLab mit org-weiter Discovery (FR-07, IR-02/03/04).
 */
public interface RepositoryConnectorPort {

    /** {@code true}, wenn dieser Connector den Repository-Typ bedienen kann (z. B. {@code "localGit"}). */
    boolean supports(RepositoryRef ref);

    /** {@code true}, wenn dieser Connector die Discovery-Quelle (Plattform-Typ) bedienen kann. */
    default boolean supportsDiscovery(DiscoverySpec spec) {
        return false;
    }

    /**
     * Löst eine org-/group-/project-weite Quelle in konkrete, klon-fähige {@link RepositoryRef}s auf
     * (FR-07). Default: keine Discovery (z. B. localGit).
     */
    default List<RepositoryRef> discover(DiscoverySpec spec) {
        return List.of();
    }

    /**
     * Iteriert die Historie und liefert je (Commit × Datei) eine {@link ScanUnit}. Der Stream ist
     * lazy/ressourcenbehaftet — vom Aufrufer in einem try-with-resources zu schliessen.
     */
    Stream<ScanUnit> walkHistory(RepositoryRef ref, HistoryMode mode);
}
