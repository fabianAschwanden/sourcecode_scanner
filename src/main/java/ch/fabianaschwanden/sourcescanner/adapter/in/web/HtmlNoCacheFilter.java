package ch.fabianaschwanden.sourcescanner.adapter.in.web;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Verhindert aggressives Caching der SPA-Einstiegsseite: Quinoa liefert alle statischen Dateien mit
 * {@code Cache-Control: public, immutable, max-age=86400}. Für die gehashten JS/CSS ist das korrekt,
 * für {@code index.html} jedoch schädlich — ein neuer Build würde bis zu einen Tag nicht sichtbar.
 * Dieser Vert.x-Routen-Filter setzt für HTML-Antworten {@code no-cache}, sodass Updates sofort
 * ankommen. Registriert über den beobachteten {@link Router} (kein zusätzliches Extension nötig).
 */
@ApplicationScoped
public class HtmlNoCacheFilter {

    void install(@Observes Router router) {
        router.route().order(-1).handler(ctx -> {
            ctx.addHeadersEndHandler(v -> {
                String contentType = ctx.response().headers().get("Content-Type");
                if (contentType != null && contentType.startsWith("text/html")) {
                    ctx.response().putHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                }
            });
            ctx.next();
        });
    }
}
