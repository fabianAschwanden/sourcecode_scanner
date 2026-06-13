package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import io.quarkus.arc.All;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/** Listet die geladenen Detektoren mit ID und Kategorie (WR-07). Read-only, alle Rollen. */
@Path("/api/detectors")
@Produces(MediaType.APPLICATION_JSON)
public class DetectorResource {

    private final List<DetectorPort> detectors;

    @Inject
    public DetectorResource(@All List<DetectorPort> detectors) {
        this.detectors = detectors;
    }

    @GET
    @RolesAllowed({"viewer", "operator", "admin"})
    public List<Map<String, String>> list() {
        return detectors.stream()
                .map(d -> Map.of("id", d.id(), "category", d.category().name()))
                .toList();
    }
}
