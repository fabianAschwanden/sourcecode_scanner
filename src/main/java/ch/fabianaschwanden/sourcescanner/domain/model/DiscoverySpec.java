package ch.fabianaschwanden.sourcescanner.domain.model;

import java.util.List;

/**
 * Beschreibt eine org-weite Repository-Discovery (FR-07, docs/03 §2): eine Plattform-Quelle, deren
 * Repos zur Laufzeit aufgelöst werden. {@code scope} ist je Plattform die Org/Group/das Projekt.
 * {@code repoFilter} ist ein optionaler Namens-Regex (IR-06), {@code tokenRef} die Secret-Referenz.
 */
public record DiscoverySpec(
        String type,
        String baseUrl,
        String scope,
        String repoFilter,
        boolean includeArchived,
        List<String> branches,
        String tokenRef) {

    public DiscoverySpec {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("discovery type must not be blank");
        }
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("discovery scope (org/group/project) must not be blank");
        }
        branches = branches == null ? List.of() : List.copyOf(branches);
    }
}
