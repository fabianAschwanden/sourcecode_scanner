package ch.fabianaschwanden.sourcescanner.adapter.in.seed;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositorySource;
import ch.fabianaschwanden.sourcescanner.domain.port.in.ManageSourcesUseCase;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Legt für lokale Tests im Entwicklungs-/Test-Modus eine Beispiel-Repository-Quelle an, falls noch
 * keine existiert: das öffentliche GitHub-Repo {@code wm-tippspiel} (Typ {@code github}, ohne Token).
 * So ist nach einem Start mit frischer Dev-DB sofort ein scanbares Repo konfiguriert.
 *
 * <p>Läuft <b>nur</b> im {@code DEVELOPMENT}-/{@code TEST}-Launch-Modus und bei aktiver Persistenz —
 * also nie im {@code server}/{@code prod}-Betrieb und nicht im DB-freien CLI-Profil. Bereits
 * vorhandene Quellen bleiben unberührt; das Seed-Repo ist regulär änder-/löschbar.
 */
@ApplicationScoped
public class DevRepositorySeeder {

    private static final Logger LOG = Logger.getLogger(DevRepositorySeeder.class);
    static final String SEED_NAME = "fabianAschwanden/wm-tippspiel";
    static final String SEED_LOCATION = "https://github.com/fabianAschwanden/wm-tippspiel";

    private final ManageSourcesUseCase sources;
    private final boolean persistenceActive;

    @Inject
    public DevRepositorySeeder(
            ManageSourcesUseCase sources,
            @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "false") boolean persistenceActive) {
        this.sources = sources;
        this.persistenceActive = persistenceActive;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        // Nur lokal (dev/test) und nur bei aktiver Persistenz — nie im server/prod-Betrieb oder CLI.
        if (!persistenceActive || !isLocalMode() || !sources.all().isEmpty()) {
            return;
        }
        RepositorySource seeded = sources.create(new RepositorySource(
                null, SEED_NAME, "github", SEED_LOCATION, List.of(), null, true, List.of(),
                false, "Seed-Repo für lokale Tests", "public"), "system");
        LOG.infof("seeded dev repository source '%s' (%s)", seeded.name(), seeded.location());
    }

    private static boolean isLocalMode() {
        LaunchMode mode = LaunchMode.current();
        return mode == LaunchMode.DEVELOPMENT || mode == LaunchMode.TEST;
    }
}
