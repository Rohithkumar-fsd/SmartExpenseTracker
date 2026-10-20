# Multi-stage build for lightweight production Docker image
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
# Fallback if application.properties was gitignored for security
RUN if [ ! -f src/main/resources/application.properties ] && [ -f src/main/resources/application.properties.example ]; then \
      cp src/main/resources/application.properties.example src/main/resources/application.properties; \
    fi
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Environment variables with sensible defaults
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
