# Multi-stage build for Orbtl Relay
FROM maven:3.8.6-amazoncorretto-17 AS builder

WORKDIR /app

# Copy Maven files
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the JAR
RUN mvn clean package -DskipTests

# Runtime stage
FROM amazoncorretto:17-alpine

# Create non-root user
RUN addgroup -g 1000 spring && adduser -u 1000 -G spring -s /bin/sh -D spring

WORKDIR /app

# Copy the JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to spring user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring

# Expose ports
EXPOSE 8091 9090

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8091/actuator/health || exit 1

# Set Spring profile from build arg
ARG SPRING_PROFILES_ACTIVE=default
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}

# Run the application
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-jar", "app.jar"]