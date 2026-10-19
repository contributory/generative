# Database Migration Plan: Lazy Loading and Relational SQLite Storage

> **Progress tracking:** [TODO.md](TODO.md) is the single source of truth for completed work, pending work, and warnings. Update `TODO.md` when implementation status changes. Do not add progress checklists or duplicate TODO entries to this document; `PLANS.md` defines the architecture and requirements only.

## Goal

Refactor persistence so launching MultiGateway does not load and deserialize every conversation and message. Use relational SQLite tables for persisted structured application data, with Room entities, DAOs, foreign keys, indexes, explicit migrations, and data-preserving repository operations.

The app must become usable quickly after launch. Sending a message immediately after opening the app must not depend on unrelated conversation history finishing loading.

## Non-negotiable requirements

- **Preserve user data.** Upgrade existing databases in place. Preserve conversation and message IDs, ordering, timestamps, contents, message versions, attachments and references, tool activity/results, summaries, reasoning settings, provider/model/profile links, MCP settings, speech options, and all other persisted configuration.
- **Never reset or recreate the database to simplify migration.** The project owner confirmed schema v10 was never released or deployed. The intended final schema is v10, upgraded directly from actually released schema v9 and supported earlier schemas. Keep the existing v1→v9 migration chain; do not add an artificial upgrade from an unreleased v10 unless a concrete compatibility requirement is demonstrated.
- **Load lazily.** Startup and conversation-list queries must not fetch or decode every message history. Load conversation content and child rows only for the selected conversation or an operation that explicitly requires them.
- **Use relational storage.** Do not store whole application objects/lists as JSON strings in migrated Room tables. Structured extensible JSON may use `structured_json_documents` and `structured_json_nodes` when a stable typed schema is inappropriate; each such use must have a clear purpose and preserve node order and value types. JSON for model APIs, import/export, and other external formats remains supported.
- **Keep the UI responsive.** Database I/O and record mapping run off the main thread. Opening the app, switching conversations, opening a conversation, and sending the first message must not wait for unrelated database data or credentials.
- **Preserve existing behavior.** Maintain chat semantics, active-version selection, retry/regenerate behavior, tool execution and history, attachments, summaries, provider/model configuration, MCP settings, speech configuration, ordering, and import/export compatibility.
- **Follow `AGENTS.md`.** Preserve the package/module architecture and avoid unrelated UI refactors.
- **Do not overuse Gradle during implementation.** Inspect source and use lightweight non-Gradle checks while editing. After related implementation work is complete, perform a consolidated verification pass that groups applicable lint, unit tests, instrumentation/migration tests, compilation, and build into as few Gradle invocations as practical. Do not launch Gradle after every file or small fix. If the final pass finds failures, batch the fixes before rerunning relevant checks; make an earlier Gradle run only for a specific blocker that cannot reasonably be diagnosed otherwise or when explicitly requested.

## Target data model

Use stable IDs, deliberate foreign-key/delete behavior, indexes aligned with real queries, and explicit ordering columns. Confirm exact fields against the domain models and all read/write call sites before finalizing the schema.

### Conversations and messages

- `conversations` stores metadata only, such as ID, title, timestamps, token count, provider/model/profile IDs, reasoning effort, and summary metadata.
- `messages` stores one row per message with its stable ID, conversation ID, sequence, role, and scalar fields. Index the conversation/sequence access pattern.
- `message_versions` stores each version separately and preserves version order, content, generation errors, provider/model metadata, reasoning, timestamps, and other version-specific fields.
- Content parts and attachment/media references use typed child tables with explicit type and order. Do not persist a heterogeneous message/part list as one JSON column.
- `tool_activities` and their file/schema/result children preserve tool identity, status, arguments, results, display metadata, and order without storing whole activity objects as JSON blobs.
- Summary data uses ordinary columns and relational rows instead of a `summaryJson` column.
- Foreign keys cascade only where child rows should truly be deleted with their parent. File cleanup must respect references from other messages or conversations; deleting a row must not blindly delete a still-referenced physical file.
- Legacy malformed message/summary payloads must not silently become empty histories. Preserve exact recoverable source data in the migration quarantine path and make affected conversations report an actionable recovery error.

### Provider and MCP metadata versus encrypted authentication

Provider/MCP metadata and authentication must be independently loadable. Do not encrypt whole provider or MCP records just because they contain credentials, and do not make metadata screens wait for unrelated authentication reads or decryption.

