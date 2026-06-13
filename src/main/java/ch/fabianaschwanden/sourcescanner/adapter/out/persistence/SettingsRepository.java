package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.Settings;
import ch.fabianaschwanden.sourcescanner.domain.port.out.SettingsPort;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/** Panache-Repository der Settings-Singleton-Zeile; mappt Entity ↔ Domänen-{@link Settings} (TR-23). */
@ApplicationScoped
public class SettingsRepository implements PanacheRepositoryBase<SettingsEntity, Long>, SettingsPort {

    @Override
    public Settings get() {
        SettingsEntity entity = findById(SettingsEntity.SINGLETON_ID);
        return entity == null ? Settings.defaults() : toDomain(entity);
    }

    @Override
    @Transactional
    public Settings save(Settings settings) {
        SettingsEntity entity = findById(SettingsEntity.SINGLETON_ID);
        if (entity == null) {
            entity = new SettingsEntity();
            entity.id = SettingsEntity.SINGLETON_ID;
        }
        entity.generalNotificationEmail = settings.generalNotificationEmail();
        entity.defaultFailOn = settings.defaultFailOn();
        entity.defaultScanMode = settings.defaultScanMode();
        entity.retentionDays = settings.retentionDays();
        entity.secretRefs = String.join(",", settings.secretRefs());
        persist(entity);
        return toDomain(entity);
    }

    static Settings toDomain(SettingsEntity e) {
        List<String> refs = e.secretRefs == null || e.secretRefs.isBlank()
                ? List.of() : List.of(e.secretRefs.split(","));
        return new Settings(e.generalNotificationEmail, e.defaultFailOn, e.defaultScanMode,
                e.retentionDays, refs);
    }
}
