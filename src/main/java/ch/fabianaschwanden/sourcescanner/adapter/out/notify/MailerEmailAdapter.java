package ch.fabianaschwanden.sourcescanner.adapter.out.notify;

import ch.fabianaschwanden.sourcescanner.domain.model.EmailReport;
import ch.fabianaschwanden.sourcescanner.domain.port.out.EmailNotificationPort;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Versendet E-Mail-Reports über den Quarkus-Mailer (IR-52). Opt-in über
 * {@code scanner.notifications.email.enabled} (Default false), damit der CLI-Lauf mailfrei bleibt und
 * der Start ohne SMTP-Konfig nicht bricht. Versendet nur redigierte Inhalte (FR-18); ein Fehler beim
 * Versand wird geloggt, aber nicht weitergeworfen (Isolation).
 */
@ApplicationScoped
public class MailerEmailAdapter implements EmailNotificationPort {

    private static final Logger LOG = Logger.getLogger(MailerEmailAdapter.class);

    private final boolean enabled;
    private final Optional<String> from;
    private final Instance<Mailer> mailer;

    @Inject
    public MailerEmailAdapter(
            @ConfigProperty(name = "scanner.notifications.email.enabled", defaultValue = "false") boolean enabled,
            @ConfigProperty(name = "scanner.notifications.email.from") Optional<String> from,
            Instance<Mailer> mailer) {
        this.enabled = enabled;
        this.from = from;
        this.mailer = mailer;
    }

    @Override
    public boolean enabled() {
        return enabled && mailer.isResolvable();
    }

    @Override
    public void send(EmailReport report) {
        if (!enabled() || !report.hasRecipients()) {
            return;
        }
        try {
            Mail mail = Mail.withText(report.recipients().getFirst(), report.subject(), report.body());
            report.recipients().stream().skip(1).forEach(mail::addTo);
            from.ifPresent(mail::setFrom);
            mailer.get().send(mail);
            LOG.infof("report e-mail sent to %d recipient(s)", report.recipients().size());
        } catch (RuntimeException e) {
            LOG.warnf("failed to send report e-mail: %s", e.getMessage());
        }
    }
}
