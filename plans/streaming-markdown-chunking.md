# Streaming Markdown Chunking Plan

## Goal

While an answer streams, the chat re-parses and re-composes the **whole** answer on every update. For a long answer this is O(n) work per update on the main thread (O(n²) overall), so frames drop. Fix: split the streaming answer into **frozen chunks** (finished top-level blocks that never change again) and one small **tail** (the last, still-growing block). Frozen chunks are parsed and composed once; only the tail is re-parsed on each update.

This file is the progress tracker. Resume at the first unchecked item. Work one item at a time, easy → hard. Do not build, commit or push unless asked; run lint + tests once at the end (see `AGENTS.md`).

## Already done (do not redo)

- `navigation/AppNavigation.kt` no longer reads the whole `currentConversation` on every streaming update (it derives id / provider+model / prompt ids with `derivedStateOf`). That removed the app-wide recomposition. This plan is only about the chat answer itself.

## Why the current code is slow (read this before editing)

Data flow today, per streaming update (about every 48 ms from `ChatGeneration`):

1. `ChatGeneration.scheduleUpdate()` publishes a new `Conversation` → `ChatScreen` gets a new message list.
2. `AssistantMessageCard` → `SmoothStreamingMarkdownMessage` → `StreamingMarkdownRenderer` (`features/home/chat/ui/markdown/MarkdownRenderer.kt`) copies the text into `displayContent` at most every 64 ms.
3. `MarkdownRenderer(content = displayContent)` runs `parseLibraryMarkdown(input)` on **the entire text** (off the main thread), assigns the new `State.Success`, and `Markdown(state = parsed, …)` then re-composes **every** block: each paragraph calls `buildMarkdownAnnotatedString`, `MathSplitter.split`, text layout, etc.

Cost grows with the answer length. A 10 000-character answer pays for 10 000 characters on every update although only the last few characters changed.

## Design

```
content (full streaming text)
 ├─ frozen prefix = chunk 1 + chunk 2 + … chunk k   (each chunk = one or more complete top-level blocks, never changes)
 └─ tail          = last block, still growing        (re-parsed on every update; small)
```

Rules that keep the result visually identical to the single-renderer version:

- The Markdown library puts a block-sized spacer **before every top-level block** and none after the last one. A `Column` of `MarkdownRenderer`s with **no extra spacing** therefore has exactly the same layout as one renderer. Do not add padding or `Arrangement.spacedBy` between chunks.
- A chunk is cut **only at a blank-line boundary outside a fenced code block**, and never inside a list or an HTML block (rules below). Everything else is left in the tail.
- Frozen chunks are rendered with `isStreaming = false` and the streaming text fade **disabled**. Only the tail keeps the fade and the "open code fence is streaming" detection (which relies on the block ending at `content.length` — the tail does end there).
- A chunk must already be **parsed and in `MarkdownParseCache` before it appears**, otherwise its first frame is `State.Loading` (zero height) and the layout jumps. So the parse happens first (off the main thread), and the state update that adds the chunk and shrinks the tail is **one atomic state write**.
- When streaming ends, switch back to the existing single `MarkdownRenderer` (the path used by finished messages, the cache and `ChatOpenCache`). The final text must be parsed and cached **before** the switch for the same reason.

## Step 1 — Pure splitter (logic layer)

New file `app/src/main/kotlin/org/starfall/generative/features/home/chat/logic/markdown/StableMarkdownPrefix.kt`. No Compose imports (`docs/structure.md`: logic must not depend on UI).

