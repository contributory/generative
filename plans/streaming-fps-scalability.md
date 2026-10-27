# Streaming FPS and Markdown Scalability Plan

## Goal

Make whole-app frame pacing remain stable while an assistant streams, including long answers and very large individual Markdown blocks. A stream update must not rebuild or republish the full conversation, rescan unchanged history, reparse the entire accumulated tail, or compose every Markdown block.

## Current implementation status (2026-10-26)

The source implementation now covers the main stream-state, parser, virtualization, and cache paths, but the plan is **not fully verified or complete**. Ordinary token deltas use a separate message-scoped stream state, immutable segmented text, a 50 ms minimum render interval, and one coalesced commit for all text received in each interval, plus terminal flushes, stream metrics, incremental `<think>` splitting, and a left-to-right reveal. Structural revisions prevent content-only response changes from rebuilding message-ID/unanswered lists. Lifecycle boundaries and explicit checkpoints still create/persist conversation snapshots; a tool call is also a display boundary that immediately commits all text received so far so pre-tool text is not hidden while the tool runs and stored tool offsets match the displayed text; the existing product caps remain 512,000 answer characters, 64,000 reasoning-signature characters, and 128,000 rendered Markdown characters.

The active Markdown path consumes append-only segments through JetBrains' streaming parser, shares stable AST nodes, caps the unstable tail at 8,000 characters, and renders the whole overflow block as plain text after overflow (line-aligned chunks, so completed chunks keep their layout) until the final Markdown render; the overflow block is never shortened or hidden behind a preview, and it does not progressively parse/render as Markdown. It renders source slices only for mounted blocks. The compatibility chunk path now avoids copying the whole stable prefix for generation-scoped append-only input, parses only a bounded tail, renders an oversized active tail in full as plain text, and keeps freezing stable chunks while the tail is oversized so formatting resumes after a long block ends. Static top-level blocks, long list items, table rows, and frozen chunks use viewport-based `SubcomposeLayout` virtualizers rather than nested vertical lazy lists inside chat. Very large paragraphs, code blocks, and raw HTML blocks use bounded previews and lazy dialog rows; exact full source extraction is delayed until the dialog/copy action. Oversized static raw HTML blocks use the same bounded preview/expand path, including when nested in virtualized list items. The fade skips its draw animation when Android reports system animator animations disabled; its Compose draw-only implementation leaves text semantics/selection paths intact, but device accessibility behavior is not yet exercised.

The in-memory Markdown AST cache is a soft weighted LRU capped by 600,000 source characters and keeps a precomputed fingerprint with its AST. The persistent cache stores versioned, plaintext per-message AST/reference-link records outside `cacheDir`, with a disk budget, atomic writes, deletion epochs, and stale-writer tests. Layout-height variants are bounded and profile-keyed in memory, but they are not persisted. Instrumentation counters and unit/Compose tests have been added, including parser differential fixtures at every split point and randomized chunk boundaries.

**Verification remains incomplete:** Java and the Android SDK are present, but the Gradle 9.5.0 wrapper distribution is not available as a complete local install, so the full project build, Gradle unit-test suite, lint, instrumentation tests, and device profiling were not run. Focused source/test checks do not substitute for project-wide Gradle verification. No device baseline, warm/cold profile, FPS, frame-time, or latency result is available. Progressive rich Markdown parsing/rendering for oversized active HTML (the expanded source is currently plain text), persistent layout-variant/scroll-anchor metadata, runtime accessibility/reduced-motion validation, and device validation of the current message-row integration remain open. Do not treat source checkmarks or test additions as passed checks, and do not claim an FPS improvement.

This plan supersedes the completed, now-removed Markdown chunking plan. Reuse the existing splitter/chunked-renderer work where it passes the stronger invariants below; do not redo it mechanically. This plan's progress checklist is the source of truth for the broader streaming-FPS work. Cache identity and persistence are **message-scoped**, never a serialized whole-conversation render snapshot. A checked item means source implementation/tests were added; it does not imply they compiled or ran. `[~]` means partially implemented with an explicit gap described below.

## Important performance contract

- **No app-wide streaming recomposition.** Streaming deltas must not invalidate navigation, drawers, unrelated screens, stable message cards, or stable Markdown blocks.
- **No whole-Conversation publication per delta.** The active response is streamed as a separate, narrow state model. Publish/update a final conversation snapshot at lifecycle boundaries, not for each token batch.
- **Only the changed visible stream region may update.** The active tail and any blocks whose presentation genuinely changed may recompose. A literal zero-recomposition guarantee for changing text is not realistic in Compose; the contract is zero invalidation of unchanged scopes, not zero recomposition anywhere.
- **Time-windowed streaming, not newline- or character-count batching.** Network fragments are arbitrary and may contain any number of lines. Publish the first available text immediately, then enforce a 50 ms minimum interval between visible commits. All text received during one interval is committed together, with no per-flush character limit. Tool boundaries and terminal events flush pending text immediately. Measure commit size, input-to-visible latency, and frame time before changing the pacing interval.
- **Per-update work scales with the new delta and visible window, not accumulated answer length.** Do not call `toString()`, copy, scan, parse, or rebuild data proportional to the full response on each ordinary stream tick.
- **Virtualize Markdown blocks.** Keep only blocks intersecting a bounded render/prefetch window composed. Start near 200% of viewport height as a tunable benchmark value, not a universal minimum. Off-window blocks must dispose their Compose subtrees and be composed again only when they approach the window.
- **Incremental parsing must also work within a single huge block.** Freezing completed top-level blocks alone is not sufficient for a long paragraph, list, HTML block, or code fence.
- **Bound retained memory.** Cache immutable parse/render models per message/version with explicit RAM and persistent-disk budgets and LRU eviction. Never keep every off-screen Compose subtree alive just to preserve its appearance.

