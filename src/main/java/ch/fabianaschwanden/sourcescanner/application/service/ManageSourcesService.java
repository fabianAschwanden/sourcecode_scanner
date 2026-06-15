package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryCard;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.model.StoredFinding;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageSourcesUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositoryConnectorPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort.SourceQuery;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Verwaltung der Repository-Quellen (WR-02). Credentials bleiben Referenzen (WR-32). */
@ApplicationScoped
public class ManageSourcesService implements ManageSourcesUseCase {

    private final RepositorySourcePort sources;
    private final AuditPort audit;
    private final List<RepositoryConnectorPort> connectors;
    private final ScanRecordPort scanRecords;
    private final FindingPort findings;

    @Inject
    public ManageSourcesService(RepositorySourcePort sources, AuditPort audit,
                                @All List<RepositoryConnectorPort> connectors,
                                ScanRecordPort scanRecords, FindingPort findings) {
        this.sources = sources;
        this.audit = audit;
        this.connectors = connectors;
        this.scanRecords = scanRecords;
        this.findings = findings;
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
                source.reportEmails(), source.remediationEnabled(), source.description(),
                source.visibility());
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
    public List<RepositoryCard> cards(SourceQuery query, String language) {
        Map<String, ScanRecord> lastScanByRepo = lastScanByRepo();
        List<RepositoryCard> cards = sources.query(query).stream()
                .map(s -> toCard(s, lastScanByRepo.get(s.name())))
                .filter(c -> language == null || language.isBlank() || language.equalsIgnoreCase(c.language()))
                .toList();
        if ("updated".equalsIgnoreCase(query.sort())) {
            // Zuletzt aktualisiert zuerst; nie gescannte (null) ans Ende.
            return cards.stream()
                    .sorted(Comparator.comparing(RepositoryCard::lastScanAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        return cards;
    }

    private RepositoryCard toCard(RepositorySource s, ScanRecord last) {
        Instant at = last == null ? null : (last.finishedAt() == null ? last.startedAt() : last.finishedAt());
        String status = last == null ? null : last.status().name();
        String error = last == null ? null : last.errorMessage();
        return RepositoryCard.from(s, dominantLanguage(s.name()), at, status, error);
    }

    /** Letzter Scan-Lauf je Repo (repoId = Quellen-Name), für Zeitpunkt/Status/Fehler der Karte. */
    private Map<String, ScanRecord> lastScanByRepo() {
        Map<String, ScanRecord> latest = new LinkedHashMap<>();
        for (ScanRecord r : scanRecords.recent(500)) {
            latest.merge(r.repoId(), r, (a, b) -> scanTime(a).isAfter(scanTime(b)) ? a : b);
        }
        return latest;
    }

    private Instant scanTime(ScanRecord r) {
        return r.finishedAt() == null ? r.startedAt() : r.finishedAt();
    }

    /** Dominanter Dateityp (Sprache) aus den Funden eines Repos; leer, wenn keine vorliegen (DR-/WR-83). */
    private String dominantLanguage(String repoName) {
        List<StoredFinding> repoFindings = findings.query(
                new FindingPort.FindingQuery(repoName, null, null, null, 0, 500));
        Map<String, Integer> byExt = new LinkedHashMap<>();
        for (StoredFinding f : repoFindings) {
            String ext = extensionOf(f.file());
            if (!ext.isBlank()) {
                byExt.merge(ext, 1, Integer::sum);
            }
        }
        return byExt.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private String extensionOf(String file) {
        int dot = file.lastIndexOf('.');
        int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        return dot > slash && dot >= 0 ? file.substring(dot + 1) : "";
    }

    @Override
    public boolean testConnection(UUID id) {
        RepositorySource source = sources.byId(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown repository source: " + id));
        return connectors.stream().anyMatch(c -> c.supports(source.toRef()));
    }
}