```kotlin
package org.starfall.generative.features.home.chat.logic.markdown

/**
 * Offset in [text] where the LAST top-level block starts; text[0 until offset] is safe to freeze.
 * Returns 0 when nothing can be frozen yet. [text] must start at a safe boundary (all fences closed),
 * which is true for the whole answer and for the tail after a previous cut.
 */
internal fun stableMarkdownPrefixLength(text: String): Int {
    var offset = 0
    var result = 0
    var fenceChar = ' '          // ' ' = not inside a fence
    var fenceLength = 0
    var sawBlank = false
    var blockIsList = false      // flags describe the block that began at the last accepted boundary
    var blockIsHtml = false
    var first = true
    while (offset < text.length) {
        val newline = text.indexOf('\n', offset)
        val lineEnd = if (newline < 0) text.length else newline
        val line = text.substring(offset, lineEnd)
        val trimmed = line.trimStart()

        if (fenceChar != ' ') {
            // Inside a fence: blank lines and everything else are content, until the closing fence.
            if (closesFence(trimmed, fenceChar, fenceLength)) fenceChar = ' '
            offset = lineEnd + 1
            continue
        }
        if (line.isBlank()) {
            sawBlank = true
            offset = lineEnd + 1
            continue
        }
        val isList = isListMarker(trimmed) && line.length - trimmed.length < 4
        if (first) {
            blockIsList = isList
            blockIsHtml = line.startsWith("<")
            first = false
        } else if (sawBlank) {
            val continuation = line.startsWith(' ') || line.startsWith('\t')
            val sameList = isList && blockIsList
            if (!continuation && !sameList && !blockIsHtml) {
                result = offset                      // a new block starts here: everything before is frozen
                blockIsList = isList
                blockIsHtml = line.startsWith("<")
            }
        }
        sawBlank = false
        openingFence(trimmed)?.let { (char, length) -> fenceChar = char; fenceLength = length }
        offset = lineEnd + 1
    }
    return result
}
```

Helpers to write in the same file (keep each tiny and unit-tested):

- `openingFence(trimmed: String): Pair<Char, Int>?` — the line starts with 3 or more `` ` `` or `~` (same character). For a backtick fence the rest of the line (info string) must not contain a backtick. Returns the character and the run length.
- `closesFence(trimmed, char, length)` — the line is only `char` repeated **at least** `length` times, optionally followed by whitespace.
- `isListMarker(trimmed)` — matches `^([-*+]|\d{1,9}[.)])[ \t]` (do not allocate a `Regex` per call; compile once in a top-level `private val`).

Why these rules (do not simplify them away):

| Case | Rule | Reason |
|---|---|---|
| Blank line inside a fence | ignored | A code block can contain blank lines; cutting there breaks it. |
| Next line starts with space/tab | no cut | Indented line continues a list item or an indented code block. |
| Next line is a list marker and the current block is a list | no cut | Splitting a loose list into two lists changes spacing and numbering. |
| Current block starts with `<` (HTML) | no cut | HTML blocks such as `<pre>`/comments can span blank lines. |
| Text ends with blank lines but no next block | no cut | Nothing after the blank line yet; the last block is still the tail. |
| Table, setext heading, blockquote lines | no blank line inside, nothing special | They cannot contain a blank line, so a blank-line cut never splits them. |

Accepted limitation: a very long loose list or an HTML block stays in the tail. That falls back to today's behavior (correct, only slower). Reference-style links defined in a later block are not resolved across chunks while streaming; the final single render resolves them.

Math (`$$…$$`, `\[…\]`) needs no special handling: `MathSplitter` works per paragraph and a paragraph never contains a blank line.

## Step 2 — Unit tests

New file `app/src/test/java/org/starfall/generative/StableMarkdownPrefixTest.kt` (same package and style as `MathSplitterTest.kt`: JUnit4, `assertEquals`). Assert on the **returned offset** using `text.indexOf("…")` so the tests stay readable. Required cases:

| Input (`\n` = newline) | Expected |
|---|---|
| `""` and `"hello"` | `0` |
| `"a\n\nb"` | offset of `b` |
| `"a\n\nb\n\nc"` | offset of `c` (the last block starts there) |
| `"a\n\n"` (trailing blank, no next block) | `0` |
| `"a\n\n```kotlin\nx\n\ny\n```\n\nb"` | offset of `b` (blank line inside the closed fence is ignored) |
| `"a\n\n```kotlin\nx\n\ny"` (unclosed fence) | offset of the opening fence (it starts the tail) |
| `"~~~\nx\n\n~~~~\n\nb"` | offset of `b` (longer closing fence closes) |
| `"- a\n\n- b\n\n- c"` | `0` (one loose list) |
| `"- a\n\ntext"` | offset of `text` |
| `"1. a\n\n   continued\n\nnext"` | offset of `next` (indented line is a continuation) |
| `"<div>\n\nx\n\ny"` | `0` (HTML block) |
| `"| a | b |\n|---|---|\n| 1 | 2 |\n\ntext"` | offset of `text` |
| `"a\r\n\r\nb"` (CRLF) | offset of `b` |
| Vietnamese text, e.g. `"Xin chào\n\nThế giới"` | offset of `Thế` |

Also add one **property-style test**: for several sample documents, for every prefix length `n`, the result is `0` or a value `< n` that points at a non-blank character, and calling `stableMarkdownPrefixLength(text.substring(result))` on the tail never returns a value `>= tail.length`. This protects the "cut only at a block start" invariant.

## Step 3 — Chunked renderer (UI layer)

New file `app/src/main/kotlin/org/starfall/generative/features/home/chat/ui/markdown/ChunkedStreamingMarkdown.kt` (file name matches its primary composable). It reuses `MarkdownRenderer` unchanged.

```kotlin
private data class FrozenMarkdown(val prefix: String, val chunks: List<String>) {
    companion object { val EMPTY = FrozenMarkdown("", emptyList()) }
}