A long response inherently occupies storage proportional to its retained content. The target is bounded work per update and per frame, not constant total memory for an arbitrarily large transcript. If retaining extremely large transcripts becomes a requirement, add a storage/spill strategy instead of promising that one billion characters can live in RAM at constant cost.

## Cross-session and scroll reuse contract

The requirement applies to the whole app lifecycle, opening a conversation, and scrolling through its history—not only to active streaming. Interpret “render again” as avoiding unnecessary Markdown parsing, preprocessing, and composition of unchanged/off-window content; Android may still redraw pixels or recompose a scope when its actual inputs or layout environment change.

- **Cold/warm app start:** restore canonical message data, then hydrate valid per-message render-cache entries off Main. Keep persistent render entries in app-private storage that Android does not treat as disposable cache (for example, `noBackupFilesDir/render-cache`) so normal app restarts do not routinely lose them. Do not eagerly parse every historical message before showing the first useful frame; prioritize visible and near-visible messages, and load the rest lazily. Temporary files may use `cacheDir`; the durable render entries must not rely on `cacheDir` surviving OS cleanup.
- **Open/reopen conversation:** load independent entries for each message/version and its block/layout variants. A conversation is only a grouping of messages; never serialize the whole conversation or one conversation-wide render snapshot as the cache unit. Cache hits skip full Markdown parsing; misses parse asynchronously and write only the affected message entry.
- **Scroll through history:** preserve stable message/block keys and reuse cached models. Compose only the visible and bounded prefetched window; begin with a measured target near 200% of viewport height rather than treating it as a universal minimum. Dispose off-window Compose subtrees while retaining eligible per-message/block data in bounded memory/disk caches. Scrolling back to a cached block must not trigger a full-message/history reparse.
- **No global invalidation:** opening a screen, changing scroll position, or receiving a stream delta must not invalidate unchanged messages just because a parent/list reference changed. Structural changes are tracked separately from content changes.
- **Deletion and edits:** deleting a message/conversation must cancel pending parse/write jobs, invalidate message-scoped cache entries, remove their plaintext files, and prevent stale content from reappearing. Edits, regeneration, version switches, and parser/render-option changes use new keys or explicit invalidation. Each writer checks a deletion/version epoch immediately before atomic rename/commit; an obsolete writer must not recreate an entry after deletion. If physical cleanup fails, tombstones/epochs must make entries unreachable and cleanup must be retried; distinguish logical invalidation from confirmed physical deletion.
- **Cache is an optimization, not truth:** canonical persisted conversation content determines what exists. Missing, evicted, stale, or corrupt cache entries are safe misses. All disk I/O and cache parsing/hydration stay off Main.
- **Measure the promise:** test cold app start, warm app start, opening an old conversation, scrolling down and back up, deletion, and cache loss. Count full parses, cache hits/misses, compositions of stable message/block IDs, and time-to-first-visible-content. Do not claim “zero rendering” where Compose must mount a newly visible subtree or Android must redraw.

## Layout/position computation cache and plaintext-cache requirement

