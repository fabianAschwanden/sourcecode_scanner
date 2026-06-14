package ch.fabianaschwanden.sourcescanner.adapter.in.cli;

import ch.fabianaschwanden.sourcescanner.domain.model.AggregatedFinding;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Meldet ein CLI-Scan-Ergebnis an den zentralen Server (IR-21/26). <b>Gate-entkoppelt:</b> jeder
 * Fehler wird geschluckt (nur Hinweis auf stderr), der Exit-Code des Gates bleibt unberührt. Auth über
 * OIDC Client-Credentials (Service-Rolle {@code ci}); Server-URL/Token nur aus Umgebungsvariablen
 * (Secret-Referenzen, NFR-08). Sendet ausschliesslich <b>redigierte</b> Treffer (FR-18).
 *
 * <p>Konfiguration per Env (CI-typisch): {@code SCANNER_SERVER_URL}, {@code SCANNER_OIDC_TOKEN_URL},
 * {@code SCANNER_CI_CLIENT_ID}, {@code SCANNER_CI_CLIENT_SECRET}; optional {@code SCANNER_RUN_REF},
 * {@code SCANNER_PIPELINE_URL}, {@code SCANNER_COMMIT}, {@code SCANNER_BRANCH}, {@code SCANNER_ACTOR}.
 */
final class ReportBackClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Liefert {@code true}, wenn die nötigen Env-Variablen gesetzt sind (Report-Back aktiv). */
    boolean enabled() {
        return env("SCANNER_SERVER_URL") != null && env("SCANNER_OIDC_TOKEN_URL") != null
                && env("SCANNER_CI_CLIENT_ID") != null && env("SCANNER_CI_CLIENT_SECRET") != null;
    }

    /** Sendet das Ergebnis; schluckt alle Fehler (gate-entkoppelt, IR-26). */
    void send(List<ScanResult> results, String mode) {
        try {
            String token = fetchToken();
            String repoId = results.isEmpty() ? "unknown" : results.getFirst().repoId();
            String payload = MAPPER.writeValueAsString(buildPayload(results, repoId, mode));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(env("SCANNER_SERVER_URL")) + "/api/ingest"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                System.err.println("report-back: Server antwortete mit HTTP " + response.statusCode()
                        + " (Gate unverändert)");
            } else {
                System.out.println("report-back: Ergebnis an zentralen Server gemeldet.");
            }
        } catch (Exception e) {
            // Bewusst geschluckt — der Build-Exit-Code hängt nie am Server (IR-26).
            System.err.println("report-back fehlgeschlagen (Gate unverändert): " + e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(List<ScanResult> results, String repoId, String mode) {
        List<Map<String, Object>> findings = new ArrayList<>();
        for (ScanResult result : results) {
            for (AggregatedFinding agg : result.aggregated()) {
                Finding f = agg.finding();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("detectorId", f.detectorId());
                m.put("category", f.category().name());
                m.put("severity", f.severity().name());
                m.put("ruleId", f.ruleId());
                m.put("file", f.file());
                m.put("line", f.line());
                m.put("redactedMatch", f.redactedMatch()); // bereits redigiert (FR-18)
                m.put("fingerprint", agg.fingerprint());
                m.put("verified", f.verified());
                findings.add(m);
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repoId", repoId);
        payload.put("mode", mode);
        payload.put("status", "COMPLETED");
        payload.put("runRef", env("SCANNER_RUN_REF"));
        payload.put("pipelineUrl", env("SCANNER_PIPELINE_URL"));
        payload.put("commit", env("SCANNER_COMMIT"));
        payload.put("branch", env("SCANNER_BRANCH"));
        payload.put("actor", env("SCANNER_ACTOR"));
        payload.put("findings", findings);
        return payload;
    }

    /** OIDC Client-Credentials-Flow: tauscht clientId/secret gegen ein Access-Token. */
    private String fetchToken() throws Exception {
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(env("SCANNER_CI_CLIENT_ID"), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(env("SCANNER_CI_CLIENT_SECRET"), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(env("SCANNER_OIDC_TOKEN_URL")))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("OIDC token endpoint returned HTTP " + response.statusCode());
        }
        Object accessToken = MAPPER.readValue(response.body(), Map.class).get("access_token");
        if (accessToken == null) {
            throw new IllegalStateException("OIDC response without access_token");
        }
        return accessToken.toString();
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
