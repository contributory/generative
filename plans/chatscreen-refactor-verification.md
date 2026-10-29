# Latest follow-up — offscreen preparation and durable saves

See [the implementation and verification record](chat-render-preparation-and-durable-save.md) for the subsequent resource-control and storage request. Earlier scroll-refactor results below remain historical.

# Chat screen refactor implementation and verification

## Latest follow-up — native Thinking scrolling

The user explicitly requested replacing the Thinking preview's custom scroll mechanics where a simpler Compose solution preserves its behavior. `LiveThinkingBlock.kt` now delegates motion to `ScrollState.animateScrollTo()` with the framework's default spring. The frame clock loop, raw delta dispatch, easing equation and minimum-speed/time constants were removed. The existing 3.5-line layout, expanded presentation, Markdown renderer, text colors and fades are unchanged.

Only a small lifecycle/data subscription remains: `snapshotFlow` observes the native measured scroll bound, `conflate` retains just the latest pending target, and a child coroutine scopes gesture cancellation. Native user input interrupts the child animation without killing the subscription, so subsequent growth can follow again. Stop/expansion cancels the containing effect. The native animation may finish its current target before taking the latest pending target; this is bounded coalescing, not a per-chunk animation queue or generation delay. No custom animation spec, speed tuning, timer, manual delta or per-frame app loop is used.

A first trial cancelling/restarting native animation for every bound change passed ordinary cases but failed a stronger test with continuous growth each frame: animation was repeatedly restarted before making progress. The final implementation corrects that request policy by allowing native motion to progress while conflating pending growth. The regression assertion was kept and passes. Preview-local ScrollState was chosen over propagating relocation requests so automatic Thinking scrolling does not reveal/scroll its chat ancestors.

**Final executed checks: 33/33 passed** across five classes: `LiveThinkingScrollTest` (5), `LiveThinkingBlockHeightTest` (2), `ThinkingMarkdownColorTest` (2), `ChatSentMessageScrollTest` (12), `ChatMessageListTest` (12). New coverage includes intermediate native interpolation, slow/continuous/bursty growth and idle waits, drag cancellation and next-growth resumption, Stop/reactivation, expansion/recollapse with new content, and a real preview inside an off-screen scrollable parent that remains unmoved. Results: `/tmp/chatscreen-final-check/thinking-native-results.json` and per-class logs.

The final cached lint recheck explicitly loaded Compose UI/runtime rules against the AGP model: the three related rules have zero findings in `LiveThinkingBlock.kt`. The run still reports the same 68 errors and six warnings in unchanged files elsewhere; full-application lint is not claimed green. `git diff --check` passes.

The final source and new test compile with the cached Kotlin/Compose compiler against actual app artifacts. No additional Gradle invocation, dependency/toolchain installation, Git reset or APK build was performed. Server preflight guards passed; memory allocation failures stayed zero. These are Robolectric Android UI checks, not a physical-device judgment of scroll feel. Earlier full-suite/shared-selection and full-application lint limitations remain documented below.


## Current feedback follow-up — accepted scrolling, Thinking height and Jump threshold

This section supersedes the older rejection/open-device-gate status below. The current plan appendix records the user's acceptance of the general chat experience. Historical missing/rejected behavior is not being reopened. The remaining large-LaTeX follow lag is an accepted possible limitation; no speculative renderer/scroll-mechanics change was made.

