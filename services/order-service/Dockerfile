# syntax=docker/dockerfile:1.7

# ---- Build stage (runs on the build platform; the jar is platform independent) ----
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Retry transient Maven Central download failures; keep output non-quiet so errors are visible.
ENV MAVEN_ARGS="-B --no-transfer-progress -Daether.connector.http.retryHandler.count=5 -Daether.connector.requestTimeout=60000"
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q package -DskipTests \
 && java -Djarmode=layertools -jar target/order-service.jar extract --destination target/extracted

# ---- Runtime stage ----
# glibc-based JRE image for broad compatibility with agent-based instrumentation.
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER 10001:10001
EXPOSE 8080
# Container-aware heap sizing. JAVA_TOOL_OPTIONS is intentionally left untouched for agents.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
