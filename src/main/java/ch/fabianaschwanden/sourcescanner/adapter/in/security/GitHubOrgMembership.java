package ch.fabianaschwanden.sourcescanner.adapter.in.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Prüft die Mitgliedschaft des eingeloggten GitHub-Nutzers in einer Organisation über
 * {@code GET /user/memberships/orgs/{org}} mit dessen Access-Token (Scope {@code read:org}). Erkennt
 * auch <b>private</b> Mitgliedschaften. 200 + {@code "state":"active"} ⇒ Mitglied; 403/404/Fehler ⇒
 * kein Mitglied (fail-closed Richtung niedrigere Rolle). Genutzt vom {@link GitHubRolesAugmentor}, um
 * Org-Mitgliedern die {@code operator}-Rolle zu geben. Das Token verlässt diese Klasse nie/wird nie
 * geloggt.
 */
@ApplicationScoped
public class GitHubOrgMembership {

    private static final Logger LOG = Logger.getLogger(GitHubOrgMembership.class);

    private final String apiBaseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public GitHubOrgMembership(
            @ConfigProperty(name = "scanner.auth.github.api-url", defaultValue = "https://api.github.com")
            String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
    }

    /**
     * {@code true}, wenn der Inhaber von {@code accessToken} aktives (auch privates) Mitglied von
     * {@code org} ist. Ohne Token nicht prüfbar ⇒ {@code false}.
     */
    public boolean isActiveMember(String org, String accessToken) {
        if (org == null || org.isBlank() || accessToken == null || accessToken.isBlank()) {
            return false;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/user/memberships/orgs/" + org))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("User-Agent", "sourcecode-scanner")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            // 200 mit aktivem Status = Mitglied; pending/Block/404/403 = nein.
            return res.statusCode() == 200 && res.body() != null && res.body().contains("\"state\":\"active\"");
        } catch (Exception e) {
            LOG.warnf("org membership check failed for %s: %s", org, e.getMessage());
            return false;
        }
    }
}
