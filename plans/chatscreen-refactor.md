# Chat Screen Refactor Plan

Status: Proposed (not implemented).
Scope: ChatScreen, message-list container, initial viewport, **native Jetpack Compose scrolling/gestures**, and generic message-level live-state wiring where unavoidable. Do **not** refactor content/attachment renderers.
Principle: Refactor **ownership and transitions**, not merely file size. Preserve behavior. Do not rewrite the whole feature. **Markdown, LaTeX and media preview are opaque child/attachment presentation components; they do not define ChatScreen's architecture.**

## 1. Current implementation reviewed

Files and responsibilities in the **current uncommitted workspace**:

- features/home/chat/ui/ChatScreen.kt (~1,170 lines): initial viewport creation, send anchor, streaming follower, manual-scroll detection, tail spacers, composer, app bar, minimap, gestures, dialogs, and message list.
- features/home/chat/ui/ChatScrollAnchor.kt and logic/ChatScrollAnchor.kt: sent-message detection, measured user height, trailing scroll space, reply height floor, and positioning.
- features/home/chat/ui/ConversationMinimapSideBar.kt: gesture and minimap navigation, **including its own LazyListState scroll command consumer**.
- features/home/chat/ui/AssistantMessageCard.kt and LiveStreamProcessing.kt: existing assistant content/tool presentation and stream handoff; **treat child renderers as pre-existing contracts**, not refactor targets.
- features/home/chat/ui/markdown/StreamingMarkdownRenderer.kt and MarkdownRenderer.kt, plus LaTeX and media-preview components: inspected only to identify boundaries; **their render logic, parser, cache and internal lifecycle are explicitly out of scope**.
- features/home/chat/ui/ChatOpeningVeil.kt and ChatRenderCaches.kt: history-opening cover and UI readiness/caching; audit only ChatScreen-facing coupling, not individual renderers.
- features/home/chat/logic/ChatGeneration.kt and ChatStreamDisplay.kt; model/ActiveChatStreamState.kt; logic/ChatViewModel.kt; database/ConversationRepository.kt: generation, per-conversation in-memory stream, final snapshot, and persistence boundaries.
- navigation/AppNavigation.kt: routes conversation, live stream, generation flags, preference, and callbacks.
- Existing tests: ChatGenerationTest, ChatScrollAnchorTest, ChatSentMessageScrollTest, StreamingToolActivityUiTest.

Observed design problems:

1. **Incorrect initial viewport plus a custom scroll engine:** opening old history uses OPEN_AT_BOTTOM_OFFSET = 10_000_000 and subsequent scroll corrections; the streaming follower drives LazyListState.dispatchRawDelta with manually calculated distance, easing, velocity and frame time. Both duplicate responsibilities that belong to Compose's initial layout and native scroll machinery.
2. **Overlapping UI flags and competing scroll commands:** followBottom, userPausedFollow, openingAtBottom, finishSettling, wasStreamingHere, revealed, jumpingToBottom and anchor.settling describe overlapping phases, while send, queue, stream, minimap and Jump can issue competing commands.
3. **State mutations inside SideEffect:** detecting new sends and stream terminal transitions during composition application makes the order difficult to reason about.
4. **Manual geometry-dependent scrolling:** dynamic spacer, measured preceding user height, reply-height floor, frame-based waiting, settle tracking and opening veil compensate for one another. Scrolling should not calculate message heights, visible-content pixel distances, velocities or item-size deltas; Compose already measures children and manages its scrolling.
5. **Presentation coupling:** active stream and stored version have message-level lifecycle differences, but fixing these must not force changes to Markdown/LaTeX/media-preview rendering or their caches.
6. **Overloaded screen:** chat business inputs and user interaction wiring are interwoven with presentation and scroll implementation.

These are refactor targets, not a claim that every mechanism is unnecessary. Benchmark and test before deleting a safeguard **in the chat/container layer**.

### Explicit architectural boundary: content and attachments

