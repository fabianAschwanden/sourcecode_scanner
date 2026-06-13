package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Bildet fachliche {@link IllegalStateException} (z. B. ein blockiertes Remediation-Gate oder
 * deaktivierte Remediation) auf HTTP 409 Conflict ab — der Aufruf ist im aktuellen Zustand nicht
 * zulässig. Die Meldung ist redigiert (kein Klartext, RMR-12).
 */
@Provider
public class ConflictMapper implements ExceptionMapper<IllegalStateException> {

    @Override
    public Response toResponse(IllegalStateException e) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", e.getMessage() == null ? "conflict" : e.getMessage()))
                .build();
    }
}
