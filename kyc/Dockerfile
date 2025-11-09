# Multi-stage build for KYC Spring Boot application
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# Warm dependency cache
RUN ./gradlew --no-daemon build -x test || true

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=postgres \
    JAVA_OPTS="" \
    SERVER_PORT=8084

# Override server port via Spring variable at runtime
ENV SERVER_PORT=8084

COPY --from=build /workspace/build/libs/kyc.jar app.jar
EXPOSE 8084
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar /app/app.jar"]
