package ch.fabianaschwanden.sourcescanner.adapter.in.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Reine Rollen-Mapping-Logik des GitHub-Augmentors (WR-31, WR-31a), framework-frei. */
class GitHubRolesAugmentorTest {

    private static final Set<String> ADMINS = GitHubRolesAugmentor.parseLogins("fabianAschwanden");
    private static final Set<String> OPERATORS = GitHubRolesAugmentor.parseLogins("teammate, dev2");

    @Test
    void admin_login_wird_admin() {
        assertEquals("admin", GitHubRolesAugmentor.roleFor("fabianAschwanden", ADMINS, OPERATORS));
    }

    @Test
    void admin_match_ist_case_insensitiv() {
        assertEquals("admin", GitHubRolesAugmentor.roleFor("FABIANASCHWANDEN", ADMINS, OPERATORS));
    }

    @Test
    void operator_login_wird_operator() {
        assertEquals("operator", GitHubRolesAugmentor.roleFor("teammate", ADMINS, OPERATORS));
    }

    @Test
    void admin_hat_vorrang_vor_operator() {
        Set<String> both = GitHubRolesAugmentor.parseLogins("dup");
        assertEquals("admin", GitHubRolesAugmentor.roleFor("dup", both, both));
    }

    @Test
    void unbekannter_login_wird_viewer() {
        assertEquals("viewer", GitHubRolesAugmentor.roleFor("stranger", ADMINS, OPERATORS));
    }

    @Test
    void null_login_wird_viewer() {
        assertEquals("viewer", GitHubRolesAugmentor.roleFor(null, ADMINS, OPERATORS));
    }

    @Test
    void leere_listen_ergeben_viewer() {
        assertEquals("viewer", GitHubRolesAugmentor.roleFor("anyone", Set.of(), Set.of()));
    }
}
