package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.RepositoryCardDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.RepositorySourceDto;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageSourcesUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RepositorySourcePort.SourceQuery;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** REST-Verwaltung der Repository-Quellen (WR-02). Lesen: Viewer; Mutation: Admin. */
@Path("/api/sources")
@Produces(MediaType.APPLICATION_JSON)
public class RepositorySourceResource {

    private final ManageSourcesUseCase sources;
    private final SecurityIdentity identity;

    @Inject
    public RepositorySourceResource(ManageSourcesUseCase sources, SecurityIdentity identity) {
        this.sources = sources;
        this.identity = identity;
    }

    @GET
    @RolesAllowed({"viewer", "operator", "admin"})
    public List<RepositorySourceDto> all() {
        return sources.all().stream().map(RepositorySourceDto::from).toList();
    }

    /** Repo-Übersicht im GitHub-Stil (WR-80..84): serverseitige Suche/Filter/Sortierung + Karten. */
    @GET
    @Path("/cards")
    @RolesAllowed({"viewer", "operator", "admin"})
    public List<RepositoryCardDto> cards(@QueryParam("q") String q,
                                         @QueryParam("type") String type,
                                         @QueryParam("language") String language,
                                         @QueryParam("sort") String sort) {
        return sources.cards(new SourceQuery(q, type, sort), language).stream()
                .map(RepositoryCardDto::from).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Response create(RepositorySourceDto dto) {
        var created = sources.create(dto.toDomain(), actor());
        return Response.status(Response.Status.CREATED).entity(RepositorySourceDto.from(created)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public RepositorySourceDto update(@PathParam("id") UUID id, RepositorySourceDto dto) {
        return RepositorySourceDto.from(sources.update(id, dto.toDomain(), actor()));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    public Response delete(@PathParam("id") UUID id) {
        sources.delete(id, actor());
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/test")
    @RolesAllowed({"operator", "admin"})
    public Map<String, Boolean> testConnection(@PathParam("id") UUID id) {
        return Map.of("reachable", sources.testConnection(id));
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