- Cache reusable per-message layout-preparation results as well as Markdown parse models: block height estimates/measured sizes keyed by a compatible layout profile, stable block-to-layout mappings, and scroll-anchor restoration metadata. Do not persist process-local Compose objects or assume `TextLayoutResult`/line-break internals can be serialized. Treat global list offsets and prefix sums as derived index state: rebuild/update them from current message order and cached per-item heights, never trust persisted absolute offsets after insertion/deletion/reorder. Update only the affected range using an indexed prefix-sum structure where benchmarks justify it.
- Every layout cache entry must be keyed by message ID plus immutable content revision/hash, block ID, available width/constraints, density, font scale, font family/weight/size, line height/spacing/letter spacing, layout direction, relevant theme/render options, and renderer version. On input changes, select a compatible variant or invalidate only the affected measurement. Never reuse incompatible pixel coordinates and risk misplaced or overlapping content.
- **Keep multiple layout variants per component/block.** A component's logical identity is not its layout-cache identity: store separate reusable variants for distinct combinations of available width, text/font size, font scale, density, typography/line height/letter spacing/weight, layout direction, relevant theme/renderer options, and content/parser version. A rotation, split-screen resize, display-density change, font-scale change, typography change, or relevant theme change must select a matching variant rather than overwrite or accidentally reuse an incompatible one.
- Build a deterministic, canonical layout-profile key from the inputs that truly affect that cached result; normalize units/precision consistently to avoid duplicate variants caused only by floating-point noise. Theme/colors belong in the key only for cached values they affect; color-only changes need not invalidate geometry. Include width constraints and height/measurement constraints when those affect the calculation.
- If no exact compatible variant exists, compute that variant off Main, show a safe estimated/placeholder layout if needed, then atomically store it alongside other profiles. Do not silently use a “nearest” profile's pixel positions or measured heights as exact data. Reuse theme-independent parse/AST data across layout profiles wherever possible, so a new layout variant doesn't force Markdown reparsing.
- Bound variant proliferation with a weighted per-component/global LRU budget and eviction; retain frequently used profiles while allowing cold variants to be dropped safely. Cache eviction must not delete canonical chat text. Track variant hits/misses and profile-specific layout recomputations.
- Persist reusable measurements in the app-private persistent render-cache directory only when their profile key fully describes the measurement inputs. Compose may still need to mount a visible item and draw a frame; the cache avoids redundant parsing/measurement, not platform drawing.
- Keep position/index lookup and scroll anchoring incremental: use stable IDs and cached offsets/prefix sums or an equivalent indexed structure. An insertion/deletion should update the affected range, not scan and recompute every historical item on every scroll frame. Verify the chosen data structure against real list sizes and variable-height blocks.
- **Plaintext cache is mandatory.** Do not encrypt cache files or cache payloads, and do not add application-level encryption/decryption for this cache. Use a documented, versioned, non-encrypted serialization format. Store persistent entries under app-private `noBackupFilesDir/render-cache` (or an equivalent non-disposable app-private directory); use `cacheDir` only for scratch/temp files. Restrict entries to rendering data and never copy credentials, API keys, or unrelated secrets into them. This requirement applies to this render/layout cache, not to the canonical conversation database or any other storage/security policy.
- Add tests and counters for layout-cache hit/miss, measurement/offset recomputation counts, invalidation after width/font-scale/typography/content changes, warm-open reuse, scroll-back reuse, and deletion. Verify cache payloads are plaintext by inspecting the format in tests without logging user conversation content.

## Streaming character-batch contract

- Separate network/input buffering, character framing, and UI publication. Network chunks are arbitrary; count Unicode code points across fragment boundaries, treating whitespace and newline characters as ordinary characters. Keep surrogate pairs intact even when split between input fragments.
- Publish the first available text immediately, then enforce `STREAM_COMMIT_INTERVAL_MS` (50 ms) as the minimum interval between visible commits. All pending text is coalesced into one commit per window, with no per-commit character limit.
- A tool boundary explicitly flushes all pending text, including a short chunk, so the text preceding the tool call is visible. Completion/cancel/error immediately flushes all pending text; make finalization idempotent so duplicate terminal signals cannot duplicate content.
- Character boundaries are rendering cadence only, not Markdown block boundaries. Preserve parser state across chunks; reference definitions and other forward dependencies may affect earlier blocks, so cache/freeze only content proven stable under the parser dependency model. Incremental rendering must preserve links, selection, accessibility order, and exact copied text.
- Test immediate first publication, 50 ms pacing/coalescing, no per-commit character cap, Unicode preservation, and boundary/terminal flushes. Record commit size and input-to-visible latency.

## Message-scoped cache identity, layout validity, and deletion protocol

- A cache entry belongs to one message version, not to a conversation. Use a namespace such as `(conversationId, messageId, contentRevision/contentHash, parserSchema)` to avoid ID collisions while still allowing one message to be read, updated, evicted, or deleted independently. A conversation-level index may map message IDs to entry keys, but it is rebuildable metadata—not a cached conversation render model and never the source of truth.
- Separate cache layers: (1) content-derived parse/block model keyed by message content + parser/render schema; (2) per-message/block layout variant keyed by the exact layout profile; (3) volatile list-position index derived from current message order and current heights. Theme-independent parse models must survive width/font/theme changes.
- Do not persist global absolute offsets as authoritative data. Persist a scroll anchor `(messageId, blockId, intra-item offset)` and reusable per-item height measurements. Rebuild/update list offsets/prefix sums from current order. Insertions/deletions/reorders invalidate only the affected index range; use an indexed structure only if profiling shows linear updates are too expensive.
- A layout profile includes every input that affects the stored measurement: width and constraints, density, font scale, font family/weight/size, line metrics, direction, relevant theme/render settings, content version, and renderer version. Normalize profile values deterministically. Select only an exact compatible profile; otherwise measure safely and create a separate variant. Do not reuse approximate/nearest pixel geometry as exact. Bound variants with weighted LRU eviction.
- Deletion must be race-safe: mark a deletion epoch/tombstone before scheduling physical removal; cancel parse/serialization tasks; writers capture the epoch and verify both epoch and canonical message existence/version immediately before atomic commit; delete final and temporary files; retry cleanup failures. A tombstone/index must not be removed until stale writers cannot recreate entries. Tests must cover delete-vs-parse, delete-vs-write, regeneration-vs-write, and conversation deletion with pending tasks. Because payloads are plaintext, document that physical deletion is best-effort on flash storage; logical invalidation prevents app access but cannot promise forensic erasure of old storage blocks.
- Persistent entries should live in an app-private, non-disposable directory such as `noBackupFilesDir/render-cache`, with a bounded app-managed budget. Use `cacheDir` only for disposable temporary artifacts. If product requirements mandate `cacheDir`, explicitly accept that Android may clear it and a later full parse may be necessary; do not claim guaranteed warm starts in that case.