- Markdown, LaTeX, media previews (image/video/file), and other rich content are **self-contained message content/attachment renderers**. They receive data and render their own contents. This plan does **not** change their rendering algorithms, incremental parsing, caching, loading, error placeholders, or internal state.
- ChatScreen/message-list may use generic message ID, role, generation status, list index/key and declarative viewport insets. It must **not** need measured item bounds or content height to initiate scrolling, nor inspect Markdown parse completion, LaTeX readiness, image dimensions, media loading state or renderer-specific signals.
- **Jetpack Compose owns child measurement and placement.** The containing Column/LazyColumn passes ordinary constraints to every message child; the child's Compose layout determines its natural width/height. ChatScreen does **not** predict or calculate reserved empty height/width for LaTeX, image/video/media previews, or other content based on format, attachment count, expected aspect ratio, parser state, or later loading.
- A child whose size changes asynchronously (e.g. an attachment becoming visible) undergoes ordinary Compose remeasure/relayout. **Do not read its height to calculate scrolling.** No manual spacer beside/under an attachment to make it "fit", no compensating offset, forced scroll, retry loop, or asset-ready gate.
- Preserve existing attachment composables and any sizes they intentionally define **inside their own layout**. This is a ChatScreen/list responsibility change, not a directive to rewrite media/LaTeX rendering or override its internal constraints.
- If existing ChatSettleTracker/ChatOpenCache coupling depends on internal render/asset readiness, remove that coupling from **ChatScreen's coordination** while leaving the actual renderer/cache implementations untouched. Do not replace it with a new list of content-type exceptions.
- Any bug requiring changes *inside* one of those renderers is **separate work**, not an excuse to expand this ChatScreen refactor's scope.

## 2. Required behavior: do not regress

### Streaming and storage

- Live answer/reasoning comes from **RAM**, through a message-scoped ActiveChatStreamState, not the database.
- **Never checkpoint database writes on a timer or per text/reasoning/tool/usage event.** ChatStreamDisplay's batched UI publication is separate from persistence and may remain.
- Persist only when an assistant block **completes, fails, is interrupted/stopped, or crosses a completed block boundary** (e.g. a tool requests another user turn). Keep completed-block-before-next-block behavior.
- Stop must immediately freeze the already-published visible text and ignore late chunks. Finalization persists that exact partial content and terminal tool status; persistence errors remain visible.
- Keep existing message-content, tool/reasoning, and media-attachment presentation unchanged, including real-time running actions, media placeholders, content ordering, and tool-only/error-only toolbar. Validate integration only; do not alter their internal renderers.
- Temporary chats are never written to Room. Switching chats never mixes streams or cancels unrelated generation.
- Retain in-memory titles/model/usage/files/tool metadata during generation and include them in the final stored snapshot.

### Layout, scrolling, and gestures

