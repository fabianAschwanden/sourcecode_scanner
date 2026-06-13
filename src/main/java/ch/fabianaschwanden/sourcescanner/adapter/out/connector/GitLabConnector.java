package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import ch.fabianaschwanden.sourcescanner.adapter.out.connector.api.GitLabApi;
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
 * GitLab-Connector (IR-04): Discovery der Group-Projekte über {@link GitLabApi} (inkl. Subgroups),
 * Clone/History über die gemeinsame Basis. Auth über {@code PRIVATE-TOKEN} (roher Token).
 * Default-Base-URL {@code https://gitlab.com/api/v4}, self-hosted per {@code baseUrl}.
 */
@Singleton
public class GitLabConnector extends AbstractPlatformConnector {

    private static final String DEFAULT_BASE_URL = "https://gitlab.com/api/v4";
    private static final int PAGE_SIZE = 100;

    public GitLabConnector(CredentialResolver credentials) {
        super(credentials);
    }

    @Override
    public boolean supports(RepositoryRef ref) {
        return "gitlab".equalsIgnoreCase(ref.type());
    }

    @Override
    public boolean supportsDiscovery(DiscoverySpec spec) {
        return "gitlab".equalsIgnoreCase(spec.type());
    }

    @Override
    public List<RepositoryRef> discover(DiscoverySpec spec) {
        String baseUrl = spec.baseUrl() == null || spec.baseUrl().isBlank() ? DEFAULT_BASE_URL : spec.baseUrl();
        GitLabApi api = RestClientBuilder.newBuilder().baseUri(URI.create(baseUrl)).build(GitLabApi.class);
        String privateToken = credentials.resolve(spec.tokenRef()).orElse("");
        Optional<Pattern> filter = RepoFilter.compile(spec.repoFilter());

        List<RepositoryRef> repos = new ArrayList<>();
        for (int page = 1; ; page++) {
            List<GitLabApi.Project> batch =
                    api.listGroupProjects(spec.scope(), PAGE_SIZE, page, true, privateToken);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (GitLabApi.Project p : batch) {
                if (p.archived() && !spec.includeArchived()) {
                    continue;
                }
                if (!RepoFilter.matches(filter, p.name())) {
                    continue;
                }
                repos.add(new RepositoryRef(p.pathWithNamespace(), "gitlab", p.httpUrlToRepo(),
                        spec.branches(), spec.tokenRef()));
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }
        return repos;
    }

    @Override
    protected org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider credentialsFor(String token) {
        // GitLab: HTTPS-Clone mit PAT als Passwort, Username 'oauth2'.
        return new org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider("oauth2", token);
    }
}
