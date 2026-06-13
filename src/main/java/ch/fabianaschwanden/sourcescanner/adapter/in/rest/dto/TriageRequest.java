package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

/** Anfrage zur Triage eines Fundes (WR-12). {@code reason} ist bei SUPPRESSED/FALSE_POSITIVE Pflicht. */
public record TriageRequest(String status, String reason) {
}
