package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.ManagedSecretDto;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageSecretsUseCase;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

/**
 * Verwaltung der UI-Secrets (WR-17/19). Nur Admin (WR-19b). Antworten tragen nie einen Klartext-Wert
 * (WR-19a); der Modus bestimmt, ob nur eine Referenz, ein Vault-Write oder ein DB-verschlüsselter Wert
 * angelegt wird. Jede Änderung wird auditiert.
 */
@Path("/api/secrets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SecretResource {

    private final ManageSecretsUseCase secrets;
    private final SecurityIdentity identity;

    @Inject
    public SecretResource(ManageSecretsUseCase secrets, SecurityIdentity identity) {
        this.secrets = secrets;
        this.identity = identity;
    }

    @GET
    @RolesAllowed("admin")
    public List<ManagedSecretDto> list() {
        return secrets.list().stream().map(ManagedSecretDto::from).toList();
    }

    @POST
    @RolesAllowed("admin")
    public ManagedSecretDto save(ManagedSecretDto request) {
        return ManagedSecretDto.from(secrets.save(request.toCommand(), actor()));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    public void delete(@PathParam("id") UUID id) {
        secrets.delete(id, actor());
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
