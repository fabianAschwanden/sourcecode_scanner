package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.Baseline;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.DiscoverySpec;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.port.in.StartScanUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.BaselinePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.CommitCachePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorRule;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositoryConnectorPort;
import ch.fabianaschwanden.sourcescanner.domain.service.BaselineEvaluation;
import ch.fabianaschwanden.sourcescanner.domain.service.FindingAggregation;
import ch.fabianaschwanden.sourcescanner.domain.service.SuppressionEvaluation;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Orchestriert einen Scan (docs/01 §3.2): löst Discovery-Quellen auf, baut {@link ScanUnit}s über die
 * Connectoren, verteilt Detektor-Aufrufe über einen Worker-Pool (NFR-01) mit Timeout/Isolation
 * (NFR-05/06) und führt die Rauschunterdrückungs-Pipeline aus: Inline-Suppression → Dedup
 * (FR-11) → Pfad-Suppression (FR-10) → Baseline (FR-09). Inkrementell werden gecachte Commits
 * übersprungen (FR-19). Keine Geschäftsregeln hier — die liegen in den Domain-Services.
 */
@ApplicationScoped
public class ScanOrchestrationService implements StartScanUseCase {

    private static final Logger LOG = Logger.getLogger(ScanOrchestrationService.class);

    private final List<RepositoryConnectorPort> connectors;
    private final List<DetectorPort> detectors;
    private final BaselinePort baselinePort;
    private final CommitCachePort commitCache;

    @Inject
    public ScanOrchestrationService(@All List<RepositoryConnectorPort> connectors,
                                    @All List<DetectorPort> detectors,
                                    BaselinePort baselinePort,
                                    CommitCachePort commitCache) {
        this.connectors = connectors;
        this.detectors = detectors;
        this.baselinePort = baselinePort;
        this.commitCache = commitCache;
    }

    @Override
    public List<ScanResult> scan(ScanConfig config) {
        configureCache(config);
        Baseline baseline = loadBaseline(config);
        List<ScanResult> results = new ArrayList<>();
        for (RepositoryRef repo : resolveRepositories(config)) {
            results.add(scanRepository(repo, config, baseline));
        }
        return results;
    }

    @Override
    public List<DetectorRule> declaredRules() {
        return detectors.stream().flatMap(d -> d.rules().stream()).toList();
    }

    /** Konkrete Repos plus die aus Discovery-Quellen aufgelösten Repos (FR-07). */
    private List<RepositoryRef> resolveRepositories(ScanConfig config) {
        List<RepositoryRef> repos = new ArrayList<>(config.repositories());
        for (DiscoverySpec spec : config.discoveries()) {
            RepositoryConnectorPort connector = connectors.stream()
                    .filter(c -> c.supportsDiscovery(spec))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no connector supports discovery type " + spec.type()));
            List<RepositoryRef> discovered = connector.discover(spec);
            LOG.infof("discovered %d repositories for %s scope '%s'", discovered.size(), spec.type(), spec.scope());
            repos.addAll(discovered);
        }
        return repos;
    }

    private ScanResult scanRepository(RepositoryRef repo, ScanConfig config, Baseline baseline) {
        Instant startedAt = Instant.now();
        List<Finding> rawFindings = Collections.synchronizedList(new ArrayList<>());
        List<String> degradations = Collections.synchronizedList(new ArrayList<>());
        Map<String, Instant> seenAt = Collections.synchronizedMap(new HashMap<>());
        Set<String> scannedCommits = Collections.synchronizedSet(new LinkedHashSet<>());
        Set<String> alreadyScanned = config.mode() == ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode.INCREMENTAL
                ? commitCache.scanned(repo.id())
                : Set.of();

        RepositoryConnectorPort connector = connectors.stream()
                .filter(c -> c.supports(repo))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no connector supports repository " + repo.id()));

        ExecutorService pool = Executors.newFixedThreadPool(config.workers());
        try (Stream<ScanUnit> units = connector.walkHistory(repo, config.mode())) {
            units.forEach(unit -> {
                if (alreadyScanned.contains(unit.commitId())) {
                    return;
                }
                scannedCommits.add(unit.commitId());
                scanUnit(unit, config, pool, rawFindings, degradations, seenAt);
            });
        } finally {
            pool.shutdownNow();
        }
        commitCache.markScanned(repo.id(), scannedCommits);

        // Pipeline: Dedup → Pfad-Suppression → Baseline.
        List<AggregatedFinding> aggregated = FindingAggregation.aggregate(
                new ArrayList<>(rawFindings), f -> seenAt.getOrDefault(f.fingerprint(), startedAt));
        aggregated = SuppressionEvaluation.applyPathRules(aggregated, config.suppressions(), this::groupOfId);
        aggregated = BaselineEvaluation.applyBaseline(aggregated, baseline);

        List<Finding> reportable = aggregated.stream()
                .filter(a -> !a.suppressed())
                .map(AggregatedFinding::finding)
                .toList();
        return new ScanResult(repo.id(), startedAt, Instant.now(), reportable, aggregated, degradations);
    }

