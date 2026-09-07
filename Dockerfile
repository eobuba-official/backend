# Runtime-only image. The jar is built in CI (./gradlew clean test bootJar);
# see .github/workflows/deploy.yml. Deploy images MUST be CI-built: a locally
# built jar embeds the untracked application-local.properties (real secrets).
FROM eclipse-temurin:17-jre

# Free-tier EC2 (1 GiB): cap heap relative to the container mem_limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45"

COPY build/libs/*-SNAPSHOT.jar /app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
