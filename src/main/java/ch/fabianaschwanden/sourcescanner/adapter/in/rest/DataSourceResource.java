package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.DataSourceDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.DataSourceSchemaDto;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageDataSourcesUseCase;
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
 * Verwaltung externer Datenquellen + Attribut-Mapping (WR-50..54). RBAC (NFR-24): Lesen ab Viewer,
 * Pflege/Probe ab Operator. Antworten sind redigiert (WR-33): tokenRef nur als Referenz, Probe-Schema
 * nur mit maskierten Beispielen — nie Klartextwerte.
 */
@Path("/api/datasources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DataSourceResource {

    private final ManageDataSourcesUseCase dataSources;
    private final SecurityIdentity identity;

    @Inject
    public DataSourceResource(ManageDataSourcesUseCase dataSources, SecurityIdentity identity) {
        this.dataSources = dataSources;
        this.identity = identity;
    }

    @GET
    @RolesAllowed({"viewer", "operator", "admin"})
    public List<DataSourceDto> list() {
        return dataSources.list().stream().map(DataSourceDto::from).toList();
    }

    @POST
    @RolesAllowed({"operator", "admin"})
    public DataSourceDto save(DataSourceDto request) {
        return DataSourceDto.from(dataSources.save(request.toDomain(), actor()));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"operator", "admin"})
    public void delete(@PathParam("id") UUID id) {
        dataSources.delete(id, actor());
    }

    /** Probe-Abruf gegen die übergebene Definition; liefert redigiertes Attribut-Schema (IR-63). */
    @POST
    @Path("/probe")
    @RolesAllowed({"operator", "admin"})
    public DataSourceSchemaDto probe(DataSourceDto request) {
        return DataSourceSchemaDto.from(dataSources.probe(request.toDomain(), actor()));
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
