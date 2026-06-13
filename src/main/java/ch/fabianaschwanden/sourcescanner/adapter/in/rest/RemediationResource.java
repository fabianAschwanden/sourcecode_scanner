package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.ScrubDryRunDto;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.ScrubExecuteRequest;
import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.ScrubResultDto;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ScrubHistoryUseCase;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Steuernde Scrub-Endpunkte je Repo (RMR-20). RBAC (RMR-40/41): Dry-Run ab Operator, der reale
 * Execute nur Admin/Break-Glass. Fachliche Sperren (Gates, deaktiviert) werden als 409 abgebildet
 * (siehe {@code ConflictMapper}). Antworten sind redigiert (RMR-12). Der Auto-Fix je Fund liegt aus
 * Routing-Gründen (gleiche Wurzel {@code /api/findings}) im {@code FindingResource}.
 */
@Path("/api/repos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RemediationResource {

    private final ScrubHistoryUseCase scrub;
    private final SecurityIdentity identity;

    @Inject
    public RemediationResource(ScrubHistoryUseCase scrub, SecurityIdentity identity) {
        this.scrub = scrub;
        this.identity = identity;
    }

    /** Pflicht-Vorschau der History-Bereinigung (Operator+, RMR-22). Ändert nichts. */
    @POST
    @Path("/{id}/scrub/dry-run")
    @RolesAllowed({"operator", "admin"})
    public ScrubDryRunDto scrubDryRun(@PathParam("id") UUID repoId) {
        return ScrubDryRunDto.from(scrub.dryRun(repoId, actor()));
    }

    /** Realer Scrub-Lauf (nur Admin/Break-Glass, RMR-25/41); verlangt vorherigen Dry-Run + Freigaben. */
    @POST
    @Path("/{id}/scrub/execute")
    @RolesAllowed("admin")
    public ScrubResultDto scrubExecute(@PathParam("id") UUID repoId, ScrubExecuteRequest request) {
        boolean force = request != null && request.forcePushApproved();
        boolean rotation = request != null && request.rotationConfirmed();
        return ScrubResultDto.from(scrub.execute(repoId, force, rotation, actor()));
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
