# Low-code integration plan

**Status:** analysis complete, implementation not started  
**Working branch:** `low-code` (currently equal to `develop` at `5426f17`)  
**Source PR:** [#225](https://github.com/vitruv-tools/methodologistUI-backend/pull/225) — *Add fine-granular meta model relations with low-code template support*  
**Fork branch:** `menof36go:internship-reinbold`  
**PR range:** `d5ad64a` … `a4e6d7f` (head SHA `a4e6d7fe07f1f99ed6d55e43f24e651d47e190dc`)  
**PR base at close:** `67da591` (Merge PR #219, 2026-04-08)  
**Closed:** 2026-07-15, **never merged** (approved by `arlange` on 2026-04-10)  
**Related UI PR:** [Vitruv-UI-Methodologist#236](https://github.com/vitruv-tools/Vitruv-UI-Methodologist/pull/236)

---

## 1. Recommendation

Do **not** merge `menof36go/internship-reinbold` into current `develop`.

A dry-run merge against HEAD already reports **8 content conflicts**. More important are **semantic conflicts** Git would auto-merge incorrectly: views in sync-changes, invitations/roles, OCL rule sets, Flyway V8/V9 already used, and JAR builds now going through **setup-service** instead of the local Vitruv CLI.

**Port the low-code feature by re-implementing it on current `develop`**, using PR #225 as the behavior spec. Copy new files, surgically patch existing ones, and skip unrelated internship-branch extras.

---

## 2. What PR #225 actually delivered

The internship work is three tightly coupled features, all driven from `PUT /api/v1/vsums/{id}/sync-changes`.

### 2.1 Fine-granular meta-model relations

A coarse `MetaModelRelation` (source MM ↔ target MM) can own many element-level mappings:

| Field | Meaning |
|---|---|
| `sourceId` / `targetId` | **Strings** (Ecore class / element names), not numeric MM ids |
| `reactionFileStorage` | Generated or uploaded `.reactions` file |
| `lowCodeReactionTemplate` | Template name, e.g. `create_corresponding_root_on_insert_root` |
| `lowCodeReactionTemplateParams` | JSONB map of FreeMarker variables |

DB (PR `V9__FINE_GRANULAR_META_MODEL_RELATION.sql`):

- Table `fine_granular_meta_model_relation`
- FK to `meta_model_relation` and `file_storage`
- Unique `(meta_model_relation_id, source_id, target_id, reaction_file_id)`

Coarse `meta_model_relation.reaction_file_id` became **nullable** (PR `V8`), so a pair can exist with only fine-granular children.

### 2.2 Low-code reaction templates

FreeMarker templates under `app/src/main/resources/lowcode/reactions/template/`:

| Template | Role |
|---|---|
| `create_corresponding_root_on_insert_root.ftl` | Real user-facing template: insert root in model A → create corresponding root in model B |
| `composite_reactions.ftl` | Internal: wrap several reaction files into one `import …` reactions file for the build |
| `ExampleRequest` | Sample DTO; **not** registered in `@JsonSubTypes` |

Flow:

1. Client sends `LowCodeReactionRequestBase` (Jackson discriminator `name`).
2. `LowCodeReactionService.applyTemplate()` renders `{name}.ftl`.
3. Result is stored as `FileEnumType.REACTION`.
4. Fine-granular row stores template name + params so the UI can re-open the form.

### 2.3 Annotation-based metadata API

`GET /api/lowcode-metadata` (role `user`) reflects `@ReactionMetadata` + Bean Validation on request DTOs so the UI can render forms without hard-coding fields.

### 2.4 Sync-changes and build

- `MetaModelRelationRequest` gained optional `id`, optional `reactionFileId`, and `fineGranularMetaModelRelationSet`.
- Relation add/update/delete (including fine-granular) moved into `MetaModelRelationService.update(...)` with a memoized history snapshot.
- Build collected **all** reaction files on a pair (coarse + fine-granular). If more than one, it generated a **composite** reaction file via `ReactionParserUtil` + `composite_reactions.ftl`.

### 2.5 Known unfinished items in the original PR

These were already TODOs / gaps at close:

1. **History snapshots do not include fine-granular relations.** `VsumRepresentation.MetaModelRelation` has an explicit TODO from Reinbold. Revert would drop FG mappings.
2. **Bidirectional template attribute was removed** (`fix(LowCode): remove unsupported bidirectional attribute`).
3. Composite generation is marked “quick and dirty” (parses the first reaction file for URIs/aliases).
4. Several `RuntimeException` throws instead of domain exceptions (`NO_TEMPLATE_PROVIDED_ERROR`, update-id mismatches).
5. `ExampleRequest` is not wired as a Jackson subtype.
6. `@ReactionMetadata` has a TODO for a `readonly` flag.

---

## 3. What current `develop` already has

**None of the low-code / fine-granular code exists.** Grep for `LowCode`, `FineGranular`, `ReactionMetadata`, and the `.ftl` templates returns nothing.

What *does* exist, and must be preserved:

| Area | Current behavior |
|---|---|
| Coarse relations | `MetaModelRelation` + V5 schema; `reaction_file_id` **NOT NULL** |
| Sync-changes | Full-state MM + **relations** + **views**; history snapshot if anything changes |
| Relation diff key | `sourceId:targetId` only (changing the reaction file on the same pair does **not** update) |
| JAR build | `VsumService.getJarfat` → `SetupServiceApiHandler.buildVsumJarOrThrow` (multipart ecores/genmodels/reactions) |
| Local Vitruv CLI | Still used for **GenModel precheck**, not for VSUM JAR |
| Views | V8/V9 (`vsum_view`) already part of sync-changes |
| Invitations | V10; VIEWER cannot sync-changes |
| OCL rule sets | V11; separate CRUD, not in sync-changes |
| Files | `FileStorageService.updateFile(email, id, MultipartFile)` — reaction-only |
| Auth | Method security via `@PreAuthorize`; HTTP layer is `permitAll` |
| JSONB | `hibernate-types-60` already in `app/pom.xml` |
| FreeMarker | Only in **builder** module, not in `app` |
| Flyway | Next free version is **V12** |

---

## 4. Gap analysis

### Already in develop (do not re-port)

- Coarse `MetaModelRelation` CRUD via sync-changes
- Reaction file upload / update / download
- VSUM history for MM + coarse relations + **views**
- Setup-service JAR build for coarse reaction files
- GenModel precheck
- Docker/Keycloak, mail, OTP, password verification, invitations, rule sets

### Missing (must implement)

1. `FineGranularMetaModelRelation` entity, repository, mapper, request/response DTOs
2. Nested `fineGranularMetaModelRelationSet` on coarse relation request/response
3. Nullable coarse `reaction_file_id` + Flyway V12
4. Low-code package: templates, metadata controller/service, generation service, request mapper
5. Sync-changes: create/update/delete fine-granular children; regenerate reactions when `regenerate=true`
6. `GET /api/lowcode-metadata`
7. Build: include fine-granular reaction files; composite when a pair has multiple files
8. Details endpoint: return FG set on each relation
9. Tests: `FineGranularMetaModelRelationServiceTest` + updates to `VsumServiceTest` / `MetaModelRelationServiceTest`
10. **History/revert for FG relations** (missing in original PR; should be done this time)

### Present in the old branch but **out of scope** (do not port)

| Item | Why skip |
|---|---|
| `DevTokenController` + no-auth Swagger profile | Security-sensitive, unrelated |
| `docker-compose.yaml` profiles, `opt/*/README.md` | Unrelated ops docs |
| `.vscode/settings.json`, CI/CD workflow edits | Unrelated |
| AssertJ 3.27.3 → 3.27.7 | Dependabot already handled later |
| Early internship FileStorage / VitruvCli Windows fixes | Already on `develop` in other form |

---

## 5. Conflicts

### 5.1 Git content conflicts (merge-tree vs HEAD)

These files changed on both sides:

1. `SecurityConfiguration.java` — skip PR’s no-auth changes
2. `GlobalExceptionHandlerController.java` — keep current handlers; add only new exception types
3. `FileStorageService.java` — add a **byte[]** update overload; do not replace MultipartFile API
4. `Message.java` — add low-code success string next to invitation messages
5. `KeycloakService.java` — skip unless a real low-code need appears
6. `MetaModelRepository.java` — inspect; likely keep current
7. `VsumService.java` — **highest risk**; must keep views + setup-service build
8. `VsumServiceTest.java` — rewrite tests against current fixtures

### 5.2 Semantic conflicts Git would hide

| Current develop | Old PR | Port strategy |
|---|---|---|
| `applySyncChanges` also syncs **views** | Relation sync moved into `MetaModelRelationService.update` | Keep view logic in `VsumService`; delegate relation+FG sync to `MetaModelRelationService` **without** dropping views |
| `getJarfat` uses setup-service | Build used `MetaModelVitruvIntegrationService.getBuildParameters` | Collect FG + composite files in `VsumService.getJarfat` (or a shared helper) and pass them to setup-service. Keep CLI path for precheck. |
| Flyway **V8 = views**, **V9 = view constraints** | PR **V8 = drop reaction NOT NULL**, **V9 = FG table** | New migrations: `V12__META_MODEL_RELATION_NULLABLE_REACTION.sql` and `V13__FINE_GRANULAR_META_MODEL_RELATION.sql` |
| `MetaModelRelation.reactionFileStorage` is `@NotNull` | Became optional | Entity + DB + `create()` must allow null when FG set is non-empty |
| `updateFile(MultipartFile)` only | PR added `updateFile(..., byte[], filename, contentType)` | Add overload used by template generation |
| `VsumRepresentation` has `views` | PR had no views and a FG TODO | Keep views; **add** FG snapshot fields |
| `Error` has private constructor (PR #321) | PR added constants | Add constants only; keep constructor |
| Relation create requires `reactionFileId` | Allowed template-only / FG-only | Validate: coarse file **or** non-empty FG set |

### 5.3 Unique-constraint / nullability pitfall

PostgreSQL unique indexes treat `NULL` as distinct. After making `reaction_file_id` nullable, `(vsum_id, source_id, target_id, NULL)` can be inserted twice.

**Fix in V12:** keep uniqueness of a pair. Prefer a partial unique index on `(vsum_id, source_id, target_id)` (one coarse relation per pair), which already matches the sync-changes diff key.

---

## 6. Target API (compatible with UI PR #236)

### Unchanged

- `PUT /api/v1/vsums/{id}/sync-changes`
- `GET /api/v1/vsums/{id}/details` (`metaModelsRelation`)

### Extended payload

`MetaModelRelationRequest`:

```json
{
  "id": 1,
  "sourceId": 10,
  "targetId": 20,
  "reactionFileId": 30,
  "fineGranularMetaModelRelationSet": [
    {
      "id": null,
      "sourceId": "Component",
      "targetId": "Class",
      "reactionFileStorageId": null,
      "lowCodeReactionRequestBase": {
        "name": "create_corresponding_root_on_insert_root",
        "regenerate": true,
        "model1Uri": "...",
        "model1Alias": "...",
        "model1RootType": "...",
        "model2Uri": "...",
        "model2Alias": "...",
        "model2RootType": "..."
      }
    }
  ]
}
```

### New

- `GET /api/lowcode-metadata` → form schema for the UI

Jackson discriminator on `lowCodeReactionRequestBase.name`. Hide `composite_reactions` from the metadata list (internal build helper).

---

## 7. Implementation plan

Work on branch `low-code`. Do not merge the old branch. Treat `a4e6d7f` as a reference checkout for copy-paste of **new** files only.

### Phase 0 — scaffolding

- Add `org.freemarker:freemarker` to `app/pom.xml` (managed by Spring Boot BOM).
- Add `Message.LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY`.
- Add `Error.NO_TEMPLATE_PROVIDED_ERROR` (and a proper exception type; avoid raw `RuntimeException`).
- Add `MemoizedSupplier` (used to snapshot history at most once per sync).
- Add `MetaModelRelationCreationException` + handler in `GlobalExceptionHandlerController`.

### Phase 1 — persistence

- `FineGranularMetaModelRelation` + repository.
- `MetaModelRelation`: optional `reactionFileStorage`; `OneToMany` FG set (orphanRemoval).
- Flyway:
  - `V12`: drop NOT NULL on `meta_model_relation.reaction_file_id`; add pair uniqueness that works with nulls.
  - `V13`: create `fine_granular_meta_model_relation` (same columns as PR V9).
- Do not reuse version numbers V8/V9.

### Phase 2 — low-code engine (mostly copy from PR)

New files (adapt package-level only as needed):

- `annotation/ReactionMetadata.java`
- `vsum/lowcode/reactions/template/**` (controller, DTOs, services, `.ftl`)
- `vsum/mapper/LowCodeReactionRequestMapper.java`
- `vsum/reaction/ReactionParserUtil.java`

Adaptations:

- `FileStorageService`: keep MultipartFile `updateFile`; add byte[] overload for generated content.
- Register template request beans so `LowCodeReactionMetadataService` can inject `List<LowCodeReactionRequestBase>`.
- Exclude `CompositeReactionsRequest` from public metadata.
- Use current exception / message style (`Error` constants, no `new RuntimeException(message)` in service APIs).

### Phase 3 — sync-changes integration

Keep `VsumService.applySyncChanges` structure:

1. Diff MMs, views, coarse pairs (as today).
2. Detect **in-place relation updates** (same pair, different reaction file or FG set) — current code misses this.
3. If any change (including FG-only), snapshot history once (`MemoizedSupplier`).
4. Order: delete relations → delete MMs → add MMs → apply views → add/update relations → FG create/update/delete.
5. `MetaModelRelationService.create` must accept null `reactionFileId` when FG set is present.

Do **not** let a null `viewRequests` wipe views. Keep today’s `normalizeViewRequests`.

### Phase 4 — details + history

- `MetaModelRelationResponse` includes `fineGranularMetaModelRelationSet`.
- Mapper: map FG children; prefer **original** MM ids (`source.source.id`) for coarse `sourceId`/`targetId` so request and response use the same convention (today’s mapper uses clone PKs).
- `VsumRepresentation.MetaModelRelation`: add FG list; update `VsumHistoryMapper` so revert restores FG mappings.

### Phase 5 — build path (setup-service)

In `VsumService.getJarfat`:

- For each coarse relation, collect coarse reaction file + all FG reaction files.
- If a pair has >1 file, generate composite content (same as PR `getBuildParameters`) and send **imports + composite** as `reactionFiles` to setup-service.
- Allow a pair with **only** FG files (nullable coarse reaction).
- Keep `MetaModelVitruvIntegrationService` for precheck; optionally extract composite helper so CLI and setup-service share it.

### Phase 6 — tests

Port and update:

- `FineGranularMetaModelRelationServiceTest`
- `MetaModelRelationServiceTest` (nullable reaction, FG nested set)
- `VsumServiceTest` (sync with FG + views together; history includes FG)
- New: metadata controller/service test; template render snapshot for `create_corresponding_root_on_insert_root.ftl`
- `FileStorageServiceTest` for byte[] update overload
- `getJarfat` / setup-service mock: multiple reaction files + composite

### Phase 7 — quality gates

- `./mvnw -pl app test`
- Spotless / Checkstyle
- No new `RuntimeException` for expected client errors
- Swagger: `/api/lowcode-metadata` and extended relation DTOs visible

---

## 8. File checklist

### Add (from PR, then adapt)

- `app/src/main/java/tools/vitruv/methodologist/annotation/ReactionMetadata.java`
- `app/src/main/java/tools/vitruv/methodologist/exception/MetaModelRelationCreationException.java`
- `app/src/main/java/tools/vitruv/methodologist/general/MemoizedSupplier.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/lowcode/**`
- `app/src/main/java/tools/vitruv/methodologist/vsum/mapper/FineGranularMetaModelRelationMapper.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/mapper/LowCodeReactionRequestMapper.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/model/FineGranularMetaModelRelation.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/model/repository/FineGranularMetaModelRelationRepository.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/reaction/ReactionParserUtil.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/service/FineGranularMetaModelRelationService.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/controller/dto/request/FineGranularMetaModelRelationRequest.java`
- `app/src/main/java/tools/vitruv/methodologist/vsum/controller/dto/response/FineGranularMetaModelRelationResponse.java`
- `app/src/main/resources/lowcode/reactions/template/*.ftl`
- `app/src/main/resources/db/migration/V12__*.sql`
- `app/src/main/resources/db/migration/V13__*.sql`
- `app/src/test/java/.../FineGranularMetaModelRelationServiceTest.java`

### Modify (surgical)

- `app/pom.xml` — FreeMarker
- `MetaModelRelation.java` / `MetaModelRelationRequest.java` / `MetaModelRelationResponse.java` / mapper
- `MetaModelRelationService.java` — FG-aware create/update
- `VsumService.java` — relation update detection, FG-aware `getJarfat`, keep views
- `VsumRepresentation.java` + `VsumHistoryMapper.java`
- `FileStorageService.java`
- `Error.java` / `Message.java` / `GlobalExceptionHandlerController.java`
- Tests listed in Phase 6

### Do not touch from the old branch

- `SecurityConfiguration` no-auth / `DevTokenController`
- Docker compose, README, `.github/workflows`, `.vscode`

---

## 9. Risks

1. **UI contract** — backend must match UI PR #236 field names (`fineGranularMetaModelRelationSet`, discriminator `name`). Confirm against the UI PR before freezing DTOs.
2. **Setup-service** — must accept multiple reaction files per VSUM the same way the old CLI `-rs` directory did. If it concatenates blindly, composite + imports may need a specific order/naming.
3. **Existing DBs** — V12/V13 are additive; dropping NOT NULL is backward compatible. Pair unique-index rewrite needs care if duplicate pairs already exist (they should not, given current sync key).
4. **History JSON** — old snapshots lack FG data; revert of pre-feature history cannot restore FG rows (acceptable). New snapshots must include them.
5. **Generated vs uploaded reactions** — updating a generated file via `/upload/{id}/update-reaction` can desync stored template params unless `regenerate` is used. Keep PR behavior; document it.

---

## 10. Suggested implementation order

1. Persistence + DTOs (compiles, no behavior change yet)
2. Template engine + metadata endpoint
3. Wire FG into sync-changes (create/update/delete)
4. Details response + history
5. Build collection for setup-service
6. Tests and Checkstyle

After Phase 3 the feature is usable from the UI for create/edit; Phase 5 is required before a FG-only VSUM can build.