- **Thinking request implemented:** only the existing collapsed preview height multiplier in `LiveThinkingBlock.kt` changed from `5f` to `3.5f`. `heightIn(max = ...)` preserves short content's natural size. Expanded layout, font scaling, header, color, fade and preview scroll mechanics remain intact.
- **Jump request implemented:** `ChatScreen` now uses `ChatMessageListState.showJumpToLatest`, derived from native layout bounds and a threshold of at least twice the full list-screen height. The actual latest-content marker is compared with the viewport above the composer. The 30% trailing allowance is not a content anchor. When the final turn is virtualized, the native scroll-indicator estimate is used with trailing content padding excluded, as explicitly selected by the user. The estimate can vary as previously unseen items are measured. No distance is supplied to scrolling, and no new controller, animation, timer, retry or token-driven observer was introduced.
- **Compilation:** the three changed production sources and three changed/new test sources compile with the cached Kotlin/Compose compiler against the existing app artifacts. No further Gradle invocation, reset, commit or APK build was performed.
- **Executed focused UI regression checks: 43/43 passed**, in seven classes. `ChatJumpButtonVisibilityTest` (4) verifies the exact inclusive threshold and return, real versus virtualized anchors, dynamic growth/completion and screen/composer resizing. `LiveThinkingBlockHeightTest` (2) verifies 3.5 lines at multiple text sizes/font scales, natural short height and uncapped expansion. Existing list (12), real-screen Send/follow (12), opening/history (6), Thinking color (2) and minimap (5) cases also pass. The opening test now verifies that a short drag hides Jump and larger travel reveals it. Results: `/tmp/chatscreen-final-check/current-feedback-results.json` and matching per-class logs.
- **Lint:** the same three relevant Compose rules were rerun with explicit UI/runtime rule jars against the real AGP model: zero findings in the nine modified production files, with 68 errors and six warnings still reported in unchanged files elsewhere. This is not a full-application lint pass. `git diff --check` passes.
- **Server checks:** tests started at 4.83/16 GiB and load 2.46; lint started at 4.82/16 GiB and load 2.12. Processes ran sequentially at nice 10 with two active processors and bounded heaps. Memory allocation failures stayed at zero.

The earlier 134/135 focused-suite result, shared-selection API mismatch and unavailable pre-change performance/hardware matrix remain historical verification limitations. That entire suite was not rerun for this small follow-up; the new result is the 43-case regression selection above. No physical-device test is claimed. The original plan's remaining unchecked verification items are documented rather than presented as completed measurements.

## Historical verification and acceptance record

**Historical acceptance correction (2026-10-28; superseded by current feedback): REJECTED for the inverted-list-dependent portion of the original plan.** This document records work implemented and tests authored, **not an accepted completion of Phase 2/3/5**. The first-viewport, newest-item/index-zero, scroll-intent, minimap/Jump, 30% gap and related test-coverage descriptions below were built on `chatMessageTurns(messages).asReversed()` and `LazyColumn(reverseLayout = true)`, an old architecture the user explicitly rejected as too complex and unmaintainable. They are **NOT ACCEPTED / NOT COMPLETE** until replaced with a normal chronological list and verified. The original plan's checkboxes remain unchecked; see **Appendix E — Acceptance audit** in [chatscreen-refactor.md](chatscreen-refactor.md). This correction does not automatically invalidate unrelated stream/persistence work or the narrower `heightIn` defect fix, but neither a code fix nor successful APK compilation proves the entire UI is correct.

The chronological replacement is now present in source (see the final follow-up section). This updates the implementation descriptions below but does **not** lift the rejection or satisfy the on-device reacceptance gate.

## Current verification run (2026-10-28; supersedes earlier environment limitations)

The user subsequently authorized one additional Gradle invocation, after implementation and a server health check. That invocation is used. No Git reset, commit, push, APK assembly or subsequent Gradle invocation was performed.

