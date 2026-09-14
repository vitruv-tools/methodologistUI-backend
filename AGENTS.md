# AGENTS.md

Instructions for AI coding agents and human contributors working in this repository.
Read this file before making any change.

## Agent Behavior

Before writing code:

- Read the relevant classes.
- Understand existing patterns.
- Follow the project's conventions.
- Do not rewrite working code unnecessarily.

When uncertain:

- Prefer consistency over cleverness.
- Match the surrounding implementation style.
- Avoid speculative improvements.

## Project Overview

**Methodologist Backend** is a Spring Boot service that validates EMF metamodels, manages VSUM
(Virtual Single Underlying Model) structures, and integrates with Keycloak for authentication and
role-based authorization. It exposes REST APIs consumed by the Methodologist UI.

**Main technologies**

- Java 21 (`maven.compiler.source/target = 21`); CI builds with a Temurin JDK 21 toolchain
- Spring Boot 3.4.x — Web, WebFlux, WebSocket, Validation, Security, OAuth2 Resource Server, Data JPA, Mail
- Hibernate 6 / PostgreSQL, Flyway migrations, H2 for tests
- Keycloak 25 (adapter + admin client)
- Lombok, MapStruct, springdoc-openapi, Logback + logstash-logback-encoder
- Eclipse EMF (Ecore, XMI, codegen) and FreeMarker in the builder module
- Maven multi-module build (`./mvnw`), Spotless (google-java-format), Checkstyle, JaCoCo, SonarCloud

**Module structure**

```
/ (parent POM, packaging=pom)
├─ app/       Spring Boot application (tools.vitruv.methodologist.MethodologistApplication)
│  └─ src/main/java/tools/vitruv/methodologist/
│       apihandler/  external API clients + DTOs
│       config/       Spring, security and web configuration
│       exception/    exception types and handlers
│       general/      shared controllers, models, repositories, services, mappers
│       log/          logging support
│       messages/     message constants / i18n
│       user/         user domain (controller, model, repository, service, mapper)
│       vsum/         VSUM domain (controller, model, repository, service, mapper)
│       vitruvcli/    Vitruv CLI integration
│  └─ src/main/resources/db/migration/  Flyway SQL migrations
├─ builder/   Standalone EMF metamodel builder (tools.vitruv.methodologist.builder.Main),
│             packaged as methodologist-build jar-with-dependencies, FreeMarker templates
└─ docker/    Keycloak realm template and helpers
```

Each domain package follows the layering `controller → service (+ impl) → model/repository`, with
MapStruct mappers between entities and DTOs.

## Development Principles

- Preserve existing behavior unless a change is explicitly requested.
- Prefer minimal, focused changes that address the task and nothing more.
- Avoid opportunistic refactoring; propose it separately instead.
- Keep public APIs — REST endpoints, DTO shapes, service signatures — backward compatible unless the
  task requires otherwise.
- Do not introduce new dependencies without justification. Prefer what the POMs already provide.
- Never edit an applied Flyway migration; add a new `V<n>__NAME.sql` file instead.

## Code Style

- Follow the existing style; formatting is enforced by Spotless (google-java-format) and Checkstyle
  (`checkstyle.xml` at the repository root).
- Keep methods small and readable; extract helpers rather than growing long methods.
- Use meaningful, descriptive names for classes, methods, and variables.
- Avoid duplicated logic; prefer composition and reuse over copy-paste.
- No star imports; keep imports ordered as the formatter produces them.
- Add Javadoc to public classes and non-trivial public methods, matching surrounding density.
- Do not suppress warnings unless genuinely necessary, and justify each suppression with a comment.

## Architecture

- Follow the existing package structure.
- Business logic belongs in services.
- Controllers should only validate requests and delegate.
- Repositories must not contain business logic.
- MapStruct mappers are responsible only for mapping.
- Do not introduce circular dependencies between packages.
- Keep domain boundaries intact.

## Dependencies

- Reuse existing libraries whenever possible.
- Do not introduce a new dependency if the project already provides equivalent functionality.
- Discuss adding new dependencies before modifying pom.xml.

## Database

- Never modify an applied Flyway migration.
- Always create a new migration.
- Keep entity mappings compatible with existing schema.
- Prefer repository queries over native SQL.
- Native SQL requires justification.

## REST API

- Preserve backward compatibility.
- Do not change request or response DTOs unless required.
- Keep endpoint URLs stable.
- Document API changes.

## Java Guidelines

- Target Java 21 language level. Do not use language features beyond Java 21.
- Prefer modern Java where it improves clarity: records, `var` for obvious types, pattern matching
  for `instanceof` and `switch`, sealed types, text blocks, `List.of`/`Map.of`, streams for simple
  transformations.
