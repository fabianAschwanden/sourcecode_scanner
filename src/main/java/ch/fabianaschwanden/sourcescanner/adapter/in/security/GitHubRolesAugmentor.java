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
 * Leitet App-Rollen aus dem GitHub-Login ab, da GitHub (OAuth2) keine Rollen liefert (WR-31). Nur
 * wirksam, wenn das Runtime-Profil {@code ghauth} aktiv ist (GitHub als IdP). Login in der
 * Admin-Allowlist ({@code scanner.auth.github.admin-logins}) ⇒ {@code admin}; jeder andere
 * authentifizierte GitHub-Nutzer ⇒ {@code viewer}. Anonyme Identitäten bleiben unverändert.
 *
 * <p>Profil-Prüfung zur Laufzeit (nicht {@code @IfBuildProfile}): das Image wird mit Build-Profil
 * {@code prod} gebaut, {@code ghauth} kommt erst beim Start über {@code QUARKUS_PROFILE}.
 */
@ApplicationScoped
public class GitHubRolesAugmentor implements SecurityIdentityAugmentor {

    private final boolean active;
    private final Set<String> adminLogins;

    public GitHubRolesAugmentor() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        this.active = config.getProfiles().contains("ghauth");
        this.adminLogins = config.getOptionalValue("scanner.auth.github.admin-logins", String.class)
                .map(GitHubRolesAugmentor::parseLogins)
                .orElse(Set.of());
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (!active || identity.isAnonymous() || identity.getPrincipal() == null) {
            return Uni.createFrom().item(identity);
        }
        SecurityIdentity augmented = QuarkusSecurityIdentity.builder(identity)
                .addRole(roleFor(login(identity), adminLogins))
                .build();
        return Uni.createFrom().item(augmented);
    }

    /** Rollen-Entscheidung: Login in der Allowlist ⇒ {@code admin}, sonst {@code viewer}. Pur/testbar. */
    static String roleFor(String login, Set<String> adminLogins) {
        return login != null && adminLogins.contains(login.toLowerCase(Locale.ROOT)) ? "admin" : "viewer";
    }

    /** GitHub-Login: bevorzugt das {@code login}-Attribut (GitHub-Provider), sonst der Principal-Name. */
    private static String login(SecurityIdentity identity) {
        Object attr = identity.getAttribute("login");
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return identity.getPrincipal().getName();
    }

    static Set<String> parseLogins(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
