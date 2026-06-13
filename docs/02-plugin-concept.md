# 02 — Plugin-Konzept (Detector-SPI)

## 1. Ziel

Neue Prüfaspekte (weitere Secret-Typen, PII-Muster, Lizenz-Checks, IaC-Regeln)
müssen **ohne Änderung am Core** ergänzbar sein. Umsetzung über Javas
`ServiceLoader` (Service Provider Interface).

## 2. Kern-Interface

```java
public interface Detector {

    /** Eindeutige, stabile ID (z. B. "secret.aws-access-key"). */
    String id();

    /** Kategorie für Gruppierung/Reporting. */
    DetectorCategory category();   // SECRET, PII, LICENSE, IAC, CUSTOM

    /** Optionaler Vorfilter zur Performance-Optimierung. */
    default boolean supports(FileType type) { return true; }

    /** Eigentliche Prüfung einer Einheit (Datei/Diff-Hunk). */
    List<Finding> scan(ScanUnit unit, DetectorConfig config);

    /** Optionale aktive Verifikation eines Treffers (z. B. Key gültig?). */
    default VerificationResult verify(Finding finding) {
        return VerificationResult.unverified();
    }
}
```

> **Verhältnis zu `DetectorPort` (Blueprint).** Dieses SPI-`Detector` ist der
> **externe Plugin-Vertrag**; es lebt im Detector-Adapter
> (`adapter/out/detector/spi`) und wird dort via `ServiceLoader` geladen. Der
> Application Service kennt nur den Domain-Port `DetectorPort`
> (`domain/port/out`). Der Detector-Adapter überführt sowohl externe
> SPI-`Detector`-Plugins als auch eingebaute CDI-Detektoren auf `DetectorPort`
> (Details: docs/09 §4). Built-in-Detektoren können `DetectorPort` direkt erfüllen.

## 3. Unterstützende Typen

```java
public enum DetectorCategory { SECRET, PII, LICENSE, IAC, CUSTOM }

public record ScanUnit(
    String repoId,
    String path,
    String commitId,
    String author,
    Instant timestamp,
    String content,        // kompletter Datei-Inhalt
    DiffHunk diffHunk      // null bei Full-File-Scan
) {}

public record Finding(
    String detectorId,
    DetectorCategory category,
    Severity severity,     // INFO, LOW, MEDIUM, HIGH, CRITICAL
    String ruleId,
    String file,
    int line,
    String redactedMatch,  // niemals Klartext-Secret
    String commitId,
    boolean verified
) {}

public record DetectorConfig(boolean enabled, Map<String,Object> params) {}
```

> **Roh- vs. aggregiertes Finding.** Das hier gezeigte `Finding` ist die **rohe
> Detektor-Ausgabe** (pre-Aggregation). Die Aggregationsschicht (docs/01 §3.4)
> reichert es zum aggregierten Finding um `id`, `firstSeen` und `lastSeen` an
> (siehe Datenmodell in docs/01 §4). Detektoren erzeugen diese Felder nicht.

## 4. Registrierung (SPI)

Jedes Plugin-JAR enthält eine Datei:

```
META-INF/services/ch.fabianaschwanden.sourcescanner.adapter.out.detector.spi.Detector
```

mit den voll qualifizierten Klassennamen seiner Detektor-Implementierungen.
Die Engine lädt sie zur Laufzeit:

```java
ServiceLoader<Detector> loaded = ServiceLoader.load(Detector.class, pluginClassLoader);
Map<String, Detector> registry = loaded.stream()
    .map(ServiceLoader.Provider::get)
    .collect(Collectors.toMap(Detector::id, Function.identity()));
```

Plugins werden aus einem konfigurierbaren Verzeichnis (`plugins/`) über einen
isolierten `URLClassLoader` geladen, sodass Abhängigkeitskonflikte mit dem Core
vermieden werden.

## 5. Lebenszyklus eines Detektors

```
discover  → instanziieren (SPI)
configure → DetectorConfig aus YAML injizieren
filter    → supports(FileType) prüfen
scan      → Findings erzeugen
verify    → optional aktive Validierung
dispose   → Ressourcen freigeben (falls Closeable)
```

## 6. Isolation & Robustheit

- Jeder `scan()`-Aufruf läuft mit **Timeout**; Überschreitung → Detektor wird für
  die Einheit übersprungen, Warnung im Report.
- Exceptions eines Detektors werden gefangen und als Degradation protokolliert,
  brechen aber nicht den Gesamtlauf.
- Optionales Ressourcen-Limit (Speicher/Regex-Backtracking-Schutz).

## 7. Beispiel: eigener Detektor

```java
public final class CustomerIdDetector implements Detector {

    private static final Pattern P = Pattern.compile("CUST-\\d{8}");

    @Override public String id() { return "pii.customer-id"; }
    @Override public DetectorCategory category() { return DetectorCategory.PII; }

    @Override
    public List<Finding> scan(ScanUnit unit, DetectorConfig cfg) {
        var findings = new ArrayList<Finding>();
        var m = P.matcher(unit.content());
        while (m.find()) {
            int line = lineOf(unit.content(), m.start());
            findings.add(new Finding(
                id(), category(), Severity.HIGH, "customer-id",
                unit.path(), line, redact(m.group()),
                unit.commitId(), false));
        }
        return findings;
    }
}
```

Registrierung in `META-INF/services/ch.fabianaschwanden.sourcescanner.adapter.out.detector.spi.Detector`:

```
ch.fabianaschwanden.sourcescanner.plugins.CustomerIdDetector
```

Aktivierung in der Konfiguration:

```yaml
scan:
  detectors:
    pii:
      enabled: true
      customRegex:
        - name: customer-id
          pattern: 'CUST-\d{8}'
          severity: HIGH
```

## 8. Built-in-Detektoren (Startumfang)

| ID | Kategorie | Zweck |
|----|-----------|-------|
| `secret.regex-ruleset` | SECRET | Gitleaks-kompatibler Regelsatz (AWS, GCP, Azure, Tokens …) |
| `secret.high-entropy` | SECRET | Hochentropische Strings (Base64/Hex) |
| `pii.patterns` | PII | IBAN, Kreditkarte, E-Mail, Telefonnummer |
| `pii.custom-regex` | PII | Frei konfigurierbare Kundendaten-Muster |
| `license.header` | LICENSE | Fehlende/abweichende Lizenz-Header |
| `iac.misconfig` | IAC | Unsichere Defaults in Terraform/K8s/Dockerfiles |

Verifikations-fähige Secret-Detektoren (aktiver Gültigkeits-Check) können optional
ergänzt werden — analog zum TruffleHog-Ansatz.

## 9. Optionale Remediation-Fähigkeit

Detektoren können zusätzlich Fix-Vorschläge liefern, indem sie das
`RemediableDetector`-Interface implementieren (Details und Governance in
docs/07-remediation.md). Die Basis-`Detector`-Fähigkeit bleibt davon unberührt —
Remediation ist rein additiv und ebenfalls plugin-erweiterbar.

```java
public interface RemediableDetector extends Detector {
    Optional<RemediationProposal> propose(Finding finding, ScanUnit unit);
}
```

Ein Detektor ohne dieses Interface ist weiterhin voll nutzbar (kein Fix-Vorschlag).
