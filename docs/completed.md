# Low-code integration — completed work

Tracks progress against [`low-code-integration-plan.md`](low-code-integration-plan.md). Update this file when a phase or checklist item is finished.

| Phase | Status |
|---|---|
| 0 — scaffolding | **done** |
| 1 — persistence | not started |
| 2 — low-code engine | not started |
| 3 — sync-changes integration | not started |
| 4 — details + history | not started |
| 5 — build path (setup-service) | not started |
| 6 — tests | not started (Phase 0 unit test only) |
| 7 — quality gates | not started |

---

## Phase 0 — scaffolding (done)

Date: 2026-08-16  
Branch: `low-code`

| Plan item | Result |
|---|---|
| Add `org.freemarker:freemarker` to `app/pom.xml` (BOM-managed, no version) | Done |
| Add `Message.LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY` | Done |
| Add `Error.NO_TEMPLATE_PROVIDED_ERROR` and a domain exception (no raw `RuntimeException`) | Done: `NoTemplateProvidedException` extends `MetaModelRelationCreationException` |
| Add `MemoizedSupplier` | Done, plus `MemoizedSupplierTest` |
| Add `MetaModelRelationCreationException` + handler | Done in `GlobalExceptionHandlerController` (HTTP 400; also covers `NoTemplateProvidedException`) |

Also added `Message.LOWCODE_REACTION_CREATED_SUCCESSFULLY` from the original PR so later phases do not have to touch `Message` again.

### Files

- `app/pom.xml`
- `app/src/main/java/tools/vitruv/methodologist/messages/Error.java`
- `app/src/main/java/tools/vitruv/methodologist/messages/Message.java`
- `app/src/main/java/tools/vitruv/methodologist/general/MemoizedSupplier.java`
- `app/src/main/java/tools/vitruv/methodologist/exception/MetaModelRelationCreationException.java`
- `app/src/main/java/tools/vitruv/methodologist/exception/NoTemplateProvidedException.java`
- `app/src/main/java/tools/vitruv/methodologist/exception/GlobalExceptionHandlerController.java`
- `app/src/test/java/tools/vitruv/methodologist/general/MemoizedSupplierTest.java`

### Intentionally not ported from PR #225

- Dev-profile stack traces / `Environment` injection in `GlobalExceptionHandlerController`
- No-auth Swagger / `DevTokenController`
- Any persistence, templates, or sync-changes code (later phases)
