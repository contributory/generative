package org.starfall.generative.home.chat.logic.markdown

/**
 * Offset in [text] where the LAST top-level block starts; text[0 until offset] is safe to freeze.
 * Returns 0 when nothing can be frozen yet. [text] must start at a safe boundary (all fences closed),
 * which is true for the whole answer and for the tail after a previous cut.
 *
 * The decision for a line only depends on that line and the lines before it, so a result never moves
 * back when more text arrives. The one exception is a last line that may still grow into a list marker;
 * that line is left undecided (see [DIGITS_ONLY]).
 */
internal fun stableMarkdownPrefixLength(text: String): Int {
    var offset = 0
    var result = 0
    var fenceChar = NO_FENCE
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

        if (fenceChar != NO_FENCE) {
            // Inside a fence: blank lines and everything else are content, until the closing fence.
            if (closesFence(trimmed, fenceChar, fenceLength)) fenceChar = NO_FENCE
            offset = lineEnd + 1
            continue
        }
        if (line.isBlank()) {
            sawBlank = true
            offset = lineEnd + 1
            continue
        }
        // Digits without a delimiter yet ("1", "12") may still become "1. item". Until the newline
        // arrives, the decision for this line is unknown, so stop here.
        if (newline < 0 && DIGITS_ONLY.matches(trimmed)) break

        val isList = isListMarker(trimmed) && line.length - trimmed.length < 4
        if (first) {
            blockIsList = isList
            blockIsHtml = trimmed.startsWith("<")
            first = false
        } else if (sawBlank) {
            val continuation = line.startsWith(' ') || line.startsWith('\t')
            val sameList = isList && blockIsList
            if (!continuation && !sameList && !blockIsHtml) {
                result = offset                      // a new block starts here: everything before is frozen
                blockIsList = isList
                blockIsHtml = trimmed.startsWith("<")
            }
        }
        sawBlank = false
        openingFence(trimmed)?.let { (char, length) -> fenceChar = char; fenceLength = length }
        offset = lineEnd + 1
    }
    return result
}

private const val NO_FENCE = ' '

private val LIST_MARKER = Regex("""^([-*+]|\d{1,9}[.)])([ \t]|$)""")
private val DIGITS_ONLY = Regex("""^\d{1,9}$""")

/** A bare marker ("-") counts as a list item too: it is the start of an empty list item. */
private fun isListMarker(trimmed: String): Boolean = LIST_MARKER.containsMatchIn(trimmed)

/** The line starts with 3 or more backticks or tildes; for backticks the info string must not contain one. */
private fun openingFence(trimmed: String): Pair<Char, Int>? {
    val char = trimmed.firstOrNull() ?: return null
    if (char != '`' && char != '~') return null
    val length = trimmed.takeWhile { it == char }.length
    if (length < 3) return null
    if (char == '`' && trimmed.indexOf('`', length) >= 0) return null
    return char to length
}

/** The line is only [char] repeated at least [length] times, optionally followed by whitespace. */
private fun closesFence(trimmed: String, char: Char, length: Int): Boolean {
    val run = trimmed.takeWhile { it == char }.length
    return run >= length && trimmed.substring(run).isBlank()
}