- Newly sent user text is placed **immediately beneath the translucent ChatAppBar**, including first send, existing-chat send, and queued message promoted with either the same or a new ID.
- Assistant content starts directly below that user message, with scroll room **after** the newest item, not between the two.
- The newest AI answer retains **30% of screen height** as its bottom breathing room (capped by usable height). Express this as **declarative Compose list-level padding/fill fraction**, not as a manually measured spacer or scroll-distance correction. Place a generic content-end anchor **before** trailing padding so native bring-into-view targets actual content.
- All content heights, trailing layout room, and inter-child placement come from Compose measure/layout. ChatScreen computes neither attachment-cell space nor the pixel distance required to move the list; no manual height-driven scroll corrections.
- Auto-scroll is only a **request to the native Compose scroll system** to reveal/follow the latest content, just as a user scroll changes the same scrollable list. Prefer a generic end-of-real-content BringIntoViewRequester; use official LazyListState index-based APIs for explicit navigation when appropriate. Compose performs actual movement, bounds/constraints handling and gesture arbitration. **Do not manually calculate item heights, remaining distances, pixel deltas, velocities, or frames.** No backward snap, jump or content remount at Stop/completion.
- **Auto-scroll cadence follows actual visible content growth:** each newly published/rendered text change moves the content-end marker through Compose layout, and eligible follow requests reveal that current marker. Fast generation produces correspondingly fast movement; slow generation produces slower movement; paused generation causes no new follow movement. **Animation may be visually smoothed by Compose-native interpolation/easing/spring**, but this smooths *only the displayed motion*, not the generation rate, content updates, target position, or eligibility logic. Never sample token/character throughput, derive pixels-per-second, impose a logical maximum follow speed, or delay/throttle text to create smoothness. Do not queue one animation per chunk: new requests target the *current* content end and supersede obsolete in-flight requests.
- User dragging/flinging through Compose's native gesture/scroll interaction suspends follow, with user input taking precedence over pending automatic requests. Resume on a new send (subject to autoScroll setting), explicit Jump to latest, or native detection that the user has returned to the end; **do not calculate distance-to-end from heights**. Minimap navigation suspends follow.
- New sends align beneath the app bar using native list-key/index positioning and declarative AppBar inset, even if continuous autoScroll is disabled. Do not compute an item-height-based anchor or silently enable continuous follow.
- Opening saved history **constructs its first viewport already at the latest readable message**. The latest item/key is known from loaded message data, and its initial position is part of LazyColumn state/layout initialization, **not a scroll-to-bottom operation, post-render reposition, animated jump, or scroll-controller intent**. Do not use an enormous offset as a substitute for layout. Do not persist scroll position. The veil/warm-cache UX must not mask a bad initial position.
- Preserve edge-to-edge chat, AppBar/status-bar fade, safe NavBar/composer insets, touch/selection/focus/keyboard handling, background images, and current themes.
- Preserve the **existing minimap edge gesture width**, draggable/tappable rail, dismiss gestures, left drawer, and Home pager routing. Do not fix gesture conflicts by enlarging targets or intercepting unrelated swipes.
- Preserve edits, retries, versions, regenerate, deleting, queued sends, summaries, model selection, skill sends, media sends, and temporary-chat lifecycle.

## 3. Unified ownership and state contracts

### A. Generation and display data

**Authoritative state by phase:**

| Phase | Source of truth | UI responsibility |
| --- | --- | --- |
| Active generation | Matching ActiveChatStreamState in RAM | Render text/reasoning incrementally, plus the latest in-memory stored-version metadata/tool events |
| Stop/finalize | Existing frozen stream + finalizing conversation snapshot | Preserve the message's stable identity and scroll offset; leave child renderers' state management untouched |
| Settled / reopened | Final StoredMessage / MessageVersion | Render saved history, not a stale stream |
| Regenerate / edit version | New message/version/generation identity | Scope the new version to the correct item; renderer-local resets remain the renderer's concern |

- In the **message-list/container layer only**, route the appropriate existing live/saved message state to the existing AssistantMessageCard API. Avoid rebuilding the whole history per character.
- Match conversation ID, message ID, version/generation identity as available. A global isGenerating flag alone is not sufficient.
- Retain stable LazyColumn message keys across streaming -> terminal -> saved; **do not rewrite** AssistantMessageCard, StreamingAssistantText, or child Markdown/LaTeX/media-preview render logic to achieve this.
- Treat current stream handoff, incremental Markdown, renderer cache, and media-preview behavior as **existing contracts**. Do not consolidate, replace, optimize, or relocate their internal reconciliation.
- Existing reasoning/tool updates must remain visible even when updatedAt/structuralRevision is unchanged; accomplish any necessary change at the generic message-data subscription boundary rather than inside attachment renderers.
- If stream snapshot retention/cleanup is changed in the surrounding generation/ViewModel lifecycle, never drop the visible response early. Do not modify the internal content renderer's freeze/buffer behavior.
- Recomposition is normal. Prevent remounts caused by **container identity changes**, without introducing type-specific renderer accommodations.

### B. Initial viewport: layout initialization, NOT scrolling

A historical conversation opening is **not navigation inside an existing list**. It creates a fresh viewport whose initial anchor is the last readable history item. The message list/layout owns this initial selection; it does not issue a runtime scroll request.

