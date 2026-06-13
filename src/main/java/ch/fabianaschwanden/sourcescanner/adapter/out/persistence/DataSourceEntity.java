package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceAuthType;
import ch.fabianaschwanden.sourcescanner.domain.model.DataSourceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA-Entity einer externen Datenquelle (IR-60). {@code tokenRef} ist nur eine Referenz (NFR-08);
 * Antwortdaten/Werte werden NIE persistiert (NFR-23) — nur die Definition + Attribut-Mapping.
 * Das Mapping liegt als JSON-Spalte ({@code attributes_json}), um ein Kind-Entity zu sparen.
 */
@Entity
@Table(name = "data_source")
public class DataSourceEntity {

    @Id
    public UUID id;

    @Column(nullable = false, unique = true)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    public DataSourceKind kind;

    @Column(name = "base_url", nullable = false)
    public String baseUrl;

    @Column(nullable = false)
    public String method;

    @Column
    public String path;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false)
    public DataSourceAuthType authType;

    @Column(name = "token_ref")
    public String tokenRef;

    @Column(name = "auth_header_name")
    public String authHeaderName;

    @Column(name = "records_path", nullable = false)
    public String recordsPath;

    @Column(name = "cache_ttl_seconds", nullable = false)
    public int cacheTtlSeconds;

    @Column(name = "min_value_length", nullable = false)
    public int minValueLength;

    @Column(nullable = false)
    public boolean enabled;

    /** Attribut-Mapping als JSON-Array {@code [{field,check,severity,category}]} (WR-52). */
    @Column(name = "attributes_json", length = 8192)
    public String attributesJson;
}
