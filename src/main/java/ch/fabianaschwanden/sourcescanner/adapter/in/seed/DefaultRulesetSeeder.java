package ch.fabianaschwanden.sourcescanner.adapter.in.seed;

import ch.fabianaschwanden.sourcescanner.domain.model.EnforcementStatus;
import ch.fabianaschwanden.sourcescanner.domain.model.RuleMatchMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RuleOverride;
import ch.fabianaschwanden.sourcescanner.domain.model.Ruleset;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.RulesetPort;
import io.quarkus.arc.All;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Macht die ab Start wirksame Default-Regelkonfiguration sichtbar (DR-56, WR-97): Existiert noch kein
 * Ruleset, wird ein global aktives Ruleset mit Namen {@code default} angelegt — befüllt aus dem
 * Detektor-Regelkatalog (alle Regeln an, Default-Severity, Abgleichsmodus {@code ALWAYS}). So
 * entspricht das, was die UI zeigt, dem, was ohne Konfiguration tatsächlich gilt; es bleibt voll
 * editierbar und löschbar.
 *
 * <p>Nur wirksam, wenn Persistenz aktiv ist (Server-/Dev-/Test-Profil); im DB-freien CLI-Profil läuft
 * der Seeder nicht, weil dort kein {@link RulesetPort}-Backend bereitsteht.
 */
@ApplicationScoped
public class DefaultRulesetSeeder {

    private static final Logger LOG = Logger.getLogger(DefaultRulesetSeeder.class);
    static final String DEFAULT_NAME = "default";

    private final RulesetPort rulesets;
    private final List<DetectorPort> detectors;
    private final boolean persistenceActive;

    @Inject
    public DefaultRulesetSeeder(
            RulesetPort rulesets,
            @All List<DetectorPort> detectors,
            @ConfigProperty(name = "quarkus.datasource.active", defaultValue = "false") boolean persistenceActive) {
        this.rulesets = rulesets;
        this.detectors = detectors;
        this.persistenceActive = persistenceActive;
    }

    @Transactional
    void onStart(@Observes StartupEvent event) {
        // Im DB-freien CLI-Profil gibt es kein Persistenz-Backend — dann nichts tun (siehe Klassen-Doku).
        if (!persistenceActive || !rulesets.all().isEmpty()) {
            return;
        }
        Ruleset seeded = rulesets.save(new Ruleset(null, DEFAULT_NAME, EnforcementStatus.ACTIVE,
                true, List.of(), defaultRules()));
        LOG.infof("seeded active default ruleset '%s' with %d rule(s)", seeded.name(),
                seeded.rules().size());
    }

    /**
     * Alle deklarierten Detektor-Regeln mit Default-Severity und Abgleichsmodus ALWAYS; der
     * Aktiv-Zustand folgt dem Default der Regel ({@code defaultEnabled}) — z. B. {@code phone} ist aus.
     */
    private List<RuleOverride> defaultRules() {
        List<RuleOverride> rules = new ArrayList<>();
        for (DetectorPort d : detectors) {
            var declared = d.rules();
            if (declared.isEmpty()) {
                rules.add(new RuleOverride(d.id(), true, null, RuleMatchMode.ALWAYS, null));
            } else {
                for (var r : declared) {
                    Severity sev = r.defaultSeverity();
                    rules.add(new RuleOverride(r.id(), r.defaultEnabled(), sev, RuleMatchMode.ALWAYS, null));
                }
            }
        }
        return rules;
    }
}
