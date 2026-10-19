# Database Migration Plan: Lazy Loading and Relational SQLite Storage

## Goal

Refactor the persistence layer so opening MultiGateway does not load and deserialize every conversation and every message. Replace JSON blobs stored in Room/SQLite with relational SQLite tables for persisted application data, using Room entities, DAOs, foreign keys, indexes, and explicit migrations.

This is a data-layer and user-experience change. The app must become usable quickly after launch, and sending a message immediately after opening the app must not depend on unrelated history having finished loading.

## Execution status

- ~~Phase 0 source inventory: current Room entities, DAO queries, repositories, model fields, call sites, and existing persistence tests inspected.~~ Completed before the first code slice; the active module is `app` and the current Room schema is version 9.
- ~~Additive first slice: expose a metadata-only conversation-list projection and an explicit full-conversation load method without changing the existing full-history API.~~ Implemented in `ConversationListItem.kt`, `Daos.kt`, and `ConversationRepository.kt`; focused tests are in `SidebarStorageTest.kt`. Verified with `./gradlew :app:testDebugUnitTest --tests org.starfall.multigateway.SidebarStorageTest` and `git diff --check`.
- ~~Representative v9 baseline fixture: create a real v9 schema fixture covering rich conversation history, versions, attachments, tool activity, summary, provider/model/profile/MCP/speech data, encrypted values, nulls, and malformed payloads.~~ Implemented in `CredentialMigrationTest.kt`; the fixture is opened through Room at version 9 and checked through repository/DAO reads. `compileDebugAndroidTestKotlin` passed and direct instrumentation ran all 3 `CredentialMigrationTest` tests successfully.
- ~~Conversation v10 migration slice: normalize conversations, messages, versions, tool activities, file references, summaries, and migration quarantine; add `MIGRATION_9_10`; replace conversation repository persistence/mapping and file-reference lookup.~~ Implemented in `ConversationEntities.kt`, `ConversationMigration9To10.kt`, `AppDatabase.kt`, `Daos.kt`, and `ConversationRepository.kt`. The v9 fixture now migrates through Room v10 and verifies relational counts, ordering, legacy transforms, quarantine, indexes, and foreign-key integrity.
- ~~Conversation UI metadata-first slice: wire `conversationList` and targeted `loadById()` into `ChatViewModel`/navigation, keep export as an explicit full-history boundary, and remove eager compatibility-flow initialization.~~ Implemented in `ConversationRepository.kt`, `ChatViewModel.kt`, `AppNavigation.kt`, drawer UI, and `Daos.kt`. Sidebar state now contains metadata only; selection, restoration, summaries, deletion cleanup, and export load only the required full conversations. A selection token prevents slow history reads from replacing a newer selection.
- Next: replace full-history conversation rewrites with targeted append/update transactions and add focused ViewModel coverage for metadata restoration and selection races.

## Non-negotiable requirements

1. **Do not lose user data.** Migrate existing databases in place and preserve conversation IDs, order, timestamps, message contents, all message versions, attachments/references, tool activity/results, summaries, reasoning settings, provider/model/profile links, and all configuration fields.
2. **Do not delete or recreate the existing database** to simplify migration. Existing installations must upgrade automatically from the current schema (version 9) and supported earlier versions.
3. **Lazy-load conversation content.** Startup and conversation-list queries must never fetch or deserialize the full message history for every conversation.
4. **Use relational SQLite for persisted structured data.** Do not retain JSON-encoded application objects in SQLite columns as the normal storage format. Keep JSON only at external boundaries that require it, such as provider API payloads and user-facing import/export formats; do not confuse wire-format JSON with database storage.
5. **Keep the UI responsive.** Database I/O and mapping must run off the main thread. Opening the app, opening a conversation, switching conversations, and sending the first message must not wait for unrelated database data.
6. **Keep behavior compatible.** Preserve current chat semantics, message version selection, retry/regenerate behavior, tool execution/history, attachments, summaries, provider/model configuration, MCP settings, speech configuration, ordering, and import/export behavior.
7. Read `AGENTS.md` before implementation and follow the current package/module rules. Do not perform unrelated UI refactors in this migration.

## Current problems confirmed in source