@Composable
internal fun ChunkedStreamingMarkdown(content: String, modifier: Modifier = Modifier) {
    val latestContent by rememberUpdatedState(content)
    var frozen by remember { mutableStateOf(FrozenMarkdown.EMPTY) }

    // Text that no longer starts with the frozen prefix (regeneration, tool-block restart, think-tag
    // stripping) invalidates it. Derive it at composition time so no stale frame is ever drawn.
    val valid = if (content.startsWith(frozen.prefix)) frozen else FrozenMarkdown.EMPTY
    val tail = content.substring(valid.prefix.length)

    LaunchedEffect(Unit) {
        snapshotFlow { latestContent }.conflate().collect { text ->
            var base = frozen.takeIf { text.startsWith(it.prefix) } ?: FrozenMarkdown.EMPTY
            if (base !== frozen) frozen = base
            while (true) {                                   // a burst may contain several finished blocks
                val current = latestContent
                if (!current.startsWith(base.prefix)) break
                val rest = current.substring(base.prefix.length)
                val cut = stableMarkdownPrefixLength(rest)
                if (cut <= 0) break
                val chunk = rest.substring(0, cut)
                val parsed = withContext(Dispatchers.Default) { parseLibraryMarkdown(chunk) }
                MarkdownParseCache.put(chunk, parsed)        // must be cached BEFORE the chunk is shown
                if (!latestContent.startsWith(base.prefix + chunk)) break   // text changed during the parse
                base = FrozenMarkdown(base.prefix + chunk, base.chunks + chunk)
                frozen = base                                // ONE atomic write: chunk appears, tail shrinks
            }
        }
    }

    Column(modifier.fillMaxWidth()) {                        // no spacing: the library already spaces blocks
        CompositionLocalProvider(LocalStreamingTextFade provides false) {
            valid.chunks.forEachIndexed { index, chunk ->
                key(index) { MarkdownRenderer(content = chunk, isStreaming = false) }
            }
        }
        if (tail.isNotBlank()) MarkdownRenderer(content = tail, isStreaming = true)
    }
}
```

Checks while implementing:

- `MarkdownParseCache.put` only stores `State.Success` whose `content == chunk`; that is true for the parse above, so do not trim or alter `chunk` between parsing and rendering.
- `MarkdownRenderer` already reads `MarkdownParseCache.get(content)` for its initial state, so a cached chunk is drawn at its final height on the first frame.
- The tail is passed through `MarkdownRenderer` unchanged, including its `isStreaming && !fence.closed && endOffset == content.length` code-block check.
- Imports needed: `snapshotFlow`, `conflate`, `Dispatchers`, `withContext`, `key`, `CompositionLocalProvider`, `LocalStreamingTextFade` (`shared/ui/text`), `parseLibraryMarkdown`, `stableMarkdownPrefixLength`, `MarkdownParseCache`.

## Step 4 — Wire it into `StreamingMarkdownRenderer`

Edit only `StreamingMarkdownRenderer` in `MarkdownRenderer.kt`. Keep its existing 64 ms `displayContent` throttle and fade-finish logic.

1. Add `var settled by remember { mutableStateOf(!isStreaming) }`. A message that is already finished when first composed uses the old single renderer immediately (cache path, `ChatOpenCache` unaffected).
2. In the existing `LaunchedEffect`, streaming branch: set `settled = false`.
3. In the non-streaming branch, after `displayContent = latestContent` and the existing fade `delay(...)`, **before** setting `settled = true`:
   ```kotlin
   val finalText = latestContent
   if (MarkdownParseCache.get(finalText) == null) {
       MarkdownParseCache.put(finalText, withContext(Dispatchers.Default) { parseLibraryMarkdown(finalText) })
   }
   displayContent = finalText
   settled = true
   ```
   Without this pre-parse the single renderer starts as `State.Loading` and the whole answer blinks blank at the end.
4. Render:
   ```kotlin
   CompositionLocalProvider(LocalStreamingTextFade provides (isStreaming || finishingFade)) {
       if (settled) MarkdownRenderer(content = displayContent, modifier = modifier, isStreaming = false)
       else ChunkedStreamingMarkdown(content = displayContent, modifier = modifier)
   }
   ```
   `isStreaming` passed to the tail is always `true` inside `ChunkedStreamingMarkdown`; this is correct because `settled` only becomes true after streaming ended.
5. Do **not** change `FormattedMarkdownMessage`, `UserMessageCard`, `MessageBubble`, `MarkdownParseCache` or `ChatOpenCache`.

## Step 5 — Verify

No automatic check can see jank, so do both:

- Automatic: `StableMarkdownPrefixTest` plus the existing `MarkdownParserTest` / `MathSplitterTest` pass; lint clean (run once, at the end).
- Manual, with the same long prompt before and after the change (ask for ~3 000 words with headings, a long list, a table, a code block with blank lines, and a LaTeX formula):
  1. Streaming is smooth; no block flickers or changes height when it becomes a chunk.
  2. No blank flash or height jump when the answer completes.
  3. An answer that ends inside an unclosed code fence still shows the "streaming" code block until the fence closes.
  4. Regenerate and switch message versions while streaming: no stale text from the previous answer.
  5. Frame stats: `adb shell dumpsys gfxinfo <applicationId> reset`, stream the answer, then `adb shell dumpsys gfxinfo <applicationId>` and compare "Janky frames" and the 90th/95th percentile frame time before vs after (`applicationId` is in `app/build.gradle.kts`).

## Not in this plan (possible later work, measure first)

- `ChatScreen` is one large composable that takes `conversation` as a parameter, so its whole body recomposes on every update. Split the streaming message into its own state-reading scope only if the frame stats above are still poor.
- Each 2 s checkpoint in `ChatGeneration` calls `ConversationRepository.persistChildren`, which reloads and diffs all existing messages and versions. It runs on `Dispatchers.IO`, so it is not a UI-thread cost, but a cheaper partial-save path for the streaming message could reduce I/O.

## Checklist

- [x] Step 1: `StableMarkdownPrefix.kt` (+ list of helper functions)
- [x] Step 2: `StableMarkdownPrefixTest.kt` (table cases + property-style test)
- [x] Step 3: `ChunkedStreamingMarkdown.kt`
- [x] Step 4: wire into `StreamingMarkdownRenderer` (settled flag, pre-parse, render switch)
- [x] Update `docs/structure.md` with the two new files
- [ ] Step 5: run lint + tests once; manual verification list

## Implementation notes (deviations from the plan above)

- **`initialParse` parameter.** `MarkdownRenderer` takes `initialParse: State? = null`, used before `MarkdownParseCache`. Frozen chunks pass the parse they were created with, so they do not depend on the soft-reference cache (which could drop an entry and show `State.Loading` on a chunk). The tail uses the same parameter with a `tailSeed` that is parsed together with each freeze.
- **`FrozenChunk` / `FrozenMarkdown`.** `FrozenChunk(text, parsed)` holds the state; `FrozenMarkdown(prefix, chunks, tailSeed)` is written in one state update. The tail renderer is keyed by the frozen state so a frozen block never also appears in the tail.
- **Failed parse stays in the tail.** A chunk whose parse is not `State.Success` is not frozen; the final render handles it.
- **Digits-only last line.** A last line made only of digits with no trailing newline (`1`, `12`) is not a cut point, because the next keystroke can turn it into `1. item`.
- **Render condition.** The single renderer is used when `(settled && !isStreaming)`, or when the text is above `MAX_MARKDOWN_RENDER_CHARS` (truncated preview, as before). The `!isStreaming` guard avoids showing the previous answer for one frame when a regeneration starts.
- **Verification so far.** `stableMarkdownPrefixLength` was checked with the 17 table/property cases in `StableMarkdownPrefixTest.kt` and mutation-tested (2 cases fail when the digits-only rule is disabled). Those checks used `kotlinc` 2.4.21 with a JUnit stub because Gradle/Maven could not be reached from the sandbox. **Gradle build, lint, unit tests, and the manual/gfxinfo checks in Step 5 have not been run.**
