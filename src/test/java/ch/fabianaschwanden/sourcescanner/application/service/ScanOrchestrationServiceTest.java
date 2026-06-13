package ch.fabianaschwanden.sourcescanner.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.GateConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode;
import ch.fabianaschwanden.sourcescanner.domain.model.OutputConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositoryConnectorPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ScanOrchestrationServiceTest {

    private final RepositoryRef repo = new RepositoryRef("self", "localGit", ".", List.of());
    private final ScanUnit unit = new ScanUnit("self", "A.java", "c1", "a@b.ch",
            Instant.now(), "secret = \"x\"", null);

    private ScanConfig config() {
        return new ScanConfig(List.of(repo), HistoryMode.FULL, 2, 1,
                Map.of("secrets", new DetectorConfig(true, Map.of())),
                new GateConfig(Severity.HIGH, false, false), OutputConfig.defaults());
    }

    private RepositoryConnectorPort connectorReturning(ScanUnit... units) {
        RepositoryConnectorPort connector = Mockito.mock(RepositoryConnectorPort.class);
        when(connector.supports(any())).thenReturn(true);
        when(connector.walkHistory(any(), any())).thenReturn(Stream.of(units));
        return connector;
    }

    /** Service mit echten No-op-Adaptern für Baseline (leer) und Commit-Cache (kein Verzeichnis). */
    private ScanOrchestrationService serviceWith(RepositoryConnectorPort connector, DetectorPort detector) {
        return new ScanOrchestrationService(
                List.of(connector), List.of(detector),
                new ch.fabianaschwanden.sourcescanner.adapter.out.baseline.JsonBaselineStore(),
                new ch.fabianaschwanden.sourcescanner.adapter.out.cache.FileCommitCache());
    }

    @Test
    void sammelt_funde_aus_aktivem_detektor() {
        DetectorPort detector = Mockito.mock(DetectorPort.class);
        when(detector.id()).thenReturn("secret.regex-ruleset");
        when(detector.category()).thenReturn(DetectorCategory.SECRET);
        when(detector.supports(any())).thenReturn(true);
        Finding finding = new Finding("secret.regex-ruleset", DetectorCategory.SECRET, Severity.HIGH,
                "rule", "A.java", 1, "***", "c1", false);
        when(detector.scan(any(), any())).thenReturn(List.of(finding));

        var service = serviceWith(connectorReturning(unit), detector);
        List<ScanResult> results = service.scan(config());

        assertEquals(1, results.size());
        assertEquals(1, results.getFirst().findings().size());
        assertTrue(results.getFirst().degradations().isEmpty());
    }

    @Test
    void detektor_timeout_degradiert_statt_abzubrechen() {
        DetectorPort slow = Mockito.mock(DetectorPort.class);
        when(slow.id()).thenReturn("slow.detector");
        when(slow.category()).thenReturn(DetectorCategory.SECRET);
        when(slow.supports(any())).thenReturn(true);
        when(slow.scan(any(), any())).thenAnswer(inv -> {
            Thread.sleep(5_000);
            return List.of();
        });

        var service = serviceWith(connectorReturning(unit), slow);
        List<ScanResult> results = service.scan(config()); // detectorTimeoutSeconds = 1

        ScanResult result = results.getFirst();
        assertTrue(result.findings().isEmpty());
        assertFalse(result.degradations().isEmpty(), "Timeout muss als Degradation erscheinen");
        assertTrue(result.degradations().getFirst().contains("timeout"));
    }
}
