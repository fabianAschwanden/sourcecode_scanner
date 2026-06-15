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
 * Prüft die <b>öffentliche</b> Mitgliedschaft eines GitHub-Nutzers in einer Organisation über
 * {@code GET /orgs/{org}/members/{login}} (204 = Mitglied, 404 = nicht/privat; ohne Token). Wird vom
 * {@link GitHubRolesAugmentor} genutzt, um Org-Mitgliedern die {@code operator}-Rolle zu geben.
 *
 * <p>Nur öffentliche Mitgliedschaften sind so sichtbar — private Mitgliedschaften müssten über
 * {@code read:org} + User-Token geprüft werden (bewusst nicht gewählt). Fehler/Timeout ⇒ kein
 * Mitglied (fail-closed Richtung niedrigere Rolle).
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

    /** {@code true}, wenn {@code login} öffentlich sichtbares Mitglied von {@code org} ist. */
    public boolean isPublicMember(String org, String login) {
        if (org == null || org.isBlank() || login == null || login.isBlank()) {
            return false;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/orgs/" + org + "/members/" + login))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "sourcecode-scanner")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            int status = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status == 204;
        } catch (Exception e) {
            LOG.warnf("org membership check failed for %s/%s: %s", org, login, e.getMessage());
            return false;
        }
    }
}