- Resolve the newest message ID/index from the loaded conversation snapshot **before creating the list state**. Initialize the keyed list/viewport with that anchor using an appropriate native LazyColumn initial-position or bottom-anchored layout strategy; keep chronological reading order and message-ID keys. If the final readable line requires alignment, derive it from layout constraints as part of initial measurement rather than issuing scroll commands after the list appears.
- Investigate the current OPEN_AT_BOTTOM_OFFSET = 10_000_000 hack, firstLoad -> scrollToBottom, and LaunchedEffect(openingAtBottom) -> snapshotFlow(!reachedLatest()) -> scrollToBottom. Remove these **initial-open repositioning** mechanisms, not merely transfer them into a controller.
- Load history before mounting its conversation-keyed message list (or keep a placeholder while loading), so the first real viewport is initialized with the correct items. Do not construct a list at index zero then patch it after asynchronous history arrives.
- An initial anchor can change only when a different conversation is opened or an explicit history view is created. Ordinary stream updates, generic child-size changes, cache misses, or composition do not reinitialize it. The veil is optional visual polish, not a mechanism for hiding corrective scrolls.
- No scroll call of any kind may be triggered **solely by opening an old conversation**; initialize the list position natively. Explicit Jump to latest *after opening* is a valid native Compose scroll request.
- Confirm the latest **generic message item** and its available content area (even when it contains opaque attachments) are visible at first display across large conversations and changing screen/font/inset sizes. Allow Compose to measure each child's natural size; test first-layout geometry, not renderer readiness or manually reserved media/LaTeX slots.

### C. Native Compose scrolling, no custom scroll engine

**Jetpack Compose owns both touch-driven scrolling and auto-scroll movement.** Keep a single LazyColumn/LazyListState and native scroll/relocation APIs, not a custom ChatScrollController, physics engine, frame loop, or direct pixel-delta "backdoor". Auto-scroll sends the same class of **scroll intent** as an explicit user navigation; Compose decides how much movement is needed and resolves scroll/gesture conflicts. **Visual smoothing belongs to Compose's animation layer**, not to a second scroll controller or the generation/display-data pipeline.

**Supported native primitives (choose the simplest after API verification):**

- **BringIntoViewRequester** attached to a generic, stable, Compose-measurable content-end marker immediately after the latest message's **real content**, before the list-level trailing allowance. When a new live content revision is displayed and auto-follow is eligible, request that marker be brought into view. Compose handles its measured position. Do not observe heights or compute target coordinates. If necessary, represent the marker as a separate keyed LazyColumn item so its visibility can be observed without measuring the assistant card. **Use Compose-native animation/smoothing for the visual transition when appropriate**, and ensure every updated request targets the latest position instead of building up animation lag. Easing/spring for visual appearance is permitted; using easing to modify *which content is followed or when it is published* is not.
- **LazyListState.scrollToItem / animateScrollToItem** for explicit message-index/key navigation, such as Jump to latest or minimap target, and ordinary native positioning for a newly accepted user bubble under AppBar. Do not pass a hand-calculated item-height offset.
- **Native list contentPadding / fillParentMaxHeight** for the 30%-screen-height bottom gap and minimum scrollable room needed to position a short final turn. Compose measures the room; it is not a media/math spacer or a scroll-distance formula.
- **Compose's existing drag/nested-scroll interactions** identify manual takeover and cancel/suppress further follow requests. Do not synthesize touch events, override scroll physics, use dispatchRawDelta, or write custom scrollBy velocity/delta loops.

**Only a small intent gate is allowed, not a controller of mechanics:**

