package ch.fabianaschwanden.sourcescanner.adapter.out.notify;

import ch.fabianaschwanden.sourcescanner.domain.model.FindingNotification;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ChatNotificationPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Chat-Benachrichtigung über einen Incoming-Webhook (Teams/Slack, IR-51). Opt-in und standardmässig
 * deaktiviert; Webhook-URL kommt als Secret-Referenz/Env. Sendet nur redigierte Zusammenfassungen (FR-18).
 */
@ApplicationScoped
public class ChatWebhookAdapter implements ChatNotificationPort {

    private static final Logger LOG = Logger.getLogger(ChatWebhookAdapter.class);

    private final boolean enabled;
    private final String webhookUrl;
    private final Severity notifyOn;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public ChatWebhookAdapter(
            @ConfigProperty(name = "scanner.integrations.chat.enabled", defaultValue = "false") boolean enabled,
            @ConfigProperty(name = "scanner.integrations.chat.webhook-url", defaultValue = "") String webhookUrl,
            @ConfigProperty(name = "scanner.integrations.chat.notify-on", defaultValue = "HIGH") String notifyOn) {
        this.enabled = enabled;
        this.webhookUrl = webhookUrl;
        this.notifyOn = parseSeverity(notifyOn);
    }

    @Override
    public boolean enabled() {
        return enabled && !webhookUrl.isBlank();
    }

    @Override
    public void notify(FindingNotification notification) {
        if (!enabled() || !notification.highestSeverity().atLeast(notifyOn)) {
            return;
        }
        String payload = "{\"text\":\"" + escape(notification.summary()) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                LOG.warnf("chat webhook returned %d for repo %s", response.statusCode(), notification.repoId());
            }
        } catch (Exception e) {
            LOG.warnf("chat notification failed for repo %s: %s", notification.repoId(), e.getMessage());
        }
    }

    private Severity parseSeverity(String raw) {
        return Optional.ofNullable(raw).filter(s -> !s.isBlank())
                .map(s -> Severity.valueOf(s.trim().toUpperCase(Locale.ROOT)))
                .orElse(Severity.HIGH);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
