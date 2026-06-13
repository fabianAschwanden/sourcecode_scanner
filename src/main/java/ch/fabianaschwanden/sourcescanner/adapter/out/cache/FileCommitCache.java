package ch.fabianaschwanden.sourcescanner.adapter.out.cache;

import ch.fabianaschwanden.sourcescanner.domain.port.out.CommitCachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Persistiert gescannte Commit-IDs je Repository als JSON unter einem Cache-Verzeichnis (FR-19,
 * NFR-02), damit inkrementelle Läufe bereits gescannte Commits überspringen. Ohne konfiguriertes
 * Cache-Verzeichnis ist der Cache inaktiv (leer, keine Persistenz).
 */
@ApplicationScoped
public class FileCommitCache implements CommitCachePort {

    private static final Logger LOG = Logger.getLogger(FileCommitCache.class);

    private final ObjectMapper json = new ObjectMapper();
    private Path cacheDir;

    @Override
    public void useDirectory(String directory) {
        this.cacheDir = directory == null || directory.isBlank() ? null : Path.of(directory);
    }

    @Override
    public Set<String> scanned(String repoId) {
        if (cacheDir == null) {
            return Set.of();
        }
        Path file = cacheFile(repoId);
        if (!Files.isRegularFile(file)) {
            return Set.of();
        }
        try {
            Set<String> ids = new LinkedHashSet<>();
            json.readTree(Files.readAllBytes(file)).path("commits").forEach(n -> ids.add(n.asText()));
            return ids;
        } catch (IOException e) {
            LOG.warnf("could not read commit cache %s: %s", file, e.getMessage());
            return Set.of();
        }
    }

    @Override
    public void markScanned(String repoId, Set<String> commitIds) {
        if (cacheDir == null || commitIds.isEmpty()) {
            return;
        }
        Set<String> merged = new LinkedHashSet<>(scanned(repoId));
        merged.addAll(commitIds);
        try {
            Files.createDirectories(cacheDir);
            ArrayNode arr = json.createObjectNode().putArray("commits");
            merged.forEach(arr::add);
            var root = json.createObjectNode();
            root.set("commits", arr);
            json.writeValue(cacheFile(repoId).toFile(), root);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write commit cache for " + repoId, e);
        }
    }

    private Path cacheFile(String repoId) {
        String safe = repoId.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return cacheDir.resolve(safe + ".commits.json");
    }
}