- A single lightweight follow eligibility state (autoScroll enabled + user has not detached), plus one-shot new-send, Jump, and minimap requests. Compose/LazyListState is the only actual scrolling owner.
- New conversation: initialize the viewport in layout, **no scrolling**.
- New sent message: issue the native index/key positioning request to align the user item under AppBar; this is a one-shot UI event, independent of continuous autoScroll.
- New streamed content: when following is allowed, request native bring-into-view of the latest real-content marker **as the visible content actually advances**. Follow the position resulting from Compose relayout, not a separately calculated generation rate. Coalesce/cancel obsolete requests; allow Compose to visually interpolate toward the newest target without accumulating per-token/per-chunk animation queues. React to *visible data revisions/events*, not measured content heights or a per-frame ticker.
- Manual gesture or minimap navigation: disable auto-follow immediately; retain the user's actual position. Jump to latest or a new sent message can restore eligibility according to the preference. Detect return to the latest **real content** using a native visible keyed end marker rather than canScrollForward alone (which may include the intentionally empty trailing 30% gap). Never calculate a pixel threshold or item height.
- Generation completes/stops/fails: **do not request any scroll merely because status changes**. Existing scroll position remains; Compose handles any child remeasurement normally.
- Send, Jump, minimap and follow requests must not be launched concurrently as independent competing animations. Use a single request path or cancellable effect to serialize *intents*, relying on Compose's native scroll mutator for motion and gesture precedence. The minimap must not own a separate animation/scroll engine.
- Switching conversation: cancel obsolete requests, create the next conversation's correctly initialized viewport, never replay the old chat's scroll commands.

**Remove**, rather than relocate: the follower's dispatchRawDelta, target-distance/remaining-pixel calculations, measured token/character generation-rate estimator, custom velocity/speed limits, logic-level easing/filtering of streaming progress, per-chunk animation queues, continuous withFrameNanos polling, scroll offset/retry loops, and measured-height-dependent anchor corrections in ChatScreen/ChatScrollAnchor. **Do not remove Compose's normal visual scroll animation/easing/spring** merely to eliminate hand-written scroll physics. Native placement may still use declarative AppBar/composer insets, but no message height is needed to scroll.

Preserve existing behavioral requirements through native API selection and regression tests. If a native primitive does not meet an edge case (such as a long final answer or the 30% gap), first adjust the **declarative list structure / target marker** instead of adding another manual scroll calculator.

### D. Opening and caching

- The historical conversation list owns its **first viewport**, independently of runtime scroll handling. LazyColumn keeps stable message-ID keys and chronological reading order.
- Loading placeholder may indicate **history data is not loaded**, but never wait for Markdown parsing, LaTeX, images, videos or other attachment renderers to report "ready". Do not conceal a scroll-into-place animation behind a veil.
- ChatSettleTracker/ChatOpenCache **must not gate viewport initialization/reveal on attachment readiness**. Remove this ChatScreen-facing dependency if present; do not change the underlying Markdown/LaTeX/media renderer caches.
- Remove opening-related frame-based correction loops. Initialize the viewport from loaded message order and native Compose layout constraints; do not calculate item heights or wait on parser/media completion to choose the initial anchor.
- Renderer implementations, caches, and their asynchronous loading remain **unchanged and independently owned**.

### E. Compose component boundaries

Keep ChatScreen as an orchestration component: gather input/state, compose screen regions, and forward intents.

Target cohesive responsibilities, extracted **only when they improve ownership**:

- Chat message list and per-item callbacks, including generic bring-into-view marker(s) and native LazyListState.
- Initial viewport anchor belongs to message-list construction. A small intent gate controls **whether/when to ask Compose to scroll**; Compose itself controls how, with no custom scroll controller or geometry calculator.
- Generic message-level data routing to the **existing** AssistantMessageCard; Markdown, LaTeX, and media-preview children remain opaque and unchanged.
- Existing app bar, minimap, composer, summary, and dialog/sheet hosts.

Avoid new multi-purpose managers, duplicated state holders, callback wrapper layers with no use, and splitting into tiny files solely to meet a line-count target. Keep dependency direction and naming from AGENTS.md and docs/structure.md.

## 4. Execution plan: small validated changes

### Phase 0 — Baseline and regression locks

