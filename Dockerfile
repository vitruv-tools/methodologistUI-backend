# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy everything (wrapper + pom + src)
COPY . .
# Ensure wrapper is executable (if present)
RUN chmod +x mvnw || true
# Build without tests
RUN ./mvnw -B -DskipTests clean package || mvn -B -DskipTests clean package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copy the built jar
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
# Optional: pass JVM flags with JAVA_OPTS env
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]