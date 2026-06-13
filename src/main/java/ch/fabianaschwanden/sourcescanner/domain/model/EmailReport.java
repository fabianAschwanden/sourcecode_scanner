package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;

/**
 * Eine zu versendende E-Mail-Benachrichtigung (IR-52/53). {@code body} enthält ausschliesslich
 * redigierte Inhalte (FR-18) — niemals Klartext-Treffer. Reines Domänen-Modell; der SMTP-Versand
 * liegt im Mailer-Adapter dahinter.
 */
public record EmailReport(List<String> recipients, String subject, String body) {

    public EmailReport {
        recipients = recipients == null ? List.of() : List.copyOf(recipients);
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("email subject must not be blank");
        }
        body = body == null ? "" : body;
    }

    public boolean hasRecipients() {
        return !recipients.isEmpty();
    }
}
