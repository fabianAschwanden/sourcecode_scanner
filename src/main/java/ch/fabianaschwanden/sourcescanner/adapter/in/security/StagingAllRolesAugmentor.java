package ch.fabianaschwanden.sourcescanner.adapter.in.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.Principal;
import java.util.Set;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Nur wirksam, wenn das <b>Runtime</b>-Profil {@code staging} aktiv ist (OIDC aus, permissive Policy):
 * wertet jede anonyme Identität zu einer authentifizierten {@code staging}-Identität mit allen
 * App-Rollen auf, damit {@code @RolesAllowed}-Endpoints ohne Login bedienbar sind. In allen anderen
 * Profilen (insbesondere {@code prod,server}) gibt der Augmentor die Identität unverändert zurück.
 *
 * <p><b>Keine Zugriffskontrolle.</b> Ausschliesslich zum Hochfahren/DB-Test auf Fly+Neon gedacht,
 * niemals produktiv. Produktiver Betrieb läuft über {@code %server} mit echtem OIDC (Rollen aus dem
 * Token).
 *
 * <p>Die Profil-Prüfung erfolgt absichtlich zur Laufzeit (nicht via {@code @IfBuildProfile}): das
 * Image wird mit dem Build-Profil {@code prod} gebaut, {@code staging} wird erst beim Start über
 * {@code QUARKUS_PROFILE} gesetzt. Ein build-time entfernter Bean stünde zur Laufzeit nicht bereit.
 */
@ApplicationScoped
public class StagingAllRolesAugmentor implements SecurityIdentityAugmentor {

    private static final Principal STAGING_PRINCIPAL = () -> "staging";

    private final boolean stagingActive;

    public StagingAllRolesAugmentor() {
        Set<String> profiles = Set.copyOf(
                ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getProfiles());
        this.stagingActive = profiles.contains("staging");
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (!stagingActive) {
            return Uni.createFrom().item(identity);
        }
        // Anonyme Identität zu einer authentifizierten „staging"-Identität mit allen Rollen aufwerten,
        // sonst lehnt @RolesAllowed die anonyme Anfrage trotz permissiver HTTP-Policy mit 403 ab.
        QuarkusSecurityIdentity.Builder builder = identity.isAnonymous()
                ? QuarkusSecurityIdentity.builder().setPrincipal(STAGING_PRINCIPAL).setAnonymous(false)
                : QuarkusSecurityIdentity.builder(identity);
        SecurityIdentity augmented = builder
                .addRole("admin")
                .addRole("operator")
                .addRole("viewer")
                .addRole("ci")
                .build();
        return Uni.createFrom().item(augmented);
    }
}
