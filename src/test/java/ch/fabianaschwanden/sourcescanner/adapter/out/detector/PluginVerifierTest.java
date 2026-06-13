package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginVerifierTest {

    private Path jar(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    @Test
    void deaktiviert_vertraut_jedem_jar(@TempDir Path dir) throws Exception {
        Path p = jar(dir, "plugin.jar", "anything");
        assertTrue(PluginVerifier.disabled().isTrusted(p));
    }

    @Test
    void allowlist_akzeptiert_bekannten_digest_und_lehnt_manipuliert_ab(@TempDir Path dir) throws Exception {
        Path trusted = jar(dir, "trusted.jar", "trusted-content");
        String digest = PluginVerifier.digestOf(trusted);
        PluginVerifier verifier = PluginVerifier.withAllowlist(Set.of(digest));

        assertTrue(verifier.isTrusted(trusted));

        Path tampered = jar(dir, "tampered.jar", "tampered-content");
        assertFalse(verifier.isTrusted(tampered), "nicht gelistetes/manipuliertes JAR wird abgelehnt");
    }

    @Test
    void allowlist_aus_datei(@TempDir Path dir) throws Exception {
        Path trusted = jar(dir, "trusted.jar", "x");
        Path allowlist = dir.resolve("allowlist.sha256");
        Files.writeString(allowlist, "# trusted plugins\n" + PluginVerifier.digestOf(trusted) + "\n");

        PluginVerifier verifier = PluginVerifier.fromAllowlistFile(allowlist);
        assertTrue(verifier.verificationEnabled());
        assertTrue(verifier.isTrusted(trusted));
        assertFalse(verifier.isTrusted(jar(dir, "other.jar", "y")));
    }
}
