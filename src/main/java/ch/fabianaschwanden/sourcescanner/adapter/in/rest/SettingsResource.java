package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto.SettingsDto;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageSettingsUseCase;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** REST-Verwaltung der systemweiten Einstellungen (WR-15). Lesen: Viewer; Ändern: Admin. */
@Path("/api/settings")
@Produces(MediaType.APPLICATION_JSON)
public class SettingsResource {

    private final ManageSettingsUseCase settings;
    private final SecurityIdentity identity;

    @Inject
    public SettingsResource(ManageSettingsUseCase settings, SecurityIdentity identity) {
        this.settings = settings;
        this.identity = identity;
    }

    @GET
    @RolesAllowed({"viewer", "operator", "admin"})
    public SettingsDto get() {
        return SettingsDto.from(settings.get(), settings.secretRefStatus());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public SettingsDto update(SettingsDto dto) {
        var saved = settings.update(dto.toDomain(), actor());
        return SettingsDto.from(saved, settings.secretRefStatus());
    }

    private String actor() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
