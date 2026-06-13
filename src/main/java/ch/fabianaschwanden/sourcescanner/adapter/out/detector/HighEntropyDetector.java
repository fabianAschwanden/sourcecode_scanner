package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorRule;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entropie-Detektor (DR-12/DR-13): meldet hochentropische Base64-/Hex-Token oberhalb eines
 * konfigurierbaren Shannon-Schwellenwerts und ab einer Mindestlänge. Schwelle, Mindestlänge und
 * Severity stammen aus {@code detectors.secrets.entropy.*} (docs/03 §2). Framework-frei testbar (TR-24).
 */
@ApplicationScoped
public class HighEntropyDetector implements DetectorPort {

    public static final String ID = "secret.high-entropy";
    private static final String RULE_ID = "high-entropy-string";

    private static final double DEFAULT_THRESHOLD = 4.5;
    private static final int DEFAULT_MIN_LENGTH = 20;
    private static final int MAX_LINE_LENGTH = 4_000;

    /** Kandidaten-Token: lange Base64-/Hex-ähnliche Sequenzen. */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9+/=_-]{16,}");

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
        Settings settings = Settings.from(config);
        if (!settings.enabled) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        String[] lines = unit.content().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() > MAX_LINE_LENGTH) {
                continue;
            }
            Matcher m = TOKEN.matcher(line);
            while (m.find()) {
                String token = m.group();
                if (token.length() < settings.minLength) {
                    continue;
                }
                if (shannonEntropy(token) >= settings.threshold) {
                    findings.add(new Finding(ID, DetectorCategory.SECRET, settings.severity, RULE_ID,
                            unit.path(), i + 1, Redaction.redact(token), unit.commitId(), false));
                }
            }
        }
        return findings;
    }

    @Override
    public List<DetectorRule> rules() {
        return List.of(new DetectorRule(RULE_ID, "high-entropy-string",
                "High-entropy string (possible secret) above the configured Shannon threshold",
                Severity.MEDIUM));
    }

    /** Shannon-Entropie in Bits/Zeichen. */
    static double shannonEntropy(String s) {
        Map<Character, Integer> counts = new HashMap<>();
        for (int i = 0; i < s.length(); i++) {
            counts.merge(s.charAt(i), 1, Integer::sum);
        }
        double entropy = 0.0;
        int len = s.length();
        for (int count : counts.values()) {
            double p = (double) count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    /** Aus der {@code entropy}-Teilkonfig der {@code secrets}-Gruppe aufgelöste Einstellungen. */
    private record Settings(boolean enabled, double threshold, int minLength, Severity severity) {

        @SuppressWarnings("unchecked")
        static Settings from(DetectorConfig config) {
            Object raw = config.params().get("entropy");
            if (!(raw instanceof Map<?, ?> map)) {
                // Kein entropy-Block: Detektor inaktiv (Regex-Detektor deckt den secrets-Block ab).
                return new Settings(false, DEFAULT_THRESHOLD, DEFAULT_MIN_LENGTH, Severity.MEDIUM);
            }
            Map<String, Object> e = (Map<String, Object>) map;
            boolean enabled = asBool(e.get("enabled"), true);
            double threshold = asDouble(e.get("threshold"), DEFAULT_THRESHOLD);
            int minLength = asInt(e.get("minLength"), DEFAULT_MIN_LENGTH);
            Severity severity = asSeverity(e.get("severity"), Severity.MEDIUM);
            return new Settings(enabled, threshold, minLength, severity);
        }

        private static boolean asBool(Object o, boolean fallback) {
            return o instanceof Boolean b ? b : fallback;
        }

        private static double asDouble(Object o, double fallback) {
            return o instanceof Number n ? n.doubleValue() : fallback;
        }

        private static int asInt(Object o, int fallback) {
            return o instanceof Number n ? n.intValue() : fallback;
        }

        private static Severity asSeverity(Object o, Severity fallback) {
            if (o instanceof String s && !s.isBlank()) {
                try {
                    return Severity.valueOf(s.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }
    }
}
