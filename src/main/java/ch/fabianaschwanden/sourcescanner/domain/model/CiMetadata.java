package ch.fabianaschwanden.sourcescanner.domain.model;

/**
 * Metadaten eines aus CI/CD eingelieferten Laufs (IR-25): externe Lauf-Referenz (für Idempotenz),
 * Pipeline-/Job-URL, Commit, Branch und Aktor. Alle Felder optional; nie Klartext-Geheimnisse.
 */
public record CiMetadata(String runRef, String pipelineUrl, String commit, String branch, String actor) {

    public static final CiMetadata NONE = new CiMetadata(null, null, null, null, null);

    public CiMetadata {
        runRef = blankToNull(runRef);
        pipelineUrl = blankToNull(pipelineUrl);
        commit = blankToNull(commit);
        branch = blankToNull(branch);
        actor = blankToNull(actor);
    }

    public boolean isEmpty() {
        return runRef == null && pipelineUrl == null && commit == null && branch == null && actor == null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
