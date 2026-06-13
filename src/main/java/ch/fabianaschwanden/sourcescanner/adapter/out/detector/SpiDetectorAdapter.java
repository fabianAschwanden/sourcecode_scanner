package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.adapter.out.detector.spi.Detector;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorCategory;
import ch.fabianaschwanden.sourcescanner.domain.model.DetectorConfig;
import ch.fabianaschwanden.sourcescanner.domain.model.FileType;
import ch.fabianaschwanden.sourcescanner.domain.model.Finding;
import ch.fabianaschwanden.sourcescanner.domain.model.ScanUnit;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import java.util.List;

/** Bildet einen extern geladenen SPI-{@link Detector} auf den Domain-{@link DetectorPort} ab. */
final class SpiDetectorAdapter implements DetectorPort {

    private final Detector delegate;

    SpiDetectorAdapter(Detector delegate) {
        this.delegate = delegate;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public DetectorCategory category() {
        return delegate.category();
    }

    @Override
    public boolean supports(FileType type) {
        return delegate.supports(type);
    }

    @Override
    public List<Finding> scan(ScanUnit unit, DetectorConfig config) {
        return delegate.scan(unit, config);
    }
}
