# Methodologist — Backend Service

![Java](https://img.shields.io/badge/Java-17-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)
![Maven](https://img.shields.io/badge/Maven-Build-blue)
![Status](https://img.shields.io/badge/Status-Active-success.svg)
![Keycloak](https://img.shields.io/badge/Auth-Keycloak-orange.svg)
![Docker](https://img.shields.io/badge/Supports-Docker-blue.svg)
![Tests](https://img.shields.io/badge/Tests-JUnit5%20%7C%20Mockito-yellow.svg)

A Spring Boot backend that validates EMF metamodels, manages VSUM structures, and integrates with Keycloak for
authentication and authorization.

This README describes how to build, configure, and run the application locally, including a recommended Keycloak setup
using Docker Compose and the included realm template.

---

## Table of contents

- About
- Requirements
- Build
- Run (development)
- Configuration
- Keycloak setup (REQUIRED)
    - Start Keycloak with Docker Compose
    - Import the provided realm (methodologist)
    - Automated realm import (optional)
- Testing
- Troubleshooting
- Useful Docker commands

---

## About

Methodologist provides REST APIs for metamodel validation and VSUM management. It relies on Keycloak for authentication
and role-based access control.

---

## Requirements

- Java 17
- Maven (recommended to use the included Maven Wrapper `./mvnw`)
- Docker & Docker Compose (for Keycloak and integration testing)
- Git (for cloning the repository)

---

## Build

From the project root, build the project using the Maven Wrapper or your system Maven:

```bash
# Recommended (uses project Maven wrapper)
./mvnw clean package -DskipTests

# Or with system Maven
mvn clean package -DskipTests
```

Run tests:

```bash
./mvnw test
```

The runnable JAR will be created under `target/` (for example: `target/methodologist.jar`).

---

## Run (development)

Run the packaged JAR with a custom configuration directory:

```bash
java -jar target/methodologist.jar --spring.config.location=file:/absolute/path/to/config/
```

The directory provided to `spring.config.location` must contain `application.properties` or `application.yml`. Use
`application-dev.properties` in the repository as a reference.

---

## Configuration

Important configuration areas:

- `server.port` — HTTP port used by the application
- Keycloak settings — issuer URI, client id, client secret
- Datasource — JDBC URL, username, password (H2 can be used for local testing)

See `src/main/resources/application-dev.properties` for example values used during development.

---

## API documentation (Swagger UI)

After the application is running, the Swagger/OpenAPI UI is available at:

- `http://localhost:9811/swagger-ui/index.html`
- (alternative) `http://localhost:9811/swagger-ui.html`

If your application runs on a different port, replace `9811` with the configured `server.port`.

---

## Architecture (ASCII)

A minimal overview of the main components:

```
  +--------+        +------------+        +------------+
  | Client | <--->  | API (REST) | <--->  | Keycloak   |
  | (UI)   |        | Spring     |        | (AuthN/Z)  |
  +--------+        | Boot App   |        +------------+
                   /| Services   |\
                  / | Repositories| \
                 v  +------------+  v
              +--------+        +--------+
              | DB     |        | File   |
              |(Postgres/H2)|   |Storage |
              +--------+        +--------+
```

- The Spring Boot application exposes REST endpoints used by the frontend.
- Keycloak provides JWTs for authentication; the app validates tokens and enforces roles.
- Persistent storage (Postgres in production, H2 for local tests) stores VSUMs, metamodels, and history.
- File storage entries point to uploaded files stored in the database (file table) or external storage.

---

## Directory structure (top-level)

A simplified view of the repository layout (important folders only):

```
/ (repo root)
├─ docker/                # Keycloak helpers and realm template
│  └─ keycloak/
│     └─ realm-template.json
├─ src/
│  ├─ main/
│  │  ├─ java/            # application sources
│  │  └─ resources/       # application properties, static resources
│  └─ test/               # unit and integration tests
├─ app/                   # module (if present in your clone)
├─ builder/               # build tooling (if present)
├─ pom.xml                # Maven parent POM
└─ README.md
```

Adjust the structure above to match any additional modules in your clone.

---

## Keycloak setup (REQUIRED)

The backend requires a Keycloak server with a realm named `methodologist`. The repository includes a realm template at:

```
docker/keycloak/realm-template.json
```

Two recommended ways to get Keycloak running locally are described below.

### Option A — Start Keycloak using the repository Docker Compose

If a `docker-compose.yaml` is present at the project root and contains a `keycloak` service, start it with:

```bash
# from repo root
docker compose up -d keycloak
```

After the container is running, open the Admin Console (typically `http://localhost:8080/`) and log in with the admin
credentials defined in the compose file.

#### Import the `methodologist` realm via the Admin Console

1. In the Keycloak Admin Console, click the realm selector (top-left) and choose **Add realm**.
2. Select **Import** and upload `docker/keycloak/realm-template.json` from the repository.
3. Confirm the import — the `methodologist` realm and its clients/roles will be created.

### Option B — Run Keycloak directly with Docker (example)

Run Keycloak in development mode (example using Keycloak 20+ `start-dev` image):

```bash
docker run --rm -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest \
  start-dev
```

Then import the realm using the Admin Console as described above.

### Automated import (optional)

You can mount `docker/keycloak/realm-template.json` into the Keycloak container and use `KC_IMPORT` (or the import flag
supported by your Keycloak image) to import the realm on first start. This behavior depends on the Keycloak distribution
and version. Example (pseudo):

```bash
docker run --rm -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -v $(pwd)/docker/keycloak/realm-template.json:/opt/keycloak/data/import/realm-template.json \
  -e KC_IMPORT=/opt/keycloak/data/import/realm-template.json \
  quay.io/keycloak/keycloak:latest start-dev
```

Check your Keycloak image documentation for the exact environment variables / flags for automated import.

---

## Testing

- Unit tests: JUnit 5 + Mockito. Run with:

```bash
./mvnw test
```

- Integration tests: may use Testcontainers. If Testcontainers fails to pull the auxiliary `ryuk` container (404),
  pre-pull it:

```bash
docker pull testcontainers/ryuk:0.3.0
```

---

## Troubleshooting

- Keycloak unreachable: verify the container is running `docker ps` and that the port mappings match your configuration.
- Realm import issues: ensure the JSON is valid and compatible with the Keycloak version in use. Try the Admin Console
  import if automated import fails.
- Token / OAuth problems: verify client configuration (client id/secret, redirect URIs) in the `methodologist` realm and
  ensure application properties match.

---

## Useful Docker commands

Stop and remove all containers:

```bash
docker rm -f $(docker ps -aq)
```

Remove unused volumes:

```bash
docker volume prune -f
```

List running containers:

```bash
docker ps
```

---
