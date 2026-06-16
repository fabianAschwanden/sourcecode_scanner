package ch.fabianaschwanden.sourcescanner.adapter.in.web;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * SPA-Deep-Link-Fallback: Ein direkter Aufruf/Reload einer Client-Route (z. B. {@code /dashboard},
 * {@code /findings}) hat keine Server-Datei und liefe sonst auf 404. Dieser Vert.x-Routen-Handler
 * leitet unbekannte GET-HTML-Aufrufe per einmaligem {@code reroute} auf {@code /} (Quinoa serviert
 * dort die SPA-{@code index.html}); Angular übernimmt das clientseitige Routing.
 *
 * <p>Wichtig: {@code reroute} lässt die Routen-Kette erneut durchlaufen — daher ein Re-Entry-Guard
 * über ein Request-Attribut, sonst Endlosschleife (StackOverflow im Router). API-, Asset-, Auth- und
 * Infrastruktur-Pfade bleiben unberührt. Registriert mit niedriger Priorität (order 10000), damit
 * echte Routen/Statische-Ressourcen zuerst greifen.
 */
@ApplicationScoped
public class SpaFallback {

    private static final String REROUTED = "spa-fallback-rerouted";

    void install(@Observes Router router) {
        router.route().order(10_000).handler(ctx -> {
            boolean alreadyRerouted = Boolean.TRUE.equals(ctx.get(REROUTED));
            if (ctx.request().method() != HttpMethod.GET
                    || alreadyRerouted
                    || !isAppRoute(ctx.normalizedPath())) {
                ctx.next();
                return;
            }
            ctx.put(REROUTED, Boolean.TRUE);
            ctx.reroute("/");
        });
    }

    /**
     * {@code true} für SPA-Client-Routen: kein Punkt im letzten Segment (also keine Datei mit
     * Endung), nicht {@code /} selbst und nicht unter API/Auth/Infrastruktur-Präfixen.
     */
    private static boolean isAppRoute(String path) {
        if (path == null || path.equals("/")) {
            return false;
        }
        if (path.startsWith("/api") || path.startsWith("/q/") || path.equals("/login")
                || path.equals("/logout") || path.startsWith("/branding")) {
            return false;
        }
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        return !lastSegment.contains(".");
    }
}
