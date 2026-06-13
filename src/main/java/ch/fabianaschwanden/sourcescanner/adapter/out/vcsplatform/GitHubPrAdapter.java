package ch.fabianaschwanden.sourcescanner.adapter.out.vcsplatform;

import ch.fabianaschwanden.sourcescanner.adapter.out.vcsplatform.api.GitHubPrApi;
import ch.fabianaschwanden.sourcescanner.domain.model.FixRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.PrRef;
import ch.fabianaschwanden.sourcescanner.domain.port.out.PrCreationPort;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

/**
 * Auto-Fix-PR für GitHub (RMR-10/11/12): pusht einen Fix-Branch (nie den Basis-Branch) via JGit und
 * öffnet einen Pull Request über die GitHub-API. PR-Titel/-Body sind redigiert (vom Aufrufer geliefert).
 * Base-URL der API konfigurierbar (Cloud-Default {@code https://api.github.com}, für Tests/Enterprise
 * überschreibbar).
 */
@Singleton
public class GitHubPrAdapter implements PrCreationPort {

    private static final Pattern OWNER_REPO =
            Pattern.compile("github\\.com[:/]([^/]+)/([^/]+?)(?:\\.git)?/?$");
    /** Fallback: die letzten zwei Pfadsegmente (z. B. für Enterprise-Hosts/Tests ohne github.com). */
    private static final Pattern TRAILING_OWNER_REPO =
            Pattern.compile("([^/:]+)/([^/]+?)(?:\\.git)?/?$");

    private final String apiBaseUrl;

    public GitHubPrAdapter(
            @ConfigProperty(name = "scanner.remediation.github.api-url",
                    defaultValue = "https://api.github.com") String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    @Override
    public boolean supports(String type) {
        return "github".equalsIgnoreCase(type);
    }

    @Override
    public PrRef createFixPr(FixRequest request) {
        // 1) Fix-Branch pushen (nie Basis-Branch, RMR-11).
        FixBranchPusher.cloneBranchCommitPush(request.repoUrl(), request.baseBranch(),
                request.fixBranch(), request.commitMessage(), request.edits(), request.tokenRef());

        // 2) PR öffnen.
        OwnerRepo or = parse(request.repoUrl());
        GitHubPrApi api = RestClientBuilder.newBuilder().baseUri(URI.create(apiBaseUrl)).build(GitHubPrApi.class);
        String auth = bearer(request.tokenRef());
        GitHubPrApi.PullResponse pr = api.createPull(or.owner(), or.repo(), auth,
                new GitHubPrApi.PullRequest(
                        "Security auto-fix: " + request.fixBranch(),
                        request.fixBranch(), request.baseBranch(), request.description()));
        return new PrRef(pr.html_url(), pr.number());
    }

    private record OwnerRepo(String owner, String repo) {
    }

    private OwnerRepo parse(String repoUrl) {
        Matcher m = OWNER_REPO.matcher(repoUrl);
        if (m.find()) {
            return new OwnerRepo(m.group(1), m.group(2));
        }
        Matcher t = TRAILING_OWNER_REPO.matcher(repoUrl);
        if (t.find()) {
            return new OwnerRepo(t.group(1), t.group(2));
        }
        throw new IllegalArgumentException("cannot derive owner/repo from URL: " + repoUrl);
    }

    /** Token nur als Referenz aufgelöst (env:); leer ⇒ kein Header (anonym, scheitert ggf. an der API). */
    private String bearer(String tokenRef) {
        if (tokenRef != null && tokenRef.startsWith("env:")) {
            String v = System.getenv(tokenRef.substring("env:".length()));
            return Optional.ofNullable(v).filter(s -> !s.isBlank()).map(t -> "Bearer " + t).orElse("");
        }
        return "";
    }
}
