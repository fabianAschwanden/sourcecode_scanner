package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.ManagedSecret;
import ch.fabianaschwanden.sourcescanner.domain.model.SecretStorageMode;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ManagedSecretPort;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Panache-Repository für UI-verwaltete Secrets (WR-17/19). Persistiert nie Klartext (NFR-29). */
@ApplicationScoped
public class ManagedSecretRepository
        implements PanacheRepositoryBase<ManagedSecretEntity, UUID>, ManagedSecretPort {

    @Override
    @Transactional
    public ManagedSecret save(UUID id, String name, SecretStorageMode mode, String reference,
                              String encryptedValue) {
        UUID secretId = id == null ? UUID.randomUUID() : id;
        ManagedSecretEntity entity = findById(secretId);
        if (entity == null) {
            entity = new ManagedSecretEntity();
            entity.id = secretId;
        }
        entity.name = name;
        entity.mode = mode;
        entity.reference = reference;
        entity.encryptedValue = encryptedValue;
        persist(entity);
        return toDomain(entity);
    }

    @Override
    public List<ManagedSecret> all() {
        return listAll().stream().map(ManagedSecretRepository::toDomain).toList();
    }

    @Override
    public Optional<ManagedSecret> byId(UUID id) {
        return Optional.ofNullable(findById(id)).map(ManagedSecretRepository::toDomain);
    }

    @Override
    public Optional<ManagedSecret> byName(String name) {
        return find("name", name).firstResultOptional().map(ManagedSecretRepository::toDomain);
    }

    @Override
    public Optional<String> encryptedValue(UUID id) {
        return Optional.ofNullable(findById(id)).map(e -> e.encryptedValue);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        deleteById(id);
    }

    static ManagedSecret toDomain(ManagedSecretEntity e) {
        boolean hasStored = e.encryptedValue != null && !e.encryptedValue.isBlank();
        // Vorläufige Auflösbarkeit: Referenz vorhanden oder Wert gespeichert. Die Anwendungs-Schicht
        // verfeinert dies für REFERENCE über den SecretReferencePort.
        boolean resolvable = hasStored || (e.reference != null && !e.reference.isBlank());
        return new ManagedSecret(e.id, e.name, e.mode, e.reference == null ? "" : e.reference,
                hasStored, resolvable);
    }
}
