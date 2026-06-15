package ch.fabianaschwanden.sourcescanner.adapter.in.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Reine Rollen-Mapping-Logik des GitHub-Augmentors (WR-31), framework-frei. */
class GitHubRolesAugmentorTest {

    @Test
    void admin_login_wird_admin() {
        Set<String> admins = GitHubRolesAugmentor.parseLogins("fabianAschwanden, otheradmin");
        assertEquals("admin", GitHubRolesAugmentor.roleFor("fabianAschwanden", admins));
    }

    @Test
    void admin_match_ist_case_insensitiv() {
        Set<String> admins = GitHubRolesAugmentor.parseLogins("FabianAschwanden");
        assertEquals("admin", GitHubRolesAugmentor.roleFor("fabianaschwanden", admins));
    }

    @Test
    void anderer_login_wird_viewer() {
        Set<String> admins = GitHubRolesAugmentor.parseLogins("fabianAschwanden");
        assertEquals("viewer", GitHubRolesAugmentor.roleFor("someone-else", admins));
    }

    @Test
    void leere_allowlist_ergibt_viewer() {
        assertEquals("viewer", GitHubRolesAugmentor.roleFor("fabianAschwanden", GitHubRolesAugmentor.parseLogins("")));
    }

    @Test
    void null_login_ergibt_viewer() {
        assertEquals("viewer", GitHubRolesAugmentor.roleFor(null, Set.of("admin")));
    }
}
