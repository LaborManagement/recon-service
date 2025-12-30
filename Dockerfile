# syntax=docker/dockerfile:1

FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn -B clean package spring-boot:repackage -DskipTests

FROM eclipse-temurin:17-jre-alpine AS runtime
RUN apk add --no-cache wget curl
RUN addgroup -g 1001 -S appuser && \
	adduser -u 1001 -S appuser -G appuser
ENV APP_HOME=/app \
	SPRING_PROFILES_ACTIVE=prod \
	SERVER_PORT=8080 \
	JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication" \
	TZ=UTC \
	LANG=C.UTF-8 \
	FILE_UPLOAD_DIR=/tmp/uploads \
	LOG_FILE=/tmp/logs/reconciliation-service.log
WORKDIR ${APP_HOME}
RUN mkdir -p /tmp/uploads /tmp/logs && \
	chown -R appuser:appuser /tmp/uploads /tmp/logs && \
	chown -R appuser:appuser ${APP_HOME}
COPY --from=build --chown=appuser:appuser /workspace/target/reconciliation-service-0.0.1-SNAPSHOT.jar app.jar
USER appuser
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
	CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT}/actuator/health || exit 1
EXPOSE ${SERVER_PORT}
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -Dserver.port=${SERVER_PORT} -jar app.jar"]
