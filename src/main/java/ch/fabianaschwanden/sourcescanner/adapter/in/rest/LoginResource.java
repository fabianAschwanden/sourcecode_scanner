package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
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

    /**
     * Lokaler Logout: GitHub bietet kein OIDC-RP-Logout (kein end_session_endpoint), daher löschen wir
     * die Quarkus-OIDC-Session-Cookies selbst und leiten auf die öffentliche Landing-Seite. Quarkus
     * teilt die Session bei grossen Tokens in mehrere Cookies ({@code q_session}, {@code q_session_1},
     * {@code q_session_github} …) — daher werden ALLE Cookies mit Präfix {@code q_session} entfernt
     * (max-age 0), sonst bleibt man eingeloggt. Nicht authentifiziert, damit auch ein abgelaufenes
     * Cookie sauber entfernt werden kann.
     */
    @GET
    @Path("/logout")
    public Response logout(@jakarta.ws.rs.core.Context HttpHeaders headers) {
        Response.ResponseBuilder res = Response.seeOther(URI.create("/"));
        List<NewCookie> expired = new ArrayList<>();
        for (Cookie c : headers.getCookies().values()) {
            if (c.getName().startsWith("q_session")) {
                expired.add(new NewCookie.Builder(c.getName())
                        .path("/")
                        .maxAge(0)
                        .expiry(new java.util.Date(0))
                        .httpOnly(true)
                        .secure(true)
                        .build());
            }
        }
        // Fallback, falls (noch) kein q_session-Cookie mitgeschickt wurde.
        if (expired.isEmpty()) {
            expired.add(new NewCookie.Builder("q_session").path("/").maxAge(0)
                    .expiry(new java.util.Date(0)).httpOnly(true).secure(true).build());
        }
        return res.cookie(expired.toArray(new NewCookie[0])).build();
    }

    /** Aktueller Nutzer (Login + Rollen) für Header/Guard — nur authentifiziert; nie ein Secret/Token. */
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
