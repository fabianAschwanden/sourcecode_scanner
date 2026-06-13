package ch.fabianaschwanden.sourcescanner.adapter.out.notify;

import ch.fabianaschwanden.sourcescanner.domain.model.FindingNotification;
import ch.fabianaschwanden.sourcescanner.domain.port.out.TicketPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Jira-Ticket-Anlage für Funde (IR-50). Opt-in und standardmässig deaktiviert; Projekt-Key,
 * Basis-URL und Token (Secret-Referenz) kommen aus der Konfiguration. Erstellt ein Ticket mit
 * redigierter Zusammenfassung (FR-18).
 */
@ApplicationScoped
public class JiraTicketAdapter implements TicketPort {

    private static final Logger LOG = Logger.getLogger(JiraTicketAdapter.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String projectKey;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public JiraTicketAdapter(
            @ConfigProperty(name = "scanner.integrations.jira.enabled", defaultValue = "false") boolean enabled,
            @ConfigProperty(name = "scanner.integrations.jira.base-url", defaultValue = "") String baseUrl,
            @ConfigProperty(name = "scanner.integrations.jira.project-key", defaultValue = "SEC") String projectKey,
            @ConfigProperty(name = "scanner.integrations.jira.token", defaultValue = "") String token) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.projectKey = projectKey;
        this.token = token;
    }

    @Override
    public boolean enabled() {
        return enabled && !baseUrl.isBlank() && !token.isBlank();
    }

    @Override
    public Optional<String> createTicket(FindingNotification notification) {
        if (!enabled()) {
            return Optional.empty();
        }
        String body = "{\"fields\":{\"project\":{\"key\":\"" + projectKey + "\"},"
                + "\"issuetype\":{\"name\":\"Bug\"},"
                + "\"summary\":\"" + escape("Secret findings in " + notification.repoId()) + "\","
                + "\"description\":\"" + escape(notification.summary()) + "\"}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/rest/api/2/issue"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                LOG.warnf("jira returned %d for repo %s", response.statusCode(), notification.repoId());
                return Optional.empty();
            }
            return Optional.of(projectKey + " ticket created");
        } catch (Exception e) {
            LOG.warnf("jira ticket creation failed for repo %s: %s", notification.repoId(), e.getMessage());
            return Optional.empty();
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