- `ConversationDao.getAllConversations()` selects every row from `conversations`.
- `ConversationEntity.messagesJson` stores the entire message list for a conversation in one text column.
- `ConversationRepository.stored` maps every conversation entity through `entityToModel()`, which decodes every `messagesJson` payload and maps all message versions/tool activity.
- `ImmediateState` holds the full `List<Conversation>` snapshot in memory and waits for the source's initial emission before publishing a ready value.
- `ConversationRepository.saveConversation()` serializes and persists a conversation's entire message list as one JSON string.
- Other Room entities also persist structured values in JSON columns. Inventory includes `summaryJson`, profile configuration/MCP/tool fields, provider model catalogs, MCP cached tools, and speech options. Provider and MCP authentication/config secrets must follow the separate encrypted-auth-table design below; non-secret provider/MCP fields must remain readable without waiting for that table.
- JSON import/export and JSON sent to model APIs are external formats and must remain supported; they are not reasons to store the same structures as JSON blobs inside SQLite.

## Target data model

Design the schema before implementation. Use stable IDs, foreign keys with deliberate delete behavior, and indexes for actual access patterns. Names below are logical targets; confirm model fields and relationships against the source before finalizing columns.

### 1. Conversations and messages

- `conversations`: metadata only — ID, title, created/updated timestamps, token count, provider/model/profile IDs, reasoning effort, and summary reference/metadata.
- `messages`: one row per message, with a stable message ID, `conversationId`, ordinal/sequence, role, and other scalar fields. Add an index on `(conversationId, sequence)` and any ordering index needed for the conversation list.
- `message_versions`: one row per message version, linked to `messages`, preserving version order, content, generation error and all scalar version metadata.
- `message_parts` (or equivalent normalized child tables): represent structured content blocks and attachment/media references with explicit type/order and typed columns. Do not serialize a heterogeneous message/part list back into a JSON column.
- `tool_activities`: normalized rows linked to the relevant message/version, preserving tool name, status, input/output/result references, timestamps and display metadata. Normalize nested tool results and parameters into relational rows where the domain model requires them; do not keep whole activity objects as JSON blobs.
- `conversation_summaries` or normalized summary tables: store summary fields as ordinary columns and child rows, rather than `summaryJson`.
- Add foreign keys and cascade rules only where deleting the parent should truly delete its children. Preserve file cleanup/reference semantics; do not cascade-delete attachment files merely because a row is removed without checking shared references.

The final schema must preserve the current model's full fidelity. Inspect `ChatMessage.kt`, `ResponseVersions.kt`, related media/tool models, and all read/write call sites before settling the exact columns and child tables.

### 2. Provider and MCP records: separate encrypted auth from public configuration

This is a required architecture decision, not an optional optimization. Provider and MCP records must be split into independently loadable non-secret records and encrypted authentication records.

