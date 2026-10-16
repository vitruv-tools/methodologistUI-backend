# Runs the VSUM application built by Methodologist as a Vitruv server in a container.
#
#   docker compose up --build -d          (see docker-compose.yaml next to this file)
#
# or without Compose:
#   docker build -t my-vsum .
#   docker run -d -p 8080:8080 -v vsum-data:/data my-vsum
FROM eclipse-temurin:21-jre-alpine

# Non-root user; /data holds the VSUM's model storage and is what you mount to persist it.
RUN addgroup -S vitruv \
    && adduser -S vitruv -G vitruv \
    && mkdir -p /app /data \
    && chown -R vitruv:vitruv /app /data
USER vitruv:vitruv

COPY vsum.jar /app/vsum.jar

# The application binds to localhost by default, which is unreachable through a port mapping.
# 0.0.0.0 makes it listen on all interfaces of the container.
ENV VITRUV_SERVER_HOST=0.0.0.0 \
    VITRUV_SERVER_PORT=8080

# The application stores its models relative to the working directory.
WORKDIR /data
VOLUME ["/data"]

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/vsum.jar"]
