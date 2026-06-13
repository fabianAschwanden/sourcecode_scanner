package ch.fabianaschwanden.sourcescanner.adapter.out.connector.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * Minimaler Bitbucket-Cloud-REST-Client (v2.0) für die Repo-Discovery eines Workspace/Projekts
 * (IR-02). Antwort ist paginiert ({@code values}/{@code next}). Auth über Bearer-Header.
 */
@RegisterRestClient(configKey = "bitbucket-api")
@Produces(MediaType.APPLICATION_JSON)
public interface BitbucketApi {

    @GET
    @Path("/repositories/{workspace}")
    Page listWorkspaceRepos(@PathParam("workspace") String workspace,
                            @QueryParam("page") int page,
                            @QueryParam("pagelen") int pageLen,
                            @HeaderParam("Authorization") String authorization);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page(List<Repo> values, String next) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Repo(String name, String full_name, Links links, String mainbranch) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Links(@JsonProperty("clone") List<Clone> cloneLinks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Clone(String name, String href) {
    }
}
