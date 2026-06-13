package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.PolicyDto;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManagePoliciesUseCase;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/** REST-Verwaltung der Governance-Policies (FR-20). Lesen: Viewer; Schreiben: Admin. */
@Path("/api/policies")
@Produces(MediaType.APPLICATION_JSON)
public class PolicyResource {

    private final ManagePoliciesUseCase policies;
    private final SecurityIdentity identity;

    @Inject
    public PolicyResource(ManagePoliciesUseCase policies, SecurityIdentity identity) {
        this.policies = policies;
        this.identity = identity;
    }

    @GET
    @RolesAllowed({"viewer", "operator", "admin"})
    public List<PolicyDto> all() {
        return policies.all().stream().map(PolicyDto::from).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Response create(PolicyDto dto) {
        var created = policies.create(dto.toDomain(), actor());
        return Response.status(Response.Status.CREATED).entity(PolicyDto.from(created)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public PolicyDto update(@PathParam("id") UUID id, PolicyDto dto) {
        return PolicyDto.from(policies.update(id, dto.toDomain(), actor()));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    public Response delete(@PathParam("id") UUID id) {
        policies.delete(id, actor());
        return Response.noContent().build();
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