- **Server:** 16 GiB cgroup, two CPUs. Preflight used 6.2 GiB, load 0.61. Gradle ran at nice 10, one worker, no parallel execution, heap 3 GiB; test forks were sequential, heap 768 MiB and two active processors. Peak cgroup usage was 10.51 GiB; allocation failures remained zero. It exited after 1,575 seconds. Dependencies were available from the normal configured repositories during this authorized run; the earlier offline limitation no longer applies.
- **Actual Gradle result:** production and focused test compilation passed. The focused run executed 135 tests: 123 passed, 12 failed, none skipped. Full application lint executed and reported 78 errors and 387 warnings, including two resource lookup errors in ChatScreen. This is a failed verification run, not a green build.
- **Follow-up corrections:** native BringIntoView animation emits NestedScrollSource.UserInput itself; manual takeover now additionally requires the list's native drag interaction. Send can arrive before its new chronological message data is applied; the existing consumer now awaits that data rather than discarding the intent. The existing Jump button belongs to Scaffold's floatingActionButton slot, avoiding changing composer height when visibility changes. ChatScreen captures localized strings with stringResource. Minimap layout reads are deferred to drawing, semantics and pointer handling.
- **Test synchronization:** paused loading clocks avoid waiting for an intentionally indefinite history-loading animation; explicit frames apply text edits/Send-button entry and minimap timer restart before assertions. Tests retain real visibility, natural-height, exact Send alignment, no-opening-request and mount-count assertions.
- **Current source validation:** the cached Kotlin 2.4.20 compiler and Compose plugin compile the eight modified production sources and 23 focused test/fixture sources against the actual app artifacts. JUnit/Robolectric rechecks use the exact runtime classpath and Android resources exported by Gradle, with current classes first, sequential JVMs and bounded resources. These calls do not invoke Gradle or change project build configuration.
- **Final focused results:** 135 tests executed across 22 classes: **134 passed, one failed, none skipped**. All 12 list, 12 real-screen Send/follow, six opening, five minimap and two Thinking color cases pass, together with generation/state, callback identity, pager/drawer gestures and Room persistence/deletion/temporary-chat tests. `focused-results.json` records each class and its actual log.
- **Remaining selection failure:** `ChatTextSelectionDismissalTest.touchingBlankSpaceOrAnotherTargetReleasesSelectionAndStillClicks` expects the legacy LocalTextToolbar status Shown, but it remains Hidden. The unchanged Compose 1.12.1 SelectionManager uses ToolbarRequester when ComposeFoundationFlags.isNewContextMenuEnabled is true; cached Android implementation enables that flag by default. That branch bypasses LocalTextToolbar. `PlatformTextSelection.kt` and Compose dependency versions are unchanged by this refactor. The original assertion was retained; no flag override or assertion weakening was used to make it pass. Updating the existing shared selection integration to the new menu API is separate work. The test is not reported as passing.
- **Final lint recheck:** direct cached lint CLI with the actual AGP model, explicit Compose UI/runtime rule jars, offline mode, two CPUs and heap 1.5 GiB checked `LocalContextGetResourceValueCall`, `FrequentlyChangingValue` and `ConfigurationScreenWidthHeight`. It reported **zero findings in all eight modified production files**; the same run found 68 errors and six warnings in unchanged files elsewhere, confirming the requested rules executed. This is a scoped correction check, not a full-app lint pass. Reports: `/tmp/chatscreen-final-check/lint-final.txt` and `lint-final.xml`. The earlier CLI attempt without explicit rule jars returned an empty report; that result is not used as verification evidence.
- **No blanket acceptance claim:** real-device selection, IME, visual flicker/scroll feel and the explicit Appendix E reacceptance gate remain unverified. The automated suite is focused, not the complete repository test suite; unrelated test-source compilation defects documented below remain outside its selection.
- **Not verified:** real-device visual/gesture behavior and before/after performance. No device is attached; automated Android UI checks use Robolectric native graphics. The user-authored on-device acceptance gate remains open.

Artifacts for this session are under `/tmp/chatscreen-final-check`: `gradle.log`, `gradle-result.json`, `compile.log`, per-class JUnit logs, exported runtime classpath and properties. Gradle XML/HTML reports are under `app/build/test-results/testDebugUnitTest` and `app/build/reports/tests/testDebugUnitTest`. Lint's full text report is `app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`.

The sections below preserve the earlier execution history. Statements there about missing dependencies, unexecuted tests or no second invocation refer to those earlier snapshots and do not describe the current environment.

## Implementation

