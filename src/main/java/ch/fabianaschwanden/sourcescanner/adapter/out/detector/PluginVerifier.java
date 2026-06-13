package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verifiziert Plugin-JARs vor dem Laden (NFR-12). Standardansatz: SHA-256-Digest des JARs gegen eine
 * mitgelieferte Allowlist (eine Hex-Zeile je vertrauenswürdigem JAR) — deterministisch und ohne
 * Umgebungsabhängigkeit. Bei {@code verify=false} ist jedes JAR vertraut (Abwärtskompatibilität).
 *
 * <p>Der Pfad zur vollen JAR-Signaturprüfung (jarsigner/CodeSigner) ist in docs/11 beschrieben; die
 * Allowlist ist der KISS-Default für Phase 5.
 */
public final class PluginVerifier {

    private final boolean verify;
    private final Set<String> trustedDigests;

    private PluginVerifier(boolean verify, Set<String> trustedDigests) {
        this.verify = verify;
        this.trustedDigests = trustedDigests;
    }

    /** Verifikation aus: jedes Plugin gilt als vertrauenswürdig. */
    public static PluginVerifier disabled() {
        return new PluginVerifier(false, Set.of());
    }

    /** Verifikation an gegen die übergebenen SHA-256-Hex-Digests. */
    public static PluginVerifier withAllowlist(Set<String> trustedDigests) {
        return new PluginVerifier(true, trustedDigests.stream()
                .map(d -> d.trim().toLowerCase()).collect(Collectors.toSet()));
    }

    /** Lädt die Allowlist aus einer Datei (eine Hex-Zeile je Eintrag; {@code #}-Kommentare erlaubt). */
    public static PluginVerifier fromAllowlistFile(Path allowlist) {
        if (allowlist == null || !Files.isRegularFile(allowlist)) {
            return new PluginVerifier(true, Set.of());
        }
        try {
            Set<String> digests = Files.readAllLines(allowlist).stream()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
            return new PluginVerifier(true, digests);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read plugin allowlist " + allowlist, e);
        }
    }

    public boolean verificationEnabled() {
        return verify;
    }

    /** {@code true}, wenn das JAR geladen werden darf (Verifikation aus oder Digest in der Allowlist). */
    public boolean isTrusted(Path jar) {
        if (!verify) {
            return true;
        }
        return trustedDigests.contains(sha256(jar));
    }

    static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to hash plugin " + file, e);
        }
    }

    /** Hilfsmethode für Tooling/Doku: liefert den Allowlist-Eintrag (Digest) eines JARs. */
    public static String digestOf(Path jar) {
        return sha256(jar);
    }
}