## Agent instructions for isolated/offline build environments

Agents may run in sandboxes with no Internet access and without a JDK, Android SDK, emulator, or cached Gradle dependencies. In that environment:

- Do not attempt to install a JDK/SDK, download Gradle distributions/dependencies, run `sdkmanager`, or use network workarounds. Do not repeatedly invoke `./gradlew` when prerequisites are absent.
- Inspect availability first (`java -version`, `test -d "$ANDROID_HOME"`, existing local Gradle caches, and available repository scripts). These checks are diagnostic only; do not assume environment variables or paths exist.
- If the JDK, Android SDK, Gradle distribution, or required dependencies are unavailable, do not claim builds/tests passed. Continue with source review and offline-safe checks such as `git diff --check`, Markdown/link checks available locally, targeted scripts that need no unavailable toolchain, and static inspection. Record exactly which checks ran and which were blocked by missing prerequisites.
- Run Gradle with `--offline` only when a JDK is available and the wrapper distribution plus all required dependencies are already cached. If it fails due to a missing prerequisite/artifact, stop and report the blocker rather than trying to fetch it.
- Keep implementation changes testable: add unit/instrumentation tests even if they cannot run in the sandbox, and specify the exact command/configuration required in a connected Android environment. Separate “not run” from “failed.”
- Do not let environment limitations justify speculative code changes. Prefer narrow changes, inspect existing tests and APIs, and clearly state confidence/verification gaps.

## Source-based bottlenecks to address

These are identified from the current source, not yet validated as runtime bottlenecks by a profiler:

1. `ChatGeneration.update()` snapshots accumulated output with `output.toString()` / reasoning text and `updateResponse()` creates a new `Conversation` and maps the full `messages` list. The collectors are under `viewModelScope` unless explicitly moved off Main; upstream adapter `flowOn(IO)` does not move downstream collection automatically.
2. `ChatScreen` receives `conversation`, derives a new combined `messages` list from the new history list, and keys other work from that list. Content-only stream changes therefore look like structural-history changes to some consumers.
3. `ChatScrollAnchor.SentMessageDetector.observe()` has a history-reference fast path, but a new list reference makes it rescan IDs. Message-ID and unseen-message calculations should react to structural changes, not response text changes.
4. `ChunkedStreamingMarkdown` freezes stable complete blocks, but its `Column` still composes/measures every frozen chunk. Its stable-prefix scan and copying of a growing unclosed tail can also grow with the tail length; a very long single block never benefits from top-level chunking.
5. The tail parser may run on `Dispatchers.Default`, avoiding a direct main-thread parse, but repeatedly parsing the whole growing tail still wastes CPU and competes with rendering.
6. The streaming auto-scroll frame loop is legitimate behavior but may amplify layout work as content height changes. Measure it separately with follow-bottom enabled and disabled before replacing it.

## Target architecture

### 1. Split stable conversation structure from active stream content

Introduce a stable conversation/message structure state and a separate stream state, owned by the chat ViewModel or a chat-specific stream holder.

Suggested conceptual types (adapt names to existing architecture rather than duplicating models):

```kotlin
data class ChatStructureState(
    val conversationId: String?,
    val structuralRevision: Long,
    val messageIds: List<String>,
    // Stable message metadata and completed content references.
)

data class ActiveStreamState(
    val conversationId: String,
    val messageId: String,
    val generationId: Long,
    val revision: Long,
    val displayedLength: Long,
    val status: StreamStatus,
    // Append-only buffer/snapshot handle; do not embed a full copied Conversation.
)
```

Implementation rules:

- Keep provider/model/title/settings and message order in their existing low-frequency state scopes.
- Publish stream revisions separately; a delta must not call `publish(updatedConversation)` or `Conversation.copy(messages = messages.map { ... })`.
- Maintain the accumulating response in an append-oriented buffer or immutable segmented store. Expose only the latest display snapshot at the controlled render cadence. Avoid materializing the entire buffer as a String on every delta.
- Commit the completed assistant message into persistent conversation state when the generation ends, is cancelled, fails, or at explicit checkpoint boundaries. Use a generation ID so late events from an obsolete/retried stream cannot overwrite the current response.
- Keep persistence off Main. Checkpoint persistence must not require rebuilding all messages or versions unless the storage schema genuinely requires it.
- Do not break conversation restore, regeneration/version switching, tool output, reasoning/signature content, interruption, or title updates.

### 2. Separate structural revisions from content revisions

- Maintain a structural revision that changes only when message IDs/order/roles or structural metadata changes (send, insert, delete, reorder, conversation switch).
- Compute message IDs and unseen/new-user-message sets only when the structural revision changes.
- Make sent-message detection incremental: process newly inserted IDs against the previous structural state instead of recreating a set from the entire history on every content tick.
- Key effects to conversation ID, structural revision, and explicit scroll intent—not to a new list object produced by every stream update.
- Keep stable Compose keys for messages and Markdown blocks. Never use the accumulated text or a full `Conversation` object as the identity key for an otherwise stable subtree.
- Confirm that title/provider/model-only updates do not force the active Markdown list to reinitialize, and stream-only updates do not invalidate unrelated app scopes.

