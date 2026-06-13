package ch.fabianaschwanden.sourcescanner.adapter.out.metrics;

import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.out.MetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer-Adapter hinter {@link MetricsPort} (docs/06 §6.2): exponiert die Scanner-Metriken über
 * {@code /q/metrics} (Prometheus). Nur im Server-Profil aktiv; der CLI-Pfad verdrahtet diesen Adapter
 * nicht (OR-09).
 */
@ApplicationScoped
public class MicrometerMetricsAdapter implements MetricsPort {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> gateGauges = new ConcurrentHashMap<>();

    @Inject
    public MicrometerMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordScan(String repoId, ScanStatus status, Duration duration) {
        registry.counter("scanner_scans_total", Tags.of("repo", repoId, "status", status.name())).increment();
        registry.timer("scanner_scan_duration_seconds", Tags.of("repo", repoId)).record(duration);
    }

    @Override
    public void recordNewFindings(String severity, int count) {
        registry.counter("scanner_findings_new_total", Tags.of("severity", severity)).increment(count);
    }

    @Override
    public void recordGateStatus(String repoId, boolean passed) {
        gateGauges.computeIfAbsent(repoId, repo -> {
            AtomicInteger gauge = new AtomicInteger();
            registry.gauge("scanner_gate_status", Tags.of("repo", repo), gauge);
            return gauge;
        }).set(passed ? 0 : 1);
    }
}
