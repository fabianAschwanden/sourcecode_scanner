# 11 — Plugin-Paketformat & Signaturprüfung (NFR-12)

Externe Detektoren werden als JAR-Plugins über den `ServiceLoader`-Pfad geladen (docs/02 §4). Phase 5
ergänzt **Vertrauensprüfung vor dem Laden** und beschreibt das Paketformat — die Grundlage einer
„Plugin-Marketplace"-Mechanik (signierte, versionierte JARs), ohne einen eigenen Store-Service.

## Paket

Ein Plugin-JAR enthält:

- die Detektor-Klassen (implementieren den SPI-`Detector`, docs/02 §2),
- `META-INF/services/ch.fabianaschwanden.sourcescanner.adapter.out.detector.spi.Detector`
  mit den voll qualifizierten Klassennamen,
- ein Manifest mit `id`, `version`, `category` (Konvention; für Anzeige/Marketplace).

## Vertrauensprüfung (`PluginVerifier`)

Vor dem Laden prüft der `SpiDetectorRegistry` jedes JAR über den `PluginVerifier`:

- **Default-Mechanismus (KISS, Phase 5):** SHA-256-**Allowlist**. Jeder vertrauenswürdige JAR-Digest
  steht (eine Hex-Zeile) in einer Allowlist-Datei. Nicht gelistete JARs werden **übersprungen** und
  geloggt (kein Lauf-Abbruch). Digest eines JARs ermitteln: `PluginVerifier.digestOf(path)`.
- **Aus (Abwärtskompatibilität):** ohne aktivierte Verifikation gilt jedes JAR als vertrauenswürdig.
- **Voller Pfad (dokumentiert, optional):** echte JAR-Signaturprüfung via `jarsigner`/`CodeSigner`
  gegen einen Trust-Store. Umgebungsabhängiger; die Allowlist ist der robuste, deterministisch
  testbare Default.

## Aktivierung (Server-Config)

```yaml
plugins:
  dir: ./plugins
  verify: true                 # Default false
  allowlist: ./plugins/allowlist.sha256
```

Das Plugin-Verzeichnis wird über `SpiDetectorRegistry.loadFrom(dir, verifier)` geladen. Geladene
Plugins erscheinen read-only in der UI/`/api/detectors` (ID + Kategorie, WR-07).
