package ch.fabianaschwanden.sourcescanner.adapter.in.rest.dto;

import ch.fabianaschwanden.sourcescanner.domain.model.PrRef;

/** REST-Transport eines erstellten Auto-Fix-PR/MR (RMR-10). */
public record PrRefDto(String url, int number) {

    public static PrRefDto from(PrRef ref) {
        return new PrRefDto(ref.url(), ref.number());
    }
}