- Saved history mounts a normal chronological LazyColumn after its data is available. The initial index selects a zero-height keyed boundary after the last turn; Compose fills the initial viewport with preceding content during measurement. No opening scroll call, synthetic offset, reversed data or reversed layout is used. Turns and their children keep chronological order and message-ID composition keys.
- The newest turn uses Compose's `heightIn(min = usable viewport height)` with an unbounded natural maximum (the original `fillParentMaxHeight` incorrectly fixed both bounds and was replaced during follow-up investigation). Its unused room is after the real-content marker. The list's content padding adds 30% of viewport height, capped by usable space. There are no measured message heights, attachment allowances, reply-height floors or corrective scroll loops.
- One replaceable intent and one `collectLatest` consumer handle Send, Jump, minimap and follow. Explicit navigation uses native index positioning followed by a generic start/end BringIntoViewRequester. A native BringIntoViewSpec adapter translates only declarative AppBar/composer/gap insets before delegating displacement and animation to Compose; it never measures message contents. Live follow requests the current real-content end; native visibility detection and nested scrolling handle manual takeover/return. Minimap taps/drags now navigate to native message start/end targets rather than calculating within-message pixel offsets.
- Content publication and generic marker placement trigger eligible follow requests. Stream status/cleanup alone triggers none. There is no generation-rate sampling, velocity limit, raw-delta movement, animation backlog or idle frame ticker in the container.
- Existing assistant/user rendering, Markdown, LaTeX, media loading and renderer caches remain unchanged. The container no longer supplies a readiness tracker or gates reveal on assets.
- Existing stream/persistence behavior remains at block boundaries. Temporary generation now explicitly skips the persistence callback as well as the repository's existing guard. Frozen streams are retained for the mounted conversation and released on navigation/deletion; other live generations survive navigation.
- Composer submission paths share accepted-send handling. The existing minimap edge width, drawer/pager gesture routing and selection/focus handling are retained.

## Test coverage authored/updated

- `ChatMessageListStateTest`: chronological end-boundary initialization, stable turn keys, queue promotion, declarative gap, follow preference, request priority/replacement, manual takeover and return-to-end eligibility.
- `ChatMessageListTest`: initial placement of a long final opaque child with zero opening intents; disabled-follow send placement; asynchronous opaque child resize; stable mount/position at completion; native drag/Jump; slow/fast/bursty growth and idle waits; delayed history mounting.
- Existing screen integration tests cover real assistant/tool rendering, first/existing/queued sends, completion, cold/cache-evicted/reopened history, input, selection and drawer/pager/minimap gestures. Tests expecting renderer-readiness veils or fixed-speed lag were updated to the new contract. Renderer tests were not changed.
- `SentMessageDetectorTest`: preserved send detection cases plus delayed history at the same structural revision.
- `ChatGenerationTest`: existing Stop freeze, late chunks, completion/failure, metadata and completed-block persistence boundaries; added temporary-generation write exclusion.
- `ChatStreamRetentionTest`: mounted frozen snapshot ownership and unrelated live-generation retention.

## Environment and execution

The user authorized exactly one Gradle invocation, after implementation and a server health check. No pre-change Gradle baseline was run. Therefore no measured before/after performance, renderer remount or Room-write-count comparison is claimed. First-placement/mount/request assertions are provided in tests instead.

Preflight: JDK 25, cached Gradle 9.5, Android SDK platform 37 and build tools 36 exist. The server has a 16 GiB memory cgroup limit and two CPU cores; preflight showed about 8 GiB headroom, low load and zero recorded memory allocation failures. Two existing Gradle daemons were left untouched. The invocation will use one worker, no parallel project execution and an explicit heap cap.

No cached Robolectric `android-all` runtime was found. Offline Compose/Room tests cannot execute without that prerequisite; no dependency or toolchain download will be attempted. The lint runtime also appears uncached. The single offline invocation requests lint and the relevant plain JVM tests with `--continue` so an unavailable lint artifact does not prevent independent checks, while compiling the entire unit-test source set. Compose/Room runtime checks and physical-device checks (orientation, font scale, IME/system insets, themes/backgrounds, selection and gesture feel) remain unverified.

