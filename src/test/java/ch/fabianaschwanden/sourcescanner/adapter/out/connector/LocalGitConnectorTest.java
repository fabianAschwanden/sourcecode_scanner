package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.HistoryMode;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalGitConnectorTest {

    private final LocalGitConnector connector = new LocalGitConnector();

    @Test
    void walkt_full_history_und_liefert_dateiinhalt(@TempDir Path repoDir) throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            Files.writeString(repoDir.resolve("Config.java"), "String k = \"AKIAIOSFODNN7EXAMPLE\";\n");
            git.add().addFilepattern("Config.java").call();
            git.commit().setMessage("add config").setAuthor("Test", "test@example.ch").call();
        }

        RepositoryRef ref = new RepositoryRef("self", "localGit", repoDir.toString(), List.of());
        List<ScanUnit> units;
        try (var stream = connector.walkHistory(ref, HistoryMode.FULL)) {
            units = stream.toList();
        }

        assertEquals(1, units.size());
        ScanUnit unit = units.getFirst();
        assertEquals("Config.java", unit.path());
        assertTrue(unit.content().contains("AKIAIOSFODNN7EXAMPLE"));
        assertTrue(unit.commitId() != null && !unit.commitId().isBlank());
    }

    @Test
    void head_modus_scannt_nur_den_aktuellen_stand_nicht_die_history(@TempDir Path repoDir) throws Exception {
        try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
            // Commit 1: alte Datei (nur in der History).
            Files.writeString(repoDir.resolve("Old.java"), "// old\n");
            git.add().addFilepattern("Old.java").call();
            git.commit().setMessage("c1").setAuthor("T", "t@example.ch").call();
            // Commit 2: alte Datei entfernt, neue hinzu (= aktueller Stand).
            Files.delete(repoDir.resolve("Old.java"));
            Files.writeString(repoDir.resolve("New.java"), "// new\n");
            git.add().addFilepattern(".").setUpdate(true).call();
            git.add().addFilepattern("New.java").call();
            git.commit().setMessage("c2").setAuthor("T", "t@example.ch").call();
        }
        RepositoryRef ref = new RepositoryRef("self", "localGit", repoDir.toString(), List.of());

        try (var head = connector.walkHistory(ref, HistoryMode.HEAD)) {
            List<String> paths = head.map(ScanUnit::path).toList();
            // HEAD: nur der aktuelle Stand -> New.java, KEIN Old.java aus der History.
            assertTrue(paths.contains("New.java"));
            assertTrue(!paths.contains("Old.java"));
        }
        try (var full = connector.walkHistory(ref, HistoryMode.FULL)) {
            List<String> paths = full.map(ScanUnit::path).toList();
            // FULL: walkt die History -> auch die in c1 hinzugefügte Old.java taucht auf.
            assertTrue(paths.contains("Old.java"));
        }
    }

    @Test
    void supports_nur_localGit() {
        assertTrue(connector.supports(new RepositoryRef("a", "localGit", ".", List.of())));
        assertTrue(connector.supports(new RepositoryRef("a", null, ".", List.of())));
    }

    @Test
    void verzeichnis_ohne_git_liefert_klaren_fehler(@TempDir Path noGit) {
        RepositoryRef ref = new RepositoryRef("self", "localGit", noGit.toString(), List.of());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> connector.walkHistory(ref, HistoryMode.FULL));
        assertTrue(ex.getMessage().contains("no git repository"));
    }

    @Test
    void nicht_existierender_pfad_liefert_klaren_fehler() {
        RepositoryRef ref = new RepositoryRef("self", "localGit", "/does/not/exist-xyz", List.of());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> connector.walkHistory(ref, HistoryMode.FULL));
        assertTrue(ex.getMessage().contains("does not exist"));
    }
}
