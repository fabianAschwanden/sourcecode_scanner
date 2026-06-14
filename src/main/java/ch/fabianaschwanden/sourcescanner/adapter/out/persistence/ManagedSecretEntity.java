package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.SecretStorageMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA-Entity eines UI-verwalteten Secrets (WR-17/19). {@code reference} hält die Secret-Store-Referenz
 * (Modus REFERENCE/VAULT_WRITE); {@code encryptedValue} hält ausschliesslich ein <b>Chiffrat</b>
 * (Modus DB_ENCRYPTED, NFR-29) — nie Klartext.
 */
@Entity
@Table(name = "managed_secret")
public class ManagedSecretEntity {

    @Id
    public UUID id;

    @Column(nullable = false, unique = true)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SecretStorageMode mode;

    /** Secret-Store-Referenz (env:/vault:) — leer bei DB_ENCRYPTED. */
    @Column(name = "reference")
    public String reference;

    /** Verschlüsselter Wert (Base64, AES-GCM) — nur bei DB_ENCRYPTED, nie Klartext. */
    @Column(name = "encrypted_value", length = 8192)
    public String encryptedValue;
}
