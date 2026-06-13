package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageSourcesUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositoryConnectorPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

/** Verwaltung der Repository-Quellen (WR-02). Credentials bleiben Referenzen (WR-32). */
@ApplicationScoped
public class ManageSourcesService implements ManageSourcesUseCase {

    private final RepositorySourcePort sources;
    private final AuditPort audit;
    private final List<RepositoryConnectorPort> connectors;

    @Inject
    public ManageSourcesService(RepositorySourcePort sources, AuditPort audit,
                                @All List<RepositoryConnectorPort> connectors) {
        this.sources = sources;
        this.audit = audit;
        this.connectors = connectors;
    }

    @Override
    public RepositorySource create(RepositorySource source, String actor) {
        RepositorySource saved = sources.save(source);
        audit.record(AuditEvent.of(actor, "source.create", saved.name(), "type=" + saved.type()));
        return saved;
    }

    @Override
    public RepositorySource update(UUID id, RepositorySource source, String actor) {
        RepositorySource toSave = new RepositorySource(id, source.name(), source.type(),
                source.location(), source.branches(), source.tokenRef(), source.enabled(),
                source.reportEmails());
        RepositorySource saved = sources.save(toSave);
        audit.record(AuditEvent.of(actor, "source.update", saved.name(), null));
        return saved;
    }

    @Override
    public void delete(UUID id, String actor) {
        sources.delete(id);
        audit.record(AuditEvent.of(actor, "source.delete", id.toString(), null));
    }

    @Override
    public List<RepositorySource> all() {
        return sources.all();
    }

    @Override
    public boolean testConnection(UUID id) {
        RepositorySource source = sources.byId(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown repository source: " + id));
        return connectors.stream().anyMatch(c -> c.supports(source.toRef()));
    }
}
