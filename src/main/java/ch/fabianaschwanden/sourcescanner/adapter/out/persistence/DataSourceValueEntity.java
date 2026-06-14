package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Persistierter <b>Hash</b> eines hochgeladenen Werts (IR-67, NFR-23) — nie der Klartext. Pro
 * (Datenquelle, Attribut) liegen viele Hashes; der Detektor vergleicht Code-Token-Hashes dagegen.
 */
@Entity
@Table(name = "data_source_value", indexes = {
        @Index(name = "idx_dsvalue_source_attr", columnList = "data_source_id, attribute")
})
public class DataSourceValueEntity {

    @Id
    public UUID id;

    @Column(name = "data_source_id", nullable = false)
    public UUID dataSourceId;

    @Column(nullable = false)
    public String attribute;

    /** Hex-SHA-256 des Werts (mit Pepper); nie der Klartext (NFR-23). */
    @Column(name = "value_hash", nullable = false)
    public String valueHash;
}
