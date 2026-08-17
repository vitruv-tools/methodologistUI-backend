# Low-code integration — completed work

Tracks progress against [`low-code-integration-plan.md`](low-code-integration-plan.md). Update this file when a phase or checklist item is finished.

| Phase | Status |
|---|---|
| 0 — scaffolding | **done** |
| 1 — persistence | **done** |
| 2 — low-code engine | **done** |
| 3 — sync-changes integration | **done** |
| 4 — details + history | not started |
| 5 — build path (setup-service) | not started |
| 6 — tests | not started (Phase 0 + Phase 2 unit tests only) |
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

---

## Phase 2 — low-code engine (done)

Date: 2026-08-16  
Branch: `low-code`

| Plan item | Result |
|---|---|
| `annotation/ReactionMetadata.java` | Done |
| `vsum/lowcode/reactions/template/**` (controller, DTOs, services, `.ftl`) | Done |
| `LowCodeReactionRequestMapper` | Done |
| `ReactionParserUtil` | Done (private constructor) |
| `FileStorageService` byte[] `storeFile` / `updateFile` overloads | Done; MultipartFile APIs unchanged (still throw on duplicate) |
| Register template request beans for metadata injection | Done (`@Component` on request DTOs) |
| Exclude `CompositeReactionsRequest` from public metadata | Done (`@ReactionMetadata(hide = true)` + filter in metadata service) |
| Domain exceptions instead of raw `RuntimeException` | Done: `LowCodeTemplateException` + HTTP 400 handler |

Also added focused unit tests for template render, metadata filtering, reaction parsing, and byte[] file storage.

### Endpoint

- `GET /api/lowcode-metadata` (role `user`)

### Files (new)

- `app/src/main/java/tools/vitruv/methodologist/annotation/ReactionMetadata.java`
- `app/src/main/java/tools/vitruv/methodologist/exception/LowCodeTemplateException.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/lowcode/**`
- `app/src/main/java/tools/vitruv/methodologist/vsum/mapper/LowCodeReactionRequestMapper.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/reaction/ReactionParserUtil.java`
- `app/src/main/resources/lowcode/reactions/template/*.ftl`
- tests under `app/src/test/java/.../lowcode/` and `.../reaction/ReactionParserUtilTest.java`

### Files (modified)

- `FileStorageService.java` / `FileStorageServiceTest.java`
- `Error.java` (`LOWCODE_TEMPLATE_APPLY_ERROR`)
- `GlobalExceptionHandlerController.java`

### Not in this phase

- Wiring into sync-changes / FG create
- History / details DTOs
- Setup-service composite build

---

## Phase 3 — sync-changes integration (done)

Date: 2026-08-16  
Branch: `low-code`

| Plan item | Result |
|---|---|
| Keep `applySyncChanges` MM + views flow | Done; null `viewRequests` still does not wipe views |
| Detect in-place relation updates (same pair, different reaction/FG) | Done via `MetaModelRelationRequest.equals` |
| Snapshot history once (`MemoizedSupplier`) | Done; FG-only changes also snapshot |
| Order: delete relations → delete MMs → add MMs → views → add/update relations → FG | Done |
| `create` accepts null `reactionFileId` when FG set is present | Done; otherwise `MetaModelRelationCreationException` |

### Endpoint contract

`PUT /api/v1/vsums/{id}/sync-changes` now accepts:

```json
{
  "sourceId": 10,
  "targetId": 20,
  "reactionFileId": null,
  "fineGranularMetaModelRelationSet": [
    {
      "sourceId": "Component",
      "targetId": "Class",
      "lowCodeReactionRequestBase": {
        "name": "create_corresponding_root_on_insert_root",
        "regenerate": true
      }
    }
  ]
}
```

A coarse relation needs a reaction file **or** a non-empty fine-granular set. Template-only FG rows generate a `.reactions` file via `LowCodeReactionService`.

### Files (new)

- `FineGranularMetaModelRelationRequest.java`
- `FineGranularMetaModelRelationService.java`
- `FineGranularMetaModelRelationServiceTest.java`

### Files (modified)

- `MetaModelRelationRequest.java` (optional `id` / `reactionFileId`, nested FG set)
- `VsumSyncChangesPutRequest.java` (`@Valid` on relation list)
- `MetaModelRelationService.java`
- `VsumService.java`
- `Error.java`
- `MetaModelRelationServiceTest.java` / `VsumServiceTest.java`

### Not in this phase

- Details response FG list / original MM id mapping
- History snapshot of FG children (revert still drops FG until Phase 4)
- Setup-service composite build