### 3. Character-chunk commits and streaming reveal

- Use `STREAM_COMMIT_INTERVAL_MS` (50 ms) as the minimum render interval at the boundary between incoming deltas and Compose-visible stream state. Publish the first text immediately, then coalesce all pending fragments into one commit per window. Do not wait for a line delimiter or impose a character limit on a commit.
- Flush immediately at completion/cancel/error so the final visible text is never stale. Handle lifecycle pause/resume without losing buffered deltas.
- Keep parsing on a background dispatcher, but never enqueue unbounded parse jobs. Conflate/cancel superseded work using the active generation ID/revision. Track batch queue depth, character-chunk wait time, batch size, input-to-visible latency, and missed frames when tuning character count and pacing independently.
- When a new character chunk is committed, use a strong left-to-right reveal: a gradient/mask sweep with alpha fade, rather than animating every character independently. Each batch/segment animates once; do not restart the animation for each subsequent stream update.
- Make animation duration/timing a measured UX constant, separate from the stream commit cadence. Animation may make the update feel immediate, but it does not make the underlying rendering take 1 ms and must not be used to conceal jank.
- If the user selects text, accessibility is enabled, or reduced-motion is requested, preserve selectable/accessibility text semantics and respect reduced-motion settings. Do not add per-character layout work.

### 4. Truly incremental Markdown parsing

Retain the current stable-prefix splitter as a correctness layer, but replace full-tail rescanning/reparsing with a stateful append parser.

- Keep a parser cursor/state per active message: consumed offset, current block kind, fence character/length, list nesting/continuation, HTML block state, and inline context required by the chosen Markdown library.
- Process only newly appended text plus the minimal look-behind needed to resolve a token that crosses a delta boundary. Do not rescan every earlier line when another line arrives.
- Support huge single blocks. For code blocks, process appended lines incrementally and only syntax-highlight visible lines or bounded line windows. For paragraphs, render-window commits preserve every source character without inserting artificial breaks. Keep Markdown parser context incremental and append committed segments so every window does not rebuild the full annotated string. For long lists, retain list-item boundaries and update only the active item. A long line with no newline must stream by the 50 ms window; do not invent line breaks that change copied/accessibility text.
- Preserve correct handling of fenced code and longer closing fences, blank lines, lists, HTML blocks/comments, tables, block quotes, headings, setext headings, links, math, CRLF, Unicode, and unfinished constructs at end-of-stream.
- Treat generation reset, edit, regeneration, cancellation, or a non-prefix content replacement as a new parser generation. Drop old parse jobs and cached state using generation IDs.
- At end-of-stream, run a bounded finalization pass to resolve unfinished Markdown constructs and cross-block references. If the parser library cannot expose true incremental parsing, build a narrow incremental tokenizer/block model around it; do not silently label repeated full parsing as incremental.
- Verify each incremental result against a full parse of the same text for semantic equivalence. Where the renderer intentionally streams an incomplete construct, test both in-progress and finalized states.

### 5. Markdown block virtualization and a bounded render window

- Use one vertical scroll owner for the chat. Preferred architecture: flatten the conversation into stable lazy rows—user-message rows plus assistant message/block rows—inside the existing top-level `LazyColumn`, with stable keys derived from message ID and block ID. Preserve message-level semantics/actions as explicit rows or grouped semantics. Do not nest a same-direction unbounded `LazyColumn` inside each assistant message. Prototype this row model and validate selection, accessibility, actions, and scroll behavior before migrating all message types.
- Use stable block IDs and known/estimated heights. Preserve scroll anchoring when earlier blocks change measured height or when a streaming block is split into frozen blocks. Avoid nesting an independently scrolling `LazyColumn` inside each assistant message.
- Start with a bounded composed/prefetched area around 200% of viewport height as a tunable benchmark parameter, not a hard minimum. The target is a bounded viewport-relative window, not a spacer around every block. Prefer available Compose lazy-layout cache/prefetch mechanisms; verify actual composition/disposal behavior with instrumentation rather than assuming a parameter guarantees it.
- As a block leaves the render window, dispose its Compose subtree. When it approaches/re-enters, compose it from the cached immutable parse/render model without reparsing the full message and without a blank-height frame.
- The active streaming block must remain mounted when required for text selection or accessibility, but it must not force all earlier blocks to remain composed. Preserve stable heights/placeholder measurements to minimize scroll jumps.
- Keep top-level message virtualization and block virtualization coordinated through one scroll/anchor policy. Test follow-bottom, manually scrolling upward during generation, jumping to latest, and inserting new messages.
- Very large blocks also require intra-block virtualization (for example, visible code-line windows); virtualizing only top-level Markdown blocks does not bound the cost of one huge block.

### 6. In-memory Markdown cache

Cache immutable data that is safe to retain after Compose disposal:

- Parsed block AST / parser model and incremental parser checkpoints.
- Expensive render data that is deterministic for the cache key, such as prebuilt annotated text or syntax-highlighted visible-line slices, where safe.
- Stable block measurements only when their key includes width, font scale, density, theme, typography, and other layout-affecting inputs.

