# Build with the JDK and Maven, then copy just this service JAR into the runtime stage.
FROM maven:3.9.16-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY course-service/pom.xml course-service/pom.xml
COPY enrollment-service/pom.xml enrollment-service/pom.xml
COPY config-server/pom.xml config-server/pom.xml
COPY config-server/src config-server/src
RUN chmod +x mvnw && ./mvnw -B -ntp -pl config-server -am package -DskipTests

# The running container only needs a JRE; build tools stay in the earlier stage.
FROM eclipse-temurin:21.0.11_10-jre-jammy@sha256:8208b3a3733529f42c4b565bfcbf16d914eb628f3ead8488b6b4d6363fcbe7cf
# curl is included for the health check, and the app runs under a nonroot account.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 uaccess && useradd --uid 10001 --gid uaccess --no-create-home uaccess
WORKDIR /app
COPY --from=build --chown=uaccess:uaccess /workspace/config-server/target/config-server-1.0.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8071
HEALTHCHECK --interval=5s --timeout=3s --start-period=45s --retries=12 CMD ["curl", "--fail", "--silent", "--max-time", "2", "http://127.0.0.1:8071/actuator/health/readiness"]
# Exec form lets Java receive the shutdown signal for graceful termination.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
