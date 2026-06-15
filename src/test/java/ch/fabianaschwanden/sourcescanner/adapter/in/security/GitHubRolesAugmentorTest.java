package ch.fabianaschwanden.sourcescanner.adapter.in.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Reine Rollen-Mapping-Logik des GitHub-Augmentors (WR-31, WR-31a), framework-frei. */
class GitHubRolesAugmentorTest {

    private static final boolean ORG = true; // Org konfiguriert
    private static final boolean MEMBER = true;

    @Test
    void admin_login_wird_admin() {
        Set<String> admins = GitHubRolesAugmentor.parseLogins("fabianAschwanden, otheradmin");
        assertEquals("admin", GitHubRolesAugmentor.roleFor("fabianAschwanden", admins, ORG, false));
    }

    @Test
    void admin_hat_vorrang_vor_org_mitgliedschaft() {
        Set<String> admins = GitHubRolesAugmentor.parseLogins("fabianAschwanden");
        assertEquals("admin", GitHubRolesAugmentor.roleFor("fabianAschwanden", admins, ORG, MEMBER));
    }

    @Test
    void admin_match_ist_case_insensitiv() {
        Set<String> admins = GitHubRolesAugmentor.parseLogins("FabianAschwanden");
        assertEquals("admin", GitHubRolesAugmentor.roleFor("fabianaschwanden", admins, ORG, false));
    }

    @Test
    void org_mitglied_wird_operator() {
        assertEquals("operator", GitHubRolesAugmentor.roleFor("teammate", Set.of(), ORG, MEMBER));
    }

    @Test
    void org_mitglied_aber_keine_org_konfiguriert_wird_viewer() {
        assertEquals("viewer", GitHubRolesAugmentor.roleFor("teammate", Set.of(), false, MEMBER));
    }

    @Test
    void nicht_mitglied_wird_viewer() {
        assertEquals("viewer", GitHubRolesAugmentor.roleFor("stranger", Set.of("admin"), ORG, false));
    }

    @Test
    void null_login_ergibt_viewer() {
        assertEquals("viewer", GitHubRolesAugmentor.roleFor(null, Set.of("admin"), ORG, false));
    }
}