- [ ] Reinspect the current diff before editing; preserve uncommitted user changes.
- [ ] Record first-visible-frame **message-item geometry** on cold/warm history opening (before scroll), container/card remounts, idle frames during tool wait, scroll offset/visible-last-message before/after stream completion, and Room write counts. Do **not** instrument or optimize renderer-internal parsing/caching for this refactor.
- [ ] Extend current tests with any missing Stop, minimap, gesture, mixed tool/text, and re-entry cases; do not assume existing failing tests are passing.

### Phase 1 — Stabilize the message-container data contract (no renderer refactor)

- [ ] Verify that the existing message list passes the correct live/terminal/saved message and flow identity to its existing child component without full-history churn.
- [ ] Preserve all existing stream-to-stored, text, reasoning, tool, and attachment rendering **as-is**. Do not change StreamingAssistantText, Markdown/LaTeX parsers, media previews, renderer caches, or their internal handoff.
- [ ] Verify normal completion, Stop, failed generation, tool turn boundary, version change, and conversation switching at **message-container integration level** (content stays visible and no list-item remount due to the container).
- [ ] If terminal stream cleanup is needed in generation/ViewModel ownership, keep it outside renderer implementations and ensure snapshots aren't cleared prematurely.
- [ ] If the source of a regression lies inside a content/attachment renderer, document it as a **separate task**; do not expand this plan.

### Phase 2 — Correct initial viewport; replace manual scrolling with native Compose

- [ ] **First fix opening as layout, not a scroll operation:** select the newest readable item as the initial viewport anchor when conversation history is ready and the keyed LazyColumn is first created. Retain chronological ordering. Remove OPEN_AT_BOTTOM_OFFSET, firstLoad scrollToBottom, and the opening-only snapshotFlow/reposition loop; do **not** move them to another controller.
- [ ] Prove on the **first displayed frame** that old history is at the correct latest **message item** without a scroll command, regardless of whether that child later renders longer text or attachments. Do not wait for renderer readiness.
- [ ] Replace the custom frame/velocity/raw-delta scroll engine with native Compose bring-into-view and index-based list navigation; Compose determines displacement from current layout and manages motion. **Preserve smooth visual movement via Compose's native animation layer**, without independent scroll-speed regulation, delayed content publication, or a per-chunk animation queue. No custom ChatScrollController.
- [ ] Retain only one tiny gate for follow eligibility and serialized one-shot intents (send, Jump, minimap). Use Compose-native gestures to suspend follow. No imperative item-height calculations, scroll-distance math, or simulated touch.
- [ ] Replace SideEffect state transitions with explicit event handling; content revisions can request bring-into-view without monitoring item heights.
- [ ] Let Compose determine all inner message/attachment dimensions and remeasure children when their content changes. Delete **ChatScreen/list-level** calculations that reserve or adjust empty space specifically for LaTeX or media cells; do not touch their internal composables.
- [ ] Place the content-end marker before a declarative 30% trailing viewport allowance and provide sufficient room for short-message anchoring via Compose list layout. Preserve AppBar alignment, pointer arbitration and edge-to-edge insets **without outer-item height/distance calculations**.
- [ ] Eliminate idle frame polling, raw-delta writes, **logic-level** custom easing/speed constants, any throughput measurement, and separate animations queued for each chunk. Keep permitted **Compose-native visual easing/spring** independent of content-generation events. Native Compose relocation follows changes in **visible content position**, with no auto-jump at Stop/completion.
- [ ] Delete superseded flags/effects **only after** integration tests and minimap/drawer gesture tests pass.

### Phase 3 — Reduce ChatScreen responsibilities

- [ ] Extract cohesive list, overlay, or composer wiring without changing the external navigation/callback behavior.
- [ ] Consolidate repeated send/error-handling paths while preserving special behavior for queue, first message, skill, media, and retry.
- [ ] Keep stable item keys and existing selection/input state; avoid remounting content children on stream status changes. Do not alter their own internal state or dropdown/render logic.

### Phase 4 — Prune proven redundancy and measure

