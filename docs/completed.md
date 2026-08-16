# Low-code integration — completed work

Tracks progress against [`low-code-integration-plan.md`](low-code-integration-plan.md). Update this file when a phase or checklist item is finished.

| Phase | Status |
|---|---|
| 0 — scaffolding | **done** |
| 1 — persistence | **done** |
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

---

## Phase 1 — persistence (done)

Date: 2026-08-16  
Branch: `low-code`

| Plan item | Result |
|---|---|
| `FineGranularMetaModelRelation` + repository | Done |
| `MetaModelRelation`: optional `reactionFileStorage`; `OneToMany` FG set (`orphanRemoval`) | Done; unique key is now `(vsum_id, source_id, target_id)` so null reaction files cannot duplicate a pair |
| Flyway V12: drop NOT NULL on `reaction_file_id`; pair uniqueness that works with nulls | `V12__META_MODEL_RELATION_NULLABLE_REACTION.sql` |
| Flyway V13: FG table (same columns as PR V9) | `V13__FINE_GRANULAR_META_MODEL_RELATION.sql` (FK named for `meta_model_relation`, `ON DELETE CASCADE`) |
| Do not reuse Flyway V8/V9 | Done |

### Files

- `app/src/main/java/tools/vitruv/methodologist/vsum/model/FineGranularMetaModelRelation.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/model/repository/FineGranularMetaModelRelationRepository.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/model/MetaModelRelation.java`
- `app/src/main/resources/db/migration/V12__META_MODEL_RELATION_NULLABLE_REACTION.sql`
- `app/src/main/resources/db/migration/V13__FINE_GRANULAR_META_MODEL_RELATION.sql`

### Not in this phase

- DTOs, mappers, template engine, sync-changes, history JSON, build path
- Service still requires a coarse `reactionFileId` (Phase 3 will allow FG-only relations)
