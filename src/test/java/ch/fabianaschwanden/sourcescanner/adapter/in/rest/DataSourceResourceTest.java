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

    @Test
    @TestSecurity(user = "op-user", roles = "operator")
    void operator_kann_key_value_liste_hochladen_und_erhaelt_nur_hash_anzahl() {
        // 1) UPLOAD-Datenquelle anlegen.
        String id = given().contentType("application/json")
                .body("""
                        {"name":"upload-%s","kind":"UPLOAD","authType":"NONE","recordsPath":"$[*]",
                         "cacheTtlSeconds":600,"minValueLength":4,"enabled":true,"attributes":[]}"""
                        .formatted(java.util.UUID.randomUUID()))
                .when().post("/api/datasources").then().statusCode(200)
                .extract().path("id");

        // 2) CSV hochladen — Antwort enthält nur Anzahl Hashes je Attribut, nie Werte.
        given().contentType("text/plain")
                .body("key,value\npartnernummer,12345678\npartnernummer,87654321\nname,Mustermann")
                .when().post("/api/datasources/" + id + "/upload").then().statusCode(200)
                .body("partnernummer", org.hamcrest.Matchers.equalTo(2))
                .body("name", org.hamcrest.Matchers.equalTo(1));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_nicht_hochladen() {
        given().contentType("text/plain").body("partnernummer,12345678")
                .when().post("/api/datasources/" + java.util.UUID.randomUUID() + "/upload")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_nicht_bulk_loeschen() {
        given().contentType("application/json").body("{\"ids\":[]}")
                .when().post("/api/datasources/bulk/delete").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "op-user", roles = "operator")
    void operator_bulk_loescht_mehrere() {
        String id1 = given().contentType("application/json").body(String.format(BODY, java.util.UUID.randomUUID()))
                .when().post("/api/datasources").then().statusCode(200).extract().path("id");
        String id2 = given().contentType("application/json").body(String.format(BODY, java.util.UUID.randomUUID()))
                .when().post("/api/datasources").then().statusCode(200).extract().path("id");
        given().contentType("application/json")
                .body("{\"ids\":[\"%s\",\"%s\"]}".formatted(id1, id2))
                .when().post("/api/datasources/bulk/delete").then().statusCode(200)
                .body("total", org.hamcrest.Matchers.equalTo(2))
                .body("succeeded", org.hamcrest.Matchers.equalTo(2));
    }
}
