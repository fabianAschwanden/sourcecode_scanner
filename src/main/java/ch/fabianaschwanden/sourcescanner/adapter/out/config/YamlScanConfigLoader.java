package ch.fabianaschwanden.sourcescanner.adapter.out.config;

import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.DiscoverySpec;
import ch.fabianaschwanden.sourcescanner.domain.model.GateConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode;
import ch.fabianaschwanden.sourcescanner.domain.model.OutputConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.SuppressionRule;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanConfigPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lädt {@link ScanConfig} aus {@code scanner.yaml} (FR-04, docs/03 §2). Phase-1-Teilmenge:
 * {@code repositories}, {@code history.mode}, {@code concurrency}, {@code detectors}, {@code gate},
 * {@code output}. Validierungsfehler nennen das betroffene Feld (NFR-19).
 */
@ApplicationScoped
public class YamlScanConfigLoader implements ScanConfigPort {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Override
    public ScanConfig load(Path configFile) {
        JsonNode root;
        try {
            if (!Files.isRegularFile(configFile)) {
                throw new ConfigException("config file not found: " + configFile);
            }
            root = YAML.readTree(Files.readAllBytes(configFile));
        } catch (IOException e) {
            throw new ConfigException("failed to read config " + configFile + ": " + e.getMessage(), e);
        }
        if (root == null || root.isMissingNode()) {
            throw new ConfigException("config is empty: " + configFile);
        }
        JsonNode scan = required(root, "scan");
        return new ScanConfigFactory(scan).build();
    }

