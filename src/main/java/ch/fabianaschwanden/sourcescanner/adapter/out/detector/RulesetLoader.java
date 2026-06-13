package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Lädt und kompiliert einen Gitleaks-kompatiblen TOML-Regelsatz (DR-10) zu {@link SecretRule}s. */
public final class RulesetLoader {

    private RulesetLoader() {
    }

    /** Lädt den eingebetteten Default-Regelsatz aus den Resources. */
    public static List<SecretRule> loadDefault() {
        try (InputStream in = RulesetLoader.class.getClassLoader()
                .getResourceAsStream("rulesets/gitleaks-default.toml")) {
            if (in == null) {
                throw new IllegalStateException("default ruleset not found on classpath");
            }
            return parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read default ruleset", e);
        }
    }

    /** Parst einen TOML-Regelsatz-Inhalt; ungültige Regex-Regeln werden mit Kontext gemeldet. */
    public static List<SecretRule> parse(String tomlContent) {
        TomlParseResult result = Toml.parse(tomlContent);
        if (result.hasErrors()) {
            throw new IllegalArgumentException("invalid ruleset TOML: " + result.errors().getFirst());
        }
        TomlArray rules = result.getArray("rules");
        if (rules == null) {
            return List.of();
        }
        List<SecretRule> parsed = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            TomlTable rule = rules.getTable(i);
            String id = rule.getString("id");
            String regex = rule.getString("regex");
            if (id == null || regex == null) {
                throw new IllegalArgumentException("rule[" + i + "] is missing 'id' or 'regex'");
            }
            String description = rule.getString("description");
            Severity severity = parseSeverity(rule.getString("severity"));
            try {
                parsed.add(new SecretRule(id, description, Pattern.compile(regex), severity));
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("rule '" + id + "' has an invalid regex: " + e.getMessage(), e);
            }
        }
        return List.copyOf(parsed);
    }

    private static Severity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) {
            return Severity.MEDIUM;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown severity '" + raw + "' in ruleset", e);
        }
    }
}
