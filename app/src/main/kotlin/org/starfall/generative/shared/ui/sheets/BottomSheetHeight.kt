package org.starfall.generative.shared.ui.sheets

import kotlin.math.roundToInt

internal data class BottomSheetHeightBounds(val minimum: Int, val maximum: Int) {
    fun heightFor(contentHeight: Int, requestedHeight: Float?): Int =
        (requestedHeight ?: contentHeight.toFloat()).coerceIn(minimum.toFloat(), maximum.toFloat()).roundToInt()

    /** Apply the complete drag distance to the height captured when the gesture began. */
    fun resizedHeight(startHeight: Float, totalDragDistance: Float): Float =
        (startHeight - totalDragDistance).coerceIn(minimum.toFloat(), maximum.toFloat())

    /** The upward scroll consumed to grow the sheet; the remainder belongs to the list. */
    fun consumeUpwardScroll(currentHeight: Float, availableY: Float): Float {
        if (availableY >= 0f) return 0f
        val height = currentHeight.coerceIn(minimum.toFloat(), maximum.toFloat())
        val remaining = maximum - height
        if (remaining <= 0f) return 0f
        return -minOf(-availableY, remaining)
    }
}

internal fun bottomSheetHeightBounds(
    windowHeight: Int,
    availableHeight: Int,
    topInset: Int = 0,
    windowBoundsHeight: Int = windowHeight
): BottomSheetHeightBounds {
    // The sheet content receives no top padding, so its available height still includes
    // the status-bar area, even if the window has been shortened by the keyboard.
    val windowLimit = minOf(availableHeight, windowBoundsHeight).coerceAtLeast(1)
    val maximum = (windowLimit - topInset.coerceAtLeast(0)).coerceAtLeast(1)
    return BottomSheetHeightBounds((windowHeight / 3).coerceIn(1, maximum), maximum)
}
