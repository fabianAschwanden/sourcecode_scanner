package ch.fabianaschwanden.sourcescanner.domain.port.out;

import ch.fabianaschwanden.sourcescanner.domain.model.FixRequest;
import ch.fabianaschwanden.sourcescanner.domain.model.PrRef;

/**
 * Erzeugt einen Auto-Fix-PR/MR auf einer Plattform (RMR-10/11). Implementierungen schreiben
 * <b>nie</b> direkt auf den Basis-Branch — sie legen einen Fix-Branch an und öffnen einen PR/MR mit
 * Review. Beschreibung redigiert (RMR-12).
 */
public interface PrCreationPort {

    /** {@code true}, wenn dieser Adapter den Plattform-Typ bedienen kann (z. B. {@code "github"}). */
    boolean supports(String type);

    PrRef createFixPr(FixRequest request);
}
