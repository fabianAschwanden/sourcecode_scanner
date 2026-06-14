package ch.fabianaschwanden.sourcescanner.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministisches Hashing vertraulicher Werte (NFR-23): hochgeladene Key-Value-Werte werden nur als
 * Hash persistiert, nie im Klartext. Der Detektor hasht Code-Tokens mit demselben Verfahren und
 * vergleicht gegen die gespeicherten Hashes — ein exakter Abgleich ohne Kenntnis des Klartexts.
 *
 * <p>Reine Domänen-Logik (framework-frei). SHA-256 über die UTF-8-Bytes des Werts; ein optionales
 * Pepper erschwert Rainbow-Table-Angriffe auf das Hash-Repository.
 */
public final class ValueHashing {

    private ValueHashing() {
    }

    /** Hex-SHA-256 über {@code pepper + value}; {@code pepper} darf leer sein. */
    public static String hash(String value, String pepper) {
        if (value == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (pepper != null && !pepper.isEmpty()) {
                digest.update(pepper.getBytes(StandardCharsets.UTF_8));
            }
            byte[] out = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
