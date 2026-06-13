package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Lädt den IaC-Regelsatz (TOML, mit {@code target}-Feld) zu {@link IacRule}s — analog RulesetLoader. */
public final class IacRulesetLoader {

    private IacRulesetLoader() {
    }

    public static List<IacRule> loadDefault() {
        try (InputStream in = IacRulesetLoader.class.getClassLoader()
                .getResourceAsStream("rulesets/iac-default.toml")) {
            if (in == null) {
                throw new IllegalStateException("iac ruleset not found on classpath");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read iac ruleset", e);
        }
    }

    public static List<IacRule> parse(String tomlContent) {
        TomlParseResult result = Toml.parse(tomlContent);
        if (result.hasErrors()) {
            throw new IllegalArgumentException("invalid iac ruleset TOML: " + result.errors().getFirst());
        }
        TomlArray rules = result.getArray("rules");
        if (rules == null) {
            return List.of();
        }
        List<IacRule> parsed = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            TomlTable rule = rules.getTable(i);
            String id = rule.getString("id");
            String regex = rule.getString("regex");
            String target = rule.getString("target");
            if (id == null || regex == null || target == null) {
                throw new IllegalArgumentException("iac rule[" + i + "] needs 'id', 'regex' and 'target'");
            }
            Severity severity = parseSeverity(rule.getString("severity"));
            try {
                parsed.add(new IacRule(id, rule.getString("description"), Pattern.compile(regex),
                        severity, target.toLowerCase(Locale.ROOT)));
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("iac rule '" + id + "' invalid regex: " + e.getMessage(), e);
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
            throw new IllegalArgumentException("unknown severity '" + raw + "' in iac ruleset", e);
        }
    }
}
