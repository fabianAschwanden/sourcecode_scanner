package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.AttributeRule;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceKind;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.FileType;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceDefinitionPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourcePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceValuePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import ch.fabianaschwanden.sourcescanner.domain.service.ValueHashing;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Kundendaten-Detektor aus einer Datenquelle (FR-21..24, DR-23..28). Quelle ist entweder eine externe
 * REST-API ({@link DataSourceKind#REST}: Werte werden geladen und per Wortgrenzen-Abgleich gesucht)
 * oder eine hochgeladene Key-Value-Liste ({@link DataSourceKind#UPLOAD}: es sind nur <b>Hashes</b>
 * gespeichert, NFR-23 — der Detektor hasht jedes Code-Token und vergleicht). Der Fund trägt nie den
 * Klartextwert, nur den Attributnamen als Regel-ID und den redigierten Treffer (DR-26, FR-18).
 *
 * <p>Datenquellen sind ein Server-Thema; im CLI-/DB-freien Pfad sind die Ports nicht verfügbar — der
 * Detektor läuft dann ohne Funde weiter (Degradation statt Abbruch, DR-28).
 */
@ApplicationScoped
public class CustomerDataDetector implements DetectorPort {

    public static final String ID = "pii.customer-data-api";

    private final Instance<DataSourceDefinitionPort> definitions;
    private final Instance<DataSourcePort> dataSource;
    private final Instance<DataSourceValuePort> valueStore;
    private final String pepper;

    @Inject
    public CustomerDataDetector(Instance<DataSourceDefinitionPort> definitions,
                                Instance<DataSourcePort> dataSource,
                                Instance<DataSourceValuePort> valueStore,
                                @ConfigProperty(name = "scanner.datasource.hash-pepper") java.util.Optional<String> pepper) {
        this.definitions = definitions;
        this.dataSource = dataSource;
        this.valueStore = valueStore;
        this.pepper = pepper.orElse("");
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
        if (!config.enabled() || !definitions.isResolvable()) {
            return List.of();
        }
        List<DataSourceDefinition> enabled = definitions.get().all().stream()
                .filter(DataSourceDefinition::enabled)
                .filter(d -> !d.checkedAttributes().isEmpty())
                .toList();
        if (enabled.isEmpty()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        String[] lines = unit.content().split("\n", -1);
        for (DataSourceDefinition def : enabled) {
            if (def.kind() == DataSourceKind.UPLOAD) {
                scanUpload(def, unit, lines, findings);
            } else if (dataSource.isResolvable()) {
                scanRest(def, unit, lines, findings);
            }
        }
        return findings;
    }

    // --- REST: konkrete Werte laden und exakt suchen ---------------------------------------------

    private void scanRest(DataSourceDefinition def, ScanUnit unit, String[] lines, List<Finding> findings) {
        Map<AttributeRule, List<String>> valuesByAttr = dataSource.get().loadValues(def);
        for (Map.Entry<AttributeRule, List<String>> entry : valuesByAttr.entrySet()) {
            AttributeRule rule = entry.getKey();
            for (String value : entry.getValue()) {
                for (int i = 0; i < lines.length; i++) {
                    if (containsWholeWord(lines[i], value)) {
                        findings.add(finding(rule, unit, i + 1, value));
                    }
                }
            }
        }
    }

    // --- UPLOAD: nur Hashes vorhanden → Code-Tokens hashen und vergleichen (NFR-23) ---------------

    private void scanUpload(DataSourceDefinition def, ScanUnit unit, String[] lines, List<Finding> findings) {
        if (!valueStore.isResolvable()) {
            return;
        }
        Map<String, Set<String>> hashesByAttr = valueStore.get().hashesByAttribute(def.id());
        if (hashesByAttr.isEmpty()) {
            return;
        }
        int minLen = def.minValueLength();
        for (AttributeRule rule : def.checkedAttributes()) {
            Set<String> hashes = hashesByAttr.get(rule.field());
            if (hashes == null || hashes.isEmpty()) {
                continue;
            }
            for (int i = 0; i < lines.length; i++) {
                for (String token : tokenize(lines[i])) {
                    if (token.length() >= minLen && hashes.contains(ValueHashing.hash(token, pepper))) {
                        findings.add(finding(rule, unit, i + 1, token));
                    }
                }
            }
        }
    }

    private Finding finding(AttributeRule rule, ScanUnit unit, int line, String matchedToken) {
        return new Finding(ID, rule.category(), rule.severity(), rule.field(),
                unit.path(), line, Redaction.redact(matchedToken), unit.commitId(), false);
    }

    /** Zerlegt eine Zeile an Wortgrenzen in Tokens (für den Hash-Abgleich, DR-25). */
    private List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (isWordChar(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
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