Cache rules:

- Use a message-scoped key including conversation namespace, message ID, immutable version/content hash, parser/render generation, block identity, and relevant theme/layout inputs. Avoid collisions between different streams or message versions; do not store a whole-conversation render object.
- Use a weighted LRU budget measured in estimated bytes/characters, not an unbounded map or entry count alone. Large blocks may bypass cache or keep only checkpoints/visible slices.
- Promote visible/recently used blocks; evict cold entries when the budget is exceeded. Cache eviction must never corrupt the active stream or require all evicted Compose subtrees to remain alive.
- Cache parsed data before publishing a frozen block, so the first visible frame can render at its real/estimated height rather than flashing a zero-height loading state.
- Do not cache live `@Composable` instances or keep composition scopes alive. Compose owns composition lifetime; the cache stores immutable data and the lazy list controls mounting.
- Test cache hits, invalidation on edit/regeneration/theme/font-scale changes, memory budget enforcement, and safe fallback after eviction.

### 7. Persistent per-message render cache

Goal: reopening an old conversation should hydrate independent cached entries per message/version instead of parsing every historical message from scratch. **A message/version is the cache unit; a conversation-wide render snapshot is prohibited.** Use a persistent app-private plaintext render-cache directory (for example, `noBackupFilesDir/render-cache`) so Android cache eviction does not routinely remove entries. This is a render-data cache, not a serialized Compose composition: Compose nodes, composition scopes, and live UI objects cannot safely be stored and restored from disk.

- Store versioned, immutable, serializable Markdown block models and deterministic preprocessing results as separate per-message entries under app-private `noBackupFilesDir/render-cache` (never user-visible/shared storage). Use atomic temp-file writes followed by rename so interrupted writes cannot leave a partially valid entry. Keep temporary files in the same filesystem/directory as the final entry to preserve atomic rename guarantees.
- Key each entry by conversation namespace + stable message ID + immutable message/content revision/hash + parser schema/version + Markdown options and rendering options that affect the serialized data. Store each message independently; never require reading/writing a full conversation bundle to access one message. Block/layout subentries are owned by that message entry or addressed by its key. Theme/font scale/window width should only be part of the key for cached data that actually depends on them; prefer caching theme-independent parse models to maximize reuse.
- On conversation open, query message entries and load only visible/near-visible entries first, off Main. Render valid cached models immediately; parse only missing/invalid message entries in the background and write back only those entries. A cache hit must not wait for a full Markdown parse before showing the message.
- Keep a bounded disk budget and eviction policy (LRU or equivalent) at per-message/per-variant granularity. Handle missing/evicted/corrupt files as cache misses; canonical message content remains the source of truth. Evicting one message entry must not evict an entire conversation unless policy explicitly removes that conversation.
- Include cache schema and parser/render implementation versions. Invalidate only the affected message/version/variant after parser changes, Markdown option changes, edits, regeneration/version changes, or changes to an input embedded in that entry.
- Write caches asynchronously and coalesce duplicate writes per message key. Persist finalized message/block entries after completion or at explicit checkpoints; never serialize the whole conversation on a stream delta. Do not block generation completion or UI rendering on disk I/O.
- Do not persist device-specific measured pixel heights unless the key includes width, density, font scale, typography, and other layout inputs. Prefer stable parse/render models and allow Compose to measure layout on demand.
- Plaintext and deletion lifecycle: entries are unencrypted and app-private. The persistent directory is not Android’s disposable `cacheDir`; bounded eviction is controlled by the app. Do not store API keys, provider credentials, or unrelated conversation metadata. On delete, cancel in-flight jobs, increment a per-message/conversation deletion epoch, remove entry/temp files, and ensure every writer validates the epoch and canonical message existence/version immediately before commit. A conversation deletion enumerates and removes all message entries in its namespace; retries handle filesystem failures.
- Add metrics for cache hit/miss, deserialization time, parse fallback time, bytes read/written, and eviction. Verify a warm reopen skips parsing for valid cached blocks and falls back correctly after cache deletion or version mismatch.

### 8. Scroll and frame pacing

- Keep bottom-following behavior, but separate scroll intent from content revisions. Do not restart a frame loop because every network delta changes a message-list reference.
- The frame loop should read the minimum necessary layout state and avoid scanning all messages or blocks per frame. Use visible layout info and stable IDs.
- Measure with auto-follow enabled/disabled and with the user reading earlier content while a stream continues. Avoid fighting user scroll intent.
- Instrument long main-thread sections and skipped frames. Avoid speculative rewrites of scrolling until traces identify the remaining cost.

## Implementation phases

Do the phases in order, one focused change at a time. Keep this file updated as each phase is implemented and verified.

### Phase 0 — Baseline and invariants

- [ ] Capture a reproducible baseline using the same generated Markdown fixture and device/build configuration before and after changes.
- [x] Add temporary/debug recomposition and structural-scan counters for navigation, message cards, stable Markdown blocks, history-ID scans, parser input length, and stream commits. Remove or appropriately gate noisy debug instrumentation once verified.
- [ ] Record frame-time/janky-frame stats for a short answer, a long multi-block answer, a very long code block, and a long list; test with follow-bottom on/off.
- [x] Confirm the current hard generation text cap and document the product decision for very large retained responses before changing/removing it.

