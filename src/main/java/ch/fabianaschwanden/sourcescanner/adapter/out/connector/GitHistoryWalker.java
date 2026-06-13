package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Gemeinsame JGit-Walk-Logik (DRY): iteriert die Commits aller Branches eines geöffneten Repositories
 * und liefert je (Commit × Textdatei) eine {@link ScanUnit}. Genutzt vom lokalen wie von den
 * Plattform-Connectoren (die nur das Klonen unterscheiden). {@code skipCommits} überspringt bereits
 * gescannte Commit-IDs (inkrementell, FR-19/NFR-02).
 */
final class GitHistoryWalker {

    private static final long MAX_BLOB_BYTES = 2_000_000;

    private GitHistoryWalker() {
    }

    static List<ScanUnit> walk(RepositoryRef ref, Repository repository, Set<String> skipCommits)
            throws IOException {
        List<ScanUnit> units = new ArrayList<>();
        Set<ObjectId> visited = new HashSet<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            for (Ref branch : repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
                revWalk.reset();
                revWalk.markStart(revWalk.parseCommit(branch.getObjectId()));
                for (RevCommit commit : revWalk) {
                    if (!visited.add(commit.getId())) {
                        continue;
                    }
                    if (skipCommits.contains(commit.getName())) {
                        continue;
                    }
                    collectCommit(ref, repository, commit, units);
                }
            }
        }
        return units;
    }

    private static void collectCommit(RepositoryRef ref, Repository repository, RevCommit commit,
                                      List<ScanUnit> units) throws IOException {
        String author = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getEmailAddress() : "";
        Instant timestamp = Instant.ofEpochSecond(commit.getCommitTime());
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);
                if (loader.getSize() > MAX_BLOB_BYTES) {
                    continue;
                }
                byte[] bytes = loader.getBytes();
                if (isBinary(bytes)) {
                    continue;
                }
                units.add(new ScanUnit(
                        ref.id(),
                        treeWalk.getPathString(),
                        commit.getName(),
                        author,
                        timestamp,
                        new String(bytes, StandardCharsets.UTF_8),
                        null));
            }
        }
    }

    /** Heuristik: ein NUL-Byte im Anfangsbereich kennzeichnet Binärinhalt. */
    private static boolean isBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 8_000);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
