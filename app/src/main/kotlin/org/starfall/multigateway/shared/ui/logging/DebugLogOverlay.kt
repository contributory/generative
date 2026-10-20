package org.starfall.multigateway.shared.ui.logging

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import kotlin.math.roundToInt

private val ButtonSize = 52.dp

/**
 * Draggable floating button drawn above the app. Tapping it shows or hides the live [DebugLogPanel].
 * Everything outside the button and the panel stays touchable, so the app can be used while logs stream.
 */
@Composable
fun DebugLogOverlay(modifier: Modifier = Modifier) {
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    // NaN means "not dragged yet": the button then sits at its default spot on the right edge.
    var offsetX by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var offsetY by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    val buttonPx = with(LocalDensity.current) { ButtonSize.toPx() }

    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val maxX = (constraints.maxWidth - buttonPx).coerceAtLeast(0f)
        val maxY = (constraints.maxHeight - buttonPx).coerceAtLeast(0f)
        val currentMaxX by rememberUpdatedState(maxX)
        val currentMaxY by rememberUpdatedState(maxY)
        val x = (if (offsetX.isNaN()) maxX else offsetX).coerceIn(0f, maxX)
        val y = (if (offsetY.isNaN()) maxY * 0.25f else offsetY).coerceIn(0f, maxY)

        if (panelOpen) {
            DebugLogPanel(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.6f).padding(8.dp)
            )
        }
        Surface(
            onClick = { panelOpen = !panelOpen },
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(ButtonSize)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val baseX = if (offsetX.isNaN()) currentMaxX else offsetX
                        val baseY = if (offsetY.isNaN()) currentMaxY * 0.25f else offsetY
                        offsetX = (baseX + drag.x).coerceIn(0f, currentMaxX)
                        offsetY = (baseY + drag.y).coerceIn(0f, currentMaxY)
                    }
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                FluentIcon(Icons.Outlined.Code, stringResource(R.string.debug_log_toggle), Modifier.size(24.dp))
            }
        }
    }
}
