package org.starfall.generative.features.home.chat.ui.input

/** Provenance for the text actually inserted by a skill menu selection, not a text match. */
internal data class SkillPromptAttachment(val start: Int, val end: Int) {
    /** Retain the attachment while any of the original inserted characters survive an edit. */
    fun afterEdit(before: String, after: String): SkillPromptAttachment? {
        if (before == after) return this
        val prefix = before.commonPrefixWith(after).length
        val suffix = before.substring(prefix).commonSuffixWith(after.substring(prefix)).length
        val removedEnd = before.length - suffix
        val delta = after.length - before.length
        val leftStart = start
        val leftEnd = minOf(end, prefix)
        val rightStart = maxOf(start, removedEnd)
        val rightEnd = end
        val keptLeft = leftEnd > leftStart
        val keptRight = rightEnd > rightStart
        if (!keptLeft && !keptRight) return null
        return SkillPromptAttachment(
            start = if (keptLeft) leftStart else rightStart + delta,
            end = if (keptRight) rightEnd + delta else leftEnd
        )
    }
}
