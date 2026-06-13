package ch.fabianaschwanden.sourcescanner.adapter.out.scrub;

import ch.fabianaschwanden.sourcescanner.domain.model.ScrubDryRun;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubReplacement;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.ScrubResult;
import ch.fabianaschwanden.sourcescanner.domain.port.out.HistoryRewritePort;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * History-Rewrite-Adapter auf Basis von {@code git-filter-repo} (RMR-20/21/28). Arbeitet konzeptuell
 * auf einem frischen Mirror-Klon (RMR-21). Da das Werkzeug in dieser Umgebung nicht installiert ist,
 * meldet {@link #available()} dessen Abwesenheit; {@link #dryRun} liefert dennoch einen vollständigen,
 * <b>redigierten</b> Bericht (was entfernt würde), und {@link #execute} verweigert mit klarer Meldung,
 * statt eine reale Historie umzuschreiben. So ist die gesamte Governance testbar (BFG als Alternative,
 * RMR-28). Trägt nie Klartext (RMR-12) — nur redigierte Treffer/Fingerprints der Funde.
 */
@ApplicationScoped
public class GitFilterRepoAdapter implements HistoryRewritePort {

    private final String engine;

    public GitFilterRepoAdapter(
            @ConfigProperty(name = "scanner.remediation.history-scrub.engine",
                    defaultValue = "git-filter-repo") String engine) {
        this.engine = engine;
    }

    @Override
    public boolean available() {
        return toolOnPath(engine);
    }

    @Override
    public ScrubDryRun dryRun(ScrubRequest request) {
        boolean tool = available();
        int affected = request.replacements().size();
        String diff = request.replacements().stream()
                .map(this::reportLine)
                .collect(Collectors.joining("\n"));
        String summary = (tool
                ? "Geplanter Scrub mit " + engine + " auf einem frischen Mirror-Klon von " + request.repoUrl() + ":\n"
                : "VORSCHAU (Werkzeug '" + engine + "' nicht installiert — kein realer Lauf möglich) für "
                        + request.repoUrl() + ":\n")
                + (affected == 0 ? "(keine offenen Secret-Funde)" : diff);
        return new ScrubDryRun(tool, affected, summary);
    }

    @Override
    public ScrubResult execute(ScrubRequest request) {
        if (!available()) {
            return ScrubResult.notExecutable(
                    "History-Rewrite nicht ausführbar: Werkzeug '" + engine + "' ist nicht installiert. "
                            + "Bitte git-filter-repo (oder BFG) bereitstellen und erneut versuchen (RMR-28).");
        }
        // Realer Lauf wäre hier: Mirror-Klon → git-filter-repo --replace-text → Re-Scan → force-with-lease.
        // Bewusst nicht implementiert in dieser Phase (kein realer Force-Push, kein installiertes Werkzeug).
        return ScrubResult.notExecutable(
                "Realer History-Rewrite ist in dieser Ausbaustufe deaktiviert (RMR-28); Governance ist aktiv.");
    }

    /** Eine redigierte Bericht-Zeile je zu entfernendem Secret (nie Klartext, RMR-12). */
    private String reportLine(ScrubReplacement r) {
        return "- " + r.file() + ":" + r.line() + " — " + r.redactedMatch() + " (fp " + shortFp(r.fingerprint()) + ")";
    }

    private String shortFp(String fp) {
        return fp.length() <= 12 ? fp : fp.substring(0, 12);
    }

    private boolean toolOnPath(String tool) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return false;
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            Path candidate = Paths.get(dir, tool);
            if (Files.isExecutable(candidate)) {
                return true;
            }
        }
        // Fallback: `tool --version` (PATH-Suche kann auf Wrapper/aliases nicht greifen).
        try {
            Process p = new ProcessBuilder(tool, "--version").redirectErrorStream(true).start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
