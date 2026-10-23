package org.starfall.generative.shared.ui.model

import kotlinx.coroutines.delay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
        fixedHeightFraction = 0.9f
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
    var expandSearchResults by remember(query) { mutableStateOf(true) }
    val forceExpanded = query.isNotBlank() && expandSearchResults
    var collapsedGroupIds by remember { mutableStateOf(collapsedGroupIdsState) }
    var collapsedProviderIds by remember { mutableStateOf(collapsedProviderIdsState) }
    val defaultTabLabel = stringResource(R.string.common_default)
    val ungroupedTabLabel = stringResource(R.string.ungrouped)
    val folderTabs = remember(providers, providerGroups, defaultTabLabel, ungroupedTabLabel) {
        computeModelPickerTabs(providers, providerGroups, defaultTabLabel, ungroupedTabLabel)
    }
    var selectedTabKey by remember { mutableStateOf(selectedTabKeyState.ifBlank { "default" }) }
    // Keep a separate LazyListState for every tab. Reusing one state here makes
    // Compose restore the previous tab's position (or jump to its initial row)
    // whenever the tab content is replaced.
    val tabListStates = remember { mutableMapOf<String, LazyListState>() }
    val initializedTabKeys = remember { mutableSetOf<String>() }
    val tabPositionsBeforeSearch = remember { mutableMapOf<String, Pair<Int, Int>>() }
    // Switching tabs replaces most rows at once while the list is also repositioned. Fading the old
    // rows out during that jump can leave them stuck on screen, so rows are swapped instantly.
    var instantTabSwap by remember { mutableStateOf(false) }
    val selectedTabIndex = folderTabs.indexOfFirst { it.key == selectedTabKey }.coerceAtLeast(0)
    LaunchedEffect(selectedTabIndex) {
        if (instantTabSwap) {
            delay(400)
            instantTabSwap = false
        }
    }
    LaunchedEffect(folderTabs) {
        if (folderTabs.none { it.key == selectedTabKey }) {
            val fallback = folderTabs.firstOrNull()?.key ?: "default"
            selectedTabKey = fallback
            onSelectedTabKeyChange(fallback)
        }
    }
    LaunchedEffect(selectedTabKeyState) {
        val requested = selectedTabKeyState.ifBlank { "default" }
        if (requested != selectedTabKey && folderTabs.any { it.key == requested }) {
            selectedTabKey = requested
        }
    }
    val selectedTab = folderTabs.getOrNull(selectedTabIndex)
    val selectedFolderId = selectedTab?.folderId
    val ungroupedOnly = selectedTab?.ungroupedOnly == true
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
        selectedFolderId,
        ungroupedOnly,
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
            selectedFolderId = selectedFolderId,
            ungroupedOnly = ungroupedOnly,
            modelFilter = modelFilter
        )
    }

    // The selected model is centered in the list. Near the top of the list the scroll position
    // simply clamps to 0, so it only sits at the very top when nothing is above it.
    val targetIndex = remember(flatItems, selectedProviderId, selectedModelId) {
        flatItems.indexOfFirst { it is ModelPickerItem.Model && it.isSelected }.coerceAtLeast(0)
    }
    val listState = remember(selectedTabKey) {
        tabListStates.getOrPut(selectedTabKey) { LazyListState((targetIndex - 3).coerceAtLeast(0)) }
    }
    val centerTopPaddingPx = with(LocalDensity.current) { 10.dp.roundToPx() }
    LaunchedEffect(selectedTabKey, flatItems, query, targetIndex) {
        if (query.isBlank() && flatItems.isNotEmpty() &&
            initializedTabKeys.add(selectedTabKey) && !listState.isScrollInProgress
        ) {
            listState.scrollToItem(targetIndex)
            val info = listState.layoutInfo
            val itemSize = info.visibleItemsInfo.firstOrNull { it.index == targetIndex }?.size
            if (itemSize != null && targetIndex > 0) {
                val centerDelta = ((info.viewportSize.height - itemSize) / 2 - centerTopPaddingPx)
                    .coerceAtLeast(0)
                listState.scrollToItem(targetIndex, -centerDelta)
            }
        }
    }
    LaunchedEffect(query, selectedTabKey) {
        if (query.isNotBlank()) {
            tabPositionsBeforeSearch.putIfAbsent(
                selectedTabKey,
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            )
            listState.scrollToItem(0)
        } else {
            tabPositionsBeforeSearch.remove(selectedTabKey)?.let { (index, offset) ->
                listState.scrollToItem(index, offset)
            }
        }
    }
    val allCollapsed = !forceExpanded && providers.all { it.id in collapsedProviderIds }

    Column(modifier = modifier.fillMaxWidth()) {
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
                onOpenSelectedModel = onOpenSelectedModel
            )

            if (folderTabs.size > 1) {
                ModelPickerTabRow(
                    tabs = folderTabs,
                    selectedIndex = selectedTabIndex,
                    onSelectTab = { tab ->
                        if (selectedTabKey != tab.key) instantTabSwap = true
                        selectedTabKey = tab.key
                        onSelectedTabKeyChange(tab.key)
                    }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("model-picker-list"),
                contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    is ModelPickerItem.Provider -> "provider_${item.provider.id}"
                                    is ModelPickerItem.Model -> "model_${item.provider.id}_${item.modelId}"
                                }
                            }
                        ) { index, item ->
                            val folderId = item.folderId
                            val first = folderId != null && flatItems.getOrNull(index - 1)?.folderId != folderId
                            val last = folderId != null && flatItems.getOrNull(index + 1)?.folderId != folderId
                        Box(
                            Modifier.fillMaxWidth()
                                .animateItem(
                                    fadeInSpec = if (instantTabSwap) null else spring<Float>(stiffness = Spring.StiffnessMediumLow),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    fadeOutSpec = if (instantTabSwap) null else spring<Float>(stiffness = Spring.StiffnessMediumLow)
                                )
                                .testTag("model-picker-frame_$index")
                                    .then(if (folderId != null) Modifier.folderOutline(
                                        first, last, MaterialTheme.colorScheme.outline
                                    ) else Modifier)
                                    .padding(
                                        start = 6.dp, end = 6.dp,
                                        top = if (first) 6.dp else 0.dp,
                                        bottom = if (last) 6.dp else 0.dp
                                    )
                            ) {
                            when (item) {
                                is ModelPickerItem.Group -> Unit

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
