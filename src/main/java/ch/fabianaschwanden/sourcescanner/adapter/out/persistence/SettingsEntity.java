package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import ch.fabianaschwanden.sourcescanner.domain.model.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA-Entity der systemweiten Einstellungen — genau eine Zeile mit fixer ID (Singleton). */
@Entity
@Table(name = "settings")
public class SettingsEntity {

    /** Fixe Singleton-ID; es existiert höchstens eine Zeile. */
    public static final long SINGLETON_ID = 1L;

    @Id
    public Long id;

    @Column(name = "general_notification_email")
    public String generalNotificationEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_fail_on", nullable = false)
    public Severity defaultFailOn;

    @Column(name = "default_scan_mode", nullable = false)
    public String defaultScanMode;

    @Column(name = "retention_days", nullable = false)
    public int retentionDays;

    /** Komma-getrennte Secret-Referenzen (z. B. {@code env:GITHUB_TOKEN}); nur Referenzen (WR-17). */
    @Column(name = "secret_refs")
    public String secretRefs;
}
