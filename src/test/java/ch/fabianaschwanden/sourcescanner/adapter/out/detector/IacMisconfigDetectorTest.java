package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.FileType;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IacMisconfigDetectorTest {

    private final IacMisconfigDetector detector = new IacMisconfigDetector();
    private final DetectorConfig allTargets = new DetectorConfig(true, Map.of());

    private ScanUnit unit(String path, String content) {
        return new ScanUnit("repo", path, "c1", "a@b.ch", Instant.now(), content, null);
    }

    @Test
    void dockerfile_user_root_und_latest() {
        List<Finding> f = detector.scan(unit("Dockerfile", "FROM ubuntu:latest\nUSER root\n"), allTargets);
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("docker-user-root")));
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("docker-latest-tag")));
    }

    @Test
    void kubernetes_privileged() {
        String yaml = "apiVersion: v1\nkind: Pod\nspec:\n  containers:\n    - securityContext:\n        privileged: true\n";
        List<Finding> f = detector.scan(unit("pod.yaml", yaml), allTargets);
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("k8s-privileged")));
    }

    @Test
    void terraform_open_ingress() {
        List<Finding> f = detector.scan(unit("main.tf", "cidr_blocks = [\"0.0.0.0/0\"]\n"), allTargets);
        assertTrue(f.stream().anyMatch(x -> x.ruleId().equals("tf-open-ingress")));
    }

    @Test
    void nicht_iac_datei_liefert_nichts() {
        assertTrue(detector.scan(unit("README.md", "USER root privileged: true"), allTargets).isEmpty());
    }

    @Test
    void targets_filter_schliesst_dockerfile_aus() {
        DetectorConfig onlyTf = new DetectorConfig(true, Map.of("targets", List.of("terraform")));
        assertTrue(detector.scan(unit("Dockerfile", "USER root\n"), onlyTf).isEmpty());
    }

    @Test
    void supports_filtert_binaerdateien() {
        assertFalse(detector.supports(FileType.BINARY));
        assertTrue(detector.supports(FileType.CONFIG));
    }
}
