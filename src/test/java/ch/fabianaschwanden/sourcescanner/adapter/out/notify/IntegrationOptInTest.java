package ch.fabianaschwanden.sourcescanner.adapter.out.notify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.FindingNotification;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import org.junit.jupiter.api.Test;

/** Integrationen sind opt-in und standardmässig aus (kein ungewollter Netz-Call). */
class IntegrationOptInTest {

    @Test
    void chat_ist_ohne_config_deaktiviert() {
        ChatWebhookAdapter chat = new ChatWebhookAdapter(false, "", "HIGH");
        assertFalse(chat.enabled());
        // notify auf deaktiviertem Adapter ist ein No-op (kein Wurf)
        chat.notify(new FindingNotification("repo", Severity.CRITICAL, 1, "x"));
    }

    @Test
    void chat_enabled_braucht_webhook_url() {
        assertFalse(new ChatWebhookAdapter(true, "", "HIGH").enabled());
        assertTrue(new ChatWebhookAdapter(true, "https://hooks.example/x", "HIGH").enabled());
    }

    @Test
    void jira_ist_ohne_config_deaktiviert_und_legt_kein_ticket_an() {
        JiraTicketAdapter jira = new JiraTicketAdapter(false, "", "SEC", "");
        assertFalse(jira.enabled());
        assertTrue(jira.createTicket(new FindingNotification("repo", Severity.HIGH, 1, "x")).isEmpty());
    }
}
