package org.starfall.generative.features.home.chat.ui

import kotlin.math.floor

/** One lazy-list row (a chronological turn) as reported by the native layout info. */
internal data class MinimapVisibleTurn(val id: String, val offset: Int, val size: Int)

/**
 * The readable viewport expressed in fractional turn indexes: `2.25` is a quarter of the way
 * through the third turn. This is the only "position" the scrollbar knows; there is no pixel
 * height estimate for off-screen content, so streaming growth never moves the thumb by itself.
 */
internal data class MinimapSpan(val start: Float, val end: Float) {
    val length: Float get() = end - start
}

/** [progress] is 0 at the oldest content and 1 at the latest; [height] is a fraction of the track. */
internal data class MinimapThumb(val progress: Float, val height: Float) {
    /** Top edge as a fraction of the track, so the thumb always stays inside it. */
    val top: Float get() = progress * (1f - height)
}

/**
 * A scroll target: [fraction] of the way through turn [index]. `index == turnCount` is the
 * zero-height end boundary, i.e. "the latest content's end".
 */
internal data class MinimapPosition(val index: Int, val fraction: Float)

private const val MINIMAP_MIN_SCROLL_RANGE = 1e-3f

/** Clip the visible rows to the readable area (below the app bar, above the composer). */
internal fun minimapVisibleSpan(
    turnIndices: Map<String, Int>,
    visible: List<MinimapVisibleTurn>,
    viewportStart: Int,
    viewportEnd: Int
): MinimapSpan? {
    if (viewportEnd <= viewportStart) return null
    var start = Float.MAX_VALUE
    var end = -1f
    for (item in visible) {
        val index = turnIndices[item.id] ?: continue
        if (item.size <= 0 || item.offset >= viewportEnd ||
            item.offset.toLong() + item.size <= viewportStart) continue
        val from = ((viewportStart - item.offset).toFloat() / item.size).coerceIn(0f, 1f)
        val to = ((viewportEnd - item.offset).toFloat() / item.size).coerceIn(0f, 1f)
        start = minOf(start, index + from)
        end = maxOf(end, index + to)
    }
    return if (end < 0f) null else MinimapSpan(start, end)
}

/** Standard scrollbar geometry measured in turns: thumb size is the visible share of all turns. */
internal fun minimapThumb(turnCount: Int, span: MinimapSpan?, minHeight: Float): MinimapThumb {
    if (turnCount <= 0 || span == null) return MinimapThumb(0f, 1f)
    val length = span.length.coerceIn(0f, turnCount.toFloat())
    val height = (length / turnCount).coerceIn(minHeight.coerceIn(0f, 1f), 1f)
    val range = turnCount - length
    val progress = if (range <= MINIMAP_MIN_SCROLL_RANGE) 0f else (span.start / range).coerceIn(0f, 1f)
    return MinimapThumb(progress, height)
}

/** Inverse of [minimapThumb]: where the viewport start belongs for a given thumb [progress]. */
internal fun minimapPosition(turnCount: Int, visibleLength: Float, progress: Float): MinimapPosition? {
    if (turnCount <= 0 || !progress.isFinite() || !visibleLength.isFinite()) return null
    // A thumb at the bottom of the track always means the end of the latest content, even when
    // the last turn is taller than the viewport.
    if (progress >= 1f) return MinimapPosition(turnCount, 0f)
    val range = (turnCount - visibleLength.coerceIn(0f, turnCount.toFloat())).coerceAtLeast(0f)
    val start = progress.coerceIn(0f, 1f) * range
    val index = floor(start).toInt().coerceIn(0, turnCount - 1)
    return MinimapPosition(index, (start - index).coerceIn(0f, 1f))
}
