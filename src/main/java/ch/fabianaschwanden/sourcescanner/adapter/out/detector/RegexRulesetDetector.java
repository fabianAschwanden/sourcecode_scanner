package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorRule;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Built-in-Secret-Detektor (DR-10/11): wendet einen Gitleaks-kompatiblen Regelsatz zeilenweise an
 * und liefert Funde mit bereits redigiertem Treffer (FR-18). Erfüllt {@link DetectorPort} direkt
 * als CDI-Bean (docs/09 §4); die Scan-Logik ist framework-frei und ohne Quarkus testbar (TR-24).
 */
@ApplicationScoped
public class RegexRulesetDetector implements DetectorPort {

    public static final String ID = "secret.regex-ruleset";

    /** Schutz gegen pathologisches Regex-Backtracking auf Riesenzeilen (NFR-04). */
    private static final int MAX_LINE_LENGTH = 4_000;

    private final List<SecretRule> rules;

    public RegexRulesetDetector() {
        this(RulesetLoader.loadDefault());
    }

    /** Test-/Plugin-Konstruktor mit explizitem Regelsatz. */
    public RegexRulesetDetector(List<SecretRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public DetectorCategory category() {
        return DetectorCategory.SECRET;
    }

    @Override
    public List<Finding> scan(ScanUnit unit, DetectorConfig config) {
        if (!config.enabled()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        String[] lines = unit.content().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() > MAX_LINE_LENGTH) {
                continue;
            }
            int lineNumber = i + 1;
            for (SecretRule rule : rules) {
                Matcher m = rule.pattern().matcher(line);
                while (m.find()) {
                    findings.add(new Finding(
                            ID,
                            DetectorCategory.SECRET,
                            rule.severity(),
                            rule.id(),
                            unit.path(),
                            lineNumber,
                            Redaction.redact(m.group()),
                            unit.commitId(),
                            false));
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
}
