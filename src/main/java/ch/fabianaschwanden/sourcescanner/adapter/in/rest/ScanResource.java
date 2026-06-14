package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.BulkResultDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.ScanDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.StartScanRequest;
import ch.fabianaschwanden.sourcescanner.application.service.ScanProgressBroadcaster;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageScansUseCase;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestStreamElementType;

/** REST-Steuerung der Scans (WR-03/04). Lesen: Viewer; Starten/Abbrechen: Operator. */
@Path("/api/scans")
@Produces(MediaType.APPLICATION_JSON)
public class ScanResource {

    private final ManageScansUseCase scans;
    private final ScanProgressBroadcaster broadcaster;
    private final SecurityIdentity identity;

    @Inject
    public ScanResource(ManageScansUseCase scans, ScanProgressBroadcaster broadcaster,
                        SecurityIdentity identity) {
        this.scans = scans;
        this.broadcaster = broadcaster;
        this.identity = identity;
    }

    @GET
    @RolesAllowed({"viewer", "operator", "admin"})
    public List<ScanDto> recent(@QueryParam("limit") int limit) {
        return scans.recentScans(limit <= 0 ? 50 : limit).stream().map(ScanDto::from).toList();
    }

    @POST
    @RolesAllowed({"operator", "admin"})
    public Response start(StartScanRequest request) {
        ScanRecord record = scans.startScan(request.sourceId(), request.mode(), actor());
        return Response.accepted(ScanDto.from(record)).build();
    }

    @POST
    @Path("/{id}/cancel")
    @RolesAllowed({"operator", "admin"})
    public Response cancel(@PathParam("id") UUID id) {
        scans.cancelScan(id, actor());
        return Response.noContent().build();
    }

    /** Sammel-Abbruch mehrerer laufender Scans (WR-67/23); je ID einzeln + auditiert. */
    @POST
    @Path("/bulk/cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"operator", "admin"})
    public BulkResultDto bulkCancel(BulkIdsRequest request) {
        String who = actor();
        BulkResultDto.Builder result = new BulkResultDto.Builder();
        for (UUID id : request.ids()) {
            try {
                scans.cancelScan(id, who);
                result.success();
            } catch (RuntimeException e) {
                result.failure(id.toString(), e.getMessage());
            }
        }
        return result.build();
    }

    /** Batch-Anfrage nur mit IDs. */
    public record BulkIdsRequest(List<UUID> ids) {
        public BulkIdsRequest {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    /** Live-Fortschritt eines Laufs als Server-Sent Events (WR-04). */
    @GET
    @Path("/{id}/events")
    @RolesAllowed({"viewer", "operator", "admin"})
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<ScanProgressBroadcaster.ScanEvent> events(@PathParam("id") UUID id) {
        return broadcaster.stream(id);
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