- `model_providers` stores non-secret data as ordinary SQLite columns, including IDs, names, API type, non-secret base URL, icon, sort order, group ID, and non-secret model configuration.
- `provider_auth` stores provider credentials and sensitive values in a separate encrypted payload or typed encrypted fields, with stable IDs and format/version metadata. `model_providers.authId` is nullable and references an auth record only when authentication is configured. No-auth providers must use `NULL`, not an empty or dummy auth row.
- `mcp_servers` stores non-secret server metadata and settings in ordinary columns. Credentials, authorization headers, OAuth tokens, private endpoint components, and other sensitive authentication values belong in a separate encrypted `mcp_auth` table or an explicitly typed shared auth table. `mcp_servers.authId` is nullable and must be `NULL` when no authentication is configured.
- Classify URLs, query parameters, and custom headers by whether they contain secrets. Keep ordinary endpoint URLs in metadata; move credential-bearing URL/query components and sensitive headers into encrypted auth. Never expose secret values in list queries, logs, debug output, backups, or UI metadata.
- Metadata reads must not join, decrypt, or wait for auth records. Load/decrypt only the selected record's auth when an edit/reveal screen or a network operation needs it. An operation must not wait for credentials belonging to unrelated providers or servers.
- Distinguish “auth not configured” from “auth not loaded,” “auth unreadable,” and “decryption failed.” Use explicit credential-load state and actionable errors; never treat unloaded or unreadable credentials as an empty authorization and send a broken request.
- Preserve Android Keystore-backed authenticated encryption. Credentials must not move into plaintext SQLite, preferences, logs, backups, or ordinary metadata columns.
- Discovered provider models, model selections/configuration/values, profile relations, MCP cached tools/schema, and speech options/auth should use normalized rows and typed columns where appropriate. Structured JSON node tables are allowed only for extensible data that does not have a suitable stable typed schema.

### Import/export and API boundaries

Keep external JSON import/export formats stable unless a separately versioned format change is approved. Use mapping layers between relational domain data and external JSON DTOs or API requests. JSON may be used in memory and over the network, but it must not become canonical persistence for the migrated Room data. Data export must explicitly load the full records and credentials it is authorized to export; metadata-only objects must not silently produce incomplete exports.

## Loading and repository architecture

- Separate conversation metadata from conversation content. The conversation-list flow queries only conversation metadata and does not join or decode message bodies.
- Provide targeted APIs consistent with repository conventions, such as observing conversation summaries/list metadata and loading one conversation or its messages by ID.
- Fetch message versions, tools, attachments, summary data, and other child rows only when the selected conversation or an explicit operation needs them.
- Avoid an application-lifetime `ImmediateState<List<Conversation>>` containing every history. Keep lightweight metadata state and scope full-message state/cache to opened or recent conversations, releasing it when appropriate.
- Sending a message resolves the active conversation, selected provider/model, and required context directly and deterministically. If a needed value is not ready, load that specific value and expose explicit loading/error state rather than temporarily using an empty provider/history list.
- Writes update only affected rows inside a Room transaction. Appending one message must not rewrite every existing message and child row. Retry, version replacement, deletion, and other multi-row operations must remain atomic.
- Use `PagingSource`/Paging 3 or a measured, stable keyset/limit-offset strategy for long message histories when UI measurements show incremental loading is necessary. Do not add paging complexity to small metadata lists without evidence that it helps.
- Define explicit ordering for messages, versions, parts, tool activities, and attachments; never depend on SQLite's implicit row order.
- Room Flow invalidations should reload only the affected metadata/messages, not decode every conversation after one conversation changes.
- Measure cold startup, first-screen readiness, conversation-list readiness, selected-conversation latency, and first-send readiness before and after changes. Do not hide initialization races with arbitrary delays or fixed sleeps.

## Migration design and data safety

The migration must transform each supported released schema directly into the intended final schema without replacing the established migration chain, losing data, or inventing an upgrade requirement for an unreleased version. Conversation and configuration changes belong in the combined v9→v10 migration path.

Legacy JSON columns such as `messagesJson`, `summaryJson`, provider configuration/model catalog fields, profile fields, MCP tool cache fields, and speech option fields are migration inputs—not evidence that the final schema should keep the same JSON blobs. Inventory their null/default semantics, relationships, secret handling, nested values, and ordering before mapping them to relational rows.

Migration work must be transactional. Preserve IDs and order, copy all required fields, and verify source/target row counts and content fidelity before dropping source tables or columns. Check foreign keys and relationships before and after conversion. If required data is malformed or cannot be represented safely, abort and roll back the full migration, or preserve the exact recoverable original through an appropriately protected quarantine path. Optional cache failures may be quarantined only when the source remains recoverable. Never silently substitute defaults for required user configuration.

Any conversion of credentials must happen as a versioned migration step, not as repeated full-table re-encryption or expensive scans on every database open. Keep credentials encrypted through conversion, support legacy encrypted/plaintext forms intentionally, and do not leak private URL/query/header values in diagnostics. Migration recovery data must be protected according to sensitivity and must not be exposed by normal list queries.

## Acceptance requirements

Data integrity means every supported released schema upgrades without data loss; null, empty, default, ordering, encrypted, malformed, interrupted-generation, and legacy error-recovery cases have explicit behavior; message/tool/file references remain valid; deletes clean relational children while preserving shared files; and external JSON import/export remains compatible.

Lazy-loading correctness means cold start does not select or deserialize every message history, the conversation list renders from metadata, opening a conversation loads only its own content and necessary child records, and first-send readiness does not depend on unrelated histories. Startup cost should scale with metadata rather than total historical message-body size. Message append/update must write only changed relational rows rather than delete and reinsert the whole conversation. Paging is required only when measured history-rendering behavior warrants it.

Technical correctness means required foreign keys and indexes match the query patterns, there are no orphan rows or broken provider/profile links after migration, normal write paths avoid main-thread database I/O and whole-history JSON serialization, and malformed or unreadable history never silently becomes an empty conversation. Source-level and host-SQL checks are not substitutes for Room migration tests, repository/chat/tool/attachment tests, Kotlin compilation/build, and a device/emulator check against a realistic existing database.
