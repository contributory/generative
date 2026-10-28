package org.starfall.generative.shared.ui.layout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.starfall.generative.shared.theme.LocalAmoledMode

/**
 * Container fill for primary cards, tiles, and list items:
 * configurable translucent item fill in normal Light/Dark themes; no fill in AMOLED dark mode.
 */
@Composable
fun prominentContainerColor(): Color =
    if (LocalAmoledMode.current) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f).itemFill(LocalItemTransparency.current)
    }

/**
 * Container border for primary cards, tiles, and list items:
 * no routine border in normal Light/Dark themes, visible outline border in AMOLED dark mode.
 */
@Composable
fun prominentContainerBorder(width: Dp = 1.dp): BorderStroke? =
    if (LocalAmoledMode.current) {
        BorderStroke(width, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }

/**
 * Container fill for interactive or elevated sub-containers nested inside a [prominentContainerColor] card,
 * or standalone code/media blocks.
 */
@Composable
fun nestedContainerColor(): Color =
    if (LocalAmoledMode.current) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 1f).itemFill(LocalItemTransparency.current)
    }

/**
 * Container border for nested sub-containers and code/media blocks:
 * no routine border in normal Light/Dark themes, visible outline border in AMOLED dark mode.
 */
@Composable
fun nestedContainerBorder(width: Dp = 1.dp): BorderStroke? =
    if (LocalAmoledMode.current) {
        BorderStroke(width, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }

/**
 * Shape for an item in a vertically stacked group: [outerCorner] on the group's exposed top/bottom
 * edges, and [innerCorner] where adjacent items meet.
 */
fun groupedItemShape(
    index: Int,
    count: Int,
    outerCorner: Dp = 24.dp,
    innerCorner: Dp = 6.dp
): RoundedCornerShape = when {
    count <= 1 -> RoundedCornerShape(outerCorner)
    index == 0 -> RoundedCornerShape(
        topStart = outerCorner,
        topEnd = outerCorner,
        bottomStart = innerCorner,
        bottomEnd = innerCorner
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = innerCorner,
        topEnd = innerCorner,
        bottomStart = outerCorner,
        bottomEnd = outerCorner
    )
    else -> RoundedCornerShape(innerCorner)
}
