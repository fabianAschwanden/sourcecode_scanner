package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.FindingNotification;

/** Chat-Benachrichtigung (Teams/Slack) ab konfigurierbarer Severity (IR-51). Opt-in. */
public interface ChatNotificationPort {

    boolean enabled();

    /** Sendet eine redigierte Benachrichtigung; Implementierungen filtern selbst nach Schwellenwert. */
    void notify(FindingNotification notification);
}