### Phase 1 — Isolate stream state from Conversation

- [x] Introduce a separate active-stream state/buffer with a generation ID and independent revision.
- [x] Remove whole-`Conversation` publication and full message-list mapping from the ordinary stream-delta path.
- [x] Add a single completion/checkpoint commit path that preserves saved conversations and message versions.
- [x] Ensure stream-only state is collected only by the active chat/message scopes.
- [x] Test completion, interruption, error, regeneration, conversation switching, and title updates while streaming.

### Phase 2 — Structural revisions and measured stream commit cadence

- [x] Add structural revisions and change message-ID/unanswered-message/sent-message detection to run only on structural changes.
- [x] Buffer incoming token fragments behind a 50 ms minimum render interval. Publish the first text immediately, coalesce all pending text into each later commit without a character cap, and flush tool/terminal boundaries immediately.
- [x] Ensure obsolete generation jobs are cancelled/conflated and cannot publish stale content.
- [x] Add tests with a fake clock for immediate first publication, 50 ms pacing/coalescing, no per-commit character cap, Unicode preservation, tool-boundary/terminal flush, cancellation/error, and rapid-delta handling.
- [~] Add the left-to-right deep fade reveal once per committed chunk; skip the draw animation when Android system animator animations are disabled. A tracker/policy test is added; verify it does not restart across later chunks or affect selection/accessibility on device.

### Phase 3 — Incremental parser for both block boundaries and huge blocks

- [x] Audit the existing splitter/chunking implementation and preserve its tested Markdown boundary rules.
- [x] Add parser checkpoints and append-only delta processing with bounded look-behind; eliminate full-tail scans/copies from the normal update path.
- [~] Incrementally represent very long paragraphs, lists, HTML blocks, and code fences; virtualize long code lines/paragraph segments within a single block. Static paragraphs/code/HTML use bounded previews with lazy expanded rows; active overflow exposes a bounded preview plus an on-demand, plain-text lazy source dialog, but does not progressively parse/render the overflow block as Markdown.
- [x] Add differential tests comparing incremental output with a full parse over every split point of representative fixtures and randomized delta boundaries.
- [x] Verify Unicode, CRLF, open/closed fences, list continuations, math, tables, HTML, reference links, regeneration, and finalization.

### Phase 4 — Virtualized Markdown blocks and render window

- [~] Evaluate the flattened stable-row option. The current design keeps the top-level chat `LazyColumn` message rows and uses custom-success `MarkdownElement` children in shared-viewport `SubcomposeLayout` rows, avoiding nested vertical lazy lists. Compose disposal tests are added; validate message actions, selection, accessibility, and scroll anchoring on device before considering a separate flattened-row migration.
- [~] Replace eager frozen-chunk `Column` composition with viewport-based `SubcomposeLayout` rows sharing chat's viewport; do not introduce nested same-direction lazy scroll containers. Compose instrumentation coverage is added; device tuning remains open.
- [~] Start with a bounded render/prefetch window near 200% of viewport height (50% overscan on each side), prove off-window blocks are disposed with composition instrumentation, then tune using frame/memory measurements.
- [x] Preserve block keys, measured-height continuity, selection, scroll anchoring, and follow-bottom behavior during chunk freezing and block insertion.
- [~] Add intra-block virtualization for content whose own size is too large for one composed node. Finalized paragraph/code/raw-HTML blocks and active overflow use bounded previews with on-demand lazy source rows; progressive rich Markdown parsing/rendering for active overflow remains open.
- [x] Verify off-screen blocks are recomposed only when entering the render window and use cached models without reparsing.

### Phase 5 — Weighted caches and memory lifecycle

- [x] Add weighted in-memory cache for immutable parse/render models with explicit byte/character budget and LRU eviction.
- [x] Include all parser/render/layout-affecting inputs in cache keys and invalidate on version, regeneration, theme, font scale, width, or option changes as appropriate.
- [x] Prevent unbounded retention of full content strings, ASTs, annotated strings, parser snapshots, and layout data. Large entries must be bypassed, segmented, or checkpointed.
- [~] Test hits, misses, eviction, regeneration invalidation, and cache-pressure behavior while streaming.

### Phase 6 — Persistent per-message render cache

