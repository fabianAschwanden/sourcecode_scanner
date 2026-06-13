package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.FindingDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.TriageRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.model.TriageStatus;
import ch.fabianaschwanden.sourcescanner.domain.port.in.TriageFindingUseCase;
import ch.fabianaschwanden.sourcescanner.domain.port.out.FindingPort;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
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
    private final SecurityIdentity identity;

    @Inject
    public FindingResource(TriageFindingUseCase triage, SecurityIdentity identity) {
        this.triage = triage;
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