## Single Gradle invocation results

Executed once on 2026-10-28, with `nice -n 10`, offline mode, `--no-daemon`, `--continue`, one worker, a 3 GiB Gradle heap/768 MiB metaspace cap and a 512 MiB test heap. No dependency downloads or second Gradle invocation were attempted. The single-use daemon exited normally after the failed check; cgroup memory allocation failures stayed at zero. Observed memory usage during compilation was about 9.2 GiB of 16 GiB, falling back to about 8.1 GiB after exit.

- **Passed for the source snapshot compiled during this invocation:** `compileDebugKotlin`, `compileDebugJavaWithJavac`, resource processing and runtime/compile class-jar creation.
- **Blocked lint:** `generateDebugUnitTestLintModel` / lint analysis could not resolve uncached `com.android.tools.lint:lint-gradle:32.3.0` in offline mode. No lint pass is claimed.
- **Failed test compilation:** existing test sources contain unresolved imports/API references and other compilation errors, including `AppDataImportInteractionTest.assertDoesNotExist`, `AppLanguageSelectionTest.key`, missing Compose state delegate imports in `ChatTextSelectionDismissalTest`, and errors in `FullBackupRestoreResilienceTest` and `HomePagerGestureUiTest`. Those unrelated test issues were left untouched.
- **Own transient test failure:** a new gate assertion referenced `manualEndReached`, introduced by a final manual-return correction after main compilation had already consumed its source snapshot. The final source removes that extra method/assertion and reuses the existing end-visibility gate. Final follow-cancellation guards for Stop, disabled auto-scroll and generation termination were also reviewed after the invocation. These final source adjustments have **not** been recompiled, respecting the one-invocation limit.
- **Blocked test runtime:** the runtime classpath is also missing cached Conscrypt, SnakeYAML, Espresso/idling-resource and Robolectric native-runtime artifacts. No unit test executed, so no passing unit-test count is claimed.
- **Passed after final edits:** `git diff --check` and static container-boundary review. No renderer source or cache implementation was modified.
- **Not run:** Robolectric Compose/Room scenarios, physical-device checks and APK assembly. The first-layout, cadence, mount-stability and persistence assertions remain authored but unexecuted.

The requested plain-JVM test filters covered `ChatGenerationTest`, `SentMessageDetectorTest`, `ChatMessageListStateTest`, `ChatStreamRetentionTest`, `ConversationStructureRevisionTest`, `ConversationMinimapLayoutTest`, `StreamResponseTextSplitterTest`, `StreamCharBatcherTest`, `TemporaryChatIsolationTest` and `StreamingFadeTrackerTest`. Gradle still compiles the complete unit-test source set before applying those runtime filters.

A future provisioned, authorized verification command is:

```sh
./gradlew --no-daemon --max-workers=1 :app:lintDebug :app:testDebugUnitTest
```

This command was **not** run again.


## Follow-up: latest-only truncation investigation

The user reported that sending another message makes the same AI reply display normally once it is no longer latest. Source investigation identified a direct constraint defect in the new container: only the latest turn had `fillParentMaxHeight()`. Despite the original comment describing it as a minimum, Compose's `ParentSizeNode` sets **both** child `minHeight` and `maxHeight` to the parent height. This was checked against the cached **foundation-android 1.12.1 bytecode** (`javap -c -p androidx.compose.foundation.lazy.ParentSizeNode`) and the [AndroidX implementation](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyItemScopeImpl.kt). A Column therefore allocated a finite remaining height to its children, compressing/truncating the long reply and its end marker. Adding another user turn removed that modifier from the previous turn, matching the reported latest/non-latest difference.

The existing modifier was replaced directly with `heightIn(min = minimumTurnHeight)`. The minimum depends only on viewport constraints and declared insets/padding; no message is measured. The upper bound remains the LazyColumn item's natural unbounded maximum. No additional spacer, overlay, status flag, timer, scroll loop, renderer change or compensating wrapper was introduced. The 30% allowance remains list content padding.

