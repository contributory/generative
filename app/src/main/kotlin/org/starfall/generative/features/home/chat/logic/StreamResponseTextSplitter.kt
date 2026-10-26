package org.starfall.generative.features.home.chat.logic

/** Newly visible pieces extracted from an arbitrarily fragmented model response. */
internal data class StreamResponseText(
    val content: String,
    val reasoning: String
)

/**
 * Incrementally strips an optional leading `<think>...</think>` wrapper without rescanning the
 * accumulated answer. Marker prefixes may be split across any number of provider events.
 */
internal class StreamResponseTextSplitter(
    initialContentLength: Int = 0,
    alreadyInsideVisibleContent: Boolean = false
) {
    private enum class Mode { PROBING_PREFIX, VISIBLE, THINKING, AFTER_THINK, FINISHED }

    private var mode = if (alreadyInsideVisibleContent) Mode.VISIBLE else Mode.PROBING_PREFIX
    private val prefixProbe = StringBuilder()
    private val closingMarkerProbe = StringBuilder()
    private var contentLength = initialContentLength.toLong()
    private var hasNonWhitespaceReasoning = false
    private var pendingReasoningWhitespace = 0L
    private var visibleReasoningLengthValue = 0L

    val visibleContentLength: Int
        get() = contentLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Matches the length of the persisted inline reasoning after its existing trim semantics. */
    val visibleReasoningLength: Int
        get() = visibleReasoningLengthValue.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    fun append(fragment: String): StreamResponseText {
        check(mode != Mode.FINISHED) { "Cannot append to a finalized response splitter" }
        val content = StringBuilder()
        val reasoning = StringBuilder()
        fragment.forEach { character ->
            when (mode) {
                Mode.PROBING_PREFIX -> processPrefixCharacter(character, content, reasoning)
                Mode.VISIBLE -> emitContent(character, content)
                Mode.THINKING -> processThinkingCharacter(character, reasoning)
                Mode.AFTER_THINK -> {
                    if (character.isWhitespace()) Unit else {
                        mode = Mode.VISIBLE
                        emitContent(character, content)
                    }
                }
                Mode.FINISHED -> error("Cannot append to a finalized response splitter")
            }
        }
        return StreamResponseText(content.toString(), reasoning.toString())
    }

    /** Emits unresolved marker-prefix characters exactly as the established full-text behavior does. */
    fun finish(): StreamResponseText {
        if (mode == Mode.FINISHED) return StreamResponseText("", "")
        val content = StringBuilder()
        val reasoning = StringBuilder()
        when (mode) {
            Mode.PROBING_PREFIX -> {
                content.append(prefixProbe)
                contentLength += prefixProbe.length.toLong()
                prefixProbe.setLength(0)
            }
            Mode.THINKING -> {
                closingMarkerProbe.forEach { emitReasoningCharacter(it, reasoning) }
                closingMarkerProbe.setLength(0)
            }
            else -> Unit
        }
        mode = Mode.FINISHED
        return StreamResponseText(content.toString(), reasoning.toString())
    }

    private fun processPrefixCharacter(character: Char, content: StringBuilder, reasoning: StringBuilder) {
        prefixProbe.append(character)
        val candidate = prefixProbe.toString()
        when {
            candidate == OPEN_THINK_TAG -> {
                prefixProbe.setLength(0)
                mode = Mode.THINKING
            }
            OPEN_THINK_TAG.startsWith(candidate) -> Unit
            else -> {
                content.append(prefixProbe)
                contentLength += prefixProbe.length.toLong()
                prefixProbe.setLength(0)
                mode = Mode.VISIBLE
            }
        }
    }

    private fun processThinkingCharacter(character: Char, reasoning: StringBuilder) {
        closingMarkerProbe.append(character)
        while (closingMarkerProbe.isNotEmpty() && !CLOSE_THINK_TAG.startsWith(closingMarkerProbe.toString())) {
            emitReasoningCharacter(closingMarkerProbe[0], reasoning)
            closingMarkerProbe.deleteCharAt(0)
        }
        if (closingMarkerProbe.length == CLOSE_THINK_TAG.length) {
            closingMarkerProbe.setLength(0)
            mode = Mode.AFTER_THINK
        }
    }

    private fun emitContent(character: Char, content: StringBuilder) {
        content.append(character)
        contentLength += 1
    }

    private fun emitReasoningCharacter(character: Char, reasoning: StringBuilder) {
        reasoning.append(character)
        if (!hasNonWhitespaceReasoning) {
            if (!character.isWhitespace()) {
                hasNonWhitespaceReasoning = true
                visibleReasoningLengthValue++
            }
        } else if (character.isWhitespace()) {
            pendingReasoningWhitespace++
        } else {
            visibleReasoningLengthValue += pendingReasoningWhitespace + 1L
            pendingReasoningWhitespace = 0L
        }
    }

    private companion object {
        const val OPEN_THINK_TAG = "<think>"
        const val CLOSE_THINK_TAG = "</think>"
    }
}
