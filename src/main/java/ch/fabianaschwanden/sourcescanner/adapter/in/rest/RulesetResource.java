package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.RulesetDto;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageRulesetsUseCase;
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
 * Verwaltung der Rulesets (FR-27, WR-90..96). Lesen ab Viewer; Anlegen/Ändern/Löschen nur Admin
 * (WR-95). Änderungen wirken auf künftige Scans (DR-54).
 */
@Path("/api/rulesets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RulesetResource {

    private final ManageRulesetsUseCase rulesets;
    private final SecurityIdentity identity;

    @Inject
    public RulesetResource(ManageRulesetsUseCase rulesets, SecurityIdentity identity) {
        this.rulesets = rulesets;
        this.identity = identity;
    }

    @GET
    @RolesAllowed({"viewer", "operator", "admin"})
    public List<RulesetDto> list() {
        return rulesets.list().stream().map(RulesetDto::from).toList();
    }

    @POST
    @RolesAllowed("admin")
    public RulesetDto save(RulesetDto request) {
        return RulesetDto.from(rulesets.save(request.toDomain(), actor()));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    public void delete(@PathParam("id") UUID id) {
        rulesets.delete(id, actor());
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
