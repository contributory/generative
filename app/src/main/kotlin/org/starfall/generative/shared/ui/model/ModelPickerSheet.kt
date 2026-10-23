package org.starfall.generative.shared.ui.model

import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.starfall.generative.R
import org.starfall.generative.shared.ui.model.LocalThinkingScreenBurst
import org.starfall.generative.shared.ui.model.ReasoningEffortControl
import org.starfall.generative.shared.ui.model.ThinkingScreenBurstLayer
import org.starfall.generative.shared.ui.model.maximumEffortCardBrush
import org.starfall.generative.shared.ui.model.maximumThinkingCardBrush
import org.starfall.generative.shared.ui.model.reasoningEffortBurstDuration
import org.starfall.generative.shared.ui.model.reasoningEffortIndex
import org.starfall.generative.shared.ui.model.reasoningEffortOptions
import org.starfall.generative.shared.ui.model.reasoningEffortTier
import org.starfall.generative.shared.ui.model.rememberThinkingColorBurst
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.DEFAULT_CONTEXT_WINDOW_TOKENS
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.language.ThinkingPassback
import org.starfall.generative.shared.model.language.passbackMode
import org.starfall.generative.shared.model.language.supportedReasoningEfforts
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField
import org.starfall.generative.shared.ui.icons.providerInitials
import org.starfall.generative.shared.ui.model.revealThinkingColor
import org.starfall.generative.shared.ui.model.drawMaximumEffortStreaks

private const val MODEL_LABEL_PREFIX_LIMIT = 20

/** Keep the model's final path segment visible when a namespace or prefix is unusually long. */
internal fun compactModelLabel(value: String): String {
    val slash = value.indexOf('/')
    if (slash <= MODEL_LABEL_PREFIX_LIMIT) return value
    val prefix = value.substring(0, slash)
    return "${prefix.first()}...${prefix.takeLast(5)}/${value.substring(slash + 1)}"
}

sealed interface ModelPickerItem {
    data class Group(
        val group: ProviderGroup,
        val providerCount: Int
    ) : ModelPickerItem

    data class Provider(
        val provider: ModelProvider,
        val modelCount: Int,
        val depth: Int,
        val hasSelectedModel: Boolean = false
    ) : ModelPickerItem

    data class Model(
        val provider: ModelProvider,
        val modelId: String,
        val config: ModelConfiguration,
        val isSelected: Boolean,
        val depth: Int
    ) : ModelPickerItem
}

private val ModelPickerItem.folderId: String?
    get() = when (this) {
        is ModelPickerItem.Group -> group.id
        is ModelPickerItem.Provider -> provider.groupId.takeIf { depth == 1 }
        is ModelPickerItem.Model -> provider.groupId.takeIf { depth == 2 }
    }

// Each lazy row draws its part of one continuous folder outline, including the
// spacing between rows. Keeping rows lazy preserves scrolling to selected models.
private fun Modifier.folderOutline(first: Boolean, last: Boolean, color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val left = stroke / 2
    val right = size.width - stroke / 2
    val top = if (first) stroke / 2 else -4.dp.toPx()
    val bottom = if (last) size.height - stroke / 2 else size.height + 4.dp.toPx()
    val radius = 18.dp.toPx().coerceAtMost((bottom - top) / 2)
    // Draw the two sides independently. Joining the open segments with moveTo()
    // makes visible diagonal/kinked corners when LazyColumn spacing is present.
    drawLine(color, Offset(left, top + if (first) radius else 0f),
        Offset(left, bottom - if (last) radius else 0f), strokeWidth = stroke)
    drawLine(color, Offset(right, top + if (first) radius else 0f),
        Offset(right, bottom - if (last) radius else 0f), strokeWidth = stroke)
    if (first || last) {
        val path = Path().apply {
            if (first) {
                moveTo(left, top + radius)
                quadraticTo(left, top, left + radius, top)
                lineTo(right - radius, top)
                quadraticTo(right, top, right, top + radius)
            }
            if (last) {
                moveTo(left, bottom - radius)
                quadraticTo(left, bottom, left + radius, bottom)
                lineTo(right - radius, bottom)
                quadraticTo(right, bottom, right, bottom - radius)
            }
        }
        drawPath(path, color, style = Stroke(stroke))
    }
}

private data class ModelPickerTab(
    val key: String,
    val folderId: String?,
    val label: String,
    val ungroupedOnly: Boolean = false
)

