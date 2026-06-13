package ch.fabianaschwanden.sourcescanner.adapter.out.vcsplatform;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.FileEdit;
import ch.fabianaschwanden.sourcescanner.domain.model.FixRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.PrRef;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Auto-Fix-PR gegen einen WireMock-gestubbten GitHub-Endpoint (Plan §5). Der Push geht auf ein lokales
 * Bare-Repo (kein realer Remote), die PR-Erstellung gegen den Stub. Geprüft: Fix-Branch wird gepusht
 * (nie der Basis-Branch, RMR-11), PR-Body trägt keinen Klartext (RMR-12).
 */
@QuarkusTest
class GitHubPrAdapterTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    /** Erzeugt ein Bare-Repo mit einem main-Branch und einer Datei, die der Fix annotiert. */
    private Path seedBareRepo() throws Exception {
        Path tmp = Files.createTempDirectory("scanner-pr-test-");
        Path work = tmp.resolve("work");
        Path bare = tmp.resolve("o/r.git");
        Files.createDirectories(bare);
        Git.init().setBare(true).setInitialBranch("main").setDirectory(bare.toFile()).call();
        try (Git git = Git.init().setInitialBranch("main").setDirectory(work.toFile()).call()) {
            Files.writeString(work.resolve("A.java"), "line1\nString k = \"x\";\nline3\n", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor("t", "t@example.com").call();
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish(bare.toUri().toString())).call();
            git.push().setRemote("origin")
                    .setRefSpecs(new org.eclipse.jgit.transport.RefSpec("refs/heads/main:refs/heads/main")).call();
        }
        return bare;
    }

    @Test
    void erstellt_fix_branch_und_pr_ohne_klartext() throws Exception {
        Path bare = seedBareRepo();
        wireMock.stubFor(post(urlPathEqualTo("/repos/o/r/pulls")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"number\":42,\"html_url\":\"https://github.com/o/r/pull/42\"}")));

        GitHubPrAdapter adapter = new GitHubPrAdapter(wireMock.baseUrl());
        FileEdit edit = new FileEdit("A.java", 2, 2,
                "<<APPEND>> // scanner:ignore-secret reason=\"rotate then remove\"");
        FixRequest request = new FixRequest(bare.toUri().toString(), "main", "fix/scanner-1",
                "chore(security): auto-fix aws", List.of(edit),
                "Automatischer Sicherheits-Fix für aws in A.java:2.", List.of(), List.of(), null);

        PrRef pr = adapter.createFixPr(request);

        assertEquals(42, pr.number());
        // Fix-Branch wurde gepusht; main blieb unverändert (RMR-11).
        try (Git pushed = Git.open(bare.toFile())) {
            boolean hasFix = pushed.branchList().call().stream()
                    .anyMatch(ref -> ref.getName().endsWith("fix/scanner-1"));
            assertTrue(hasFix, "Fix-Branch muss gepusht sein");
        }
        // PR-Body trägt keinen Klartext-Token.
        wireMock.verify(postRequestedFor(urlPathEqualTo("/repos/o/r/pulls")));
        var events = wireMock.getAllServeEvents();
        String body = events.getFirst().getRequest().getBodyAsString();
        assertTrue(body.contains("\"base\":\"main\""));
        assertFalse(body.contains("\"x\""), "PR-Body darf den Original-Inhalt nicht tragen");
    }
}
