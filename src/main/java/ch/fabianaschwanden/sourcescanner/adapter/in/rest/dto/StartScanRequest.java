package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import java.util.UUID;

/** Anfrage zum Starten eines Scans (WR-03). */
public record StartScanRequest(UUID sourceId, String mode) {
}
