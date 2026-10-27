package org.starfall.generative.shared.ui.model

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.ui.sheets.AppSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<ModelProvider>,
    providerGroups: List<ProviderGroup> = emptyList(),
    collapsedGroupIdsState: Set<String> = emptySet(),
    collapsedProviderIdsState: Set<String> = emptySet(),
    onCollapsedGroupIdsChange: (Set<String>) -> Unit = {},
    onCollapsedProviderIdsChange: (Set<String>) -> Unit = {},
    selectedTabKeyState: String = "default",
    onSelectedTabKeyChange: (String) -> Unit = {},
    selectedProviderId: String,
    selectedModelId: String,
    conversationReasoningEffort: String?,
    onSelectModel: (providerId: String, modelId: String) -> Unit,
    onSetReasoningEffort: (String?) -> Unit,
    dynamicModelsMap: Map<String, List<String>> = emptyMap(),
    modelFilter: (ModelProvider, String, ModelConfiguration) -> Boolean = { _, _, _ -> true },
    showReasoningEffort: Boolean = true,
    onEditProvider: (providerId: String) -> Unit = {},
    onDismiss: () -> Unit
) {
    AppSheet(
        onDismissRequest = onDismiss,
        fixedHeightFraction = 1f
    ) {
        ModelPickerSheetContent(
            providers = providers,
            providerGroups = providerGroups,
            collapsedGroupIdsState = collapsedGroupIdsState,
            collapsedProviderIdsState = collapsedProviderIdsState,
            onCollapsedGroupIdsChange = onCollapsedGroupIdsChange,
            onCollapsedProviderIdsChange = onCollapsedProviderIdsChange,
            selectedTabKeyState = selectedTabKeyState,
            onSelectedTabKeyChange = onSelectedTabKeyChange,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
            conversationReasoningEffort = conversationReasoningEffort,
            onSelectModel = onSelectModel,
            onSetReasoningEffort = onSetReasoningEffort,
            dynamicModelsMap = dynamicModelsMap,
            modelFilter = modelFilter,
            showReasoningEffort = showReasoningEffort,
            onEditProvider = onEditProvider,
            onDismiss = onDismiss
        )
    }
}

/**
 * The picker body without its own sheet, so it can live as a page inside a shared sheet.
 * [onOpenSelectedModel] adds a button next to the search field that switches to the selected-model page.
 */
