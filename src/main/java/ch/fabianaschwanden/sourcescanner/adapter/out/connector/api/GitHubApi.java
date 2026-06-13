package ch.fabianaschwanden.sourcescanner.adapter.out.connector.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Minimaler GitHub-REST-Client (Cloud/Enterprise) für die Repo-Discovery einer Organisation
 * (IR-03). Base-URL kommt zur Laufzeit aus der {@code baseUrl} der Discovery-Quelle.
 */
@RegisterRestClient(configKey = "github-api")
@Produces(MediaType.APPLICATION_JSON)
public interface GitHubApi {

    @GET
    @Path("/orgs/{org}/repos")
    List<Repo> listOrgRepos(@PathParam("org") String org,
                            @QueryParam("per_page") int perPage,
                            @QueryParam("page") int page,
                            @HeaderParam("Authorization") String authorization);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Repo(String name, String full_name, String clone_url, boolean archived, String default_branch) {
    }
}
