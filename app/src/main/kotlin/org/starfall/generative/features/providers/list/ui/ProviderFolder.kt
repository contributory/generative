package org.starfall.generative.features.providers.list.ui

import org.starfall.generative.features.providers.list.logic.*
import org.starfall.generative.features.providers.list.model.*

import org.starfall.generative.features.providers.list.model.ProviderDragSurfaces

import org.starfall.generative.features.providers.list.model.ProviderDragSession

import org.starfall.generative.features.providers.list.logic.ProviderRootItem

import org.starfall.generative.features.providers.shared.logic.isEmpty

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.first
import org.starfall.generative.R
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.theme.LocalAmoledMode
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.icons.providerInitials

/**
 * A folder shows its own logo when it has one (picked, or recognised from the folder name).
 * Only a folder without a logo falls back to a 2x2 preview of its first four providers.
 */
@Composable
internal fun ProviderFolderPreview(
    group: ProviderGroup,
    providers: List<ModelProvider>,
    modifier: Modifier = Modifier
) {
    val previewProviders = providers.take(4)
    EntityIcon(
        image = group.icon,
        modifier = modifier,
        fallback = Icons.Outlined.Folder,
        matchName = group.name,
        fallbackContent = if (previewProviders.isEmpty()) null else ({ ProviderFolderMiniGrid(group.id, previewProviders) })
    )
}

@Composable
private fun ProviderFolderMiniGrid(groupId: String, providers: List<ModelProvider>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(4.dp).testTag("provider_group_preview_$groupId"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        providers.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEach { provider ->
                    EntityIcon(
                        image = provider.icon,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        text = providerInitials(provider.name),
                        matchName = provider.name
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The open folder: an in-window overlay (not a Dialog) so the screen-level drag gesture keeps
 * receiving the finger when a member is dragged out of it.
 */
@Composable
internal fun ProviderFolderOverlay(
    group: ProviderGroup,
    providers: List<ModelProvider>,
    isGrid: Boolean,
    surfaces: ProviderDragSurfaces,
    draggingKey: String?,
    showExitHint: Boolean,
    visible: Boolean,
    animateEnter: Boolean = true,
    onDismiss: () -> Unit,
    onExitFinished: () -> Unit,
    onEditGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onEditProvider: (ModelProvider) -> Unit,
    onMoveProvider: (ModelProvider) -> Unit,
    onDeleteProvider: (String) -> Unit
) {
    val amoled = LocalAmoledMode.current
    val gridState = rememberLazyGridState()
    SideEffect { surfaces.folderGrid = gridState }
    DisposableEffect(Unit) {
        onDispose {
            surfaces.folderGrid = null
            surfaces.panelBounds = null
        }
    }
    val visibility = remember { MutableTransitionState(!animateEnter && visible) }
    LaunchedEffect(visible) { visibility.targetState = visible }
    LaunchedEffect(visibility.isIdle, visibility.currentState, visible) {
        if (!visible && visibility.isIdle && !visibility.currentState) onExitFinished()
    }
    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn() + scaleIn(initialScale = 0.5f),
        exit = fadeOut() + scaleOut(targetScale = 0.5f)
    ) {
        Box(Modifier.fillMaxSize().zIndex(5f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .widthIn(max = 520.dp)
                    .onGloballyPositioned { surfaces.panelBounds = it.boundsInRoot() }
                    .testTag("provider_folder_dialog_${group.id}"),
                shape = RoundedCornerShape(28.dp),
                // The panel background is drawn by the feathered layer below (40% transparent, soft edges,
                // no outline even in AMOLED); the Surface itself only provides bounds, clipping and content color.
                color = Color.Transparent,
                border = null
            ) {
                val panelColor = (if (amoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh).copy(alpha = 0.6f)
                // Tapping empty space inside the panel closes the folder; the cards consume their own taps.
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                ) {
                Box(Modifier.matchParentSize().featheredPanel(panelColor, 28.dp, 24.dp))
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (providers.isEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            FluentIcon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.empty_provider_group),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(if (isGrid) 2 else 1),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .onGloballyPositioned { surfaces.folderGridOrigin = it.positionInRoot() }
                                .testTag("provider_folder_grid_${group.id}"),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(providers, key = { "provider_${it.id}" }) { provider ->
                                ProviderFolderProviderTile(
                                    provider = provider,
                                    isGrid = isGrid,
                                    onEdit = { onEditProvider(provider) },
                                    onMoveToGroup = { onMoveProvider(provider) },
                                    onDelete = { onDeleteProvider(provider.id) },
                                    testTag = true,
                                    modifier = Modifier
                                        .animateItem()
                                        .graphicsLayer {
                                            alpha = if (draggingKey == "provider_${provider.id}") 0.25f else 1f
                                        }
                                )
                            }
                        }
                    }

                }
                }
            }
            AnimatedVisibility(
                visible = showExitHint,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut()
            ) { ProviderFolderExitHint() }
        }
    }
}

/**
 * Hint shown just above the nav bar while a folder member is dragged. It is a plain translucent label
 * (no Surface, no click handling), so it never intercepts touches meant for what sits behind it.
 */
@Composable
private fun ProviderFolderExitHint() {
    Text(
        text = stringResource(R.string.drag_to_exit_folder_hint),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("provider_folder_exit_hint")
    )
}

/** Rounded panel whose edges fade out to transparent instead of ending in a hard boundary. */
private fun Modifier.featheredPanel(color: Color, corner: androidx.compose.ui.unit.Dp, fade: androidx.compose.ui.unit.Dp): Modifier =
    this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx()))
            val fx = (fade.toPx() / size.width).coerceIn(0f, 0.5f)
            val fy = (fade.toPx() / size.height).coerceIn(0f, 0.5f)
            drawRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to Color.Transparent, fx to Color.Black, 1f - fx to Color.Black, 1f to Color.Transparent
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
            )
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.Transparent, fy to Color.Black, 1f - fy to Color.Black, 1f to Color.Transparent
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
            )
        }
