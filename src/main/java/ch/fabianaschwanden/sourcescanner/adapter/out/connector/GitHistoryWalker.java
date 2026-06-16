package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
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

    /** Eager-Variante (alle Einheiten als Liste) — für Tests/kleine Repos. */
    static List<ScanUnit> walk(RepositoryRef ref, Repository repository, Set<String> skipCommits)
            throws IOException {
        List<ScanUnit> units = new ArrayList<>();
        for (RevCommit commit : commitsToScan(ref, repository, skipCommits, false)) {
            collectCommit(ref, repository, commit, units);
        }
        return units;
    }

    /**
     * Lazy/gestreamt: liefert die ScanUnits commit-für-commit (History-Slicing). Die Dateien eines
     * Commits werden erst beim Konsumieren materialisiert und danach freigegeben — Peak-RAM ist ein
     * Commit, nicht das ganze Repo. {@code headOnly} = nur der HEAD-Commit (Default-Branch, keine History).
     */
    static Stream<ScanUnit> stream(RepositoryRef ref, Repository repository, Set<String> skipCommits,
                                   boolean headOnly) {
        List<RevCommit> commits;
        try {
            commits = commitsToScan(ref, repository, skipCommits, headOnly);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read commits of " + ref.id(), e);
        }
        return commits.stream().flatMap(commit -> {
            List<ScanUnit> units = new ArrayList<>();
            try {
                collectCommit(ref, repository, commit, units);
            } catch (IOException e) {
                throw new UncheckedIOException("failed to read commit " + commit.getName(), e);
            }
            return units.stream();
        });
    }

    /** Zu scannende Commits: nur HEAD ({@code headOnly}) oder alle Branches, ohne {@code skipCommits}. */
    private static List<RevCommit> commitsToScan(RepositoryRef ref, Repository repository,
                                                 Set<String> skipCommits, boolean headOnly) throws IOException {
        List<RevCommit> commits = new ArrayList<>();
        Set<ObjectId> visited = new HashSet<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            if (headOnly) {
                ObjectId head = repository.resolve(Constants.HEAD);
                if (head != null) {
                    commits.add(revWalk.parseCommit(head));
                }
                return commits;
            }
            for (Ref branch : repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
                revWalk.reset();
                revWalk.markStart(revWalk.parseCommit(branch.getObjectId()));
                for (RevCommit commit : revWalk) {
                    if (visited.add(commit.getId()) && !skipCommits.contains(commit.getName())) {
                        commits.add(commit);
                    }
                }
            }
        }
        return commits;
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
