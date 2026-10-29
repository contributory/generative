# Offscreen Markdown preparation and durable terminal saves

This follow-up implements the user's subsequent request. Its targeted renderer/cache work is separately authorized; it does not broaden the original chat-scroll refactor or change its accepted layout.

## Implementation

- `PrepareStreamingMarkdown` observes the existing active stream outside the lazy list. It prepares stable Markdown and closed LaTeX into the existing bounded RAM caches while the new reply is offscreen, including its Thinking source. Already composed messages use their normal renderer. Flow conflation discards superseded requests; there is no hidden composed tree, timer, polling loop or history-wide warming.
- `MarkdownRenderWork` uses the coroutine library's single-permit semaphore across suspensions and runs work on `Dispatchers.Default`. Foreground parsing, persistent AST reconstruction/storage and formula construction share that budget. Cached lookups remain cheap. LaTeX construction no longer holds the monitor used by UI lookups; the weighted LRU also remembers unsupported formulas so they are not repeatedly rebuilt.
- Existing parser limits remain: 128,000 rendered Markdown characters, 4,096-character incremental appends and the bounded unstable tail. A newly mounted live row consumes the unseen source as a batch rather than replaying every historical token publication. A row first mounted after Stop can render immutable Markdown using prepared caches; a row already visible at Stop preserves its freeze contract. UI layout remains lazy and native Compose.
- Save triggers stay at the existing completed/interrupted block boundaries (natural completion, Stop, error, completed response blocks around tool transitions). No writes are scheduled per token or by a periodic timer. Pruning and storage run on IO. Finalization is installed atomically and runs non-cancellably; a late Stop cannot veto the only final save. A failed save retains the final RAM snapshot, including across conversation navigation.
- `ConversationSaveJournal` uses Android `AtomicFile` and streaming serialization in app-private `noBackupFilesDir`. At an existing save event, it syncs the complete snapshot before the Room transaction, then removes the journal only after commit. One startup recovery pass and explicit conversation load replay pending records; failures retain them for another event. Explicit deletion/restore discards pending records. Temporary chats never enter this storage path. There is no new scheduling dependency, custom retry queue or periodic worker.
- Fixed an independently reproduced ordering defect in incremental persistence: after shifting old sequence numbers to avoid unique-key conflicts, all surviving rows must receive their final sequence even when their original model values compare equal. Previously an appended answer could appear before shifted old history.

## Durability boundary

Recovery covers a complete atomic journal record whose Room commit did not finish. As required, unfinished streaming content is not journaled or saved periodically. Process termination before the stop/completion save record has been synced cannot be guaranteed recoverable. Memory caches are bounded and evictable; speculative preparation does not promise every historical AST/formula remains cached forever.

## Verification

The final production sources and focused tests compile with the cached Kotlin/Compose compiler (21 production files, 31 test/fixture files). **105/105 selected tests pass in 19 classes.**

- Storage/generation: terminal Stop races, failed save RAM retention, cancellation before first dispatch, SQL-triggered failure in the middle of a Room transaction, journal replay by a new repository, load-time recovery, idempotency, explicit deletion, temporary-chat isolation and append/reorder integrity.
- Preparation: offscreen AST/formula warming without composing the reply, terminal-state fallback, mounted-row exclusion and unmount-triggered preparation, early closed-formula preparation, reuse of failed LaTeX entries, a row first opened after Stop, and batching 200 unseen stream publications. Two concurrency tests verify peak one worker even across suspension and cancellation-safe permits.
- Regression: message list, Send/follow, history opening, Thinking follow/height/color, Jump, minimap, deletion, temporary persistence, conversation selection and stopped-stream retention.
- Test fixtures explicitly initialize JLaTeX as its Android manifest provider normally does, and apply the snapshot-only mount-state mutation deterministically. The persistent-AST reference-link assertion now uses the native AST label (including brackets), matching the renderer's lookup API; no production reference-link behavior changed.

Per-class logs and the machine-readable summary are in `/tmp/chatscreen-final-check/render-durable-results.json`. No additional Gradle invocation or APK build is authorized by this follow-up; verification uses cached Kotlin/Compose compiler and the existing exported JUnit/Robolectric classpath. Heavy processes run sequentially with resource preflight checks, bounded heaps, two active processors and nice 10. No physical-device verification is claimed.


Final lint check used the real AGP model and explicit Compose UI/runtime lint rule jars, offline, for `LocalContextGetResourceValueCall`, `FrequentlyChangingValue` and `ConfigurationScreenWidthHeight`. It reports **68 existing errors and six warnings**. Two findings are in untouched clipboard/toast lines of `MarkdownRenderer.kt`; the other findings are in unchanged files. None point to the implementation lines added or changed in this follow-up. This is a scoped lint check, not a full lint/build pass. Reports: `/tmp/chatscreen-final-check/lint-final.xml` and `lint-final.txt`. `git diff --check` passes.

Resource guards deferred both a test launch and lint while CPU load exceeded the configured threshold. Test launch resumed at 4.83/16 GiB and load 3.18; lint started at 4.84/16 GiB and load 2.47. Test launches were also paused between JVMs to let load fall. Processes stayed sequential; the memory allocation failure counter remained zero.
