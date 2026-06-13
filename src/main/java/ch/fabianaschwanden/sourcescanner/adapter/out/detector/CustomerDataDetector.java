package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.AttributeRule;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.FileType;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceDefinitionPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourcePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kundendaten-Detektor, gespeist aus einer externen REST-Datenquelle (FR-21..23, DR-23..28). Lädt die
 * im Attribut-Mapping als geprüft markierten Werte (z. B. Partnernummern, Namen) und erkennt sie im
 * Code mit Wortgrenzen-Abgleich. Trägt nie den Klartextwert — der Fund enthält nur den Attributnamen
 * als Regel-ID und den redigierten Treffer (DR-26, FR-18).
 *
 * <p>Die persistierten Datenquellen sind ein Server-Thema; im CLI-/DB-freien Pfad sind die Ports nicht
 * verfügbar. Der Detektor läuft dann ohne Funde weiter (Degradation statt Abbruch, DR-28).
 */
@ApplicationScoped
public class CustomerDataDetector implements DetectorPort {

    public static final String ID = "pii.customer-data-api";

    private final Instance<DataSourceDefinitionPort> definitions;
    private final Instance<DataSourcePort> dataSource;

    @Inject
    public CustomerDataDetector(Instance<DataSourceDefinitionPort> definitions, Instance<DataSourcePort> dataSource) {
        this.definitions = definitions;
        this.dataSource = dataSource;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public DetectorCategory category() {
        return DetectorCategory.PII;
    }

    @Override
    public boolean supports(FileType type) {
        return type != FileType.BINARY;
    }

    @Override
    public List<Finding> scan(ScanUnit unit, DetectorConfig config) {
        if (!config.enabled() || !definitions.isResolvable() || !dataSource.isResolvable()) {
            return List.of();
        }
        List<DataSourceDefinition> enabled = definitions.get().all().stream()
                .filter(DataSourceDefinition::enabled)
                .filter(d -> !d.checkedAttributes().isEmpty())
                .toList();
        if (enabled.isEmpty()) {
            return List.of();
        }
        DataSourcePort port = dataSource.get();
        List<Finding> findings = new ArrayList<>();
        String[] lines = unit.content().split("\n", -1);
        for (DataSourceDefinition def : enabled) {
            Map<AttributeRule, List<String>> valuesByAttr = port.loadValues(def);
            scanValues(unit, lines, valuesByAttr, findings);
        }
        return findings;
    }

    private void scanValues(ScanUnit unit, String[] lines, Map<AttributeRule, List<String>> valuesByAttr,
                            List<Finding> findings) {
        for (Map.Entry<AttributeRule, List<String>> entry : valuesByAttr.entrySet()) {
            AttributeRule rule = entry.getKey();
            for (String value : entry.getValue()) {
                for (int i = 0; i < lines.length; i++) {
                    if (containsWholeWord(lines[i], value)) {
                        findings.add(new Finding(ID, rule.category(), rule.severity(), rule.field(),
                                unit.path(), i + 1, Redaction.redact(value), unit.commitId(), false));
                    }
                }
            }
        }
    }

    /** Exakter Treffer mit Wortgrenzen (DR-25): kein Teil-Treffer mitten in alphanumerischem Kontext. */
    private boolean containsWholeWord(String line, String value) {
        int from = 0;
        while (true) {
            int idx = line.indexOf(value, from);
            if (idx < 0) {
                return false;
            }
            boolean leftOk = idx == 0 || !isWordChar(line.charAt(idx - 1));
            int end = idx + value.length();
            boolean rightOk = end >= line.length() || !isWordChar(line.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = idx + 1;
        }
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
