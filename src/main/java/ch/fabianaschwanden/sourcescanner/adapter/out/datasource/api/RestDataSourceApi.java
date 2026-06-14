package ch.fabianaschwanden.sourcescanner.adapter.out.datasource.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Generischer REST-Client für eine externe Datenquelle (IR-60): ein GET auf die vollständige,
 * vom Adapter zusammengesetzte URI (Base-URL + Pfad + Query landen in der {@code baseUri} des
 * {@code RestClientBuilder}). Der rohe JSON-Baum wird zurückgegeben (Schema unbekannt → {@link JsonNode}).
 * Auth-Header wird zur Laufzeit gesetzt.
 */
@RegisterRestClient(configKey = "data-source-api")
@Produces(MediaType.APPLICATION_JSON)
public interface RestDataSourceApi {

    @GET
    JsonNode fetch(@HeaderParam("Authorization") String authorization);
}
