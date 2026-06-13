package ch.fabianaschwanden.sourcescanner.adapter.out.detector;

import ch.fabianaschwanden.sourcescanner.adapter.out.detector.spi.Detector;
import ch.fabianaschwanden.sourcescanner.domain.port.out.DetectorPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Lädt externe Detektor-Plugins aus einem Verzeichnis (docs/02 §4): jedes {@code *.jar} wird über
 * einen isolierten {@link URLClassLoader} eingebunden, seine {@link Detector}-Services via
 * {@link ServiceLoader} entdeckt und auf {@link DetectorPort} abgebildet.
 *
 * <p>Built-in-Detektoren laufen über CDI (z. B. {@link RegexRulesetDetector}) und werden hier
 * nicht geladen — beide Pfade erfüllen denselben Port (docs/09 §4).
 */
@ApplicationScoped
public class SpiDetectorRegistry {

    private static final Logger LOG = Logger.getLogger(SpiDetectorRegistry.class);

    /** Lädt alle Plugin-Detektoren aus {@code pluginDir}; ein leeres/fehlendes Verzeichnis liefert nichts. */
    public List<DetectorPort> loadFrom(Path pluginDir) {
        if (pluginDir == null || !Files.isDirectory(pluginDir)) {
            return List.of();
        }
        List<URL> jars = new ArrayList<>();
        try (Stream<Path> entries = Files.list(pluginDir)) {
            entries.filter(p -> p.toString().endsWith(".jar")).forEach(p -> {
                try {
                    jars.add(p.toUri().toURL());
                } catch (MalformedURLException e) {
                    LOG.warnf("skipping plugin %s: %s", p, e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warnf("failed to list plugin directory %s: %s", pluginDir, e.getMessage());
            return List.of();
        }
        if (jars.isEmpty()) {
            return List.of();
        }
        URLClassLoader pluginLoader = new URLClassLoader(
                jars.toArray(URL[]::new), getClass().getClassLoader());
        List<DetectorPort> loaded = new ArrayList<>();
        for (Detector detector : ServiceLoader.load(Detector.class, pluginLoader)) {
            LOG.infof("loaded plugin detector %s (%s)", detector.id(), detector.category());
            loaded.add(new SpiDetectorAdapter(detector));
        }
        return List.copyOf(loaded);
    }
}
