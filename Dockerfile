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
ENTRYPOINT ["java","-jar","/app/app.jar"]
