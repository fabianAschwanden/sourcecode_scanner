package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Liefert der UI die Konfiguration zum Einbetten von Grafana-Panels (OR-07). Standardmässig
 * deaktiviert; bei {@code embedGrafana=true} gibt die UI die Panel-URLs als iframe aus, sonst
 * verlinkt sie nur (Deep-Link). Es werden ausschliesslich konfigurierte URLs ausgegeben, keine Tokens.
 */
@Path("/api/observability/grafana")
@Produces(MediaType.APPLICATION_JSON)
public class GrafanaEmbedResource {

    private final boolean embed;
    private final String baseUrl;
    private final List<String> dashboards;

    public GrafanaEmbedResource(
            @ConfigProperty(name = "scanner.observability.grafana.embed", defaultValue = "false") boolean embed,
            @ConfigProperty(name = "scanner.observability.grafana.base-url") Optional<String> baseUrl,
            @ConfigProperty(name = "scanner.observability.grafana.dashboards") Optional<String> dashboards) {
        this.embed = embed;
        this.baseUrl = baseUrl.orElse("");
        String d = dashboards.orElse("");
        this.dashboards = d.isBlank() ? List.of() : List.of(d.split(","));
    }

    @GET
    @RolesAllowed({"viewer", "operator", "admin"})
    public EmbedConfig config() {
        boolean active = embed && !baseUrl.isBlank();
        List<Map<String, String>> panels = dashboards.stream()
                .map(uid -> Map.of("uid", uid.trim(), "url", baseUrl + "/d/" + uid.trim()))
                .toList();
        return new EmbedConfig(active, active ? panels : List.of());
    }

    /** Embed-Konfiguration für die UI: aktiv? + Panel-Deep-Links/URLs. */
    public record EmbedConfig(boolean enabled, List<Map<String, String>> panels) {
    }
}
