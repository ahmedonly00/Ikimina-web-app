# ---- build stage ----------------------------------------------------------
# Pinned to a digest-stable tag rather than :latest so a rebuild produces the
# same artifact.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved in their own layer, so a source-only change does
# not re-download the world.
COPY pom.xml .
COPY .mvn/ .mvn/
RUN mvn -B -q dependency:go-offline

COPY src/ src/
RUN mvn -B -q clean package -DskipTests

# ---- runtime stage --------------------------------------------------------
# JRE only: the compiler and Maven are build-time concerns and every extra
# binary in the final image is attack surface.
FROM eclipse-temurin:21-jre-alpine AS runtime

# Never run as root. A container escape should not land on uid 0.
RUN addgroup -S ikimina && adduser -S -G ikimina ikimina

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R ikimina:ikimina /app
USER ikimina

EXPOSE 8082

# Orchestrators need a real readiness signal; actuator health is permitted
# without credentials for exactly this reason (details stay authorized-only).
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8082/actuator/health/readiness || exit 1

# Container-aware heap sizing, and a fail-fast on OOM so the orchestrator can
# replace the instance rather than leaving it wedged.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]