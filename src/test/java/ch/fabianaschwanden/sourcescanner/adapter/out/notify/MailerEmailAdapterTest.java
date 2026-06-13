package ch.fabianaschwanden.sourcescanner.adapter.out.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.EmailReport;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Report-Versand gegen die Quarkus-MockMailbox (kein echter SMTP); im %test-Profil ist Mail aktiv. */
@QuarkusTest
class MailerEmailAdapterTest {

    @Inject
    MailerEmailAdapter adapter;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clear() {
        mailbox.clear();
    }

    @Test
    void versendet_report_an_empfaenger_ohne_klartext() {
        assertTrue(adapter.enabled(), "im %test-Profil ist der Mailer aktiv (mock)");
        adapter.send(new EmailReport(List.of("team@firma.ch"),
                "[scanner] repo-x: 1 Fund(e)", "HIGH aws src/A.java:2 AKIA****MPLE"));

        var sent = mailbox.getMailMessagesSentTo("team@firma.ch");
        assertEquals(1, sent.size());
        assertTrue(sent.getFirst().getSubject().contains("repo-x"));
        assertFalse(sent.getFirst().getText().contains("AKIAIOSFODNN7EXAMPLE"), "kein Klartext");
    }

    @Test
    void ohne_empfaenger_kein_versand() {
        adapter.send(new EmailReport(List.of(), "subj", "body"));
        assertEquals(0, mailbox.getTotalMessagesSent());
    }
}
