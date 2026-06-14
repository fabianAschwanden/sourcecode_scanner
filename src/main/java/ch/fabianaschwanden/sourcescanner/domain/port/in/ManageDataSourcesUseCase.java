package ch.fabianaschwanden.sourcescanner.domain.port.in;

import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceSchema;
import java.util.List;
import java.util.UUID;

/**
 * Verwaltung externer Datenquellen + Attribut-Mapping (WR-50..54). Pflege nur Operator/Admin (NFR-24);
 * jede Änderung wird auditiert (NFR-11). Der Probe-Abruf liefert nur ein redigiertes Schema (IR-63).
 */
public interface ManageDataSourcesUseCase {

    List<DataSourceDefinition> list();

    DataSourceDefinition save(DataSourceDefinition definition, String actor);

    void delete(UUID id, String actor);

    /** Testabruf gegen die (ggf. noch nicht gespeicherte) Definition; redigiertes Schema (IR-63/WR-51). */
    DataSourceSchema probe(DataSourceDefinition definition, String actor);
}