    private void scanUnit(ScanUnit unit, ScanConfig config, ExecutorService pool,
                          List<Finding> findings, List<String> degradations, Map<String, Instant> seenAt) {
        List<Future<List<Finding>>> futures = new ArrayList<>();
        List<DetectorPort> active = new ArrayList<>();
        for (DetectorPort detector : detectors) {
            DetectorConfig dc = config.detector(groupOf(detector));
            if (!dc.enabled() || !detector.supports(unit.fileType())) {
                continue;
            }
            active.add(detector);
            futures.add(pool.submit((Callable<List<Finding>>) () -> detector.scan(unit, dc)));
        }
        String[] lines = unit.content().split("\n", -1);
        for (int i = 0; i < futures.size(); i++) {
            collect(active.get(i), unit, futures.get(i), config, lines, findings, degradations, seenAt);
        }
    }

    private void collect(DetectorPort detector, ScanUnit unit, Future<List<Finding>> future, ScanConfig config,
                         String[] lines, List<Finding> findings, List<String> degradations,
                         Map<String, Instant> seenAt) {
        try {
            for (Finding f : future.get(config.detectorTimeoutSeconds(), TimeUnit.SECONDS)) {
                if (isInlineSuppressed(f, lines, config.requireSuppressionReason())) {
                    continue;
                }
                findings.add(f);
                seenAt.putIfAbsent(f.fingerprint(), unit.timestamp() == null ? Instant.now() : unit.timestamp());
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            degrade(degradations, detector, unit, "timeout after " + config.detectorTimeoutSeconds() + "s");
        } catch (ExecutionException e) {
            degrade(degradations, detector, unit, "error: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            degrade(degradations, detector, unit, "interrupted");
        }
    }

    /** Inline-Direktive an der Fundzeile bzw. auf der Vorzeile (ignore-next-line), docs/03 §4. */
    private boolean isInlineSuppressed(Finding f, String[] lines, boolean requireReason) {
        int idx = f.line() - 1;
        if (idx < 0 || idx >= lines.length) {
            return false;
        }
        String findingLine = lines[idx];
        String previousLine = idx > 0 ? lines[idx - 1] : null;
        return SuppressionEvaluation.isInlineSuppressed(findingLine, previousLine, f.category(), requireReason);
    }

    private void configureCache(ScanConfig config) {
        commitCache.useDirectory(config.cacheDirectory());
    }

    private Baseline loadBaseline(ScanConfig config) {
        if (config.baseline() == null) {
            return Baseline.empty();
        }
        Optional<Baseline> loaded = baselinePort.load(Path.of(config.baseline()));
        return loaded.orElse(Baseline.empty());
    }

    private void degrade(List<String> degradations, DetectorPort detector, ScanUnit unit, String reason) {
        String message = detector.id() + " on " + unit.path() + "@" + shortId(unit.commitId()) + ": " + reason;
        LOG.warnf("detector degraded — %s", message);
        degradations.add(message);
    }

    private String shortId(String commitId) {
        return commitId == null || commitId.length() < 8 ? String.valueOf(commitId) : commitId.substring(0, 8);
    }

    /** Bildet die Detektor-Kategorie auf den YAML-Konfig-Gruppennamen ab (docs/03 §2). */
    private String groupOf(DetectorPort detector) {
        return groupOfCategory(detector.category());
    }

    /** Gruppenname zu einer Detektor-ID (für die Pfad-Suppression-Auswertung). */
    private String groupOfId(String detectorId) {
        DetectorPort detector = detectors.stream()
                .filter(d -> d.id().equals(detectorId))
                .findFirst()
                .orElse(null);
        return detector == null ? detectorId : groupOfCategory(detector.category());
    }

    private String groupOfCategory(DetectorCategory category) {
        return switch (category) {
            case SECRET -> "secrets";
            case PII -> "pii";
            case LICENSE -> "license";
            case IAC -> "iac";
            case CUSTOM -> "custom";
        };
    }
}
