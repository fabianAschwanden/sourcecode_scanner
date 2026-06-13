package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;

/**
 * Auftrag zur Erzeugung eines Auto-Fix-PR/MR (RMR-10/11). Es wird stets ein {@code fixBranch} +
 * PR/MR erzeugt — nie direkt auf {@code baseBranch} geschrieben (RMR-11). {@code description} ist
 * redigiert (RMR-12); {@code tokenRef} verweist auf das (höher privilegierte) Schreib-Token (RMR-44).
 */
public record FixRequest(
        String repoUrl,
        String baseBranch,
        String fixBranch,
        String commitMessage,
        List<FileEdit> edits,
        String description,
        List<String> reviewers,
        List<String> labels,
        String tokenRef) {

    public FixRequest {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl must not be blank");
        }
        if (fixBranch == null || fixBranch.isBlank()) {
            throw new IllegalArgumentException("fixBranch must not be blank");
        }
        baseBranch = baseBranch == null || baseBranch.isBlank() ? "main" : baseBranch;
        commitMessage = commitMessage == null || commitMessage.isBlank()
                ? "chore(security): scanner auto-fix" : commitMessage;
        edits = edits == null ? List.of() : List.copyOf(edits);
        description = description == null ? "" : description;
        reviewers = reviewers == null ? List.of() : List.copyOf(reviewers);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}
