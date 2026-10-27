package org.starfall.generative.features.home.chat.ui

/** Chronological, normalized bounds. Each entry maps to one keyed item in the reversed chat list. */
internal data class MinimapSpan(val id: String, val start: Float, val end: Float)
internal data class MinimapTarget(val id: String, val reverseIndex: Int, val fraction: Float)
internal data class MinimapVisibleItem(val id: String, val offset: Int, val size: Int)

internal fun minimapSpans(items: List<Pair<String, Float>>): List<MinimapSpan> {
    val weights = items.map { (_, height) -> if (height.isFinite()) height.coerceAtLeast(1f).toDouble() else 1.0 }
    val total = weights.sum()
    if (total == 0.0) return emptyList()
    var cursor = 0.0
    return items.mapIndexed { index, (id, _) ->
        val start = cursor / total
        cursor += weights[index]
        MinimapSpan(id, start.toFloat(), (cursor / total).toFloat())
    }
}

internal fun minimapTarget(spans: List<MinimapSpan>, position: Float): MinimapTarget? {
    if (spans.isEmpty() || !position.isFinite()) return null
    val fraction = position.coerceIn(0f, 1f)
    val index = spans.indexOfFirst { fraction < it.end }.takeIf { it >= 0 } ?: spans.lastIndex
    val span = spans[index]
    return MinimapTarget(
        span.id, spans.lastIndex - index,
        ((fraction - span.start) / (span.end - span.start).coerceAtLeast(0.0000001f)).coerceIn(0f, 1f)
    )
}

/** LazyColumn(reverseLayout = true) measures offsets from the newest/bottom end. */
internal fun minimapViewport(
    spans: List<MinimapSpan>,
    visible: List<MinimapVisibleItem>,
    viewportStart: Int,
    viewportEnd: Int
): ClosedFloatingPointRange<Float>? {
    if (viewportEnd <= viewportStart) return null
    val byId = spans.associateBy { it.id }
    var top = 1f
    var bottom = 0f
    visible.forEach { item ->
        val span = byId[item.id] ?: return@forEach
        val from = maxOf(item.offset, viewportStart)
        val to = minOf(item.offset + item.size, viewportEnd)
        if (item.size <= 0 || to <= from) return@forEach
        val length = span.end - span.start
        top = minOf(top, span.start + length * (1f - (to - item.offset).toFloat() / item.size))
        bottom = maxOf(bottom, span.start + length * (1f - (from - item.offset).toFloat() / item.size))
    }
    return if (bottom > top) top.coerceIn(0f, 1f)..bottom.coerceIn(0f, 1f) else null
}

/** Center a point inside a long message after its actual height has been measured. */
internal fun minimapScrollOffset(itemSize: Int, fraction: Float, viewportSize: Int): Int =
    ((1f - fraction.coerceIn(0f, 1f)) * itemSize - viewportSize.coerceAtLeast(0) / 2f)
        .toInt().coerceAtLeast(0)