    private static JsonNode required(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new ConfigException("missing required field '" + field + "'");
        }
        return node;
    }

    /** Übersetzt den {@code scan}-Teilbaum in das Domänen-Modell; kapselt das defensive Parsen. */
    private static final class ScanConfigFactory {

        private final JsonNode scan;

        ScanConfigFactory(JsonNode scan) {
            this.scan = scan;
        }

        ScanConfig build() {
            List<RepositoryRef> repos = new ArrayList<>();
            List<DiscoverySpec> discoveries = new ArrayList<>();
            parseRepositories(repos, discoveries);
            if (repos.isEmpty() && discoveries.isEmpty()) {
                throw new ConfigException("scan.repositories must contain at least one entry");
            }
            return new ScanConfig(
                    repos,
                    discoveries,
                    historyMode(),
                    intAt("concurrency", "workers", Runtime.getRuntime().availableProcessors()),
                    intAt("concurrency", "detectorTimeoutSeconds", 30),
                    detectors(),
                    gate(),
                    output(),
                    text(scan, "baseline", null),
                    suppressions(),
                    scan.path("gate").path("requireSuppressionReason").asBoolean(false),
                    text(scan, "cacheDirectory", null));
        }

        /** Trennt konkrete Repos (localGit/Plattform-Repo) von org-weiten Discovery-Quellen (FR-07). */
        private void parseRepositories(List<RepositoryRef> repos, List<DiscoverySpec> discoveries) {
            JsonNode arr = scan.get("repositories");
            if (arr == null || !arr.isArray()) {
                return;
            }
            for (int i = 0; i < arr.size(); i++) {
                JsonNode r = arr.get(i);
                String type = text(r, "type", "localGit");
                String tokenRef = r.path("auth").path("tokenRef").isMissingNode()
                        ? null : r.path("auth").get("tokenRef").asText();
                List<String> branches = new ArrayList<>();
                JsonNode b = r.get("branches");
                if (b != null && b.isArray()) {
                    b.forEach(n -> branches.add(n.asText()));
                }
                String scope = firstNonNull(text(r, "org", null), text(r, "group", null), text(r, "project", null));
                boolean concreteRepo = !text(r, "path", "").isBlank() || !text(r, "repo", "").isBlank();
                if ("localGit".equalsIgnoreCase(type)) {
                    String path = text(r, "path", null);
                    if (path == null || path.isBlank()) {
                        throw new ConfigException("scan.repositories[" + i + "].path is required for type localGit");
                    }
                    repos.add(new RepositoryRef(text(r, "id", path), type, path, branches, tokenRef));
                } else if (concreteRepo && !text(r, "repo", "").isBlank() && scope == null) {
                    // Plattform-Einzelrepo mit fertiger Clone-URL
                    String url = text(r, "cloneUrl", text(r, "repo", null));
                    repos.add(new RepositoryRef(text(r, "id", url), type, url, branches, tokenRef));
                } else if (scope != null) {
                    // org-/group-/project-weite Discovery
                    discoveries.add(new DiscoverySpec(type, text(r, "baseUrl", null), scope,
                            text(r, "repoFilter", null), r.path("includeArchived").asBoolean(false),
                            branches, tokenRef));
                } else {
                    throw new ConfigException("scan.repositories[" + i + "]: needs 'path' (localGit), "
                            + "a concrete 'repo' clone URL, or an org/group/project for discovery");
                }
            }
        }

        private List<SuppressionRule> suppressions() {
            List<SuppressionRule> rules = new ArrayList<>();
            JsonNode arr = scan.get("suppress");
            if (arr != null && arr.isArray()) {
                for (JsonNode s : arr) {
                    String path = text(s, "path", null);
                    if (path == null || path.isBlank()) {
                        throw new ConfigException("scan.suppress[].path must not be blank");
                    }
                    rules.add(new SuppressionRule(path, text(s, "detector", null), text(s, "reason", null)));
                }
            }
            return rules;
        }

        private String firstNonNull(String... values) {
            for (String v : values) {
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
            return null;
        }

        private HistoryMode historyMode() {
            JsonNode history = scan.get("history");
            String mode = history == null ? "full" : text(history, "mode", "full");
            return switch (mode.toLowerCase(Locale.ROOT)) {
                case "head" -> HistoryMode.HEAD;
                case "full" -> HistoryMode.FULL;
                case "incremental" -> HistoryMode.INCREMENTAL;
                case "sincecommit" -> HistoryMode.SINCE_COMMIT;
                case "diff", "pr" -> HistoryMode.DIFF;
                default -> throw new ConfigException("scan.history.mode: unknown value '" + mode + "'");
            };
        }

        private Map<String, DetectorConfig> detectors() {
            Map<String, DetectorConfig> result = new LinkedHashMap<>();
            JsonNode det = scan.get("detectors");
            if (det == null) {
                return result;
            }
            det.fieldNames().forEachRemaining(group -> {
                JsonNode node = det.get(group);
                boolean enabled = node.path("enabled").asBoolean(false);
                Map<String, Object> params = new LinkedHashMap<>();
                node.fieldNames().forEachRemaining(key -> {
                    if (!"enabled".equals(key)) {
                        params.put(key, YAML.convertValue(node.get(key), Object.class));
                    }
                });
                result.put(group, new DetectorConfig(enabled, params));
            });
            return result;
        }

        private GateConfig gate() {
            JsonNode gate = scan.get("gate");
            if (gate == null) {
                return GateConfig.defaults();
            }
            Severity failOn = severity(text(gate, "failOn", "HIGH"));
            boolean failOnNewOnly = gate.path("failOnNewOnly").asBoolean(false);
            boolean softFail = gate.path("softFail").asBoolean(false);
            return new GateConfig(failOn, failOnNewOnly, softFail);
        }

        private OutputConfig output() {
            JsonNode out = scan.get("output");
            if (out == null) {
                return OutputConfig.defaults();
            }
            List<String> formats = new ArrayList<>();
            JsonNode f = out.get("formats");
            if (f != null && f.isArray()) {
                f.forEach(n -> formats.add(n.asText().toLowerCase(Locale.ROOT)));
            }
            String dir = text(out, "directory", "./scan-reports");
            boolean redact = out.path("redact").asBoolean(true);
            return new OutputConfig(formats, dir, redact);
        }

        private Severity severity(String raw) {
            try {
                return Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ConfigException("scan.gate.failOn: unknown severity '" + raw + "'");
            }
        }

        private int intAt(String parent, String field, int fallback) {
            JsonNode p = scan.get(parent);
            if (p == null) {
                return fallback;
            }
            JsonNode v = p.get(field);
            return v == null ? fallback : v.asInt(fallback);
        }

        private String text(JsonNode node, String field, String fallback) {
            JsonNode v = node.get(field);
            return v == null || v.isNull() ? fallback : v.asText();
        }
    }
}
