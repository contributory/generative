package org.starfall.multigateway.features.providers.list.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.starfall.multigateway.R
import org.starfall.multigateway.features.providers.list.logic.*
import org.starfall.multigateway.features.providers.list.model.*
import org.starfall.multigateway.features.providers.shared.logic.isEmpty
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.auth.defaultAuthorization
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.ProviderGroup
import org.starfall.multigateway.shared.model.provider.ProviderPlacement
import org.starfall.multigateway.shared.model.provider.ProviderRootOrderItem
import org.starfall.multigateway.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.multigateway.shared.ui.lists.FadeGridListContent
import org.starfall.multigateway.shared.ui.lists.moved

internal fun newProviderId(): String = "custom_${UUID.randomUUID()}"

@Composable
fun ProvidersScreen(
    providers: List<ModelProvider>,
    providerGroups: List<ProviderGroup> = emptyList(),
    collapsedSectionsState: Set<String> = emptySet(),
    onCollapsedSectionsChange: (Set<String>) -> Unit = {},
    isGridView: Boolean = false,
    onToggleGridView: ((Boolean) -> Unit)? = null,
    onOpenProviderEditor: (ModelProvider, Boolean) -> Unit,
    onSaveGroup: (ProviderGroup) -> Unit = {},
    onDeleteGroup: (String) -> Unit = {},
    onMoveProviderToGroup: (String, String?) -> Unit = { _, _ -> },
    onPlaceProvider: (suspend (ProviderPlacement) -> Result<Unit>)? = null,
    onPlaceProviderImmediately: ((ProviderPlacement) -> Unit)? = null,
    onDeleteProvider: (String) -> Unit,
    onReorderProviders: (List<String>) -> Unit,
    onReorderRootItems: (List<ProviderRootOrderItem>) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deletingProviderId by remember { mutableStateOf<String?>(null) }
    var orderedProviders by remember { mutableStateOf(providers) }
    var rootItems by remember { mutableStateOf(providerRootItems(providers, providerGroups)) }
    var pendingProviderOrder by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var pendingRootOrder by remember { mutableStateOf<List<ProviderRootOrderItem>?>(null) }
    var pendingGridLayout by remember { mutableStateOf<ProviderDragLayout?>(null) }
    var collapsedSections by remember { mutableStateOf(collapsedSectionsState) }
    var pendingCollapsedSections by remember { mutableStateOf<Set<String>?>(null) }
    var groupToRename by remember { mutableStateOf<ProviderGroup?>(null) }
    var deletingGroup by remember { mutableStateOf<ProviderGroup?>(null) }
    var movingProvider by remember { mutableStateOf<ModelProvider?>(null) }
    // Preserve the opened folder when navigation temporarily disposes this screen for provider editing.
    var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    // Keep the last opened folder composed until its reverse exit animation finishes.
    var renderedFolderGroup by remember { mutableStateOf<ProviderGroup?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFields by remember { mutableStateOf(setOf(ProviderSearchField.PROVIDER_NAME)) }
    var searchFilterOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val query = searchQuery.trim()
    fun matches(provider: ModelProvider) = matchesProviderSearch(provider, query, searchFields)
    // Search is a flat provider list, including members of matching folders.
    val matchingGroupIds = providerGroups.filter {
        matchesFolderSearch(it.name, query, searchFields)
    }.mapTo(hashSetOf()) { it.id }
    val visibleRootItems = if (searching) {
        orderedProviders.filter { matches(it) || it.groupId in matchingGroupIds }
            .map { ProviderRootItem.ProviderItem(it) }
    } else rootItems
    fun closeSearch() { searching = false; searchQuery = ""; searchFilterOpen = false }
    BackHandler(enabled = LocalScreenTransitionActive.current && searching) { closeSearch() }
    BackHandler(enabled = LocalScreenTransitionActive.current && openGroupId != null) { openGroupId = null }
    var draggedProviderId by remember { mutableStateOf<String?>(null) }
    var draggedGroupId by remember { mutableStateOf<String?>(null) }
    var dragRootSnapshot by remember { mutableStateOf<List<ProviderRootItem>?>(null) }
    var dragProviderSnapshot by remember { mutableStateOf<List<ModelProvider>?>(null) }
    var dragCollapsedSnapshot by remember { mutableStateOf<Set<String>?>(null) }
    var liveOrderChanged by remember { mutableStateOf(false) }
    var dragSession by remember { mutableStateOf<ProviderDragSession?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dropFolderId by remember { mutableStateOf<String?>(null) }
    var dragHoverKey by remember { mutableStateOf<String?>(null) }
    val surfaces = remember { ProviderDragSurfaces() }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val sortedGroups = remember(providerGroups) {
        providerGroups.sortedWith(compareBy<ProviderGroup> { it.sortOrder }.thenBy { it.name.lowercase() })
    }
    val validGroupIds = remember(providerGroups) { providerGroups.mapTo(hashSetOf()) { it.id } }
    val openGroup = sortedGroups.firstOrNull { it.id == openGroupId }
    LaunchedEffect(openGroup?.id) {
        if (openGroup != null) renderedFolderGroup = openGroup
    }
    val folderOverlayGroup = openGroup ?: renderedFolderGroup

    fun createProvider(groupId: String? = null) {
        onOpenProviderEditor(
            ModelProvider(
                id = newProviderId(),
                name = "",
                type = ModelApiType.OPENAI,
                baseUrl = "",
                auth = ModelApiType.OPENAI.defaultAuthorization(),
                groupId = groupId
            ),
            true
        )
    }

    fun setCollapsedSections(value: Set<String>) {
        collapsedSections = value
        pendingCollapsedSections = value
        onCollapsedSectionsChange(value)
    }

    fun beginDrag(key: String) {
        dragRootSnapshot = rootItems
        dragProviderSnapshot = orderedProviders
        dragCollapsedSnapshot = collapsedSections
        liveOrderChanged = false
        dragHoverKey = null
        if (key.startsWith("provider_")) draggedProviderId = key.removePrefix("provider_")
        else draggedGroupId = key.removePrefix("group_")
    }

    fun currentLayout() = ProviderDragLayout(orderedProviders, rootItems.map { it.toOrderItem() })

    fun applyLayout(next: ProviderDragLayout) {
        orderedProviders = next.providers
        val groups = rootItems.filterIsInstance<ProviderRootItem.GroupItem>().associateBy { it.id }
        val byId = next.providers.associateBy { it.id }
        rootItems = next.root.mapNotNull { item ->
            if (item.isGroup) groups[item.id] else byId[item.id]?.let { ProviderRootItem.ProviderItem(it) }
        }
        liveOrderChanged = true
        // The grids still describe the previous layout until they re-measure; hold further reorders back.
        surfaces.layoutGate = 2
    }

    fun applyProviderPreview(id: String, target: ProviderRootOrderItem?) {
        val layout = currentLayout()
        val next = moveProviderDrag(layout, id, target)
        if (next != layout) applyLayout(next)
    }

    fun moveRootItem(fromKey: String, toKey: String) {
        val from = rootItems.indexOfFirst { it.cellKey() == fromKey }
        val to = rootItems.indexOfFirst { it.cellKey() == toKey }
        if (from < 0 || to < 0 || from == to) return
        rootItems = rootItems.moved(from, to)
        liveOrderChanged = true
        surfaces.layoutGate = 2
    }

    fun commitProviderMove(id: String) {
        val layout = ProviderDragLayout(orderedProviders, rootItems.map { it.toOrderItem() })
        val original = dragProviderSnapshot?.firstOrNull { it.id == id }
        val moved = orderedProviders.firstOrNull { it.id == id }
        if (original != null && moved != null && liveOrderChanged) {
            val groupOrders = rootItems.filterIsInstance<ProviderRootItem.GroupItem>().associate { item ->
                item.id to orderedProviders.filter { it.groupId == item.id }.map { it.id }
            }
            pendingGridLayout = layout
            val placement = ProviderPlacement(id, moved.groupId, layout.root, groupOrders)
            val previousProviders = dragProviderSnapshot.orEmpty()
            val previousRoot = dragRootSnapshot.orEmpty()
            val previousCollapsed = dragCollapsedSnapshot.orEmpty()
            if (onPlaceProviderImmediately != null) {
                // The persistence owner must outlive this screen. Calling this synchronously
                // lets the ViewModel enqueue the write before navigation can dispose the screen.
                onPlaceProviderImmediately(placement)
            } else if (onPlaceProvider != null) scope.launch {
                if (onPlaceProvider(placement).isFailure) {
                    if (pendingGridLayout == layout) {
                        pendingGridLayout = null
                        orderedProviders = previousProviders
                        rootItems = previousRoot
                        setCollapsedSections(previousCollapsed)
                    }
                    Toast.makeText(context, context.getString(R.string.provider_drag_save_failed), Toast.LENGTH_SHORT).show()
                }
            } else {
                if (original.groupId != moved.groupId) onMoveProviderToGroup(id, moved.groupId)
                groupOrders.filter { (group, ids) -> previousProviders.filter { it.groupId == group }.map { it.id } != ids }
                    .values.forEach(onReorderProviders)
                if (previousRoot.map { it.toOrderItem() } != layout.root) onReorderRootItems(layout.root)
            }
        }
        draggedProviderId = null
        draggedGroupId = null
        dragRootSnapshot = null
        dragProviderSnapshot = null
        liveOrderChanged = false
    }

    fun finishDrag(key: String, cancelled: Boolean) {
        if (cancelled) {
            dragRootSnapshot?.let { rootItems = it }
            dragProviderSnapshot?.let { orderedProviders = it }
            dragCollapsedSnapshot?.let { collapsedSections = it }
        } else {
            if (key.startsWith("provider_")) {
                commitProviderMove(key.removePrefix("provider_"))
            }
            else if (liveOrderChanged) {
                val order = rootItems.map { it.toOrderItem() }
                pendingRootOrder = order
                onReorderRootItems(order)
            }
            if (collapsedSections != dragCollapsedSnapshot) setCollapsedSections(collapsedSections)
        }
        draggedProviderId = null
        draggedGroupId = null
        dragRootSnapshot = null
        dragProviderSnapshot = null
        dragCollapsedSnapshot = null
        liveOrderChanged = false
    }

    fun rootTiles(): List<DragTile> = surfaces.rootGrid?.dragTiles().orEmpty()

    fun folderTiles(): List<DragTile> {
        val grid = surfaces.folderGrid ?: return emptyList()
        val origin = surfaces.folderGridOrigin - surfaces.boxOrigin
        return grid.dragTiles().map { DragTile(it.key, it.rect.translate(origin)) }
    }

    fun panelRect(): Rect? = surfaces.panelBounds?.translate(-surfaces.boxOrigin)

    fun pickTile(point: Offset): DragTile? {
        if (openGroupId != null) {
            // While a folder is open only its members can be picked up; the scrim blocks the root grid.
            val panel = panelRect() ?: return null
            return if (panel.contains(point)) hitTile(folderTiles(), point) else null
        }
        return hitTile(rootTiles(), point)
    }

    fun updateDragTarget(finalDrop: Boolean = false) {
        val session = dragSession ?: return
        if (!finalDrop && surfaces.layoutGate > 0) return
        val point = dragPointer
        val folderId = session.fromFolderId
        if (folderId != null) {
            val panel = panelRect()
            val slop = with(density) { 24.dp.toPx() }
            if (panel != null && panel.inflate(slop).contains(point)) {
                // Reorder among the folder's members.
                dropFolderId = null
                val target = hitTile(folderTiles(), point)?.key
                if (target != null && target != session.key && surfaces.layoutGate == 0 && dragHoverKey != target) {
                    dragHoverKey = target
                    applyProviderPreview(
                        session.key.removePrefix("provider_"),
                        ProviderRootOrderItem(target.removePrefix("provider_"), false)
                    )
                } else if (target == null || target == session.key) {
                    dragHoverKey = null
                }
                return
            }
            // Dragged out of the container: close the folder and continue on the root grid.
            dragHoverKey = null
            val layout = currentLayout()
            val next = ejectProviderFromFolder(layout, session.key.removePrefix("provider_"), folderId)
            if (next != layout) applyLayout(next)
            dragSession = session.copy(fromFolderId = null)
            openGroupId = null
            return
        }
        val tiles = rootTiles()
        val tile = hitTile(tiles, point)
        if (tile == null || tile.key == session.key) {
            dropFolderId = null
            // Empty space is still a valid root drop position. Pick the closest visible cell so
            // a provider leaving a folder can be placed above, between, or after root items.
            if (tile == null && surfaces.layoutGate == 0) {
                val nearest = tiles
                    .filterNot { it.key == session.key }
                    .minByOrNull { candidate ->
                        val dx = point.x - candidate.rect.center.x
                        val dy = point.y - candidate.rect.center.y
                        dx * dx + dy * dy
                    }
                if (nearest != null && surfaces.layoutGate == 0) {
                    dragHoverKey = nearest.key
                    moveRootItem(session.key, nearest.key)
                } else dragHoverKey = null
            }
            return
        }
        if (session.key.startsWith("provider_") && tile.key.startsWith("group_") && isFolderDropZone(tile.rect, point)) {
            // Releasing here moves the provider into the folder. Nothing is rearranged while hovering.
            dropFolderId = tile.key.removePrefix("group_")
            dragHoverKey = tile.key
            return
        }
        dropFolderId = null
        if (surfaces.layoutGate == 0 && dragHoverKey != tile.key) {
            dragHoverKey = tile.key
            moveRootItem(session.key, tile.key)
        }
    }

    fun pickUp(point: Offset) {
        val tile = pickTile(point) ?: return
        beginDrag(tile.key)
        dragPointer = point
        dragSession = ProviderDragSession(
            key = tile.key,
            size = IntSize(tile.rect.width.roundToInt(), tile.rect.height.roundToInt()),
            grab = point - tile.rect.topLeft,
            fromFolderId = openGroupId,
            isGrid = isGridView
        )
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragMove(point: Offset) {
        dragPointer = point
        updateDragTarget()
    }

    fun dropDrag() {
        // Resolve the release position even if the last preview is waiting for layout.
        val wasInFolder = dragSession?.fromFolderId != null
        updateDragTarget(finalDrop = true)
        // Leaving a folder changes the drag surface; resolve the root target too.
        if (wasInFolder && dragSession?.fromFolderId == null) updateDragTarget(finalDrop = true)
        val session = dragSession ?: return
        val folder = dropFolderId
        if (session.key.startsWith("provider_")) {
            val id = session.key.removePrefix("provider_")
            val layout = currentLayout()
            val next = if (folder != null) moveProviderDrag(layout, id, ProviderRootOrderItem(folder, true)) else layout
            if (next != layout) applyLayout(next)
        }
        dragSession = null
        dropFolderId = null
        dragHoverKey = null
        finishDrag(session.key, cancelled = false)
    }

    fun cancelDrag() {
        val session = dragSession ?: return
        dragSession = null
        dropFolderId = null
        dragHoverKey = null
        finishDrag(session.key, cancelled = true)
    }

    LaunchedEffect(dragSession != null) {
        if (dragSession == null) return@LaunchedEffect
        val zone = with(density) { 80.dp.toPx() }
        val pxPerSecond = with(density) { 720.dp.toPx() }
        while (true) {
            val session = dragSession ?: break
            val inFolder = session.fromFolderId != null
            val grid = if (inFolder) surfaces.folderGrid else surfaces.rootGrid
            val top = if (inFolder) surfaces.folderGridOrigin.y - surfaces.boxOrigin.y else 0f
            val pointer = dragPointer
            val nearEdge = grid != null && autoScrollDelta(pointer.y - top, grid.viewportHeightPx, zone, 1f) != 0f
            if (surfaces.layoutGate == 0 && !nearEdge) {
                // Nothing time-based to do: sleep until the finger moves instead of ticking every frame.
                snapshotFlow { dragPointer to dragSession }.first { (p, s) -> p != pointer || s !== session }
                continue
            }
            if (surfaces.layoutGate > 0) surfaces.layoutGate--
            if (dragSession == null) break
            updateDragTarget()
            if (grid != null) {
                // A coroutine delay keeps the drag loop out of Compose's animation clock. This
                // preserves continuous edge scrolling without making the whole screen perpetually
                // non-idle while a finger is held down.
                val delta = autoScrollDelta(dragPointer.y - top, grid.viewportHeightPx, zone, pxPerSecond * 0.016f)
                if (delta != 0f) grid.scrollBy(delta)
            }
            delay(16)
        }
    }

    LaunchedEffect(providers, providerGroups, draggedProviderId, draggedGroupId) {
        if (draggedProviderId != null || draggedGroupId != null) return@LaunchedEffect

        val incomingProvidersById = providers.associateBy { it.id }
        pendingGridLayout?.let { preview ->
            val incomingRoot = providerRootItems(providers, providerGroups).map { it.toOrderItem() }
            val sameCatalog = preview.providers.map { it.id }.toSet() == providers.map { it.id }.toSet() &&
                preview.root.filter { it.isGroup }.map { it.id }.toSet() == providerGroups.map { it.id }.toSet()
            val acknowledged = incomingRoot == preview.root && preview.providers.all { p ->
                incomingProvidersById[p.id]?.groupId == p.groupId
            } && providerGroups.all { g ->
                providers.filter { it.groupId == g.id }.map { it.id } == preview.providers.filter { it.groupId == g.id }.map { it.id }
            }
            if (sameCatalog && !acknowledged) return@LaunchedEffect
            pendingGridLayout = null
        }
        val pendingProvider = pendingProviderOrder
        if (pendingProvider == null) {
            orderedProviders = providers
        } else {
            val (groupId, expectedOrder) = pendingProvider
            val actualOrder = providers.filter { it.groupId == groupId }.map { it.id }
            // A folder move/add/delete changes membership, so an old drag order
            // can no longer be acknowledged and must not keep the old positions.
            if (actualOrder == expectedOrder || actualOrder.toSet() != expectedOrder.toSet()) {
                pendingProviderOrder = null
                orderedProviders = providers
            } else {
                val currentIds = orderedProviders.mapTo(hashSetOf()) { it.id }
                orderedProviders =
                    orderedProviders.mapNotNull { incomingProvidersById[it.id] } +
                        providers.filter { it.id !in currentIds }
            }
        }

        val incomingRootItems = providerRootItems(providers, providerGroups)
        val pendingRoot = pendingRootOrder
        if (pendingRoot == null) {
            rootItems = incomingRootItems
        } else if (incomingRootItems.map { it.toOrderItem() } == pendingRoot ||
            incomingRootItems.map { it.toOrderItem() }.toSet() != pendingRoot.toSet()) {
            pendingRootOrder = null
            rootItems = incomingRootItems
        } else {
            val incomingByKey = incomingRootItems.associateBy { it.toOrderItem() }
            val currentKeys = rootItems.mapTo(hashSetOf()) { it.toOrderItem() }
            rootItems =
                rootItems.mapNotNull { incomingByKey[it.toOrderItem()] } +
                    incomingRootItems.filter { it.toOrderItem() !in currentKeys }
        }
    }

    LaunchedEffect(collapsedSectionsState) {
        val pending = pendingCollapsedSections
        when {
            pending == null -> collapsedSections = collapsedSectionsState
            collapsedSectionsState == pending -> pendingCollapsedSections = null
        }
    }
    LaunchedEffect(validGroupIds) {
        if (validGroupIds.isEmpty()) return@LaunchedEffect // groups not loaded yet; don't wipe the saved collapsed state
        val cleaned = collapsedSections.filterTo(linkedSetOf()) { it in validGroupIds }
        if (cleaned != collapsedSections) setCollapsedSections(cleaned)
    }

    Scaffold(
            topBar = {
                ProviderScreenTopBar(
                    visibleGroupId = openGroup?.id,
                    sortedGroups = sortedGroups,
                    orderedProviders = orderedProviders,
                    isGridView = isGridView,
                    searching = searching,
                    searchQuery = searchQuery,
                    searchFields = searchFields,
                    searchFilterOpen = searchFilterOpen,
                    searchFocus = searchFocus,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchFieldsChange = { searchFields = it },
                    onSearchFilterOpenChange = { searchFilterOpen = it },
                    onBack = onBack,
                    onToggleGridView = onToggleGridView,
                    onAddProvider = { createProvider(openGroupId) },
                    onAddProviderToGroup = { createProvider(it) },
                    onCreateGroup = { creatingGroup = true },
                    onOpenSearch = { openGroupId = null; searching = true },
                    onCloseSearch = ::closeSearch,
                    onCloseGroup = { openGroupId = null },
                    onEditGroup = { groupToRename = it },
                    onDeleteGroup = { deletingGroup = it; openGroupId = null }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .onGloballyPositioned { surfaces.boxOrigin = it.positionInRoot() }
                    .providerDragGesture(
                        enabled = !searching,
                        canPickUp = { pickTile(it) != null },
                        onPickUp = ::pickUp,
                        onMove = ::dragMove,
                        onDrop = ::dropDrag,
                        onCancel = ::cancelDrag
                    )
            ) {
                // Folder open: blur what sits behind the translucent folder panel (API 31+; no-op below).
                val backdropBlur = if (openGroup != null) 20.dp else 0.dp
                if (visibleRootItems.isEmpty()) {
                    ProviderEmptyState(hasSearchQuery = query.isNotEmpty(), backdropBlur = backdropBlur)
                } else {
                    FadeGridListContent(
                        isGrid = isGridView,
                        modifier = Modifier.fillMaxSize().blur(backdropBlur)
                    ) { gridMode ->
                        val gridState = rememberLazyGridState()
                        if (gridMode == isGridView) SideEffect { surfaces.rootGrid = gridState }
                        val cellHeight = if (gridMode) 164.dp else 100.dp
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(if (gridMode) 2 else 1),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxSize().testTag("provider_list")
                        ) {
                            items(visibleRootItems, key = { it.cellKey() }) { item ->
                                val key = item.cellKey()
                                Box(
                                    Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .height(cellHeight)
                                        .graphicsLayer { alpha = if (dragSession?.key == key) 0.25f else 1f }
                                        .padding(6.dp)
                                ) {
                                    ProviderRootCard(
                                        item = item,
                                        members = (item as? ProviderRootItem.GroupItem)
                                            ?.let { g -> orderedProviders.filter { it.groupId == g.id } }.orEmpty(),
                                        isGrid = gridMode,
                                        isDropTarget = item is ProviderRootItem.GroupItem && dropFolderId == item.id,
                                        onEditProvider = { onOpenProviderEditor(it, false) },
                                        onMoveProvider = { movingProvider = it },
                                        onDeleteProvider = { deletingProviderId = it },
                                        onOpenGroup = { openGroupId = it }
                                    )
                                }
                            }
                        }
                    }
                }

                folderOverlayGroup?.let { group ->
                    val members = orderedProviders.filter { it.groupId == group.id }
                    val folderMatches = query.isEmpty() || matchesFolderSearch(group.name, query, searchFields)
                    key(group.id) {
                        ProviderFolderOverlay(
                            group = group,
                            providers = if (folderMatches) members else members.filter(::matches),
                            isGrid = isGridView,
                            surfaces = surfaces,
                            draggingKey = dragSession?.key,
                            showExitHint = dragSession?.fromFolderId != null,
                            visible = openGroupId == group.id && openGroup != null,
                            onDismiss = { openGroupId = null },
                            onExitFinished = {
                                if ((openGroupId == null || openGroup == null) && renderedFolderGroup?.id == group.id) {
                                    renderedFolderGroup = null
                                }
                            },
                            onEditGroup = { groupToRename = group },
                            onDeleteGroup = { deletingGroup = group; openGroupId = null },
                            onEditProvider = { onOpenProviderEditor(it, false) },
                            onMoveProvider = { movingProvider = it },
                            onDeleteProvider = { deletingProviderId = it }
                        )
                    }
                }

                dragSession?.let { session ->
                    ProviderDragGhost(
                        session = session,
                        pointer = { dragPointer },
                        orderedProviders = orderedProviders,
                        rootItems = rootItems
                    )
                }
            }
        }

        if (creatingGroup) {
            ProviderGroupNameDialog(
                title = stringResource(R.string.new_provider_group),
                initialName = "",
                initialIcon = null,
                onDismiss = { creatingGroup = false },
                onSave = { name, icon ->
                    onSaveGroup(
                        ProviderGroup(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            sortOrder = (rootItems.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                            icon = icon
                        )
                    )
                    creatingGroup = false
                }
            )
        }

        groupToRename?.let { group ->
            ProviderGroupNameDialog(
                title = stringResource(R.string.edit_provider_group),
                initialName = group.name,
                initialIcon = group.icon,
                onDismiss = { groupToRename = null },
                onSave = { name, icon ->
                    onSaveGroup(group.copy(name = name, icon = icon))
                    groupToRename = null
                }
            )
        }

        deletingGroup?.let { group ->
            ProviderDeleteGroupDialog(
                onDismiss = { deletingGroup = null },
                onConfirm = {
                    onDeleteGroup(group.id)
                    setCollapsedSections(collapsedSections - group.id)
                    deletingGroup = null
                }
            )
        }

        movingProvider?.let { provider ->
            ProviderGroupPickerDialog(
                provider = provider,
                groups = sortedGroups,
                onDismiss = { movingProvider = null },
                onSelect = { groupId ->
                    onMoveProviderToGroup(provider.id, groupId)
                    movingProvider = null
                }
            )
        }

        if (deletingProviderId != null) {
            ProviderDeleteDialog(
                onDismiss = { deletingProviderId = null },
                onConfirm = {
                    deletingProviderId?.let { onDeleteProvider(it) }
                    deletingProviderId = null
                }
            )
        }
}
