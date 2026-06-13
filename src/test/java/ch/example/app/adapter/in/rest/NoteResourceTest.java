package ch.example.app.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/** Integrationstest gegen Dev Services (PostgreSQL-Container, Blueprint §10). */
@QuarkusTest
class NoteResourceTest {

    @Test
    void legt_note_an_und_liest_sie_wieder() {
        String id = given()
                .contentType(JSON)
                .body(Map.of("title", "Erste Note", "body", "Hallo"))
                .when().post("/api/notes")
                .then().statusCode(201)
                .header("Location", containsString("/api/notes/"))
                .body("id", notNullValue())
                .extract().path("id");

        given()
                .when().get("/api/notes/{id}", id)
                .then().statusCode(200)
                .body("title", is("Erste Note"));

        given()
                .when().get("/api/notes")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void weist_leeren_titel_mit_400_zurueck() {
        given()
                .contentType(JSON)
                .body(Map.of("title", " "))
                .when().post("/api/notes")
                .then().statusCode(400);
    }

    @Test
    void unbekannte_id_liefert_404() {
        given()
                .when().get("/api/notes/{id}", "00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }
}
