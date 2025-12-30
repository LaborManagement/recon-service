# ============================================
# Dockerfile for recon-service (Multi-stage build)
# Supports: prod, staging, prod environments
# Compatible with ARM64 (Apple Silicon) and AMD64
# ============================================

# ==================== STAGE 1: Build ====================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy shared-lib first (if present) and install it
COPY shared-lib/pom.xml ./shared-lib/pom.xml
COPY shared-lib/src ./shared-lib/src

# Build and install shared-lib to local Maven repository
RUN cd shared-lib && mvn clean install -DskipTests -B -q

# Copy recon-service pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer if pom.xml unchanged)
RUN mvn dependency:go-offline -B -q || true

# Copy recon-service source code
COPY src ./src

# Build recon-service (shared-lib is now available in local Maven repo)
RUN mvn clean package spring-boot:repackage -DskipTests -B -q

# ==================== STAGE 2: Runtime ====================
FROM eclipse-temurin:17-jre

# Labels for container metadata
LABEL maintainer="LMS Team"
LABEL service="recon-service"
LABEL version="1.0"

# Update system packages to patch security vulnerabilities (CVE-2025-64720, CVE-2025-64506, CVE-2025-64505)
# Then clean up to reduce image size
RUN apt-get update && \
    apt-get upgrade -y --no-install-recommends libpng16-16t64 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash appuser

WORKDIR /app

# Create necessary directories
RUN mkdir -p /app/logs /app/config /tmp && \
    chown -R appuser:appgroup /app /tmp

# Create a volume for temporary files and logs
VOLUME ["/tmp", "/app/logs"]

# Copy the built jar from builder stage
COPY --from=builder /build/target/*recon-service-*.jar app.jar

# Change ownership of the jar
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Environment variables with defaults
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8082

# JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/heapdump.hprof \
    -Djava.security.egd=file:/prod/./urandom"

# Expose the application port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8082/actuator/health || exit 1

# Run the application
# Pass DB_URL, DB_USERNAME, DB_PASSWORD, INTERNAL_API_KEY at runtime via -e flags
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -Dserver.port=${SERVER_PORT} -jar app.jar"]

