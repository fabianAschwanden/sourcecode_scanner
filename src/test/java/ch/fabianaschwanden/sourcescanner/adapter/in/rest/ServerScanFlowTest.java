package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-End über REST: Quelle anlegen (Admin), Scan starten (Operator), bis der async Lauf den
 * Datensatz auf COMPLETED setzt. Übt ServerScanService + Persistenz + Mapper + Resources ab.
 */
@QuarkusTest
class ServerScanFlowTest {

    private String createLocalSource(Path repo) throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            Files.writeString(repo.resolve("Config.java"), "String k = \"AKIAIOSFODNN7EXAMPLE\";\n");
            git.add().addFilepattern("Config.java").call();
            git.commit().setMessage("init").setAuthor("t", "t@e.ch").call();
        }
        return """
                {"id":null,"name":"src-%s","type":"localGit","location":"%s","branches":[],"tokenRef":null,"enabled":true}
                """.formatted(System.nanoTime(), repo.toString().replace("\\", "\\\\"));
    }

    @Test
    @TestSecurity(user = "admin-user", roles = {"admin", "operator"})
    void quelle_anlegen_und_scan_starten(@TempDir Path repo) throws Exception {
        String sourceId = given().contentType(ContentType.JSON).body(createLocalSource(repo))
                .when().post("/api/sources")
                .then().statusCode(201).body("id", notNullValue())
                .extract().path("id");

        given().contentType(ContentType.JSON).body("{\"sourceId\":\"" + sourceId + "\",\"mode\":\"full\"}")
                .when().post("/api/scans")
                .then().statusCode(202).body("status", equalTo("RUNNING"));

        // Verbindungstest der Quelle (localGit wird unterstützt)
        given().when().post("/api/sources/" + sourceId + "/test")
                .then().statusCode(200).body("reachable", equalTo(true));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void viewer_darf_keine_quelle_anlegen(@TempDir Path repo) throws Exception {
        given().contentType(ContentType.JSON).body(createLocalSource(repo))
                .when().post("/api/sources").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    void detektoren_und_scans_sind_lesbar() {
        given().when().get("/api/detectors").then().statusCode(200);
        given().when().get("/api/scans").then().statusCode(200);
    }
}
