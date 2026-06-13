package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.adapter.out.connector.api.GitHubApi;
import ch.fabianaschwanden.sourcescanner.domain.model.DiscoverySpec;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

/**
 * GitHub-Connector (IR-03): Discovery der Org-Repos über {@link GitHubApi}, Clone/History über die
 * gemeinsame {@link AbstractPlatformConnector}-Basis. Default-Base-URL {@code https://api.github.com},
 * für Enterprise per {@code baseUrl} überschreibbar.
 */
@Singleton
public class GitHubConnector extends AbstractPlatformConnector {

    private static final String DEFAULT_BASE_URL = "https://api.github.com";
    private static final int PAGE_SIZE = 100;

    public GitHubConnector(CredentialResolver credentials) {
        super(credentials);
    }

    @Override
    public boolean supports(RepositoryRef ref) {
        return "github".equalsIgnoreCase(ref.type());
    }

    @Override
    public boolean supportsDiscovery(DiscoverySpec spec) {
        return "github".equalsIgnoreCase(spec.type());
    }

    @Override
    public List<RepositoryRef> discover(DiscoverySpec spec) {
        String baseUrl = spec.baseUrl() == null || spec.baseUrl().isBlank() ? DEFAULT_BASE_URL : spec.baseUrl();
        GitHubApi api = RestClientBuilder.newBuilder().baseUri(URI.create(baseUrl)).build(GitHubApi.class);
        String authorization = bearer(credentials.resolve(spec.tokenRef()));
        Optional<Pattern> filter = RepoFilter.compile(spec.repoFilter());

        List<RepositoryRef> repos = new ArrayList<>();
        for (int page = 1; ; page++) {
            List<GitHubApi.Repo> batch = api.listOrgRepos(spec.scope(), PAGE_SIZE, page, authorization);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (GitHubApi.Repo r : batch) {
                if (r.archived() && !spec.includeArchived()) {
                    continue;
                }
                if (!RepoFilter.matches(filter, r.name())) {
                    continue;
                }
                repos.add(new RepositoryRef(r.full_name(), "github", r.clone_url(),
                        spec.branches(), spec.tokenRef()));
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }
        return repos;
    }
}
