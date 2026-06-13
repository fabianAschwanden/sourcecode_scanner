package ch.fabianaschwanden.sourcescanner.adapter.out.connector;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.DiscoverySpec;
import ch.fabianaschwanden.sourcescanner.domain.model.RepositoryRef;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Connector-Discovery gegen einen WireMock-gestubbten GitHub-Endpoint (Plan §5). {@code @QuarkusTest},
 * damit der MicroProfile-REST-Client-Builder verfügbar ist; die Discovery zielt über
 * {@link DiscoverySpec#baseUrl()} auf den Stub.
 */
@QuarkusTest
class GitHubConnectorDiscoveryTest {

    private static WireMockServer wireMock;
    private final GitHubConnector connector = new GitHubConnector(new CredentialResolver());

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    private DiscoverySpec spec(String repoFilter, boolean includeArchived) {
        return new DiscoverySpec("github", wireMock.baseUrl(), "my-org", repoFilter, includeArchived,
                List.of("main"), null);
    }

    @Test
    void listet_org_repos_und_mappt_clone_url() {
        wireMock.stubFor(get(urlPathEqualTo("/orgs/my-org/repos")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        [
                          {"name":"svc-a","full_name":"my-org/svc-a","clone_url":"https://github.com/my-org/svc-a.git","archived":false,"default_branch":"main"},
                          {"name":"lib-b","full_name":"my-org/lib-b","clone_url":"https://github.com/my-org/lib-b.git","archived":true,"default_branch":"main"}
                        ]""")));

        List<RepositoryRef> repos = connector.discover(spec(null, false));

        // archiviertes lib-b wird ohne includeArchived ausgeschlossen
        assertEquals(1, repos.size());
        assertEquals("my-org/svc-a", repos.getFirst().id());
        assertEquals("https://github.com/my-org/svc-a.git", repos.getFirst().location());
        assertEquals("github", repos.getFirst().type());
    }

    @Test
    void repo_regex_filter_greift() {
        wireMock.stubFor(get(urlPathEqualTo("/orgs/my-org/repos")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        [
                          {"name":"svc-a","full_name":"my-org/svc-a","clone_url":"https://github.com/my-org/svc-a.git","archived":false,"default_branch":"main"},
                          {"name":"docs","full_name":"my-org/docs","clone_url":"https://github.com/my-org/docs.git","archived":false,"default_branch":"main"}
                        ]""")));

        List<RepositoryRef> repos = connector.discover(spec("^svc-", false));

        assertEquals(1, repos.size());
        assertTrue(repos.getFirst().id().endsWith("svc-a"));
    }
}
