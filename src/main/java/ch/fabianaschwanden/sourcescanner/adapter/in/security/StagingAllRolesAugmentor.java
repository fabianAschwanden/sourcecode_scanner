package ch.fabianaschwanden.sourcescanner.adapter.in.security;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

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

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        QuarkusSecurityIdentity augmented = QuarkusSecurityIdentity.builder(identity)
                .addRole("admin")
                .addRole("operator")
                .addRole("viewer")
                .addRole("ci")
                .build();
        return Uni.createFrom().item(augmented);
    }
}
