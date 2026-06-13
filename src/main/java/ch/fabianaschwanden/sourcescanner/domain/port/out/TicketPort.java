package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.FindingNotification;
import java.util.Optional;

/** Ticket-Anlage (Jira) für Funde (IR-50). Opt-in; liefert die Ticket-Referenz, falls erstellt. */
public interface TicketPort {

    boolean enabled();

    /** Legt ein Ticket an und liefert dessen Schlüssel/URL (redigiert), oder {@code empty} wenn aus. */
    Optional<String> createTicket(FindingNotification notification);
}
