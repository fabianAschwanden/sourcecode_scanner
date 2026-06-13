package ch.fabianaschwanden.sourcescanner.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA-Entity einer verwalteten Repository-Quelle. {@code tokenRef} ist nur eine Referenz (WR-32). */
@Entity
@Table(name = "repository_source")
public class RepositorySourceEntity {

    @Id
    public UUID id;

    @Column(nullable = false, unique = true)
    public String name;

    @Column(nullable = false)
    public String type;

    @Column(nullable = false)
    public String location;

    /** Komma-getrennte Branch-Liste (einfaches Schema; JSONB wäre Overkill für Phase 4-Start). */
    @Column(name = "branches")
    public String branches;

    @Column(name = "token_ref")
    public String tokenRef;

    @Column(nullable = false)
    public boolean enabled;

    /** Komma-getrennte Report-Empfänger (WR-08, IR-53); leer = keine E-Mail. */
    @Column(name = "report_emails")
    public String reportEmails;
}