- `model_providers` stores non-secret provider data as ordinary SQLite columns: ID, display name, API type, base URL, icon, sort order, group ID and other non-secret configuration fields. Do not encrypt the entire provider row or its non-secret configuration.
- `provider_auth` (or a consistently named equivalent) stores authentication material separately, with its own stable ID, encrypted secret payload/typed secret fields, and the encryption format/version metadata needed for safe upgrades. `model_providers.authId` is nullable and references this record only when authentication is actually configured. If a provider has no auth, store `NULL` and do not create an empty/dummy auth row.
- `mcp_servers` stores non-secret server metadata and configuration as ordinary SQLite columns, including name, protocol, URL where the URL is not itself a credential, sort order, icon and other non-secret fields. Credentials, authorization headers, OAuth tokens and other sensitive authentication values belong in a separate encrypted `mcp_auth` (or shared auth table with an explicit owner/type) table. `mcp_servers.authId` is nullable and references an auth record only when authentication is actually configured. If an MCP server has no auth, store `NULL` and do not create an empty/dummy auth row. Do not encrypt the whole MCP record just because it has credentials.
- Decide explicitly whether URL/query parameters or custom headers contain secrets. Any credential-bearing URL/query data must be moved to the encrypted auth record; a non-secret endpoint URL remains raw in the server/provider row. Never expose secret values in list queries, logs, debug output or UI metadata.
- Keep the non-secret provider/MCP metadata queries independent from encrypted auth reads. Conversation-independent provider/server lists must be able to render as soon as their raw rows are read; they must not join, decrypt or wait for auth rows. Load/decrypt auth only when a screen explicitly needs to edit/reveal authentication or when a network operation requires it. A network operation must await the selected record's auth specifically, but must not wait for unrelated providers' or servers' auth. A `NULL authId` means authentication is not configured; it is distinct from an auth record that exists but has not yet loaded, is unreadable or failed decryption.
- Use distinct model types or clearly separated fields for metadata and loaded credentials, so an unloaded auth record cannot be mistaken for an empty/unauthenticated configuration. Represent auth load state explicitly (not requested/loading/loaded/error) and surface actionable errors if decryption fails. Never silently replace unavailable credentials with defaults and send a broken request.
- Keep credentials encrypted with Android Keystore-backed authenticated encryption. Do not move secrets into plaintext SQLite, DataStore, logs, backups or ordinary provider configuration columns. Preserve the existing encryption guarantees while removing the current coupling between loading a provider/server and decrypting its secrets.
- `provider_models`: replace `modelsJson` with one row per discovered model, using scalar columns and typed metadata rows for extensible capabilities rather than one opaque JSON document.
- Replace `chat_profiles.configJson`, `activeMcpJson`, and `activeModelToolsJson` with normalized configuration and relation tables. Use join tables for selections, with ordering columns where order matters.
- Replace `mcp_servers.cachedToolsJson` with normalized cached-tool/schema rows. Tool schema properties and required-parameter lists must be reconstructable without storing the whole schema as one JSON string.
- Replace `speech_services.optionsJson` with explicit columns and/or child option rows for `extraBody` and other supported options. Preserve provider-specific extensibility through typed rows rather than a JSON blob.
- Keep `DataStore`/preferences out of the Room schema migration unless the audit shows the requested database scope includes those stores. Do not convert unrelated preference formats just because they use JSON; document their separate storage boundary.

### 3. Import/export and API boundaries

- Keep JSON import/export formats stable unless a separate, explicitly versioned format change is approved.
- Add mapping layers between the relational domain model and external JSON DTOs/API request objects. JSON may still be used in memory and over the network; it must not be the canonical on-disk representation for the migrated Room data.
- Update any logic that searches inside `messagesJson` (for example `ConversationDao.mentionsFile`) to query normalized attachment/reference tables with indexed columns.

## Loading and repository architecture

1. Split conversation metadata from conversation content. The conversation-list flow must query only `conversations` metadata and must not join/fetch message bodies unnecessarily.
2. Provide targeted APIs such as `observeConversationSummaries()` / `observeConversationList()` and `getConversationById()` / `observeMessages(conversationId)`; choose names consistent with existing conventions.
3. Fetch a conversation's messages and child rows only when that conversation is opened or when an operation explicitly requires its full context.
4. Avoid an application-lifetime `ImmediateState<List<Conversation>>` containing all message histories. Keep lightweight metadata state if needed; scope message state/cache to opened/recent conversations and release it when appropriate.
5. Sending a message must resolve the active conversation, selected provider/model, and required context directly and deterministically. It must not wait for the entire conversation list or all histories to load. If data is not ready, load only the exact records needed and expose an explicit loading/error state rather than silently using an empty provider/history list.
6. Writes must update only affected rows in a Room transaction. Appending one message must not serialize and rewrite the entire conversation history. Multi-row operations such as retry/version replacement and conversation deletion must remain atomic.
7. Use `PagingSource`/Paging 3 or a measured, stable keyset/limit-offset strategy for long message histories if the UI needs incremental history display. Do not add paging complexity to small metadata lists without evidence it helps.
8. Define a clear ordering contract for messages, versions, content parts, tools and attachments. Never depend on SQLite's implicit row order.
9. Ensure Room Flow invalidations only reload the affected metadata/messages needed by the observer. Do not re-decode every conversation after any single conversation changes.
10. Measure startup, first-screen readiness, open-conversation latency, and first-send readiness before and after. Avoid arbitrary delays or fixed sleep-based initialization.

## Migration strategy

### Phase 0 — Inventory and baseline

- ~~Read `AGENTS.md` and inspect current entities, DAO queries, repositories, model data classes, backup/import/export code, and all call sites.~~
- Inventory every JSON-backed Room column and identify its full schema, default/null semantics, secret handling, ordering, and relationships.
- Add/identify remaining fixtures for an empty database, long chats, all provider types, and earlier supported schemas; the representative v9 rich-history fixture is tracked as completed above.
- Capture baseline timings and counts on a realistic database: database open, first usable screen, first-send readiness, conversation list display, and opening a large conversation. Record row counts and representative data checksums for migration verification.

