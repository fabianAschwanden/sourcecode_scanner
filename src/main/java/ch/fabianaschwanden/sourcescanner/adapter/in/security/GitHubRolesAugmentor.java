package ch.fabianaschwanden.sourcescanner.adapter.in.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Leitet App-Rollen aus dem GitHub-Login ab, da GitHub (OAuth2) keine Rollen liefert (WR-31/31a). Nur
 * wirksam, wenn das Runtime-Profil {@code ghauth} aktiv ist.
 *
 * <p>Rollen-Reihenfolge über Login-Allowlists (kommasepariert, case-insensitiv):
 * {@code scanner.auth.github.admin-logins} ⇒ {@code admin}; sonst
 * {@code scanner.auth.github.operator-logins} ⇒ {@code operator} (Bearbeiten); sonst ⇒ {@code viewer}.
 * Anonyme Identitäten bleiben unverändert.
 *
 * <p>Profil-Prüfung zur Laufzeit (nicht {@code @IfBuildProfile}): das Image wird mit Build-Profil
 * {@code prod} gebaut, {@code ghauth} kommt erst beim Start über {@code QUARKUS_PROFILE}.
 */
@ApplicationScoped
public class GitHubRolesAugmentor implements SecurityIdentityAugmentor {

    private final boolean active;
    private final Set<String> adminLogins;
    private final Set<String> operatorLogins;

    public GitHubRolesAugmentor() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        this.active = config.getProfiles().contains("ghauth");
        this.adminLogins = logins(config, "scanner.auth.github.admin-logins");
        this.operatorLogins = logins(config, "scanner.auth.github.operator-logins");
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (!active || identity.isAnonymous() || identity.getPrincipal() == null) {
            return Uni.createFrom().item(identity);
        }
        String role = roleFor(login(identity), adminLogins, operatorLogins);
        SecurityIdentity augmented = QuarkusSecurityIdentity.builder(identity).addRole(role).build();
        return Uni.createFrom().item(augmented);
    }

    /**
     * Rollen-Entscheidung (testbar): Admin-Allowlist ⇒ {@code admin}; sonst Operator-Allowlist ⇒
     * {@code operator}; sonst ⇒ {@code viewer}. Vergleich case-insensitiv.
     */
    static String roleFor(String login, Set<String> adminLogins, Set<String> operatorLogins) {
        if (login == null) {
            return "viewer";
        }
        String lower = login.toLowerCase(Locale.ROOT);
        if (adminLogins.contains(lower)) {
            return "admin";
        }
        if (operatorLogins.contains(lower)) {
            return "operator";
        }
        return "viewer";
    }

    /** GitHub-Login: bevorzugt das {@code login}-Attribut (GitHub-Provider), sonst der Principal-Name. */
    private static String login(SecurityIdentity identity) {
        Object attr = identity.getAttribute("login");
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return identity.getPrincipal().getName();
    }

    private static Set<String> logins(SmallRyeConfig config, String key) {
        return config.getOptionalValue(key, String.class)
                .map(GitHubRolesAugmentor::parseLogins)
                .orElse(Set.of());
    }

    static Set<String> parseLogins(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
