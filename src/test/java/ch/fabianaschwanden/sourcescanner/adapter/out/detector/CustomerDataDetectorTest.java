package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.fabianaschwanden.sourcescanner.domain.model.AttributeRule;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceDefinition;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceKind;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceSchema;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceDefinitionPort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourcePort;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DataSourceValuePort;
import ch.fabianaschwanden.sourcescanner.domain.service.ValueHashing;
import jakarta.enterprise.inject.Instance;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Wert- und Hash-Abgleich des Kundendaten-Detektors (DR-23..30): REST-Werte und Upload-Hashes. */
class CustomerDataDetectorTest {

    private final UUID dsId = UUID.randomUUID();

    private DataSourceDefinition rest(AttributeRule... rules) {
        return new DataSourceDefinition(dsId, "crm", DataSourceKind.REST, "https://crm.intern", "GET",
                "/partners", null, null, null, "$[*]", 600, 4, true, List.of(rules));
    }

    private DataSourceDefinition upload(AttributeRule... rules) {
        return new DataSourceDefinition(dsId, "crm-upload", DataSourceKind.UPLOAD, "", "GET", "",
                null, null, null, "$[*]", 600, 4, true, List.of(rules));
    }

    private ScanUnit unit(String content) {
        return new ScanUnit("repo", "src/A.java", "c1", "me", Instant.now(), content, null);
    }

    private CustomerDataDetector detector(DataSourceDefinition def, Map<AttributeRule, List<String>> restValues,
                                          Map<String, Set<String>> uploadHashes) {
        return new CustomerDataDetector(single(new FakeDefs(List.of(def))), single(new FakePort(restValues)),
                single(new FakeValues(uploadHashes)), Optional.of(""));
    }

    // --- REST -------------------------------------------------------------------------------------

    @Test
    void rest_findet_exakten_partnernummer_wert() {
        AttributeRule rule = new AttributeRule("partnernummer", true, Severity.HIGH, DetectorCategory.PII);
        var detector = detector(rest(rule), Map.of(rule, List.of("12345678")), Map.of());
        List<Finding> findings = detector.scan(
                unit("final String p = \"12345678\"; // partner"), new DetectorConfig(true, Map.of()));
        assertEquals(1, findings.size());
        assertEquals("partnernummer", findings.getFirst().ruleId());
        assertFalse(findings.getFirst().redactedMatch().contains("12345678"), "Treffer muss redigiert sein");
    }

    @Test
    void rest_teiltreffer_in_wort_wird_ignoriert() {
        AttributeRule rule = new AttributeRule("partnernummer", true, Severity.HIGH, DetectorCategory.PII);
        var detector = detector(rest(rule), Map.of(rule, List.of("12345678")), Map.of());
        assertTrue(detector.scan(unit("id = 9912345678990"), new DetectorConfig(true, Map.of())).isEmpty());
    }

    // --- UPLOAD (nur Hashes) ----------------------------------------------------------------------

    @Test
    void upload_findet_wert_per_hash_abgleich() {
        AttributeRule rule = new AttributeRule("partnernummer", true, Severity.HIGH, DetectorCategory.PII);
        Set<String> hashes = Set.of(ValueHashing.hash("12345678", ""));
        var detector = detector(upload(rule), Map.of(), Map.of("partnernummer", hashes));
        List<Finding> findings = detector.scan(
                unit("String p = \"12345678\";"), new DetectorConfig(true, Map.of()));
        assertEquals(1, findings.size());
        assertEquals("partnernummer", findings.getFirst().ruleId());
        assertFalse(findings.getFirst().redactedMatch().contains("12345678"));
    }

    @Test
    void upload_ohne_passenden_hash_kein_fund() {
        AttributeRule rule = new AttributeRule("partnernummer", true, Severity.HIGH, DetectorCategory.PII);
        Set<String> hashes = Set.of(ValueHashing.hash("99999999", ""));
        var detector = detector(upload(rule), Map.of(), Map.of("partnernummer", hashes));
        assertTrue(detector.scan(unit("String p = \"12345678\";"), new DetectorConfig(true, Map.of())).isEmpty());
    }

    @Test
    void deaktivierter_detektor_liefert_nichts() {
        AttributeRule rule = new AttributeRule("partnernummer", true, Severity.HIGH, DetectorCategory.PII);
        var detector = detector(rest(rule), Map.of(rule, List.of("12345678")), Map.of());
        assertTrue(detector.scan(unit("12345678"), DetectorConfig.disabled()).isEmpty());
    }

    // --- Fakes -----------------------------------------------------------------------------------

    private record FakeDefs(List<DataSourceDefinition> all) implements DataSourceDefinitionPort {
        @Override public DataSourceDefinition save(DataSourceDefinition d) {
            return d;
        }
        @Override public Optional<DataSourceDefinition> byId(UUID id) {
            return all.stream().filter(d -> id.equals(d.id())).findFirst();
        }
        @Override public Optional<DataSourceDefinition> byName(String name) {
            return all.stream().filter(d -> name.equals(d.name())).findFirst();
        }
        @Override public void delete(UUID id) {
        }
    }

    private record FakePort(Map<AttributeRule, List<String>> values) implements DataSourcePort {
        @Override public DataSourceSchema probe(DataSourceDefinition definition) {
            return DataSourceSchema.unreachable("test");
        }
        @Override public Map<AttributeRule, List<String>> loadValues(DataSourceDefinition definition) {
            return values;
        }
    }

    private record FakeValues(Map<String, Set<String>> hashes) implements DataSourceValuePort {
        @Override public void replace(UUID dataSourceId, Map<String, Set<String>> hashesByAttribute) {
        }
        @Override public Map<String, Set<String>> hashesByAttribute(UUID dataSourceId) {
            return hashes;
        }
        @Override public Map<String, Integer> countByAttribute(UUID dataSourceId) {
            return Map.of();
        }
        @Override public void deleteFor(UUID dataSourceId) {
        }
    }

    /** Minimale {@link Instance}, die genau eine Bean liefert (resolvable). */
    private static <T> Instance<T> single(T value) {
        return new Instance<>() {
            @Override public T get() {
                return value;
            }
            @Override public Instance<T> select(Annotation... qualifiers) {
                return this;
            }
            @Override public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
                throw new UnsupportedOperationException();
            }
            @Override public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype,
                                                              Annotation... qualifiers) {
                throw new UnsupportedOperationException();
            }
            @Override public boolean isUnsatisfied() {
                return false;
            }
            @Override public boolean isAmbiguous() {
                return false;
            }
            @Override public boolean isResolvable() {
                return true;
            }
            @Override public void destroy(T instance) {
            }
            @Override public jakarta.enterprise.inject.Instance.Handle<T> getHandle() {
                throw new UnsupportedOperationException();
            }
            @Override public Iterable<? extends jakarta.enterprise.inject.Instance.Handle<T>> handles() {
                throw new UnsupportedOperationException();
            }
            @Override public Iterator<T> iterator() {
                return List.of(value).iterator();
            }
        };
    }
}
