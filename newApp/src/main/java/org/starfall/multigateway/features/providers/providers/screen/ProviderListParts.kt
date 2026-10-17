package org.starfall.multigateway.features.providers.providers.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons

/** Placeholder for an empty list, or a search without results. */
@Composable
internal fun ProviderEmptyState(hasSearchQuery: Boolean, backdropBlur: Dp) {
    Box(modifier = Modifier.fillMaxSize().blur(backdropBlur), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FluentIcon(
                imageVector = Icons.Outlined.Hub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(if (hasSearchQuery) R.string.no_provider_search_results else R.string.no_providers_added),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Floating copy of the tile under the finger while a drag is in progress. [pointer] is a lambda so
 * the position is read during layout only and pointer moves never recompose the ghost.
 */
@Composable
internal fun ProviderDragGhost(
    session: ProviderDragSession,
    pointer: () -> Offset,
    orderedProviders: List<ModelProvider>,
    rootItems: List<ProviderRootItem>
) {
    val density = LocalDensity.current
    val provider = if (session.key.startsWith("provider_"))
        orderedProviders.firstOrNull { it.id == session.key.removePrefix("provider_") } else null
    val groupItem = if (session.key.startsWith("group_"))
        rootItems.firstOrNull { it.cellKey() == session.key } as? ProviderRootItem.GroupItem else null
    Box(
        Modifier
            .offset {
                val point = pointer()
                IntOffset(
                    (point.x - session.grab.x).roundToInt(),
                    (point.y - session.grab.y).roundToInt()
                )
            }
            .size(with(density) { session.size.width.toDp() }, with(density) { session.size.height.toDp() })
            .zIndex(10f)
            .graphicsLayer {
                // Keep the drag ghost the same size and shape as the source
                // tile; only its z-order distinguishes it while dragging.
                alpha = 1f
            }
            .then(if (provider != null) Modifier.testTag("dragged_provider") else Modifier)
    ) {
        when {
            provider != null && session.folderTile ->
                ProviderFolderProviderTile(
                    provider = provider,
                    isGrid = session.isGrid,
                    onEdit = {},
                    onMoveToGroup = {},
                    onDelete = {},
                    testTag = false
                )
            provider != null || groupItem != null -> Box(Modifier.fillMaxSize().padding(6.dp)) {
                ProviderRootCard(
                    item = groupItem ?: ProviderRootItem.ProviderItem(provider!!),
                    members = groupItem?.let { g -> orderedProviders.filter { it.groupId == g.id } }.orEmpty(),
                    isGrid = session.isGrid,
                    isDropTarget = false,
                    testTag = false,
                    onEditProvider = {}, onMoveProvider = {}, onDeleteProvider = {}, onOpenGroup = {}
                )
            }
        }
    }
}
