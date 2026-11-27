# Methodologist — Backend Service

![Java](https://img.shields.io/badge/Java-21-blue.svg)
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
    - Collaboration server notifications
- Health checks
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

- Java 21
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

### Collaboration server notifications

After a user changes a project through a user endpoint (sync changes, meta model rename or Ecore save, history
revert), the backend asks the collaboration server to reload the project, so its next writeback does not undo the
change. The request is `POST {collaboration.url}/internal/projects/{id}/reload`, sent in the background after the
change is committed, authenticated with a token of the backend's own Keycloak service account. Changes the
collaboration server writes back through the internal endpoints send no notification.

After the owner moves a project to the trash or deletes it permanently, the backend sends
`POST {collaboration.url}/internal/projects/{id}/close` the same way. The collaboration server then drops the
project's shared document without writing it back and disconnects the users who still have it open, instead of
letting them keep editing a project whose writebacks fail with `404`. Projects deleted by the 30-day trash cleanup
send no notification; they were closed when they were moved to the trash. The collaboration server drops a project's
document only after the backend's internal details endpoint answers `404` for it, so a close that arrives late, e.g.
after the project was restored, does no harm.

After the owner removes a member or viewer from a project, the backend sends
`POST {collaboration.url}/internal/projects/{id}/check-access` the same way. After a user removes their own account,
it sends `POST {collaboration.url}/internal/users/{id}/check-access` with the backend's user ID, one notification
however many projects the user is in. Neither has a body. Without them, a removed user would keep editing through a
connection opened before the removal, because the collaboration server checks the role only when a user connects.

- **Checked against the current state.** A check-access notification only means "check again now". The collaboration
  server asks the backend for the project's members with `GET /api/internal/v1/vsums/{id}/members` and closes the
  connections of users who are no longer members (close code `4403`) and of users who could edit and may now only
  view (`4412`). Because it decides from the backend's current state, late, repeated or reordered notifications do no
  harm, e.g. for a member who was added again in the meantime.
- **Members endpoint.** `GET /api/internal/v1/vsums/{id}/members` may only be called by the collaboration server's
  service account (realm role `collaboration-service`). It answers
  `{"data":[{"userId":7,"role":"OWNER"},{"userId":9,"role":"MEMBER"},{"userId":12,"role":"VIEWER"}],"message":null}`
  with the active users of an active project (users who removed their account are left out), and `404` if the
  project is missing or in the trash. Every user is listed once: a user with several memberships in the project gets
  the most privileged of their roles (`OWNER`, then `MEMBER`, then `VIEWER`), the same role the details endpoint
  returns to them.
- **User ID in the details.** `GET /api/v1/vsums/{id}/details` also returns the caller's user ID as `data.userId`,
  next to `data.role`. The collaboration server reads both when a user connects and identifies the connection's user
  by the ID. Both are `null` in the internal details endpoint's answer.
- **Deploy the backend first.** A collaboration server that handles check-access notifications refuses connections
  when the details have no `data.userId`. An older collaboration server answers check-access notifications with
  `404`, which is logged and not retried.
- **New ways to lose access.** Today a user loses access to a project only when the owner removes them, when they
  remove their account, or when the project is removed (see the close notification above). Any future code path that
  takes access or write access away, e.g. leaving a project, changing a role or transferring the ownership, must
  publish a `VsumAccessChangedEvent`; one that removes users must publish a `UserRemovedEvent`. Granting access needs
  no notification.

| Property                         | Meaning                                                                    |
|----------------------------------|----------------------------------------------------------------------------|
| `collaboration.url`              | Base URL of the collaboration server. Empty or unset disables the feature. |
| `keycloak.backend.client-id`     | Keycloak client of the backend's service account (`methodologist-backend`). |
| `keycloak.backend.client-secret` | Secret of that client.                                                     |
| `collaboration.reload.max-retries` | How often a failed notification is sent again (default `5`; `0` sends it once). |
| `collaboration.reload.first-retry-delay` | Delay before the first retry (default `1s`); it doubles with every failure, up to 10 s. |

- **Disabled by default.** Without `collaboration.url`, or without the client ID or secret, no notification is sent
  and the backend works as before; the reason is logged once at startup.
- **Retried.** A notification the collaboration server couldn't take (not reachable, no answer within 3 s, `5xx` or
  `429`) is sent again with a growing delay, so the collaboration server's next writeback doesn't undo the change and
  a removed project doesn't stay open. If the collaboration server rejects the token (`401`), a new token is fetched
  and the notification is sent once more. Other answers (e.g. `400`) are not retried. Reload, close and check-access
  notifications share one thread, its queue and the retry settings, so they are sent in the order of the commits;
  retries run on that thread, so later notifications wait for them.
- **Best effort.** A notification that still fails, or a failing Keycloak, is only logged; the user's request still
  succeeds. If a check-access notification is lost this way, a removed user keeps an open connection until it
  reconnects.
- **Keycloak.** `docker/keycloak/realm-template.json` contains the confidential client `methodologist-backend`
  (client credentials grant) and the realm role `methodologist-backend` assigned to its service account, so a fresh
  local Keycloak needs no manual setup. The collaboration server requires that role on its internal endpoints.
- **Same Keycloak URL.** The collaboration server checks the token's issuer, which is the URL the backend requested
  the token from. Its `KEYCLOAK_URL` must therefore equal the backend's `third_api.keycloak.base_url`; otherwise
  every notification is rejected with `401` (logged as a warning by the backend).
- **Secrets.** The secret in the realm template and in `application-dev.properties` is for local development only.
  Outside development, regenerate the client secret in Keycloak (client `methodologist-backend` → *Credentials*) and
  pass it through external configuration or an environment variable; never commit it.

---

## API documentation (Swagger UI)

After the application is running, the Swagger/OpenAPI UI is available at:

- `http://localhost:9811/swagger-ui/index.html`
- (alternative) `http://localhost:9811/swagger-ui.html`

If your application runs on a different port, replace `9811` with the configured `server.port`.

---

## Health checks

The backend exposes Spring Boot Actuator health and info endpoints:

| Endpoint | Token | What it reports |
|---|---|---|
| `GET /actuator/health/liveness` | no | Whether the JVM is alive. Used by the Docker `HEALTHCHECK`. |
| `GET /actuator/health/readiness` | no | Whether the backend can serve requests: PostgreSQL, Keycloak and the setup-service must all be reachable. `503` otherwise. |
| `GET /actuator/health` | no | Overall status. With a token, also each component (`db`, `keycloak`, `setupService`, ...). |
| `GET /actuator/info` | yes | Version, build time and the git commit the running instance was built from. |

No other actuator endpoint is exposed. Keycloak and setup-service probes time out after
`health.upstream.timeout` (default `3s`).

```bash
curl http://localhost:9811/actuator/health/readiness
```

The Docker Compose services have health checks too, so you can wait for the dependencies before
starting the backend:

```bash
docker compose up -d --wait methodologist-postgresdb keycloak-db keycloak
```

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
