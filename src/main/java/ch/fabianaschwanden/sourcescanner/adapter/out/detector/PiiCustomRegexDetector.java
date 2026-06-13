package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.FileType;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Frei konfigurierbarer PII-Detektor (DR-21): liest {@code customRegex: [{name, pattern, severity}]}
 * aus der {@code pii}-Konfiguration und meldet redigierte Treffer mit {@code ruleId = name}. Ungültige
 * Muster werden bei der Auswertung gemeldet (Degradation), brechen den Lauf nicht ab. Framework-frei.
 */
@ApplicationScoped
public class PiiCustomRegexDetector implements DetectorPort {

    public static final String ID = "pii.custom-regex";

    /** Pattern-Cache über den Regex-String (Detektor ist ApplicationScoped/Singleton-artig). */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

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
        if (!config.enabled()) {
            return List.of();
        }
        List<CustomRule> customRules = parseRules(config);
        if (customRules.isEmpty()) {
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
            for (CustomRule rule : customRules) {
                Matcher m = rule.pattern().matcher(safe);
                while (m.find()) {
                    findings.add(new Finding(ID, DetectorCategory.PII, rule.severity(), rule.name(),
                            unit.path(), i + 1, Redaction.redact(m.group()), unit.commitId(), false));
                }
            }
        }
        return findings;
    }

    private List<CustomRule> parseRules(DetectorConfig config) {
        Object raw = config.params().get("customRegex");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<CustomRule> rules = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object name = map.get("name");
            Object pattern = map.get("pattern");
            if (name == null || pattern == null) {
                throw new IllegalArgumentException("pii.customRegex entry needs 'name' and 'pattern'");
            }
            Severity severity = parseSeverity(map.get("severity"));
            try {
                Pattern compiled = patternCache.computeIfAbsent(pattern.toString(), Pattern::compile);
                rules.add(new CustomRule(name.toString(), compiled, severity));
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                        "pii.customRegex '" + name + "' has an invalid pattern: " + e.getMessage(), e);
            }
        }
        return rules;
    }

    private Severity parseSeverity(Object raw) {
        if (raw == null) {
            return Severity.HIGH;
        }
        try {
            return Severity.valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Severity.HIGH;
        }
    }

    private record CustomRule(String name, Pattern pattern, Severity severity) {
    }
}
