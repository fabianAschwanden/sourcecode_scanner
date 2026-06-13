package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistenz der verwalteten externen Datenquellen inkl. Attribut-Mapping (WR-50/53). */
public interface DataSourceDefinitionPort {

    DataSourceDefinition save(DataSourceDefinition definition);

    Optional<DataSourceDefinition> byId(UUID id);

    Optional<DataSourceDefinition> byName(String name);

    List<DataSourceDefinition> all();

    void delete(UUID id);
}