- [ ] Remove the custom size/scroll calculation machinery (reply-height floors, tail-height bookkeeping, pixel-distance helpers, manual settling), replacing any required list-level trailing room with declarative Compose layout. Remove renderer-ready gates and format-specific LaTeX/media padding in the chat container. Do not touch attachment rendering.
- [ ] Eliminate avoidable history scans, broad **container-level** state subscriptions, and excess per-frame work. **Do not** optimize/alter Markdown, LaTeX or media materialization, parsing, rendering or caches.
- [ ] Verify bounded lifetime of stopped, completed, replaced, and deleted stream snapshots.
- [ ] Compare with Phase 0; no hidden regressions disguised as lower line count.

### Phase 5 — Verification

- [ ] Unit: initial viewport anchor selection, send detector, native scroll-intent gating/priority, list-level bottom-gap policy, message-state routing, Stop freeze and database write boundaries. **No custom geometry/distance/velocity math tests should be needed for scrolling.** Existing renderer tests stay unchanged.
- [ ] Compose: cold/warm history first frame at latest item with **zero opening scroll calls**; new/empty/existing/queued sends; short and very long answers; opaque dynamically resizing children; native manual drag/fling; user interruption of native auto-scroll; Jump; minimap tap/drag; actual 30% trailing gap; Stop/completion; edits/regeneration; reopen and chat switch. **Feed visible text at slow, fast and bursty cadences, including tool waits; verify smooth native visual interpolation without fixed-speed lag, a per-chunk animation backlog, delayed text, or artificial continued movement during idle.** Assert native requests without item-height or generation-speed measurement.
- [ ] Test keyboard/composer size, portrait/landscape, light/dark themes, backgrounds, system insets, temporary chats, selection and gesture conflicts.
- [ ] Assess first-frame item visibility, stable scroll position, gesture priority, and parent/list remounts. No custom speed/delta/height calculations or renderer-specific callbacks; do **not** require zero Compose recompositions.
- [ ] Run focused tests/lint together after implementation **only when explicitly permitted to run Gradle in this workspace**. Never run Gradle merely to author this plan.
- [ ] Update docs/structure.md only if the implementation moves files. Do not build, commit, push, change version, or touch unrelated code as part of planning.

## 5. Definition of done

1. Only one presentation authority for each streaming message and only block-boundary database writes. Stopped/completed text remains visible and exactly preserved.
2. Historical opening initializes the viewport **without scrolling**. All subsequent manual/automatic scrolling uses **Compose's native LazyColumn scrolling and gesture machinery**; auto-scroll only requests a reveal, with no backdoor pixel movement, competing scroll engine, or content-height math.
3. Finishing a stream preserves stable **message-item** identity and scroll position; content/attachment renderers behave exactly as before and require no ChatScreen-specific handling.
4. First/queued/normal user messages align below AppBar using native index/key navigation; the newest AI reply keeps its declarative 30% trailing gap and Compose computes the required movement without scrolling into invisible padding.
5. Existing actions/reasoning/attachment presentation stays functional and independent. **Compose alone sizes and lays out LaTeX/media content**; ChatScreen has **no format-specific blank-space calculations, placeholder heights, or conditions/effects specialized for Markdown, LaTeX or media-preview readiness**.
6. **Scroll pace emerges from the rate at which content becomes visible**; **Compose-native animation may smooth the visual trajectory** but must not slow, buffer, filter, or cap generation-follow logic. Fast/slow/bursty streams track their actual growth without accumulating animations. Long tool waits do not keep an idle frame loop running; there are **no custom frame-based scroll calculations, raw deltas, or logical speed/easing regulators**. Historical rows are stable and performance is no worse than baseline.
7. Existing and new regression tests pass when executed in an authorized environment; otherwise clearly record what was not verified.

## Out of scope

**All Markdown, LaTeX, and media-preview rendering logic**, their parsers, async loading, caching, lifecycle and UI implementation; other rich-content renderers; API provider protocols, feature tools, Room schema, app-wide navigation redesign, new settings, gesture hitboxes, unrelated services, or eliminating normal Compose recomposition. Do not touch renderer files to resolve ChatScreen-specific architectural problems.

