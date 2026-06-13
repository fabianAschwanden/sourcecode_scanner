package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.FileType;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorRule;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * IaC-Detektor (DR-31): erkennt unsichere Defaults in Terraform-, Kubernetes- und Dockerfile-Dateien
 * über den IaC-Regelsatz. Die Ziel-Technologie einer Datei wird aus Pfad/Inhalt bestimmt; nur Regeln
 * mit passendem {@code target} (und durch {@code targets: [...]} aktiviert) laufen (DR-04). Framework-frei.
 */
@ApplicationScoped
public class IacMisconfigDetector implements DetectorPort {

    public static final String ID = "iac.misconfig";

    private final List<IacRule> rules;

    public IacMisconfigDetector() {
        this(IacRulesetLoader.loadDefault());
    }

    /** Test-/Plugin-Konstruktor mit explizitem Regelsatz. */
    public IacMisconfigDetector(List<IacRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public DetectorCategory category() {
        return DetectorCategory.IAC;
    }

    @Override
    public boolean supports(FileType type) {
        // IaC liegt in Config- oder (Dockerfile) Other-Dateien, nie binär.
        return type == FileType.CONFIG || type == FileType.OTHER;
    }

    @Override
    public List<Finding> scan(ScanUnit unit, DetectorConfig config) {
        if (!config.enabled()) {
            return List.of();
        }
        String target = targetOf(unit);
        if (target == null) {
            return List.of();
        }
        Set<String> enabledTargets = enabledTargets(config);
        if (!enabledTargets.contains(target)) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        String[] lines = unit.content().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (SafeRegex.tooLong(line)) {
                continue;
            }
            CharSequence safe = SafeRegex.interruptible(line);
            for (IacRule rule : rules) {
                if (!rule.target().equals(target)) {
                    continue;
                }
                Matcher m = rule.pattern().matcher(safe);
                if (m.find()) {
                    findings.add(new Finding(ID, DetectorCategory.IAC, rule.severity(), rule.id(),
                            unit.path(), i + 1, Redaction.redact(m.group()), unit.commitId(), false));
                }
            }
        }
        return findings;
    }

    @Override
    public List<DetectorRule> rules() {
        return rules.stream()
                .map(r -> new DetectorRule(r.id(), r.id(), r.description(), r.severity()))
                .toList();
    }

    /** Leitet die IaC-Technologie aus dem Pfad/Inhalt ab; {@code null} wenn keine IaC-Datei. */
    private String targetOf(ScanUnit unit) {
        String path = unit.path().toLowerCase(Locale.ROOT);
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (path.endsWith(".tf") || path.endsWith(".tf.json")) {
            return "terraform";
        }
        if (fileName.equals("dockerfile") || fileName.startsWith("dockerfile.") || fileName.endsWith(".dockerfile")) {
            return "dockerfile";
        }
        if ((path.endsWith(".yaml") || path.endsWith(".yml")) && looksLikeKubernetes(unit.content())) {
            return "kubernetes";
        }
        return null;
    }

    private boolean looksLikeKubernetes(String content) {
        return content.contains("apiVersion:") && content.contains("kind:");
    }

    private Set<String> enabledTargets(DetectorConfig config) {
        Object raw = config.params().get("targets");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(o -> String.valueOf(o).toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        }
        return Set.of("terraform", "kubernetes", "dockerfile");
    }
}
