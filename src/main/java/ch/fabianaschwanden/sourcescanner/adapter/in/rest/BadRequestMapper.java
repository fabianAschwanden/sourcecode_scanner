package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/** Bildet fachliche {@link IllegalArgumentException} (z. B. fehlende Triage-Begründung) auf HTTP 400 ab. */
@Provider
public class BadRequestMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage() == null ? "bad request" : e.getMessage()))
                .build();
    }
}
