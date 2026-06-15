package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

/**
 * Login-Einstieg und aktuelle Identität für die UI (WR-30/31). {@code /login} ist authentifiziert:
 * der erste (anonyme) Aufruf stösst den OIDC-Redirect zum IdP an (im {@code ghauth}-Profil GitHub);
 * nach erfolgreichem Login leitet er in die App ({@code /dashboard}). {@code /api/me} liefert den
 * eingeloggten Login + Rollen für Header/Guard.
 */
@Path("/")
public class LoginResource {

    private final SecurityIdentity identity;

    @Inject
    public LoginResource(SecurityIdentity identity) {
        this.identity = identity;
    }

    /** Authentifizierter Einstieg: löst den IdP-Login aus und leitet danach in die App. */
    @GET
    @Path("/login")
    @Authenticated
    public Response login() {
        return Response.seeOther(URI.create("/dashboard")).build();
    }

    /** Aktueller Nutzer (Login + Rollen) — nur authentifiziert; nie ein Secret/Token. */
    @GET
    @Path("/api/me")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public CurrentUser me() {
        String login = identity.getPrincipal() == null ? "unknown" : identity.getPrincipal().getName();
        return new CurrentUser(login, List.copyOf(identity.getRoles()));
    }

    public record CurrentUser(String login, List<String> roles) {}
}
