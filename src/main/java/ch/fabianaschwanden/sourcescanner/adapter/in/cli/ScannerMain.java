package ch.fabianaschwanden.sourcescanner.adapter.in.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Einstiegspunkt mit zwei Betriebsmodi (docs/09 §9):
 *
 * <ul>
 *   <li><b>CLI-/CI-Modus</b> — mit Argumenten (z. B. {@code --config scanner.yaml}): führt den
 *       Picocli-{@link ScanCommand} aus und beendet sich mit dessen Exit-Code (Exit-Code-Vertrag).</li>
 *   <li><b>Server-Modus</b> — ohne Argumente: hält die Anwendung als langlaufenden HTTP-Service oben
 *       (REST + Web-UI), statt den Scan-Command auszuführen und sofort zu terminieren.</li>
 * </ul>
 */
@QuarkusMain
public class ScannerMain implements QuarkusApplication {

    private final ScanCommand scanCommand;
    private final IFactory factory;

    @Inject
    public ScannerMain(@TopCommand ScanCommand scanCommand, IFactory factory) {
        this.scanCommand = scanCommand;
        this.factory = factory;
    }

    @Override
    public int run(String... args) {
        if (args.length == 0) {
            // Server-Modus: HTTP-Service offen halten, bis er gestoppt wird.
            Quarkus.waitForExit();
            return 0;
        }
        return new CommandLine(scanCommand, factory).execute(args);
    }
}
