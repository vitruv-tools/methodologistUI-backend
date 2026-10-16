FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user + writable logs
RUN addgroup -S spring \
    && adduser -S spring -G spring \
    && mkdir -p /app/log \
    && chown -R spring:spring /app
USER spring:spring

# Your prebuilt JAR (already copied to /opt/methodologist/backend/app.jar)
COPY app.jar /app/app.jar

# Helpful defaults
ENV SERVER_PORT=8080 \
    LOG_PATH=/app/log

EXPOSE 8080

# Liveness only: it reflects this JVM, so a database or setup-service outage never gets the
# container restarted. Readiness (with upstreams) is at /actuator/health/readiness.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q --spider "http://localhost:${SERVER_PORT}/actuator/health/liveness" || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
