# CLI-/CI-Image des Source Code Scanners (für Build-Gates in fremden Pipelines, docs/08).
# Dasselbe Fast-Jar wie der Server, aber der Entrypoint reicht Argumente an den Scan-Command durch:
#   docker run --rm -v "$PWD:/work" -w /work ghcr.io/<owner>/sourcecode-scanner-cli \
#     --config .scanner.yaml --output sarif
# Das Jar IST bereits der scan-Command (kein führendes "scan"); die Argumente sind seine Optionen.
# Ohne Argumente liefe es im Server-Modus (ScannerMain) — im CI immer mit Optionen aufrufen.

# --- Build -------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
# Node nur für den Build: Quinoa baut beim package-Goal die Angular-UI (auch wenn das CLI-Image sie
# nicht ausliefert) — ohne npm bricht der Build. Das schlanke JRE-Runtime-Image enthält kein Node.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline || true
COPY src/ src/
COPY webapp/ webapp/
COPY config/ config/
RUN ./mvnw -B -DskipTests package

# --- Runtime -----------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
ENV LANG=C.UTF-8
# git im Runtime-Image: In einem GitHub-Actions-Container-Job erkennt actions/checkout sonst kein
# git und lädt das Repo als Tarball OHNE .git -> der localGit-Scan findet kein Repository
# ("no git repository found"). Mit git macht checkout einen echten Klon inkl. .git.
RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/quarkus-app/lib/ /app/lib/
COPY --from=build /build/target/quarkus-app/*.jar /app/
COPY --from=build /build/target/quarkus-app/app/ /app/app/
COPY --from=build /build/target/quarkus-app/quarkus/ /app/quarkus/
COPY --from=build /build/config/ /app/config/

# Standard-Arbeitsverzeichnis für das gescannte Repo (per -v/-w überschreibbar).
WORKDIR /work

# CLI-Modus: alle Container-Argumente gehen an den Scan-Command (Exit-Code-Vertrag, docs/08 §7).
# Default-Profil prod (kein OIDC/DB) — der CLI-Lauf braucht weder IdP noch Datenbank.
ENV QUARKUS_PROFILE=prod
# PII-Konfigdateien liegen im Image unter /app/config; absolut referenzieren, da das CWD beim CLI-Lauf
# das gescannte Repo (/work) ist — sonst „file not found"-Warnungen und nur die eingebauten Defaults.
ENV PII_ALLOWLIST_FILE=/app/config/pii-allowlist.yaml \
    PII_TEST_EMAILS_FILE=/app/config/pii-test-emails.yaml
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
