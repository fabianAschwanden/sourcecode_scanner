package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Auffang-Mapper für sonst nicht behandelte Fehler (z. B. Persistenz-/SQL-Fehler): liefert HTTP 500
 * mit einer kurzen, aussagekräftigen Meldung statt eines leeren Bodys, damit die UI nicht nur „Error"
 * zeigt und die Ursache nachvollziehbar ist. Loggt die vollständige Exception serverseitig.
 *
 * <p>Die Meldung trägt Exception-Typ + Message — nie Klartext-Secrets (die liegen in dieser App nur
 * als Chiffrat/Referenz vor, WR-33). {@link WebApplicationException} (inkl. 401/403/404) wird
 * durchgereicht, damit Auth-/Routing-Semantik erhalten bleibt.
 */
@Provider
public class UnhandledErrorMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UnhandledErrorMapper.class);

    @Override
    public Response toResponse(Throwable e) {
        if (e instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        LOG.error("unhandled error in REST layer", e);
        Throwable root = rootCause(e);
        String message = root.getClass().getSimpleName()
                + (root.getMessage() == null ? "" : ": " + root.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", message))
                .build();
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
