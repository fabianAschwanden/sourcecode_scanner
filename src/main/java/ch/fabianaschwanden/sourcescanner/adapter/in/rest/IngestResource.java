package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.IngestRequestDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.ScanDto;
import ch.fabianaschwanden.sourcescanner.domain.port.in.IngestScanResultUseCase;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Ingest-Endpoint für CI/CD-Läufe (IR-22): nimmt ein abgeschlossenes Build-Ergebnis (redigiert) und
 * legt es in der zentralen DB ab. RBAC: nur die Service-Rolle {@code ci} (IR-23). Idempotent über
 * {@code runRef} (IR-25). Bewusst getrennt vom UI-getriebenen {@code /api/scans}.
 */
@Path("/api/ingest")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IngestResource {

    private final IngestScanResultUseCase ingest;
    private final SecurityIdentity identity;

    @Inject
    public IngestResource(IngestScanResultUseCase ingest, SecurityIdentity identity) {
        this.ingest = ingest;
        this.identity = identity;
    }

    @POST
    @RolesAllowed("ci")
    public ScanDto ingest(IngestRequestDto request) {
        return ScanDto.from(ingest.ingest(request.toDomain(), actor()));
    }

    private String actor() {
        return identity.isAnonymous() ? "ci" : identity.getPrincipal().getName();
    }
}
