package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Secret-Verwaltung (WR-17/19): RBAC (nur Admin), Modi REFERENCE/DB_ENCRYPTED; Klartext wird nie
 * zurückgegeben (WR-19a). Vault-Write ist ohne Anbindung nicht verfügbar (409).
 */
@QuarkusTest
class SecretResourceTest {

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void nicht_admin_darf_keine_secrets_sehen_oder_anlegen() {
        given().when().get("/api/secrets").then().statusCode(403);
        given().contentType("application/json")
                .body("{\"name\":\"x\",\"mode\":\"REFERENCE\",\"reference\":\"env:X\"}")
                .when().post("/api/secrets").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin-user", roles = "admin")
    void admin_legt_referenz_secret_an() {
        given().contentType("application/json")
                .body("{\"name\":\"ref-%s\",\"mode\":\"REFERENCE\",\"reference\":\"env:GITHUB_TOKEN\"}"
                        .formatted(java.util.UUID.randomUUID()))
                .when().post("/api/secrets").then().statusCode(200)
                .body("mode", Matchers.equalTo("REFERENCE"))
                .body("reference", Matchers.equalTo("env:GITHUB_TOKEN"))
                .body("plaintext", Matchers.nullValue());
    }

    @Test
    @TestSecurity(user = "admin-user", roles = "admin")
    void admin_legt_db_verschluesseltes_secret_an_und_wert_wird_nie_zurueckgegeben() {
        given().contentType("application/json")
                .body("{\"name\":\"enc-%s\",\"mode\":\"DB_ENCRYPTED\",\"plaintext\":\"super-secret\"}"
                        .formatted(java.util.UUID.randomUUID()))
                .when().post("/api/secrets").then().statusCode(200)
                .body("mode", Matchers.equalTo("DB_ENCRYPTED"))
                .body("hasStoredValue", Matchers.equalTo(true))
                // Weder Klartext noch Chiffrat dürfen in der Antwort erscheinen:
                .body("plaintext", Matchers.nullValue())
                .body(Matchers.not(Matchers.containsString("super-secret")));
    }

    @Test
    @TestSecurity(user = "admin-user", roles = "admin")
    void vault_write_ohne_anbindung_wird_abgelehnt() {
        given().contentType("application/json")
                .body("{\"name\":\"vault-%s\",\"mode\":\"VAULT_WRITE\",\"plaintext\":\"x\"}"
                        .formatted(java.util.UUID.randomUUID()))
                .when().post("/api/secrets").then().statusCode(409);
    }
}