This fixes the identified constraint cause in source. It also removes the same constraint's obstruction of live content/end-marker growth. It is **not** an on-device confirmation that all auto-follow or Thinking-contrast symptoms are resolved. The existing Thinking preview has its own scroll/fade implementation; that renderer was inspected read-only and was not changed without evidence that it owns the reported flicker.

Regression coverage was strengthened to assert the actual height and unbounded incoming maximum of a long reply while latest and after another send, stable mounts, reachability of first/last lines, asynchronous growth to 1800 dp, and streamed text genuinely exceeding a viewport at slow/fast/bursty cadences. Presence of a visible text node alone cannot detect this constraint bug.

No device is attached (`adb devices -l` returned an empty list). No further Gradle invocation, dependency download or APK build was performed. New/updated Compose tests remain unexecuted under the previously documented validation constraints. `git diff --check` and static boundary checks were run after the correction; physical visibility/contrast verification remains pending.


## Follow-up: removal of rejected inverted-list architecture

The explicit architectural rejection in Appendix A.4 supersedes the earlier reversed-layout implementation. `chatMessageTurns(messages)` is now used in its original chronological order and the chat LazyColumn has `reverseLayout = false`. No index reversal/remapping remains in the viewport or minimap.

`ChatMessageListState(initialMessages)` chooses the zero-height keyed list boundary after the loaded chronological turns as `firstVisibleItemIndex`. This boundary is part of native layout initialization. Compose's normal [LazyListMeasure initial viewport filling](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyListMeasure.kt) measures preceding items to fill an otherwise empty final viewport. Application code issues no opening scroll, waits for no renderer and computes no content height/offset. Follow/Send/minimap still use each message's real-content start/end requesters, not the minimum-height turn's unused space. Jump navigates natively to the chronological end and reveals the real latest content.

The viewport is recreated when loaded history becomes available, including empty-to-loaded history; ordinary nonempty stream updates do not recreate it. The final-turn minimum and warning now use `turns.lastIndex`. Minimap drawing uses normal chronological coordinates; obsolete reverse-coordinate conversions were removed.

Fixtures now seed viewport state from their initial loaded messages. Authored assertions cover a normal list layout, the chronological initial boundary with zero opening intents, correct latest-turn first-layout visibility, and ordinary scroll-direction semantics. Documentation no longer recommends bottom-first or inverted layouts. These edits are statically checked only; no additional Gradle invocation or device test was run, and the visible regression checklist remains unresolved.


## Follow-up: publication, navigation and Thinking color corrections

The current implementation was retained without a Git reset. The native follow subscription now applies `drop(1)` to the initial StateFlow replay **before** null filtering; the former order skipped the first non-null content publication after an initially null subscription. The corresponding authored Compose test uses an unchanged child layout so the request must come from the publication rather than marker relocation.

Minimap requests now preserve an identical in-flight native navigation target instead of cancelling it and repeating index positioning as the rail continues to emit the same message/start-or-end target. A state test covers this coalescing and changing the target.

Thinking's own presentation wrapper now provides `LocalContentColor` equal to its themed body color. Read-only call-path inspection identified why this matters: `CompletePlainTextMarkdown` passes a body style without an explicit color to `OverflowPlainText`/Material3 Text, while `RenderParagraph` explicitly selects `MaterialTheme.colorScheme.onSurface`. Previously the Thinking wrapper changed only the theme colors, so parent content color could differ from parsed paragraph color as live content alternated between fallback and parsed presentation. The correction is scoped to the existing `ThinkingMarkdown` wrapper; no parser, Markdown-renderer, fallback, cache, media or LaTeX implementation was edited. `ThinkingMarkdownColorTest` supplies a deliberately conflicting parent color and asserts resolved Thinking text color through live updates and completion in light and dark themes.

These source paths establish concrete defects and corrections; visual flicker and follow acceptance still require a device run. The new tests were authored but not executed. Only static checks and `git diff --check` were run in this follow-up, respecting the single-Gradle limit.
