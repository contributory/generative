package org.starfall.generative.features.settings.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.starfall.generative.extensions.launcher.Launcher

/** Hit boxes follow native logical slots; animateItem only moves their rendered contents. */
@Composable
internal fun LauncherDragTestCollection(
    items: List<String>,
    state: Launcher.State<String>,
    options: LauncherDragTestOptions,
    modifier: Modifier,
    padding: Dp,
    enabled: Boolean,
    item: @Composable (String, Modifier, Rect?) -> Unit
) = with(Launcher) {
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val inset = with(LocalDensity.current) { padding.roundToPx().toFloat() }
    val placement = tween<androidx.compose.ui.unit.IntOffset>(Launcher.defaults.PlacementDurationMillis)
    fun slot(x: Float, y: Float, width: Float, height: Float): Rect? {
        if (viewport.width <= 0f) return null
        // Native lazy item offsets already use physical RTL coordinates.
        val left = viewport.left + inset + x
        return Rect(Offset(left, viewport.top + inset + y), Size(width, height)).intersect(viewport)
    }
    if (options.list) {
        val scroll = rememberLazyListState()
        PreserveViewportOnReorder(items) {
            scroll.requestScrollToItem(scroll.firstVisibleItemIndex, scroll.firstVisibleItemScrollOffset)
        }
        LazyColumn(
            state = scroll,
            modifier = modifier.onGloballyPositioned { viewport = it.boundsInWindow() }
                .then(if (enabled) Modifier.autoScroll(state, scroll) else Modifier),
            contentPadding = PaddingValues(padding), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it }) { id ->
                val bounds by remember(scroll, id, inset) { derivedStateOf {
                    scroll.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.let {
                        slot(0f, it.offset.toFloat(), (viewport.width - 2 * inset).coerceAtLeast(0f), it.size.toFloat())
                    }
                } }
                item(id, Modifier.animateItem(placementSpec = placement), bounds)
            }
        }
    } else {
        val scroll = rememberLazyGridState()
        PreserveViewportOnReorder(items) {
            scroll.requestScrollToItem(scroll.firstVisibleItemIndex, scroll.firstVisibleItemScrollOffset)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(options.columns), state = scroll,
            modifier = modifier.onGloballyPositioned { viewport = it.boundsInWindow() }
                .then(if (enabled) Modifier.autoScroll(state, scroll) else Modifier),
            contentPadding = PaddingValues(padding),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it }) { id ->
                val bounds by remember(scroll, id, inset) { derivedStateOf {
                    scroll.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.let {
                        slot(it.offset.x.toFloat(), it.offset.y.toFloat(), it.size.width.toFloat(), it.size.height.toFloat())
                    }
                } }
                item(id, Modifier.animateItem(placementSpec = placement), bounds)
            }
        }
    }
}

/** Do not let a moved first-visible key drag the entire viewport to its new index. */
@Composable
private fun PreserveViewportOnReorder(items: List<String>, preserve: () -> Unit) {
    val previous = remember { arrayOf(items) }
    if (previous[0] != items) SideEffect {
        preserve()
        previous[0] = items
    }
}
