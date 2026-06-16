package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.BulkResultDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.ScanDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.StartScanRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanRecord;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageScansUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.ScanRecordPort;
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
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestStreamElementType;

/** REST-Steuerung der Scans (WR-03/04). Lesen: Viewer; Starten/Abbrechen: Operator. */
@Path("/api/scans")
@Produces(MediaType.APPLICATION_JSON)
public class ScanResource {

    private final ManageScansUseCase scans;
    private final ScanRecordPort scanRecords;
    private final SecurityIdentity identity;

    @Inject
    public ScanResource(ManageScansUseCase scans, ScanRecordPort scanRecords,
                        SecurityIdentity identity) {
        this.scans = scans;
        this.scanRecords = scanRecords;
        this.identity = identity;
    }

    /** SSE-Nutzlast eines Fortschritts-Ticks (Frontend-Vertrag: scanId/status/progress/findingCount). */
    public record ScanEvent(UUID scanId, String status, int progress, int findingCount) {
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

    /**
     * Live-Fortschritt eines Laufs als Server-Sent Events (WR-04). Pollt den Scan-Datensatz in der
     * DB — so funktioniert der Stream pod-übergreifend (der Lauf kann auf einem anderen Pod laufen
     * als der, der diese SSE-Verbindung hält). Der Stream endet nach dem ersten terminalen Zustand.
     */
    @GET
    @Path("/{id}/events")
    @RolesAllowed({"viewer", "operator", "admin"})
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<ScanEvent> events(@PathParam("id") UUID id) {
        // Ein Element pro Tick (oder keins, wenn der Lauf verschwand). Der Stream läuft bis
        // einschliesslich des ersten terminalen Events: ein Flag lässt genau dieses noch durch und
        // beendet danach (select().first(p) liefert Elemente, solange p true ist, terminales inkl.).
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        return Multi.createFrom().ticks().every(Duration.ofMillis(1500))
                .onItem().transformToIterable(tick -> {
                    Optional<ScanRecord> r = scanRecords.byId(id);
                    return r.map(this::toEvent).map(List::of).orElseGet(List::of);
                })
                .select().first(event -> {
                    if (done.get()) {
                        return false; // nach dem terminalen Event nichts mehr
                    }
                    if (isTerminal(event.status())) {
                        done.set(true); // dieses terminale Event noch ausliefern, dann Schluss
                    }
                    return true;
                });
    }

    private static boolean isTerminal(String status) {
        return !ScanStatus.RUNNING.name().equals(status) && !ScanStatus.QUEUED.name().equals(status);
    }

    private ScanEvent toEvent(ScanRecord r) {
        return new ScanEvent(r.id(), r.status().name(), r.progress(), r.findingCount());
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