internal val chatModelPickerFilter: (ModelProvider, String, ModelConfiguration) -> Boolean = { _, _, config ->
    config.modelType == org.starfall.generative.shared.model.language.ModelType.TEXT_GENERATION
}

fun computeModelPickerItems(
    providers: List<ModelProvider>,
    providerGroups: List<ProviderGroup>,
    dynamicModelsMap: Map<String, List<String>>,
    selectedProviderId: String,
    selectedModelId: String,
    query: String = "",
    providerFilterId: String? = null,
    collapsedGroupIds: Set<String> = emptySet(),
    collapsedProviderIds: Set<String> = emptySet(),
    expandSearchResults: Boolean = true,
    selectedFolderId: String? = null,
    ungroupedOnly: Boolean = false,
    modelFilter: (ModelProvider, String, ModelConfiguration) -> Boolean = { _, _, _ -> true }
): List<ModelPickerItem> {
    data class ProviderNode(val provider: ModelProvider, val models: List<String>)

    val defaultConfig = ModelConfiguration()
    val normalizedQuery = query.trim()
    val groupById = providerGroups.associateBy { it.id }
    val providerOrder = compareBy<ModelProvider> { it.id != selectedProviderId }
        .thenBy { it.sortOrder }
        .thenBy { it.id }
    val orderedProviders = when {
        selectedFolderId != null -> providers
            .filter { it.groupId == selectedFolderId }
            .sortedWith(providerOrder)
        ungroupedOnly -> providers
            .filter { it.groupId == null || it.groupId !in groupById }
            .sortedWith(providerOrder)
        else -> buildList {
            providerGroups
                .sortedWith(compareBy<ProviderGroup> { it.sortOrder }.thenBy { it.name.lowercase() })
                .forEach { group ->
                    addAll(providers.filter { it.groupId == group.id }.sortedWith(providerOrder))
                }
            addAll(
                providers
                    .filter { it.groupId == null || it.groupId !in groupById }
                    .sortedWith(providerOrder)
            )
        }
    }
    val visibleProviders = orderedProviders.mapNotNull { provider ->
        if (providerFilterId != null && provider.id != providerFilterId) return@mapNotNull null

        val groupMatches = provider.groupId
            ?.let(groupById::get)
            ?.name
            ?.contains(normalizedQuery, ignoreCase = true) == true
        val providerMatches = provider.name.contains(normalizedQuery, ignoreCase = true)
        val models = providerModels(provider, dynamicModelsMap, selectedProviderId, selectedModelId)
            .filter { modelId ->
                modelFilter(provider, modelId, provider.config.modelConfigs[modelId] ?: defaultConfig)
            }
            .filter { modelId ->
                normalizedQuery.isBlank() ||
                    groupMatches ||
                    providerMatches ||
                    modelId.contains(normalizedQuery, ignoreCase = true) ||
                    provider.config.modelConfigs[modelId]?.displayName.orEmpty()
                        .contains(normalizedQuery, ignoreCase = true)
            }

        if (models.isEmpty()) null else ProviderNode(provider, models)
    }

    val forceExpanded = (normalizedQuery.isNotBlank() && expandSearchResults) || providerFilterId != null
    fun modelItems(node: ProviderNode, depth: Int): List<ModelPickerItem.Model> =
        node.models.map { modelId ->
            val config = node.provider.config.modelConfigs[modelId] ?: defaultConfig
            // Model IDs are scoped to a provider. Display names are presentation-only and may
            // legitimately be shared by multiple models, so selection must use both IDs exactly.
            val isSelected = selectedProviderId.isNotBlank() &&
                selectedModelId.isNotBlank() &&
                node.provider.id == selectedProviderId &&
                modelId == selectedModelId
            ModelPickerItem.Model(node.provider, modelId, config, isSelected, depth)
        }

    return buildList {
        visibleProviders.forEach { node ->
            val models = modelItems(node, depth = 1)
            add(ModelPickerItem.Provider(
                node.provider,
                node.models.size,
                depth = 0,
                hasSelectedModel = models.any { it.isSelected }
            ))
            addAll(if (!forceExpanded && node.provider.id in collapsedProviderIds) {
                // Keep the selected model visible so collapsing a provider never hides
                // the model that the picker is currently representing.
                models.filter { it.isSelected }
            } else models)
        }
    }
}

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
        val groupsById = providerGroups.associateBy { it.id }
        val providerGroupIds = providers.mapNotNull { it.groupId }.filter { it in groupsById }.toSet()
        val hasUngroupedProviders = providers.any { provider ->
            provider.groupId == null || provider.groupId !in groupsById
        }
        buildList {
            add(ModelPickerTab(key = "default", folderId = null, label = defaultTabLabel))
            providerGroups
                .asSequence()
                .filter { it.id in providerGroupIds }
                .sortedWith(compareBy<ProviderGroup> { it.sortOrder }.thenBy { it.name.lowercase() })
                .forEach { group ->
                    add(ModelPickerTab(key = "folder_${group.id}", folderId = group.id, label = group.name))
                }
            if (hasUngroupedProviders) {
                add(ModelPickerTab(key = "ungrouped", folderId = null, label = ungroupedTabLabel, ungroupedOnly = true))
            }
        }
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    enabled = providers.isNotEmpty() || providerGroups.isNotEmpty(),
                    onClick = {
                        expandSearchResults = false
                        if (allCollapsed) {
                            setCollapsedGroups(emptySet())
                            setCollapsedProviders(emptySet())
                        } else {
                            setCollapsedProviders(providers.map { it.id }.toSet())
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    FluentIcon(
                        if (allCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        contentDescription = stringResource(
                            if (allCollapsed) R.string.expand_all_model_sections else R.string.collapse_all_model_sections
                        )
                    )
                }
                SelectableOutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { FluentIcon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.search_models_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f)
                )
                if (onOpenSelectedModel != null) {
                    FilledTonalIconButton(
                        onClick = onOpenSelectedModel,
                        modifier = Modifier.size(48.dp).testTag("open-selected-model-button")
                    ) {
                        FluentIcon(
                            Icons.Outlined.Tune,
                            contentDescription = stringResource(R.string.open_selected_model)
                        )
                    }
                }
            }

            if (folderTabs.size > 1) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 12.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    folderTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                if (selectedTabKey != tab.key) instantTabSwap = true
                                selectedTabKey = tab.key
                                onSelectedTabKeyChange(tab.key)
                            },
                            modifier = Modifier.testTag("model-picker-tab_${tab.key}"),
                            text = {
                                Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        )
                    }
                }
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