### Phase 1 — Design schema and migration tests

- ~~Finalize normalized entities, foreign keys, indexes, delete semantics, and DAO access patterns.~~ Completed for the conversation/message migration slice; provider/MCP and remaining JSON-backed tables are still pending.
- ~~Increment the Room database version from 9 and implement sequential migrations from every supported schema version; do not replace the existing migration chain.~~ Room is now version 10 with the existing v1→v9 chain preserved and `MIGRATION_9_10` registered.
- ~~Write migration tests against real v9 schema fixtures and earlier supported schema versions. Include malformed/legacy JSON cases and encrypted values.~~ The v9 fixture and existing v1/encryption tests pass through the new schema; broader earlier-version fixture coverage remains pending.
- ~~Ensure migration is transactional and idempotent where applicable. If a row cannot be decoded, do not silently discard it: preserve the original value in a documented recovery/quarantine mechanism or fail migration safely with an actionable error.~~ Malformed conversation and summary payloads are preserved in `conversation_migration_quarantine`; opening an affected conversation raises `ConversationMigrationException`.
- Do not run full-table secret re-encryption or unrelated expensive scans on every database open. Any required secret conversion must be versioned, bounded, and performed once in an explicit migration or resumable migration step.

### Phase 2 — Normalize conversations first

- ~~Create relational message/version/part/activity/summary tables and required indexes.~~ Implemented for messages, versions, tool activities, message/tool file references, summaries, and quarantine in the conversation slice.
- ~~In a migration transaction, read each legacy `messagesJson` and `summaryJson`, decode it, and insert equivalent rows while preserving order and IDs where present.~~ Implemented by `MIGRATION_9_10`; message/version/file/tool order is stored explicitly by sequence/index columns.
- ~~Validate counts and content fidelity before removing the legacy columns. Use a staged migration if SQLite/Room constraints make a single migration too risky.~~ The migration removes only the conversation JSON columns after conversion; the fixture asserts relational counts/content and `PRAGMA foreign_key_check`.
- ~~Migrate `mentionsFile` and all file-reference cleanup checks to relational queries.~~ `ConversationDao.mentionsFile()` now searches both relational reference tables while preserving bare, `tool-file:` and stored path forms.
- Add repository APIs that read conversation metadata separately from full messages; change save/update/delete paths to use targeted row operations. The metadata/list and targeted-load APIs are present; UI caller migration and append-only write optimization remain.
- ~~Ensure the migration is safe for large histories: avoid materializing the entire database into one giant in-memory object. Process conversations in bounded batches inside a controlled migration strategy while preserving atomicity/data integrity.~~ Migration iterates source conversations one row at a time inside Room's migration transaction; one legacy conversation is decoded at a time.

### Phase 3 — Remove eager loading and implement lazy loading

- ~~Replace the `SELECT * FROM conversations` plus full `entityToModel()` flow with a metadata-only conversation-list query.~~ Implemented for the active sidebar/navigation path; `allConversations` remains only as a cold compatibility API.
- ~~Load the selected conversation's message rows and related child rows on demand.~~ Implemented through `ConversationRepository.loadById()`.
- ~~Refactor `ChatViewModel` and all consumers that currently assume `allConversations` includes message bodies. Audit uses such as stored conversation selection, retries, summaries, upload reference cleanup, deletion, and generation context.~~ Active UI callers now use metadata or targeted reads; export remains an explicit full-history boundary.
- Ensure first-send behavior works immediately after app launch without waiting for unrelated history. The UI no longer subscribes to full history, but end-to-end first-send timing and targeted append writes remain to be measured and optimized.
- Implement incremental history loading for large conversations if the measured UI/rendering path requires it.

### Phase 4 — Split provider/MCP metadata from encrypted auth, then normalize remaining data

