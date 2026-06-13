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
 * Minimaler GitLab-REST-Client (SaaS/self-hosted) für die Repo-Discovery einer Group (IR-04).
 * Auth über {@code PRIVATE-TOKEN}-Header. Base-URL inkl. {@code /api/v4} kommt aus der Quelle.
 */
@RegisterRestClient(configKey = "gitlab-api")
@Produces(MediaType.APPLICATION_JSON)
public interface GitLabApi {

    @GET
    @Path("/groups/{group}/projects")
    List<Project> listGroupProjects(@PathParam("group") String group,
                                    @QueryParam("per_page") int perPage,
                                    @QueryParam("page") int page,
                                    @QueryParam("include_subgroups") boolean includeSubgroups,
                                    @HeaderParam("PRIVATE-TOKEN") String privateToken);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Project(String name,
                   @JsonProperty("path_with_namespace") String pathWithNamespace,
                   @JsonProperty("http_url_to_repo") String httpUrlToRepo,
                   boolean archived,
                   @JsonProperty("default_branch") String defaultBranch) {
    }
}
