package ch.fabianaschwanden.sourcescanner.adapter.out.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileCommitCacheTest {

    @Test
    void markiert_und_liest_gescannte_commits(@TempDir Path dir) {
        FileCommitCache cache = new FileCommitCache();
        cache.useDirectory(dir.toString());

        cache.markScanned("repo-a", Set.of("c1", "c2"));
        cache.markScanned("repo-a", Set.of("c2", "c3"));

        Set<String> scanned = cache.scanned("repo-a");
        assertEquals(Set.of("c1", "c2", "c3"), scanned);
        assertTrue(cache.scanned("repo-b").isEmpty());
    }

    @Test
    void ohne_verzeichnis_inaktiv() {
        FileCommitCache cache = new FileCommitCache();
        cache.useDirectory(null);
        cache.markScanned("repo", Set.of("c1"));
        assertTrue(cache.scanned("repo").isEmpty());
    }
}
