package ch.fabianaschwanden.sourcescanner.adapter.out.datasource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.AttributeRule;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceSchema;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Datenquellen-Abruf gegen WireMock: JSONPath-Extraktion, redigiertes Schema, Wert-Laden (IR-62/63). */
@QuarkusTest
class RestDataSourceAdapterTest {

    private static WireMockServer wireMock;
    private final RestDataSourceAdapter adapter = new RestDataSourceAdapter();

    @BeforeAll
    static void start() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stop() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    private DataSourceDefinition definition(AttributeRule... rules) {
        return new DataSourceDefinition(UUID.randomUUID(), "crm",
                ch.fabianaschwanden.sourcescanner.domain.model.DataSourceKind.REST, wireMock.baseUrl(), "GET",
                "/partners", null, null, null, "$.data[*]", 600, 4, true, List.of(rules));
    }

    private void stubPartners() {
        wireMock.stubFor(get(urlPathEqualTo("/partners")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data":[
                          {"partnernummer":"12345678","name":"Mustermann","vorname":"Max"},
                          {"partnernummer":"87654321","name":"Beispiel","vorname":"Erika"}
                        ]}""")));
    }

    @Test
    void probe_liefert_redigiertes_schema() {
        stubPartners();
        DataSourceSchema schema = adapter.probe(definition());
        assertTrue(schema.reachable());
        assertEquals(2, schema.sampleRecords());
        var partner = schema.attributes().stream().filter(a -> a.field().equals("partnernummer")).findFirst();
        assertTrue(partner.isPresent());
        assertFalse(partner.get().maskedExample().contains("12345678"), "Beispiel muss maskiert sein");
        assertTrue(partner.get().maskedExample().startsWith("12"));
    }

    @Test
    void load_values_extrahiert_nur_gepruefte_attribute() {
        stubPartners();
        AttributeRule partner = new AttributeRule("partnernummer", true, Severity.HIGH, DetectorCategory.PII);
        AttributeRule vorname = new AttributeRule("vorname", false, Severity.LOW, DetectorCategory.PII);
        Map<AttributeRule, List<String>> values = adapter.loadValues(definition(partner, vorname));
        assertTrue(values.containsKey(partner));
        assertFalse(values.containsKey(vorname), "ungeprüftes Attribut wird nicht geladen");
        assertEquals(List.of("12345678", "87654321"), values.get(partner));
    }

    @Test
    void nicht_erreichbar_meldet_unreachable() {
        // kein Stub registriert ⇒ 404/Fehler ⇒ unreachable statt Exception
        DataSourceSchema schema = adapter.probe(definition());
        assertFalse(schema.reachable());
    }
}
