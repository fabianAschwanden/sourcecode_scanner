package ch.fabianaschwanden.sourcescanner.application.service;

import ch.fabianaschwanden.sourcescanner.domain.model.AuditEvent;
import ch.fabianaschwanden.sourcescanner.domain.model.Settings;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageSettingsUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.AuditPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretReferencePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.SettingsPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Verwaltung der systemweiten Einstellungen (WR-15..18). Audit jeder Änderung (WR-34). */
@ApplicationScoped
public class ManageSettingsService implements ManageSettingsUseCase {

    private final SettingsPort settings;
    private final SecretReferencePort secretReferences;
    private final AuditPort audit;

    @Inject
    public ManageSettingsService(SettingsPort settings, SecretReferencePort secretReferences, AuditPort audit) {
        this.settings = settings;
        this.secretReferences = secretReferences;
        this.audit = audit;
    }

    @Override
    public Settings get() {
        return settings.get();
    }

    @Override
    public Settings update(Settings updated, String actor) {
        Settings saved = settings.save(updated);
        audit.record(AuditEvent.of(actor, "settings.update", "settings",
                "defaultFailOn=" + saved.defaultFailOn() + ", retentionDays=" + saved.retentionDays()));
        return saved;
    }

    @Override
    public Map<String, Boolean> secretRefStatus() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (String ref : settings.get().secretRefs()) {
            status.put(ref, secretReferences.resolvable(ref));
        }
        return status;
    }
}
