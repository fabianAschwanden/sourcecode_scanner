package ch.fabianaschwanden.sourcescanner.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.KeyValuePair;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Auto-Erkennung CSV/JSON und Key=Attribut/Value=Wert (IR-67). */
class KeyValueUploadParserTest {

    @Test
    void csv_mit_kopfzeile() {
        List<KeyValuePair> pairs = KeyValueUploadParser.parse("key,value\npartnernummer,12345678\nname,Mustermann");
        assertEquals(2, pairs.size());
        assertEquals("partnernummer", pairs.getFirst().key());
        assertEquals("12345678", pairs.getFirst().value());
    }

    @Test
    void csv_mit_semikolon_und_quotes() {
        List<KeyValuePair> pairs = KeyValueUploadParser.parse("\"partnernummer\";\"12345678\"");
        assertEquals(1, pairs.size());
        assertEquals("12345678", pairs.getFirst().value());
    }

    @Test
    void json_array_von_key_value_objekten() {
        List<KeyValuePair> pairs = KeyValueUploadParser.parse(
                "[{\"key\":\"partnernummer\",\"value\":\"12345678\"},{\"key\":\"vorname\",\"value\":\"Max\"}]");
        assertEquals(2, pairs.size());
        assertEquals("vorname", pairs.get(1).key());
    }

    @Test
    void json_einzelnes_objekt() {
        List<KeyValuePair> pairs = KeyValueUploadParser.parse("{\"partnernummer\":\"12345678\",\"name\":\"X\"}");
        assertEquals(2, pairs.size());
    }

    @Test
    void leere_eingabe_liefert_leer() {
        assertTrue(KeyValueUploadParser.parse("").isEmpty());
        assertTrue(KeyValueUploadParser.parse("   ").isEmpty());
    }
}