@Composable
internal fun ModelPickerSheetContent(
    providers: List<ModelProvider>,
    providerGroups: List<ProviderGroup> = emptyList(),
    collapsedGroupIdsState: Set<String> = emptySet(),
    collapsedProviderIdsState: Set<String> = emptySet(),
    onCollapsedGroupIdsChange: (Set<String>) -> Unit = {},
    onCollapsedProviderIdsChange: (Set<String>) -> Unit = {},
    selectedTabKeyState: String = "default",
    onSelectedTabKeyChange: (String) -> Unit = {},
    selectedProviderId: String,
    selectedModelId: String,
    conversationReasoningEffort: String?,
    onSelectModel: (providerId: String, modelId: String) -> Unit,
    onSetReasoningEffort: (String?) -> Unit,
    dynamicModelsMap: Map<String, List<String>> = emptyMap(),
    modelFilter: (ModelProvider, String, ModelConfiguration) -> Boolean = { _, _, _ -> true },
    showReasoningEffort: Boolean = true,
    onEditProvider: (providerId: String) -> Unit = {},
    onOpenSelectedModel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var searchBounds by remember { mutableStateOf<Rect?>(null) }
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    var expandSearchResults by remember(query) { mutableStateOf(true) }
    val forceExpanded = query.isNotBlank() && expandSearchResults
    var collapsedGroupIds by remember { mutableStateOf(collapsedGroupIdsState) }
    var collapsedProviderIds by remember { mutableStateOf(collapsedProviderIdsState) }
    val ungroupedTabLabel = stringResource(R.string.ungrouped)
    var selectedTabKey by remember { mutableStateOf(selectedTabKeyState) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var initialized by remember { mutableStateOf(false) }
    var scrollingToTab by remember { mutableStateOf(false) }
    var scrollRequest by remember { mutableIntStateOf(0) }
    var positionBeforeSearch by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(collapsedGroupIdsState) {
        collapsedGroupIds = collapsedGroupIdsState
    }
    LaunchedEffect(collapsedProviderIdsState) {
        collapsedProviderIds = collapsedProviderIdsState
    }
    fun setCollapsedGroups(value: Set<String>) {
        collapsedGroupIds = value
        onCollapsedGroupIdsChange(value)
    }
    fun setCollapsedProviders(value: Set<String>) {
        collapsedProviderIds = value
        onCollapsedProviderIdsChange(value)
    }

    val flatItems = remember(
        providers,
        providerGroups,
        dynamicModelsMap,
        selectedProviderId,
        selectedModelId,
        query,
        expandSearchResults,
        collapsedGroupIds,
        collapsedProviderIds,
        modelFilter
    ) {
        computeModelPickerItems(
            providers = providers,
            providerGroups = providerGroups,
            dynamicModelsMap = dynamicModelsMap,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
            query = query,
            expandSearchResults = expandSearchResults,
            collapsedGroupIds = collapsedGroupIds,
            collapsedProviderIds = collapsedProviderIds,
            modelFilter = modelFilter
        )
    }

    val folderTabs = remember(flatItems, ungroupedTabLabel) {
        computeModelPickerTabs(flatItems, ungroupedTabLabel)
    }
    val sectionIndexes = remember(flatItems) {
        flatItems.mapIndexedNotNull { index, item -> item.sectionKey?.let { it to index } }.toMap()
    }
    val selectedTabIndex = folderTabs.indexOfFirst { it.key == selectedTabKey }.coerceAtLeast(0)
    val centerTopPaddingPx = with(LocalDensity.current) { 10.dp.roundToPx() }

    LaunchedEffect(flatItems) {
        if (!initialized && flatItems.isNotEmpty()) {
            val restoredIndex = sectionIndexes[selectedTabKeyState]
            val selectedIndex = flatItems.indexOfFirst { it is ModelPickerItem.Model && it.isSelected }
            val targetIndex = restoredIndex ?: selectedIndex.coerceAtLeast(0)
            listState.scrollToItem(targetIndex)
            if (restoredIndex == null && selectedIndex > 0) {
                val info = listState.layoutInfo
                val itemSize = info.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.size
                if (itemSize != null) {
                    val centerDelta = ((info.viewportSize.height - itemSize) / 2 - centerTopPaddingPx)
                        .coerceAtLeast(0)
                    listState.scrollToItem(targetIndex, -centerDelta)
                }
            }
            selectedTabKey = sectionIndexes.entries.lastOrNull { it.value <= targetIndex }?.key
                ?: folderTabs.firstOrNull()?.key.orEmpty()
            initialized = true
        }
    }
    // Manual scrolling moves the tab indicator; a tab click retains its target even if the
    // final section is too short for its header to reach the top of the viewport.
    LaunchedEffect(listState, flatItems, initialized) {
        if (initialized) {
            snapshotFlow { listState.firstVisibleItemIndex }.drop(1).collect { index ->
                if (!scrollingToTab) {
                    val key = sectionIndexes.entries.lastOrNull { it.value <= index }?.key
                    if (key != null && key != selectedTabKey) {
                        selectedTabKey = key
                        onSelectedTabKeyChange(key)
                    }
                }
            }
        }
    }
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            if (positionBeforeSearch == null) {
                positionBeforeSearch = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
            listState.scrollToItem(0)
        } else {
            positionBeforeSearch?.let { (index, offset) ->
                listState.scrollToItem(index.coerceAtMost((flatItems.size - 1).coerceAtLeast(0)), offset)
            }
            positionBeforeSearch = null
        }
    }
    val allCollapsed = !forceExpanded && providers.all { it.id in collapsedProviderIds }
    val selectedModel = selectedModelId.takeIf { it.isNotBlank() }?.let { modelId ->
        providers.firstOrNull { it.id == selectedProviderId }?.config?.modelConfigs?.let { configs ->
            configs[modelId] ?: configs.values.firstOrNull { it.displayName == modelId }
        }
    }

    Column(modifier = modifier.fillMaxWidth()
        .onGloballyPositioned { contentOrigin = it.positionInRoot() }
        // Observe taps before children consume them without interrupting scrolling or clicks.
        .pointerInput(searchBounds, contentOrigin, focusManager) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    if (change.changedToUpIgnoreConsumed()) {
                        if (searchBounds != null && searchBounds?.contains(contentOrigin + down.position) != true) {
                            focusManager.clearFocus(force = true)
                            keyboard?.hide()
                        }
                        break
                    }
                }
            }
        }
    ) {
            ModelPickerToolbar(
                query = query,
                onQueryChange = { query = it },
                toggleEnabled = providers.isNotEmpty() || providerGroups.isNotEmpty(),
                allCollapsed = allCollapsed,
                onToggleAllCollapsed = {
                    expandSearchResults = false
                    if (allCollapsed) {
                        setCollapsedGroups(emptySet())
                        setCollapsedProviders(emptySet())
                    } else {
                        setCollapsedProviders(providers.map { it.id }.toSet())
                    }
                },
                selectedModelId = selectedModelId,
                selectedModel = selectedModel,
                onOpenSelectedModel = onOpenSelectedModel,
                onSearchBoundsChange = { searchBounds = it }
            )

            if (folderTabs.isNotEmpty()) {
                ModelPickerTabRow(
                    tabs = folderTabs,
                    selectedIndex = selectedTabIndex,
                    onSelectTab = { tab ->
                        val index = sectionIndexes[tab.key]
                        if (index != null) {
                            selectedTabKey = tab.key
                            onSelectedTabKeyChange(tab.key)
                            scrollRequest++
                            val request = scrollRequest
                            scrollingToTab = true
                            scope.launch {
                                try {
                                    listState.animateScrollToItem(index)
                                } finally {
                                    if (request == scrollRequest) scrollingToTab = false
                                }
                            }
                        }
                    }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("model-picker-list"),
                contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (flatItems.isEmpty()) {
                    item(key = "empty") {
                        Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_matching_models),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        flatItems,
                        key = { _, item ->
                                when (item) {
                                    is ModelPickerItem.Group -> "group_${item.group.id}"
                                    ModelPickerItem.Ungrouped -> "ungrouped"
                                    is ModelPickerItem.Provider -> "provider_${item.provider.id}"
                                    is ModelPickerItem.Model -> "model_${item.provider.id}_${item.modelId}"
                                }
                            }
                        ) { index, item ->
                        Box(
                            Modifier.fillMaxWidth()
                                .animateItem(
                                    fadeInSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    fadeOutSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                                )
                                .testTag("model-picker-frame_$index")
                        ) {
                            when (item) {
                                is ModelPickerItem.Group -> ModelPickerSection(
                                    label = item.group.name,
                                    tag = "model-picker-group_${item.group.id}"
                                )
                                ModelPickerItem.Ungrouped -> ModelPickerSection(
                                    label = ungroupedTabLabel,
                                    tag = "model-picker-group_ungrouped"
                                )

                                is ModelPickerItem.Provider -> {
                                    val collapsed = item.provider.id in collapsedProviderIds &&
                                        !forceExpanded
                                        ModelPickerProviderRow(
                                            provider = item.provider,
                                            collapsed = collapsed,
                                            hasSelectedModel = item.hasSelectedModel,
                                            onEdit = {
                                                onDismiss()
                                                onEditProvider(item.provider.id)
                                            },
                                            onToggle = {
                                            expandSearchResults = false
                                            setCollapsedProviders(
                                                if (collapsed) collapsedProviderIds - item.provider.id
                                                else collapsedProviderIds + item.provider.id
                                            )
                                        }
                                    )
                                }

                                is ModelPickerItem.Model -> {
                                    Box(Modifier.fillMaxWidth().testTag("model-picker-model_${item.provider.id}_${item.modelId}")) {
                                        ModelPickerCard(
                                            modelId = item.modelId,
                                            config = item.config,
                                            isSelected = item.isSelected,
                                            conversationReasoningEffort = conversationReasoningEffort,
                                            onSetReasoningEffort = onSetReasoningEffort,
                                            showReasoningEffort = showReasoningEffort,
                                            onClick = {
                                                onSelectModel(item.provider.id, item.modelId)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}
