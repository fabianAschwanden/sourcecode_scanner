package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.adapter.out.connector.api.BitbucketApi;
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
 * Bitbucket-Cloud-Connector (IR-02): Discovery der Repos eines Workspace über {@link BitbucketApi}
 * (paginiert über {@code page}/{@code pagelen}), Clone/History über die gemeinsame Basis.
 * Default-Base-URL {@code https://api.bitbucket.org/2.0}, Data Center per {@code baseUrl}.
 */
@Singleton
public class BitbucketConnector extends AbstractPlatformConnector {

    private static final String DEFAULT_BASE_URL = "https://api.bitbucket.org/2.0";
    private static final int PAGE_SIZE = 100;

    public BitbucketConnector(CredentialResolver credentials) {
        super(credentials);
    }

    @Override
    public boolean supports(RepositoryRef ref) {
        return "bitbucket".equalsIgnoreCase(ref.type());
    }

    @Override
    public boolean supportsDiscovery(DiscoverySpec spec) {
        return "bitbucket".equalsIgnoreCase(spec.type());
    }

    @Override
    public List<RepositoryRef> discover(DiscoverySpec spec) {
        String baseUrl = spec.baseUrl() == null || spec.baseUrl().isBlank() ? DEFAULT_BASE_URL : spec.baseUrl();
        BitbucketApi api = RestClientBuilder.newBuilder().baseUri(URI.create(baseUrl)).build(BitbucketApi.class);
        String authorization = bearer(credentials.resolve(spec.tokenRef()));
        Optional<Pattern> filter = RepoFilter.compile(spec.repoFilter());

        List<RepositoryRef> repos = new ArrayList<>();
        for (int page = 1; ; page++) {
            BitbucketApi.Page result = api.listWorkspaceRepos(spec.scope(), page, PAGE_SIZE, authorization);
            if (result == null || result.values() == null || result.values().isEmpty()) {
                break;
            }
            for (BitbucketApi.Repo r : result.values()) {
                if (!RepoFilter.matches(filter, r.name())) {
                    continue;
                }
                httpsCloneUrl(r).ifPresent(url ->
                        repos.add(new RepositoryRef(r.full_name(), "bitbucket", url,
                                spec.branches(), spec.tokenRef())));
            }
            if (result.next() == null || result.next().isBlank()) {
                break;
            }
        }
        return repos;
    }

    private Optional<String> httpsCloneUrl(BitbucketApi.Repo repo) {
        if (repo.links() == null || repo.links().cloneLinks() == null) {
            return Optional.empty();
        }
        return repo.links().cloneLinks().stream()
                .filter(c -> "https".equalsIgnoreCase(c.name()))
                .map(BitbucketApi.Clone::href)
                .findFirst();
    }
}
