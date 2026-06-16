package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistenz der verwalteten Repository-Quellen (WR-02). */
public interface RepositorySourcePort {

    RepositorySource save(RepositorySource source);

    Optional<RepositorySource> byId(UUID id);

    /** Quelle anhand ihres (eindeutigen) Namens — Scans referenzieren das Repo über den Namen. */
    Optional<RepositorySource> byName(String name);

    List<RepositorySource> all();

    /** Serverseitige Suche/Filter/Sortierung für die Repo-Übersicht (WR-81); alle Kriterien optional. */
    List<RepositorySource> query(SourceQuery query);

    void delete(UUID id);

    /**
     * Filter-/Sortierkriterien der Repo-Übersicht. {@code q} sucht in Name/Beschreibung; {@code type}
     * filtert die Quellenart; {@code sort} ist {@code name} (Default) oder {@code updated} (Letzteres
     * wird in der Application-Schicht über den letzten Scan aufgelöst). {@code language} wird ebenfalls
     * dort angewandt (abgeleiteter Wert, nicht persistiert).
     */
    record SourceQuery(String q, String type, String sort) {
        public SourceQuery {
            q = q == null || q.isBlank() ? null : q.trim();
            type = type == null || type.isBlank() ? null : type.trim();
            sort = sort == null || sort.isBlank() ? "name" : sort.trim();
        }
    }
}
