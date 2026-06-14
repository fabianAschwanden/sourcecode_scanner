package ch.fabianaschwanden.sourcescanner.adapter.out.secret;

import ch.fabianaschwanden.sourcescanner.domain.port.out.SecretStoreWritePort;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Default-Adapter für das Schreiben in einen externen Secret-Store (Vault, IR-30). In dieser
 * Ausbaustufe ist keine echte Vault-Anbindung verdrahtet: {@link #available()} ist nur {@code true},
 * wenn {@code scanner.secrets.vault.enabled} gesetzt ist; andernfalls verweigert der Modus VAULT_WRITE
 * mit klarer Meldung (statt Klartext unkontrolliert abzulegen). Eine echte Vault-Implementierung
 * tritt später hinter diesen Port.
 */
@ApplicationScoped
public class StubSecretStoreWriteAdapter implements SecretStoreWritePort {

    private final boolean enabled;

    public StubSecretStoreWriteAdapter(
            @ConfigProperty(name = "scanner.secrets.vault.enabled", defaultValue = "false") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public String write(String path, String plaintext) {
        throw new UnsupportedOperationException(
                "Vault-Write ist nicht verdrahtet (scanner.secrets.vault.enabled); echte Vault-Anbindung folgt.");
    }
}
