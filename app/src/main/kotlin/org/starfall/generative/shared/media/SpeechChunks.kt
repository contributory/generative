package org.starfall.generative.shared.media

// A period followed by a line break (or other adjacent separators) is one boundary.
// Decimal points inside a number are not sentence boundaries.
private const val SENTENCE_SEPARATOR = "(?<!\\d)\\.|\\.(?!\\d)|[\\r\\n。]"
private val sentenceBoundaries = Regex("(?:$SENTENCE_SEPARATOR)(?:[\\t ]*(?:$SENTENCE_SEPARATOR))*")

/** Group speech by a service's configured sentence count, keeping an independent API size cap. */
internal fun speechChunks(text: String, sentencesPerChunk: Int = 10, maxCharacters: Int = 4_000): List<String> {
    require(sentencesPerChunk > 0)
    require(maxCharacters >= 2)
    if (text.isBlank()) return emptyList()
    val sentences = buildList {
        var start = 0
        sentenceBoundaries.findAll(text).forEach { boundary ->
            val end = boundary.range.last + 1
            text.substring(start, end).trim().takeIf { part -> part.any { it.isLetterOrDigit() } }?.let { add(it) }
            start = end
        }
        text.substring(start).trim().takeIf { part -> part.any { it.isLetterOrDigit() } }?.let { add(it) }
    }
    val groups = sentences.chunked(sentencesPerChunk).map { it.joinToString(" ") }
    return groups.flatMap { group ->
        buildList {
            var remaining = group
            while (remaining.length > maxCharacters) {
                var split = remaining.lastIndexOf(' ', maxCharacters).takeIf { it > maxCharacters / 2 } ?: maxCharacters
                if (Character.isHighSurrogate(remaining[split - 1]) && Character.isLowSurrogate(remaining[split])) split--
                add(remaining.substring(0, split).trim())
                remaining = remaining.substring(split).trimStart()
            }
            if (remaining.isNotEmpty()) add(remaining)
        }
    }
}
