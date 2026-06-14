package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryCard;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort.SourceQuery;
import java.util.List;
import java.util.UUID;

/** Driving Port — Verwaltung der Repository-Quellen (WR-02). */
public interface ManageSourcesUseCase {

    RepositorySource create(RepositorySource source, String actor);

    RepositorySource update(UUID id, RepositorySource source, String actor);

    void delete(UUID id, String actor);

    List<RepositorySource> all();

    /**
     * Repo-Karten für die Übersicht (WR-80..84): serverseitig gefiltert/sortiert, angereichert um die
     * abgeleiteten Felder Sprache (dominanter Dateityp der Funde) + letzter Scan.
     */
    List<RepositoryCard> cards(SourceQuery query, String language);

    /** Testet die Erreichbarkeit/Berechtigung einer Quelle, ohne Credentials zurückzugeben (WR-02). */
    boolean testConnection(UUID id);
}
