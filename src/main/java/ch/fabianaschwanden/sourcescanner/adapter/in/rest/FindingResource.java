package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.BulkResultDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.FindingDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.PrRefDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.TriageRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.in.RemediateUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.in.TriageFindingUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import io.quarkus.security.identity.SecurityIdentity;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** REST-Zugriff auf Funde (WR-10/11/12). Lesen: Viewer; Triage: Operator. Nur redigierte Treffer. */
@Path("/api/findings")
@Produces(MediaType.APPLICATION_JSON)
public class FindingResource {

    private final TriageFindingUseCase triage;
    private final RemediateUseCase remediate;
    private final SecurityIdentity identity;

    @Inject
    public FindingResource(TriageFindingUseCase triage, RemediateUseCase remediate, SecurityIdentity identity) {
        this.triage = triage;
        this.remediate = remediate;
        this.identity = identity;
    }

    @GET
    @RolesAllowed({"viewer", "operator", "admin"})
    public List<FindingDto> list(@QueryParam("repo") String repo,
                                 @QueryParam("minSeverity") String minSeverity,
                                 @QueryParam("detector") String detector,
                                 @QueryParam("status") String status,
                                 @QueryParam("offset") int offset,
                                 @QueryParam("limit") int limit) {
        FindingPort.FindingQuery query = new FindingPort.FindingQuery(
                blankToNull(repo), parseSeverity(minSeverity), blankToNull(detector),
                parseStatus(status), offset, limit);
        return triage.findings(query).stream().map(FindingDto::from).toList();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"viewer", "operator", "admin"})
    public FindingDto byId(@PathParam("id") UUID id) {
        return FindingDto.from(triage.byId(id));
    }

    @POST
    @Path("/{id}/triage")
    @RolesAllowed({"operator", "admin"})
    public FindingDto triage(@PathParam("id") UUID id, TriageRequest request) {
        TriageStatus status = TriageStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        return FindingDto.from(triage.triage(id, status, request.reason(), actor()));
    }

    /** Auto-Fix per PR/MR für einen Fund (Operator+, RMR-10). Opt-in pro Repo (RMR-02). */
    @POST
    @Path("/{id}/remediate")
    @RolesAllowed({"operator", "admin"})
    public PrRefDto remediate(@PathParam("id") UUID id) {
        return PrRefDto.from(remediate.remediate(id, actor()));
    }

    /** Sammel-Triage mehrerer Funde (WR-67/23); je ID einzeln ausgeführt + auditiert. */
    @POST
    @Path("/bulk/triage")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"operator", "admin"})
    public BulkResultDto bulkTriage(BulkTriageRequest request) {
        TriageStatus status = TriageStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        String who = actor();
        BulkResultDto.Builder result = new BulkResultDto.Builder();
        for (UUID id : request.ids()) {
            try {
                triage.triage(id, status, request.reason(), who);
                result.success();
            } catch (RuntimeException e) {
                result.failure(id.toString(), e.getMessage());
            }
        }
        return result.build();
    }

    /** Sammel-Auto-Fix mehrerer Funde (WR-67/23); je ID einzeln ausgeführt + auditiert. */
    @POST
    @Path("/bulk/remediate")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"operator", "admin"})
    public BulkResultDto bulkRemediate(BulkIdsRequest request) {
        String who = actor();
        BulkResultDto.Builder result = new BulkResultDto.Builder();
        for (UUID id : request.ids()) {
            try {
                remediate.remediate(id, who);
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

    /** Batch-Triage: IDs + Ziel-Status + (für Suppress/FP) Begründung. */
    public record BulkTriageRequest(List<UUID> ids, String status, String reason) {
        public BulkTriageRequest {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private Severity parseSeverity(String s) {
        return s == null || s.isBlank() ? null : Severity.valueOf(s.trim().toUpperCase(Locale.ROOT));
    }

    private TriageStatus parseStatus(String s) {
        return s == null || s.isBlank() ? null : TriageStatus.valueOf(s.trim().toUpperCase(Locale.ROOT));
    }
}
