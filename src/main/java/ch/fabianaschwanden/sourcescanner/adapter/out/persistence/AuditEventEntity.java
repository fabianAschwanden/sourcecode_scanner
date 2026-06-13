package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA-Entity eines Audit-Eintrags (WR-34). */
@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    @GeneratedValue
    public Long id;

    @Column(nullable = false)
    public String actor;

    @Column(nullable = false)
    public String action;

    @Column(name = "target")
    public String target;

    @Column(name = "detail")
    public String detail;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;
}
