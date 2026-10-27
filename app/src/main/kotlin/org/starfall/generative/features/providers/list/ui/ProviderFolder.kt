package org.starfall.generative.features.providers.list.ui

import org.starfall.generative.features.providers.list.model.ProviderDragSurfaces

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.icons.providerInitials
import org.starfall.generative.shared.ui.lists.ListViewToggle
import org.starfall.generative.shared.ui.layout.prominentContainerColor

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

private const val FolderPageOpacity = 0.6f

/** Bounds of the opening/closing page relative to the shared, full-screen drag host. */
internal fun folderExpansionRect(source: Rect?, width: Float, height: Float, progress: Float): Rect {
    val full = Rect(0f, 0f, width, height)
    val from = source ?: full
    val t = progress.coerceIn(0f, 1f)
    fun interpolate(a: Float, b: Float) = a + (b - a) * t
    return Rect(
        interpolate(from.left, full.left),
        interpolate(from.top, full.top),
        interpolate(from.right, full.right),
        interpolate(from.bottom, full.bottom)
    )
}

/** Full-screen folder page. Its header belongs to the folder, never to ProvidersScreen's app bar. */
@Composable
internal fun ProviderFolderOverlay(
    group: ProviderGroup,
    providers: List<ModelProvider>,
    isGrid: Boolean,
    originBounds: Rect?,
    hostOrigin: Offset,
    animateExitImmediately: Boolean,
    surfaces: ProviderDragSurfaces,
    draggingKey: String?,
    showExitHint: Boolean,
    visible: Boolean,
    animateEnter: Boolean = true,
    onExitFinished: () -> Unit,
    onDismiss: () -> Unit,
    onAddProvider: () -> Unit,
    onToggleGridView: () -> Unit,
    onEditGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onEditProvider: (ModelProvider) -> Unit,
    onMoveProvider: (ModelProvider) -> Unit,
    onDeleteProvider: (String) -> Unit
) {
    val gridState = rememberLazyGridState()
    SideEffect { surfaces.folderGrid = gridState }
    DisposableEffect(Unit) {
        onDispose {
            surfaces.folderGrid = null
            surfaces.panelBounds = null
            surfaces.folderGridOrigin = androidx.compose.ui.geometry.Offset.Zero
        }
    }
    // Animating a single progress value keeps position, size, corner radius, and content in sync.
    // Starting with the actual card rect also works when the root list has been scrolled.
    val progress = remember { Animatable(if (animateEnter) 0f else 1f) }
    LaunchedEffect(visible, animateExitImmediately) {
        if (visible) {
            if (animateEnter) progress.animateTo(
                1f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
            ) else progress.snapTo(1f)
        } else {
            // When dragging a member out, uncover the root grid immediately for drop targeting.
            if (animateExitImmediately) progress.snapTo(0f)
            else progress.animateTo(
                0f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
            )
            onExitFinished()
        }
    }
    val fraction = progress.value.coerceIn(0f, 1f)
    val contentAlpha = ((fraction - 0.72f) / 0.22f).coerceIn(0f, 1f)
    val start = originBounds?.translate(-hostOrigin)
    val startColor = prominentContainerColor()
    // The opened folder page is 60% opaque so the root list stays faintly visible behind it.
    val endColor = MaterialTheme.colorScheme.background.copy(alpha = FolderPageOpacity)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val fullHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val currentBounds = folderExpansionRect(start, fullWidthPx, fullHeightPx, fraction)
        val cornerRadius = 22.dp * (1f - fraction)
        val shape = RoundedCornerShape(cornerRadius)
        // This entire layer intercepts touches while the folder is growing or shrinking.
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
        )
        Surface(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = currentBounds.left
                    translationY = currentBounds.top
                    this.scaleX = currentBounds.width / fullWidthPx
                    this.scaleY = currentBounds.height / fullHeightPx
                    alpha = if (start == null) fraction else (fraction * 6f).coerceIn(0f, 1f)
                    this.shape = shape
                    clip = true
                }
                .testTag("provider_folder_screen_${group.id}"),
            shape = shape,
            color = lerp(startColor, endColor, fraction)
        ) {
            Box(
                Modifier.fillMaxSize()
                    // Taps on any empty space close the folder. Tiles, header buttons and scrolling
                    // consume their own gestures first, so only unclaimed taps reach this handler.
                    // pointerInput (not clickable) keeps the children's semantics unmerged.
                    .pointerInput(Unit) { detectTapGestures { currentOnDismiss() } }
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    // Header actions remain below the status bar; the grid below may
                    // scroll into the transparent navigation-bar region.
                    Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        ProviderFolderHeader(
                            group = group,
                            providers = providers,
                            isGrid = isGrid,
                            onAddProvider = onAddProvider,
                            onToggleGridView = onToggleGridView,
                            onEditGroup = onEditGroup,
                            onDeleteGroup = onDeleteGroup
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (providers.isEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .onGloballyPositioned { surfaces.panelBounds = it.boundsInRoot() },
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
                            modifier = Modifier.weight(1f).fillMaxWidth()
                                .onGloballyPositioned {
                                    surfaces.folderGridOrigin = it.positionInRoot()
                                    // Dragging above the list, into the header, ejects a provider.
                                    surfaces.panelBounds = it.boundsInRoot()
                                }
                                .testTag("provider_folder_grid_${group.id}"),
                            contentPadding = PaddingValues(
                                start = 16.dp, top = 8.dp, end = 16.dp,
                                bottom = 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            ),
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
                                    modifier = Modifier.animateItem().graphicsLayer {
                                        alpha = if (draggingKey == "provider_${provider.id}") 0.25f else 1f
                                    }
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showExitHint,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) { ProviderFolderExitHint() }
            }
        }
    }
}

@Composable
private fun ProviderFolderHeader(
    group: ProviderGroup,
    providers: List<ModelProvider>,
    isGrid: Boolean,
    onAddProvider: () -> Unit,
    onToggleGridView: () -> Unit,
    onEditGroup: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            .testTag("provider_folder_header_${group.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProviderFolderPreview(
            group = group,
            providers = providers,
            modifier = Modifier.size(80.dp).testTag("provider_folder_header_icon_${group.id}")
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("provider_folder_header_name_${group.id}")
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("provider_folder_toolbar_${group.id}")
            ) {
                IconButton(onClick = onAddProvider, modifier = Modifier.testTag("add_folder_provider")) {
                    FluentIcon(Icons.Default.Add, stringResource(R.string.common_add))
                }
                ListViewToggle(isGrid, onToggleGridView)
                ProviderGroupContextMenu(onEdit = onEditGroup, onDelete = onDeleteGroup, compact = false)
            }
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