@Composable
private fun ModelPickerGroupRow(
    group: ProviderGroup,
    collapsed: Boolean,
    onToggle: () -> Unit
) {
    val toggleDescription = stringResource(
        if (collapsed) R.string.expand_group else R.string.collapse_group, group.name
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().animateContentSize(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        ).testTag("model-picker-group_${group.id}")
            .semantics { contentDescription = toggleDescription }
            .clickable(onClick = onToggle)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EntityIcon(
                image = group.icon,
                modifier = Modifier.size(24.dp),
                fallback = if (collapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen,
                matchName = group.name
            )
            Spacer(Modifier.width(8.dp))
            Text(
                group.name,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(32.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelPickerProviderRow(
    provider: ModelProvider,
    collapsed: Boolean,
    hasSelectedModel: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (hasSelectedModel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else Color.Transparent,
        contentColor = if (hasSelectedModel) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
            .testTag("model-picker-provider_${provider.id}")
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FluentIcon(
                if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(if (collapsed) R.string.expand_group else R.string.collapse_group, provider.name),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            ProviderMark(
                provider = provider,
                compact = true
            )
            Spacer(Modifier.width(8.dp))
            Text(
                provider.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                 color = if (hasSelectedModel) MaterialTheme.colorScheme.onPrimaryContainer
                     else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                 fontWeight = FontWeight.Medium
             )
             if (hasSelectedModel) {
                 FluentIcon(
                     Icons.Filled.Check,
                     contentDescription = null,
                     tint = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.size(18.dp)
                 )
             }
        }
    }
}

private fun providerModels(
    provider: ModelProvider,
    dynamicModelsMap: Map<String, List<String>>,
    selectedProviderId: String,
    selectedModelId: String
): List<String> = (
        provider.config.modelIds
            ?: ((dynamicModelsMap[provider.id]
                ?: defaultProviderModels(provider.type)) + provider.config.modelConfigs.keys)
        )
    .plus(if (provider.id == selectedProviderId && selectedModelId.isNotBlank()) listOf(selectedModelId) else emptyList())
    .distinct()


internal fun defaultProviderModels(type: ModelApiType): List<String> = emptyList()
