package ch.fabianaschwanden.sourcescanner.adapter.in.web;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * SPA-Deep-Link-Fallback: Ein direkter Aufruf/Reload einer Client-Route (z. B. {@code /dashboard},
 * {@code /findings}) hat keine Server-Datei und lief auf 404. Dieser Vert.x-Routen-Handler reroutet
 * unbekannte GET-HTML-Aufrufe auf {@code /} (die SPA-{@code index.html}); Angular übernimmt dann das
 * clientseitige Routing. API-, Asset-, Auth- und Infrastruktur-Pfade bleiben unberührt.
 *
 * <p>Registriert mit niedriger Priorität (order 10000), damit echte Routen/Statische-Ressourcen
 * zuerst greifen und nur wirklich „nicht gefundene" Pfade hier landen.
 */
@ApplicationScoped
public class SpaFallback {

    void install(@Observes Router router) {
        router.route().order(10_000).handler(ctx -> {
            if (ctx.request().method() != HttpMethod.GET) {
                ctx.next();
                return;
            }
            String path = ctx.normalizedPath();
            if (isAppRoute(path)) {
                ctx.reroute("/");
            } else {
                ctx.next();
            }
        });
    }

    /**
     * {@code true} für SPA-Client-Routen: kein Punkt im letzten Segment (also keine Datei mit
     * Endung), und nicht unter API/Auth/Infrastruktur-Präfixen, die der Server selbst bedient.
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
