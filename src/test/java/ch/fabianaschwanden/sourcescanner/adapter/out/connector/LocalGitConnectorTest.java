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
