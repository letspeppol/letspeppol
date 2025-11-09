# Multi-stage build for Spring Boot backend
# 1. Build stage with Gradle to leverage dependency caching
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy Gradle wrapper & build scripts first for caching layers
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Prepare gradle cache directories (optional but helps with permissions)
RUN chmod +x gradlew

# Download dependencies (no source yet) to leverage layer caching
RUN ./gradlew --no-daemon dependencyManagement || true
RUN ./gradlew --no-daemon build -x test || true

# Now copy the actual source and build again
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# 2. Runtime image (smaller JRE)
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Set active profile default to postgres (override if needed)
ENV SPRING_PROFILES_ACTIVE=postgres \
    JAVA_OPTS=""

# Copy jar
COPY --from=build /workspace/build/libs/app.jar app.jar

EXPOSE 8085

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
