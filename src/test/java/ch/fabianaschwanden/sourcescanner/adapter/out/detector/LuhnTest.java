package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LuhnTest {

    @Test
    void gueltige_nummern_bestehen() {
        assertTrue(Luhn.isValid("4111111111111111"));
        assertTrue(Luhn.isValid("4111 1111 1111 1111"));
        assertTrue(Luhn.isValid("5500005555555559"));
    }

    @Test
    void ungueltige_nummern_fallen_durch() {
        assertFalse(Luhn.isValid("4111111111111112"));
        assertFalse(Luhn.isValid("1234567890123456"));
    }

    @Test
    void falsche_laenge_oder_null() {
        assertFalse(Luhn.isValid("123"));
        assertFalse(Luhn.isValid(null));
        assertFalse(Luhn.isValid("abcd"));
    }
}
