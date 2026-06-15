# Multi-stage Build für Fly.io: Quarkus (JVM, fast-jar) + Angular via Quinoa.
# Stage 1 baut das Backend; Quinoa baut dabei das Frontend (braucht Node im Build).
# Stage 2 ist ein schlankes JRE-Image mit dem quarkus-app-Layout.

# --- Build ---------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build

# Node für den Quinoa-Frontend-Build (Angular). Version an webapp anpassen, falls nötig.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Maven-Wrapper + Abhängigkeiten zuerst (Layer-Caching).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline || true

# Quelle + Frontend kopieren und bauen. Quinoa baut webapp/ als Teil von package.
COPY src/ src/
COPY webapp/ webapp/
RUN ./mvnw -B -DskipTests package

# --- Runtime -------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

ENV LANG=C.UTF-8 \
    JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0" \
    QUARKUS_PROFILE=prod,staging

WORKDIR /app

# Fast-jar-Layout (quarkus-app): erst die statischen Schichten, zuletzt der App-Code.
COPY --from=build /build/target/quarkus-app/lib/ /app/lib/
COPY --from=build /build/target/quarkus-app/*.jar /app/
COPY --from=build /build/target/quarkus-app/app/ /app/app/
COPY --from=build /build/target/quarkus-app/quarkus/ /app/quarkus/

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS_APPEND -jar /app/quarkus-run.jar"]
