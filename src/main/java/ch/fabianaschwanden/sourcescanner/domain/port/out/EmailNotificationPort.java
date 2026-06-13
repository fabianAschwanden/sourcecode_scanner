package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.EmailReport;

/**
 * Versendet E-Mail-Benachrichtigungen (IR-52). Opt-in: bei deaktiviertem oder fehlend konfiguriertem
 * Versand ist {@link #enabled()} false und {@link #send(EmailReport)} ein No-op. Der SMTP-Adapter
 * dahinter ist nur im Server-Profil aktiv (CLI bleibt mailfrei).
 */
public interface EmailNotificationPort {

    boolean enabled();

    /** Versendet den Report an seine Empfänger; ohne Empfänger oder bei {@code !enabled()} ein No-op. */
    void send(EmailReport report);
}