- Create separate encrypted auth tables and nullable `authId` references for providers and MCP servers. Only create an auth row when authentication is configured; use `NULL` otherwise. Define foreign-key deletion/update behavior so removing auth clears the owning row's reference atomically and never leaves dangling references. Keep non-secret metadata/configuration in raw relational columns.
- Migrate existing encrypted and legacy plaintext credential fields without exposing secrets or losing credentials. Preserve auth records even if a metadata row is temporarily unavailable; validate references and delete semantics.
- Refactor repository APIs and state flows so provider/MCP metadata loads independently. Do not decrypt auth as part of `allProviders`, `allServers`, startup initialization or list rendering. Fetch credentials on demand for editing or the specific network request that needs them.
- Ensure a missing/unreadable auth record has an explicit state/error and is never treated as a valid empty auth configuration. Do not block non-secret list UI on unrelated auth failures.
- Migrate profile settings and selection relations.
- Migrate provider model catalogs and extensible model metadata.
- Migrate MCP cached tool definitions, schemas and required-parameter metadata.
- Migrate speech service options and provider-specific fields.
- Update all repository mappers, write paths, queries and tests. Remove obsolete JSON columns only after all rows are converted and verified.
- Keep external import/export JSON compatibility and add round-trip tests proving export → import preserves equivalent domain data.

### Phase 5 — Cleanup and performance validation

- Remove legacy entity fields, codecs and DAO methods that are no longer used for Room persistence. Do not remove JSON serializers needed for APIs, import/export or unrelated preferences.
- Verify no application object/list is serialized to a JSON string for storage in a migrated Room table. A source search for `*Json` columns and `encodeToString` in Room repositories should be reviewed and every remaining occurrence explicitly justified. Verify provider/MCP metadata queries do not read or decrypt auth records, and that only the selected operation loads the required credentials.
- Run migration tests from every supported schema, repository tests, chat/tool/attachment tests, and app compile/build checks.
- Compare baseline and new timings on the same dataset. Verify startup work scales with metadata, not total historical message volume, and opening one conversation scales with that conversation's data rather than all conversations.
- Inspect database contents after migration and compare row counts, ordered contents and references against the legacy data. Verify no orphan rows, missing attachments, duplicate versions or broken provider/profile links.

## Required tests and acceptance criteria

### Data integrity

- Upgrading v9 and earlier supported databases preserves every conversation and message, version, tool activity, attachment reference, summary, provider/model/profile setting, MCP setting, speech option and ordering field.
- Nulls, empty strings, defaults, encrypted fields, legacy generation-error recovery, interrupted tool statuses and malformed historical payloads have explicit tested behavior. Provider/MCP metadata remains readable if an unrelated auth record is unavailable, while credential-required operations report a clear auth/decryption error.
- Deleting a conversation or message cleans relational rows correctly without deleting files still referenced elsewhere.
- JSON import/export round-trips remain compatible and preserve all fields.

### Lazy loading and responsiveness

- Cold start does not select or decode all message histories.
- Conversation list renders using metadata without waiting for message bodies.
- Opening one conversation loads only that conversation's messages and required child rows.
- With many large conversations in the database, startup cost does not grow linearly with total message-body size; measure and document the result.
- Sending immediately after opening the app works without an arbitrary wait and does not depend on unrelated history loading.
- Appending a message writes only the affected message/child rows and required conversation metadata, not a rewritten JSON history blob.
- Large history scrolling loads incrementally if paging is enabled and does not freeze the UI.

### Technical checks

- No database reset, data-loss shortcut, or silent fallback that replaces unreadable history with an empty conversation.
- No main-thread database I/O or whole-history JSON serialization on the normal message-write path.
- Foreign keys and indexes are enabled and verified against actual queries.
- `git diff --check`, migration tests, repository tests, `:app:compileDebugKotlin`, and a debug build pass.
- Verify on a device/emulator using a realistic existing database, not compile-only validation.

## Implementation order

1. Inventory and baseline measurements.
2. Schema design and migration fixtures/tests.
3. Normalize conversation/message storage and prove data-preserving migration.
4. Switch repositories and UI to metadata-first, on-demand message loading.
5. Split provider/MCP metadata from encrypted auth and verify independent loading.
6. Normalize remaining JSON-backed Room entities and preserve encryption/import/export.
7. Verify first-send readiness and optimize measured bottlenecks; run migration, regression, performance and on-device checks and document results.

Do not start by deleting the existing schema, rewriting all repositories at once, or changing UI behavior unrelated to loading and persistence. Each phase must leave the app buildable and data-safe. Do not commit or push unless explicitly requested.
