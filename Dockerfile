# syntax=docker/dockerfile:1.7

# ---------- Build stage: JDK 21 + Maven Wrapper ----------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Resolve dependencies first so they are cached independently of source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    bash ./mvnw -B -ntp dependency:go-offline

# FHIR NPM packages (NZ Base, IPS, ...) are downloaded and SHA-256 verified during the build and
# bundled inside the jar, so the runtime image validates fully offline and never re-downloads them.
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    bash ./mvnw -B -ntp package -DskipTests \
 && cp target/nz-patient-summary-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---------- Runtime stage: slim JRE 21, non-root -----------------------------------------------
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="nz-patient-summary" \
      org.opencontainers.image.description="Generates and validates New Zealand Patient Summary (NZPS/IPS) FHIR R4 Bundles"

RUN addgroup -S app && adduser -S -G app -h /app app
WORKDIR /app

# Spring Boot layers, least to most frequently changing, for better image layer caching.
COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
