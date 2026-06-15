package ch.fabianaschwanden.sourcescanner.adapter.in.security;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.Principal;

/**
 * NUR im {@code staging}-Profil aktiv (OIDC aus, permissive Policy): stattet jede Identität — auch die
 * anonyme — mit allen App-Rollen aus, damit {@code @RolesAllowed}-Endpoints ohne Login bedienbar sind.
 *
 * <p><b>Keine Zugriffskontrolle.</b> Ausschliesslich zum Hochfahren/DB-Test auf Fly+Neon gedacht,
 * niemals produktiv. Produktiver Betrieb läuft über {@code %server} mit echtem OIDC (Rollen aus dem
 * Token). Diese Bohne wird ausserhalb von {@code staging} gar nicht erst instanziiert.
 */
@ApplicationScoped
@IfBuildProfile("staging")
public class StagingAllRolesAugmentor implements SecurityIdentityAugmentor {

    private static final Principal STAGING_PRINCIPAL = () -> "staging";

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
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
