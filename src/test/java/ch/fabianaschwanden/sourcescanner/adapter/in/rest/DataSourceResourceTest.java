package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

/** REST + RBAC der Datenquellen-Endpunkte (NFR-24): Lesen ab Viewer, Pflege/Probe ab Operator. */
@QuarkusTest
class DataSourceResourceTest {

    private static final String BODY = """
            {"name":"crm-%s","baseUrl":"https://crm.intern","method":"GET","path":"/partners",
             "authType":"NONE","recordsPath":"$[*]","cacheTtlSeconds":600,"minValueLength":4,
             "enabled":true,"attributes":[{"field":"partnernummer","check":true,"severity":"HIGH","category":"PII"}]}""";

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_lesen_aber_nicht_pflegen() {
        given().when().get("/api/datasources").then().statusCode(200);
        given().contentType("application/json").body(String.format(BODY, "v"))
                .when().post("/api/datasources").then().statusCode(403);
        given().contentType("application/json").body(String.format(BODY, "v"))
                .when().post("/api/datasources/probe").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "op-user", roles = "operator")
    void operator_kann_anlegen_und_listet_redigiert() {
        given().contentType("application/json").body(String.format(BODY, "op"))
                .when().post("/api/datasources").then().statusCode(200)
                .body("name", org.hamcrest.Matchers.equalTo("crm-op"))
                .body("attributes[0].field", org.hamcrest.Matchers.equalTo("partnernummer"));
    }
}
