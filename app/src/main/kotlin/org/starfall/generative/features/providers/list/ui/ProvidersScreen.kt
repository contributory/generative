package org.starfall.generative.features.providers.list.ui

import org.starfall.generative.shared.ui.layout.scrollableScaffoldPadding
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import java.util.UUID
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import com.mohamedrejeb.compose.dnd.annotation.ExperimentalDndApi
import com.mohamedrejeb.compose.dnd.reorder.reorderableItem
import com.mohamedrejeb.compose.dnd.drop.dropTarget
import com.mohamedrejeb.compose.dnd.scroll.dragAutoScroll
import com.mohamedrejeb.compose.dnd.drag.DropStrategy
import kotlinx.coroutines.launch
import org.starfall.generative.R
import org.starfall.generative.features.providers.list.logic.*
import org.starfall.generative.features.providers.shared.logic.isEmpty
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.auth.defaultAuthorization
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.model.provider.ProviderPlacement
import org.starfall.generative.shared.model.provider.ProviderRootOrderItem
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.lists.FadeGridListContent
import org.starfall.generative.shared.ui.lists.moved

internal fun newProviderId(): String = "custom_${UUID.randomUUID()}"

@OptIn(ExperimentalDndApi::class)
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
    val dialogs = rememberProviderListDialogs()
    var orderedProviders by remember { mutableStateOf(providers) }
    var rootItems by remember { mutableStateOf(providerRootItems(providers, providerGroups)) }
    var pendingProviderOrder by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var pendingRootOrder by remember { mutableStateOf<List<ProviderRootOrderItem>?>(null) }
    var pendingGridLayout by remember { mutableStateOf<ProviderDragLayout?>(null) }
    var collapsedSections by remember { mutableStateOf(collapsedSectionsState) }
    var pendingCollapsedSections by remember { mutableStateOf<Set<String>?>(null) }
    // Preserve the opened folder when navigation temporarily disposes this screen for provider editing.
    var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var skipNextFolderEnterAnimation by rememberSaveable { mutableStateOf(false) }
    // Keep the last opened folder composed until its reverse exit animation finishes.
    var renderedFolderGroup by remember { mutableStateOf<ProviderGroup?>(null) }
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
    var draggedProviderId by remember { mutableStateOf<String?>(null) }
    var draggedGroupId by remember { mutableStateOf<String?>(null) }
    var dragRootSnapshot by remember { mutableStateOf<List<ProviderRootItem>?>(null) }
    var dragProviderSnapshot by remember { mutableStateOf<List<ModelProvider>?>(null) }
    var dragCollapsedSnapshot by remember { mutableStateOf<Set<String>?>(null) }
    var liveOrderChanged by remember { mutableStateOf(false) }
    // A single DND state owns the gesture and shadow across root/folder composition changes.
    val dndState = rememberDragAndDropState<ProviderRootOrderItem>(dragAfterLongPress = true)
    var acceptedDrop by remember { mutableStateOf(false) }
    var cancelledDrag by remember { mutableStateOf(false) }
    // Track the actual visible folder card, including root grid scrolling and grid/list changes.
    val folderCardBounds = remember { mutableStateMapOf<String, Rect>() }
    var openedCardBounds by remember { mutableStateOf<Rect?>(null) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }

    val sortedGroups = remember(providerGroups) {
        providerGroups.sortedWith(compareBy<ProviderGroup> { it.sortOrder }.thenBy { it.name.lowercase() })
    }
    val validGroupIds = remember(providerGroups) { providerGroups.mapTo(hashSetOf()) { it.id } }
    val openGroup = sortedGroups.firstOrNull { it.id == openGroupId }
    LaunchedEffect(openGroup?.id) {
        if (openGroup != null) renderedFolderGroup = openGroup
    }
    // Barely-there blur: items behind the translucent folder stay recognisable and their text
    // stays faintly legible (API 31+; a no-op below).
    val backdropBlur by animateDpAsState(
        targetValue = if (openGroup != null) 1.5.dp else 0.dp,
        animationSpec = tween(
            durationMillis = if (openGroup != null) FolderOpenDurationMillis else FolderCloseDurationMillis
        ),
        label = "providerFolderBackdropBlur"
    )
    val folderOverlayGroup = openGroup ?: renderedFolderGroup
    BackHandler(enabled = LocalScreenTransitionActive.current && openGroup != null) {
        if (openGroupId != null) openGroupId = null
    }
    // The skip flag is one-shot: the overlay reads it when first composed, then it is cleared so later opens animate.
    LaunchedEffect(folderOverlayGroup?.id) {
        if (folderOverlayGroup != null) skipNextFolderEnterAnimation = false
    }

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
        acceptedDrop = false
        cancelledDrag = false
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

    fun exitOpenFolder(providerId: String) {
        val folderId = openGroupId ?: return
        val layout = currentLayout()
        val next = ejectProviderFromFolder(layout, providerId, folderId)
        if (next != layout) applyLayout(next)
        // Only the page closes; DragAndDropContainer and its shadow remain mounted.
        openGroupId = null
        // The hover is only a preview. An actual drop target must confirm the release.
    }

    fun finishDnd(key: String) {
        finishDrag(key, cancelled = cancelledDrag || !acceptedDrop)
        acceptedDrop = false
        cancelledDrag = false
    }

    // Allow Back to cancel a drag without persisting its preview.
    BackHandler(enabled = LocalScreenTransitionActive.current && dndState.draggedItem != null) {
        cancelledDrag = true
        dndState.cancelDrag()
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

    DragAndDropContainer(
        state = dndState,
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { containerOrigin = it.positionInRoot() },
        enabled = !searching
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            // Root controls are covered by the folder page, and must also be hidden from accessibility.
            modifier = (if (openGroup != null) Modifier.clearAndSetSemantics {} else Modifier)
                .then(if (backdropBlur > 0.dp) Modifier.blur(backdropBlur) else Modifier),
            topBar = {
                // This target remains composed while the full-screen folder is visible.
                // Crossing it closes the folder without ending the active DND session.
                Box(Modifier.dropTarget(
                    // The header is an exit/drop fallback, never higher priority than a root tile.
                    key = "root-header", state = dndState, canDrop = true, zIndex = -0.5f,
                    dropAnimationEnabled = false,
                    onDragEnter = { dragged ->
                        if (openGroupId != null && !dragged.data.isGroup) exitOpenFolder(dragged.data.id)
                    },
                    onDrop = { acceptedDrop = true }
                )) {
                ProviderScreenTopBar(
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
                    onAddProvider = { createProvider() },
                    onCreateGroup = { dialogs.creatingGroup = true },
                    onOpenSearch = { openGroupId = null; searching = true },
                    onCloseSearch = ::closeSearch
                )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (visibleRootItems.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding)) { ProviderEmptyState(hasSearchQuery = query.isNotEmpty(), backdropBlur = 0.dp) }
                } else {
                    FadeGridListContent(
                        isGrid = isGridView,
                        modifier = Modifier.fillMaxSize()
                    ) { gridMode ->
                        val gridState = rememberLazyGridState()
                        val cellHeight = if (gridMode) 164.dp else 100.dp
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(if (gridMode) 2 else 1),
                            contentPadding = scrollableScaffoldPadding(padding, horizontal = 16.dp, top = 8.dp, bottom = 8.dp),
                            modifier = Modifier.fillMaxSize()
                                .dropTarget(
                                    key = "root-end", state = dndState, zIndex = -1f,
                                    canDrop = openGroupId == null,
                                    onDragEnter = { dragged ->
                                        val key = if (dragged.data.isGroup) "group_${dragged.data.id}"
                                            else "provider_${dragged.data.id}"
                                        rootItems.lastOrNull()?.cellKey()?.let { moveRootItem(key, it) }
                                    },
                                    onDrop = { acceptedDrop = true },
                                    dropAnimationEnabled = false
                                )
                                .dragAutoScroll(state = dndState, lazyGridState = gridState)
                                .testTag("provider_list")
                        ) {
                            items(visibleRootItems, key = { it.cellKey() }) { item ->
                                val key = item.cellKey()
                                Box(
                                    Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .height(cellHeight)
                                        .graphicsLayer { alpha = if (dndState.isDragging(key)) 0f else 1f }
                                        .padding(6.dp)
                                        // Folder membership is a drop target, not a reorderable slot.
                                        .then(if (item is ProviderRootItem.GroupItem) Modifier.dropTarget(
                                            key = "folder-drop_${item.id}", state = dndState,
                                            zIndex = 1f,
                                            canDrop = openGroupId == null && dndState.draggedItem?.data?.isGroup == false,
                                            dropAnimationEnabled = false,
                                            onDrop = { dragged ->
                                                val layout = currentLayout()
                                                val next = moveProviderDrag(layout, dragged.data.id, item.toOrderItem())
                                                if (next != layout) applyLayout(next)
                                                acceptedDrop = true
                                            }
                                        ) else Modifier)
                                        .reorderableItem(
                                            key = key,
                                            data = item.toOrderItem(),
                                            state = dndState,
                                            enabled = !searching && openGroupId == null,
                                            canDrop = openGroupId == null,
                                            dropStrategy = DropStrategy.Surface,
                                            onDragStart = { beginDrag(key) },
                                            onDragEnd = { finishDnd(key) },
                                            onDragEnter = { dragged ->
                                                val from = if (dragged.data.isGroup) "group_${dragged.data.id}"
                                                    else "provider_${dragged.data.id}"
                                                if (from != key && rootItems.any { it.cellKey() == from } &&
                                                    (item is ProviderRootItem.ProviderItem || dragged.data.isGroup)) {
                                                    moveRootItem(from, key)
                                                }
                                            },
                                            onDrop = { dragged ->
                                                if (item is ProviderRootItem.GroupItem && !dragged.data.isGroup) {
                                                    val layout = currentLayout()
                                                    val next = moveProviderDrag(layout, dragged.data.id, item.toOrderItem())
                                                    if (next != layout) applyLayout(next)
                                                }
                                                acceptedDrop = true
                                            },
                                            dropAnimationEnabled = false,
                                            draggableContent = {
                                                Box(Modifier.fillMaxWidth().height(cellHeight - 12.dp)
                                                    .testTag(if (item is ProviderRootItem.ProviderItem) "dragged_provider" else "dragged_group")) {
                                                ProviderRootCard(
                                                    item = item, members = (item as? ProviderRootItem.GroupItem)
                                                        ?.let { g -> orderedProviders.filter { it.groupId == g.id } }.orEmpty(),
                                                    isGrid = gridMode, isDropTarget = false, testTag = false,
                                                    onEditProvider = {}, onMoveProvider = {},
                                                    onDeleteProvider = {}, onOpenGroup = {}
                                                )
                                                }
                                            }
                                        )
                                        .onGloballyPositioned { coordinates ->
                                            if (item is ProviderRootItem.GroupItem && gridMode == isGridView) {
                                                folderCardBounds[item.id] = coordinates.boundsInRoot()
                                            }
                                        }
                                ) {
                                    ProviderRootCard(
                                        item = item,
                                        members = (item as? ProviderRootItem.GroupItem)
                                            ?.let { g -> orderedProviders.filter { it.groupId == g.id } }.orEmpty(),
                                        isGrid = gridMode,
                                        isDropTarget = item is ProviderRootItem.GroupItem &&
                                            (dndState.isDropTargetHovered(key) ||
                                                dndState.isDropTargetHovered("folder-drop_${item.id}")) &&
                                            dndState.draggedItem?.data?.isGroup == false,
                                        onEditProvider = { onOpenProviderEditor(it, false) },
                                        onMoveProvider = { dialogs.movingProvider = it },
                                        onDeleteProvider = { dialogs.deletingProviderId = it },
                                        onOpenGroup = { id ->
                                            openedCardBounds = folderCardBounds[id]
                                            renderedFolderGroup = sortedGroups.firstOrNull { it.id == id }
                                            openGroupId = id
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Folder draws over the entire Scaffold, including its app bar, with a separate header.
        folderOverlayGroup?.let { group ->
            val members = orderedProviders.filter { it.groupId == group.id }
            val folderMatches = query.isEmpty() || matchesFolderSearch(group.name, query, searchFields)
            key(group.id) {
                ProviderFolderOverlay(
                    group = group,
                    providers = if (folderMatches) members else members.filter(::matches),
                    isGrid = isGridView,
                    originBounds = folderCardBounds[group.id] ?: openedCardBounds,
                    hostOrigin = containerOrigin,
                    animateExitImmediately = dndState.draggedItem != null,
                    dndState = dndState,
                    onBeginDrag = ::beginDrag,
                    onEndDrag = ::finishDnd,
                    onReorderMember = { draggedId, targetId ->
                        val layout = currentLayout()
                        if (orderedProviders.firstOrNull { it.id == draggedId }?.groupId == group.id) {
                            val next = moveProviderDrag(layout, draggedId, ProviderRootOrderItem(targetId, false))
                            if (next != layout) applyLayout(next)
                        }
                    },
                    onDropMember = { acceptedDrop = true },
                    showExitHint = dndState.draggedItem?.data?.let { !it.isGroup } == true && openGroupId == group.id,
                    visible = openGroupId == group.id && openGroup != null,
                    animateEnter = !skipNextFolderEnterAnimation,
                    onExitFinished = {
                        if ((openGroupId == null || openGroup == null) && renderedFolderGroup?.id == group.id) {
                            renderedFolderGroup = null
                        }
                    },
                    onDismiss = { openGroupId = null },
                    onAddProvider = {
                        // Returning from the editor keeps this folder open, so don't replay the open animation.
                        skipNextFolderEnterAnimation = true
                        createProvider(group.id)
                    },
                    onToggleGridView = { onToggleGridView?.invoke(!isGridView) },
                    onEditGroup = { dialogs.groupToRename = group },
                    onDeleteGroup = { dialogs.deletingGroup = group; openGroupId = null },
                    onEditProvider = {
                        skipNextFolderEnterAnimation = true
                        onOpenProviderEditor(it, false)
                    },
                    onMoveProvider = { dialogs.movingProvider = it },
                    onDeleteProvider = { dialogs.deletingProviderId = it }
                )
            }
        }


    }

        ProviderDialogHost(
            dialogs = dialogs,
            sortedGroups = sortedGroups,
            nextRootSortOrder = { (rootItems.maxOfOrNull { it.sortOrder } ?: -1) + 1 },
            onSaveGroup = onSaveGroup,
            onGroupDeleteConfirmed = { group ->
                onDeleteGroup(group.id)
                setCollapsedSections(collapsedSections - group.id)
            },
            onMoveProviderToGroup = onMoveProviderToGroup,
            onProviderDeleteConfirmed = onDeleteProvider
        )
}
