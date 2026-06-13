package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceSchema;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageDataSourcesUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceDefinitionPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourcePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

/**
 * Verwaltung externer Datenquellen + Attribut-Mapping (WR-50..54). Persistiert die Definition (nie
 * Werte, NFR-23), auditiert jede Änderung (NFR-11) und liefert beim Probe-Abruf nur ein redigiertes
 * Schema (IR-63). RBAC (Operator/Admin) wird in der REST-Schicht erzwungen.
 */
@ApplicationScoped
public class ManageDataSourcesService implements ManageDataSourcesUseCase {

    private final DataSourceDefinitionPort definitions;
    private final DataSourcePort dataSource;
    private final AuditPort audit;

    @Inject
    public ManageDataSourcesService(DataSourceDefinitionPort definitions, DataSourcePort dataSource, AuditPort audit) {
        this.definitions = definitions;
        this.dataSource = dataSource;
        this.audit = audit;
    }

    @Override
    public List<DataSourceDefinition> list() {
        return definitions.all();
    }

    @Override
    public DataSourceDefinition save(DataSourceDefinition definition, String actor) {
        DataSourceDefinition saved = definitions.save(definition);
        audit.record(AuditEvent.of(actor, "datasource.save", saved.name(),
                saved.checkedAttributes().size() + " geprüfte Attribut(e); enabled=" + saved.enabled()));
        return saved;
    }

    @Override
    public void delete(UUID id, String actor) {
        String name = definitions.byId(id).map(DataSourceDefinition::name).orElse(id.toString());
        definitions.delete(id);
        audit.record(AuditEvent.of(actor, "datasource.delete", name, "Datenquelle gelöscht"));
    }

    @Override
    public DataSourceSchema probe(DataSourceDefinition definition, String actor) {
        DataSourceSchema schema = dataSource.probe(definition);
        audit.record(AuditEvent.of(actor, "datasource.probe", definition.name(),
                "erreichbar=" + schema.reachable() + "; " + schema.attributes().size() + " Attribut(e)"));
        return schema;
    }
}
