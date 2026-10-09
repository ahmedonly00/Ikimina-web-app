# ---- build ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies in their own layer, so a source-only change does not re-download them.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src/ src/
RUN mvn -B -q package -DskipTests

# ---- runtime ----------------------------------------------------------------
# JRE only: compilers and build tools are attack surface in a running image.
FROM eclipse-temurin:25-jre-alpine AS runtime

# Never run as root.
RUN addgroup -S ikimina && adduser -S -G ikimina ikimina

WORKDIR /app
COPY --from=build /build/target/ikimina-backend-*.jar app.jar
USER ikimina

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8082/actuator/health/readiness || exit 1

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