- [~] Define a serializable, versioned, non-encrypted cache format for immutable Markdown parse/block models and reusable layout/position metadata; do not serialize Compose UI objects.
- [x] Implement a persistent app-private plaintext cache outside OS-disposable `cacheDir` (for example `noBackupFilesDir/render-cache`), with separate entries per message/version, atomic writes, bounded storage, and per-entry eviction.
- [x] Hydrate visible/near-visible message entries off Main and reuse valid Markdown/layout variants without full reparsing; handle misses asynchronously per message.
- [~] Cache per-message/block measurements and scroll-anchor metadata with multiple profile-keyed variants for distinct widths, font sizes/scales, densities, typography, and relevant theme/layout settings; derive global offsets/prefix sums from current order and update only affected ranges.
- [x] Select only an exact compatible layout profile; create a separate variant on a profile miss and bound variant growth with weighted LRU eviction.
- [x] Confirm cache payloads are stored unencrypted in the documented serialization format; do not add cache encryption.
- [x] Invalidate only affected message/block/profile entries on content/version/parser-schema/render-option changes; implement deletion epochs/tombstones and prove stale writers cannot recreate deleted plaintext entries.
- [x] Persist finalized message/block entries asynchronously; coalesce writes and never write the full conversation on each stream delta.
- [~] Add tests for warm reopen, message-level hit/miss/eviction, schema/profile invalidation, content edits/regeneration, corruption, cache reset, delete-vs-write races, and conversation deletion with pending jobs.
- [ ] Measure warm-open parse avoidance and time-to-first-render against a cold open on the same fixture.
- [ ] Verify cold/warm app start, conversation reopen, scroll-away/scroll-back reuse, and deletion invalidation; count parse and composition work by stable message/block ID.
- [ ] Verify persistent entries survive normal app process restarts and are not dependent on Android preserving `cacheDir`; document expected behavior after app-data clear/uninstall.

### Phase 7 — Profile, tune, and document

- [ ] Run unit tests, lint, and the project checks together once implementation is complete, consistent with `AGENTS.md`.
- [ ] Repeat the exact Phase 0 scenarios on the same configuration and compare frame stats, main-thread traces, allocations, parser characters processed, number of state publications, and composed block count.
- [ ] Compare cold-open and warm-open behavior for an old conversation; confirm valid disk-cache hits skip full Markdown parsing and cache I/O never blocks Main.
- [ ] Verify ordinary stream work is proportional to new delta plus visible/prefetched content; no ordinary update copies or scans the whole response/history.
- [ ] Record stream commit frequency and input-to-visible latency; ensure commits follow the measured configured policy and terminal flushes are immediate.
- [x] Update `docs/structure.md` and any existing streaming Markdown plan/checklist if file boundaries or responsibilities changed.

## Acceptance criteria

- A stream delta does not publish a replacement `Conversation` or map the full `messages` list.
- Navigation, drawer, unrelated content, stable historical message cards, and frozen off-window Markdown blocks do not recompose merely because new response text arrived.
- Structural history scans occur only on an actual structural revision.
- Normal stream updates publish immediately when possible, then at most once per 50 ms while caught up. Each commit contains all text accumulated in its render window, with no character-count cap; structural and terminal boundaries flush immediately. Commit size and input-to-visible latency are measured.
- The left-to-right reveal runs once per new committed segment and has no effect on correctness, parsing, selection, or accessibility.
- Appending a delta to a huge block processes only the delta and bounded parser context; it does not rescan/reparse the entire accumulated block.
- Only blocks in the bounded, measured render window remain composed (initial target near 200% of viewport); off-window models may remain in the bounded cache but Compose subtrees are disposed.
- Cache memory stays within its configured weighted budget, with correct rendering after eviction.
- Reopening a conversation loads valid per-message disk-cached immutable Markdown models without full reparsing; stale/missing/corrupt message entries fall back safely to background parsing.
- Cold/warm app start and history scrolling reuse valid cached models; scrolling away disposes off-window Compose subtrees, and scrolling back does not reparse unchanged content.
- Deleting a message/conversation makes its per-message plaintext render files unreachable and schedules physical removal; deletion epochs prevent in-flight writers from recreating stale entries. Edits/regeneration invalidate only affected message keys.
- Persistent render cache is bounded, versioned, app-private, unencrypted/plaintext, stored outside the OS-disposable cache directory, and never treated as canonical message storage.
- Reopening/scrolling reuses compatible per-message layout measurements and scroll anchors; global offsets are derived from current order and only affected ranges are recomputed when content/layout/order changes.
- A component can retain multiple independent layout-cache variants; changing width, font size, font scale, density, typography, or relevant theme settings selects the matching variant or creates a new one without corrupting other variants or reusing incompatible pixel coordinates.
- Incremental parser output matches finalized full-parse semantics for supported Markdown fixtures.
- Baseline and after-change measurements are recorded. Report actual results; do not claim a frame-rate improvement unless profiling supports it. In offline environments, explicitly list unavailable toolchain prerequisites and checks not run.

## Explicit non-goals / cautions

- Do not promise constant memory for an indefinitely growing retained transcript. Constant per-update work and bounded resident rendering are the achievable goals; retention/storage policy is a separate decision.
- Do not try to suppress all Compose recomposition at any cost. The active visible tail must update when displayed content changes; isolate invalidation and bound its work.
- Do not assume a timer or character batching alone fixes jank. Expensive parse work, allocation, layout, scroll anchoring, and cache churn must each be bounded. A short trailing character chunk intentionally waits for more characters or an explicit tool/terminal flush; do not force it onto screen just because a timer fired.
- Do not use a 200%-viewport render window as a substitute for virtualization; it must still have a strict bound and must dispose subtrees outside the window.
- Do not keep all frozen blocks composed just because parsed data is cached.
- Do not remove the existing response-size guard until a safe storage/rendering strategy and product behavior for extremely large output are specified.
