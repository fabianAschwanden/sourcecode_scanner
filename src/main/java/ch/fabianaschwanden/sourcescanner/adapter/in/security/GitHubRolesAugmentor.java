package ch.fabianaschwanden.sourcescanner.adapter.in.security;

import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Leitet App-Rollen aus dem GitHub-Login ab, da GitHub (OAuth2) keine Rollen liefert (WR-31). Nur
 * wirksam, wenn das Runtime-Profil {@code ghauth} aktiv ist (GitHub als IdP).
 *
 * <p>Rollen-Reihenfolge: Login in der Admin-Allowlist ({@code scanner.auth.github.admin-logins}) ⇒
 * {@code admin}; sonst öffentliches Mitglied der konfigurierten Org ({@code scanner.auth.github.org},
 * z. B. {@code css-ch}) ⇒ {@code operator} (Bearbeiten); sonst ⇒ {@code viewer}. Anonyme Identitäten
 * bleiben unverändert.
 *
 * <p>Profil-Prüfung zur Laufzeit (nicht {@code @IfBuildProfile}): das Image wird mit Build-Profil
 * {@code prod} gebaut, {@code ghauth} kommt erst beim Start über {@code QUARKUS_PROFILE}.
 */
@ApplicationScoped
public class GitHubRolesAugmentor implements SecurityIdentityAugmentor {

    private final boolean active;
    private final Set<String> adminLogins;
    private final String org;
    private final GitHubOrgMembership membership;

    @Inject
    public GitHubRolesAugmentor(GitHubOrgMembership membership) {
        this.membership = membership;
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        this.active = config.getProfiles().contains("ghauth");
        this.adminLogins = config.getOptionalValue("scanner.auth.github.admin-logins", String.class)
                .map(GitHubRolesAugmentor::parseLogins)
                .orElse(Set.of());
        this.org = config.getOptionalValue("scanner.auth.github.org", String.class)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (!active || identity.isAnonymous() || identity.getPrincipal() == null) {
            return Uni.createFrom().item(identity);
        }
        // Org-Check (HTTP) im blockierenden Executor, da augment() reaktiv aufgerufen wird.
        return context.runBlocking(() -> {
            String role = resolveRole(login(identity), accessToken(identity));
            return QuarkusSecurityIdentity.builder(identity).addRole(role).build();
        });
    }

    private String resolveRole(String login, String accessToken) {
        if (login != null && adminLogins.contains(login.toLowerCase(Locale.ROOT))) {
            return "admin";
        }
        boolean isOrgMember = org != null && membership.isActiveMember(org, accessToken);
        return roleFor(login, adminLogins, org != null, isOrgMember);
    }

    /** OIDC-Access-Token des eingeloggten Nutzers (für den Org-API-Call); {@code null}, wenn keins. */
    private static String accessToken(SecurityIdentity identity) {
        AccessTokenCredential cred = identity.getCredential(AccessTokenCredential.class);
        return cred == null ? null : cred.getToken();
    }

    /**
     * Reine Rollen-Entscheidung (testbar): Admin-Allowlist ⇒ {@code admin}; sonst Org-Mitglied (wenn
     * eine Org konfiguriert ist) ⇒ {@code operator}; sonst ⇒ {@code viewer}.
     */
    static String roleFor(String login, Set<String> adminLogins, boolean orgConfigured, boolean isOrgMember) {
        if (login != null && adminLogins.contains(login.toLowerCase(Locale.ROOT))) {
            return "admin";
        }
        if (orgConfigured && isOrgMember) {
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

    static Set<String> parseLogins(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
