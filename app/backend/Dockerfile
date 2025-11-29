# Multi-stage build for APP Spring Boot application
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# Warm dependency cache
RUN ./gradlew --no-daemon dependencyManagement || true
RUN ./gradlew --no-daemon build -x test || true

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=postgres \
    JAVA_OPTS="" \
    SERVER_PORT=8085

# Install Postgres client (includes pg_isready)
RUN apt-get update \
    && apt-get install -y postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# Add the wait script
COPY ../../postgres/wait-for-db.sh /usr/local/bin/wait-for-db.sh
RUN chmod +x /usr/local/bin/wait-for-db.sh

# Override server port via Spring variable at runtime
ENV SERVER_PORT=8085

COPY --from=build /workspace/build/libs/app.jar app.jar
EXPOSE 8085
ENTRYPOINT ["sh", "-c", "wait-for-db.sh java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar /app/app.jar"]

