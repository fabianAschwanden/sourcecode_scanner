package ch.fabianaschwanden.sourcescanner.adapter.out.vcsplatform.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** Minimaler GitHub-REST-Client zum Erzeugen eines Pull Requests (RMR-10). */
@RegisterRestClient(configKey = "github-pr-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitHubPrApi {

    @POST
    @Path("/repos/{owner}/{repo}/pulls")
    PullResponse createPull(@PathParam("owner") String owner,
                            @PathParam("repo") String repo,
                            @HeaderParam("Authorization") String authorization,
                            PullRequest body);

    record PullRequest(String title, String head, String base, String body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PullResponse(int number, String html_url) {
    }
}