- Use `Optional` for return values only; never for fields or parameters, and never call `get()`
  without a prior presence check — prefer `orElseThrow`, `map`, `ifPresent`.
- Avoid raw types; always parameterize generics.
- Prefer immutable objects and `final` fields where practical.
- Never catch generic `Exception` (or `Throwable`) unless an API forces it; catch the specific type.
- Throw specific exceptions from the `exception` package rather than `RuntimeException`.
- Prefer constructor injection (Lombok `@RequiredArgsConstructor` with `final` fields is the
  established pattern).
- Keep transactional boundaries unchanged unless the task requires otherwise.

## Spring Guidelines

- Use constructor injection only. Do not add `@Autowired` on fields or setters — the codebase has
  none.
- Respect existing `@Transactional` placement, propagation, and read-only flags.
- Avoid unnecessary `flush()` / `saveAndFlush()` calls; let the persistence context manage flushing.
- Use `JpaRepository` derived queries or `@Query`; drop to `EntityManager` only when the repository
  abstraction genuinely cannot express the operation.
- Do not introduce N+1 queries. Use fetch joins or `@EntityGraph` when a collection is needed.
- Preserve lazy loading behavior; do not change `FetchType` to work around a `LazyInitializationException` —
  fix the query or the transactional scope instead.
- Keep configuration in the existing `application-*.properties` profiles; do not hardcode
  environment-specific values.

## Test Priority

When changing code:

1. Update existing tests if behavior changed.
2. Add unit tests for new logic.
3. Add integration tests only when required.
4. Never remove a failing test simply to make CI pass.

## Testing

- Every functional change must include new tests or update existing ones.
- Keep tests deterministic: no reliance on wall-clock time, ordering, random data, or network access.
- Prefer fast unit tests (JUnit 5 + Mockito + AssertJ); use `@SpringBootTest` only when the wiring
  itself is under test.
- Reuse existing test utilities, fixtures, and configuration (`spring.profiles.active=test`, H2,
  `spring-security-test`, MockWebServer) instead of inventing parallel helpers.
- Do not delete or weaken existing coverage. If a test must change, explain why.

## Logging

- Never log passwords, client secrets, or database credentials.
- Never log access tokens, refresh tokens, or raw JWTs.
- Mask sensitive values (identifiers, emails, file contents) before logging.
- Use parameterized logging: `log.debug("Building VSUM {}", vsumId)` — never string concatenation.
- Use the existing `@Slf4j` logger; choose levels deliberately (`error` for failures, `debug` for
  diagnostics), and do not log the same failure at multiple layers.

## Security

- Preserve authorization checks: keep `@PreAuthorize`, role checks, and `SecurityFilterChain` rules
  intact unless the task explicitly changes them.
- Preserve request validation (`@Valid`, Bean Validation constraints) on controllers and DTOs.
- Never weaken authentication: do not add permit-all rules, disable CSRF, widen CORS, or bypass
  Keycloak token validation for convenience.
- Follow secure defaults: deny by default, validate all external input, and never commit secrets —
  use configuration properties or environment variables.

## Pull Requests

- Keep commits focused; one logical change per commit.
- Keep PRs small and reviewable.
- Do not mix refactoring with functional changes in the same PR.
- Explain *why* the change was made, not only what changed; link the related issue.
- Target `develop` unless instructed otherwise; CI runs on `main` and `develop`.

## Self Review Checklist

Before finishing:

- Does the code compile?
- Are all tests passing?
- Is formatting correct?
- Are Sonar issues introduced?
- Is the implementation minimal?
- Is backward compatibility preserved?
- Is documentation updated if needed?

## Before Finishing

Verify each of the following before reporting the work complete:

- The project builds: `./mvnw clean verify` (or at minimum `./mvnw -q clean install -DskipTests`).
- Tests pass: `./mvnw test`.
- Formatting is correct: `./mvnw spotless:apply` and Checkstyle reports no new violations.
- No new compiler warnings were introduced.
- No unnecessary files changed: run `git status` and `git diff` and confirm every hunk is intentional.
  Never commit build output, IDE files, logs, or local property overrides.

## Forbidden

- Do not reformat code unrelated to the task, even if the formatter would change it.
- Do not rename files, classes, or packages without a stated reason.
- Do not modify generated code (MapStruct implementations, EMF-generated sources, `target/`).
- Do not remove comments or Javadoc unless they are demonstrably obsolete.
- Do not change behavior without explicit instruction.
