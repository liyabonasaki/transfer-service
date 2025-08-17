# ===== Stage 1: Build the application =====
FROM maven:3.8.7-eclipse-temurin-17 AS build

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Package the application
RUN mvn clean package -DskipTests

# ===== Stage 2: Run the application =====
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /build/target/transfer-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# Set a default environment variable
ENV LEDGER_SERVICE_URL=http://ledger-service:8081

# Run the Spring Boot application and pass the env var into Spring
ENTRYPOINT ["java","-jar","app.jar", "--ledger.service.base-url=${LEDGER_SERVICE_URL}"]
