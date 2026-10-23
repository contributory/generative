package org.starfall.generative.features.chatoptions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.PromptLibrary
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.ToolSettings
import java.util.UUID
import kotlinx.coroutines.launch
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.ui.layout.SlideScreenContent
import org.starfall.generative.shared.ui.model.DefaultModelSelector
import org.starfall.generative.shared.ui.model.ModelPickerSheet
import org.starfall.generative.shared.storage.preferences.AppPreferences

private sealed interface PromptPage {
    data class Editor(val promptId: String?) : PromptPage
    data class Preview(val promptId: String) : PromptPage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatOptionsScreen(
    providers: List<ModelProvider>,
    settings: ToolSettings,
    appPreferences: AppPreferences = AppPreferences(),
    onAutoSummaryEnabledChange: (Boolean) -> Unit = {},
    onSummarySuggestionEnabledChange: (Boolean) -> Unit = {},
    onAutoSummaryPercentChange: (Int) -> Unit = {},
    providerGroups: List<ProviderGroup> = emptyList(),
    collapsedGroupIds: Set<String> = emptySet(),
    collapsedProviderIds: Set<String> = emptySet(),
    onCollapsedGroupIdsChange: (Set<String>) -> Unit = {},
    onCollapsedProviderIdsChange: (Set<String>) -> Unit = {},
    selectedTabKey: String = "default",
    onSelectedTabKeyChange: (String) -> Unit = {},
    onEditProvider: (providerId: String) -> Unit = {},
    promptLibrary: PromptLibrary? = null,
    defaultSystemPrompt: String = "",
    onPromptLibraryChange: (PromptLibrary) -> Unit = {},
    initialTab: String = "helpers",
    initialPromptId: String = "",
    onSave: (String, SystemToolConfig) -> Unit,
    onBack: () -> Unit
) {
    var choosing by remember { mutableStateOf<String?>(null) }
    var helperSettings by rememberSaveable { mutableStateOf<String?>(null) }
    val initialPage = if (initialTab == "instructions") 1 else 0
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var lastRequestedTab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) {
        if (lastRequestedTab != initialTab) {
            lastRequestedTab = initialTab
            val requestedPage = if (initialTab == "instructions") 1 else 0
            pager.scrollToPage(requestedPage)
        }
    }
    var creatingPrompt by rememberSaveable(initialPromptId) { mutableStateOf(false) }
    var editingPromptId by rememberSaveable(initialPromptId) {
        mutableStateOf(initialPromptId.takeIf(String::isNotBlank))
    }
    var previewingPromptId by rememberSaveable { mutableStateOf<String?>(null) }
    var library by remember(promptLibrary, defaultSystemPrompt) {
        mutableStateOf(promptLibrary ?: PromptLibrary.fromLegacy(defaultSystemPrompt))
    }
    fun updateLibrary(next: PromptLibrary) {
        library = next
        onPromptLibraryChange(next)
    }
    fun savePromptDraft(draft: SavedPrompt): SavedPrompt? {
        if (draft.name.isBlank() || draft.content.isBlank()) return null
        val prompt = if (draft.id.isBlank()) {
            draft.copy(id = UUID.randomUUID().toString())
        } else {
            draft
        }
        val exists = library.prompts.any { it.id == prompt.id }
        updateLibrary(
            library.copy(
                prompts = if (exists) library.prompts.map { if (it.id == prompt.id) prompt else it }
                else library.prompts + prompt,
                selectedIds = library.selectedIds
            )
        )
        return prompt
    }

    fun leavePromptEditor(draft: SavedPrompt? = null) {
        draft?.let(::savePromptDraft)
        creatingPrompt = false
        editingPromptId = null
        previewingPromptId = null
        coroutineScope.launch { pager.animateScrollToPage(1) }
    }

    fun openPromptPreview(draft: SavedPrompt) {
        val saved = savePromptDraft(draft) ?: return
        creatingPrompt = false
        editingPromptId = null
        previewingPromptId = saved.id
    }

    val promptPage: PromptPage? = when {
        creatingPrompt || editingPromptId != null -> PromptPage.Editor(editingPromptId)
        previewingPromptId != null -> PromptPage.Preview(previewingPromptId!!)
        else -> null
    }

    SlideScreenContent(
        editor = promptPage,
        label = "Prompt details"
    ) { editorPage ->
        when (editorPage) {
            is PromptPage.Editor -> {
                PromptEditor(
                    prompt = editorPage.promptId?.let { id -> library.prompts.firstOrNull { it.id == id } },
                    onSave = { saved ->
                        if (savePromptDraft(saved) != null) leavePromptEditor()
                    },
                    onPreview = ::openPromptPreview,
                    onBack = ::leavePromptEditor
                )
            }
            is PromptPage.Preview -> {
                val prompt = library.prompts.firstOrNull { it.id == editorPage.promptId }
                if (prompt == null) {
                    LaunchedEffect(editorPage.promptId) { previewingPromptId = null }
                } else {
                    PromptPreview(
                        prompt = prompt,
                        onEdit = {
                            previewingPromptId = null
                            editingPromptId = prompt.id
                        },
                        onBack = { previewingPromptId = null }
                    )
                }
            }
            null -> {
            BackHandler(enabled = LocalScreenTransitionActive.current, onBack = onBack)
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.settings_chat_options)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                            }
                        }
                    )
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                        Tab(
                            selected = pager.currentPage == 0,
                            onClick = { coroutineScope.launch { pager.animateScrollToPage(0) } },
                            text = { Text(stringResource(R.string.chat_options_tab_helpers)) }
                        )
                        Tab(
                            selected = pager.currentPage == 1,
                            onClick = { coroutineScope.launch { pager.animateScrollToPage(1) } },
                            text = { Text(stringResource(R.string.chat_options_tab_instructions)) }
                        )
                    }
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        when (page) {
                            0 -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Text(
                                        stringResource(R.string.tool_settings_conversation_helpers_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                items(listOf("title_generation", "chat_summary")) { name ->
                                    val config = settings.system[name] ?: SystemToolConfig()
                                    val provider = providers.find { it.id == config.providerId }
                                    val model = provider?.config?.modelConfigs?.get(config.modelId)
                                    val title = stringResource(
                                        if (name == "title_generation") R.string.tool_title_generation_title
                                        else R.string.tool_chat_summary_title
                                    )
                                    val description = stringResource(
                                        if (name == "title_generation") R.string.tool_title_generation_description
                                        else R.string.tool_chat_summary_description
                                    )

                                    SettingsCard {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(
                                                Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(title, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { helperSettings = name }) {
                                                FluentIcon(
                                                    Icons.Outlined.Settings,
                                                    stringResource(R.string.tool_settings_accessibility, title)
                                                )
                                            }
                                        }
                                        DefaultModelSelector(
                                            provider = provider,
                                            modelId = config.modelId,
                                            model = model,
                                            onClick = { choosing = name }
                                        )
                                    }
                                }
                            }
                            else -> InstructionsTab(
                                 library = library,
                                 onAddPrompt = { creatingPrompt = true },
                                 onEditPrompt = { editingPromptId = it.id },
                                 onPreviewPrompt = { previewingPromptId = it.id },
                                 onDeletePrompt = { prompt ->
                                    updateLibrary(
                                        library.copy(
                                            prompts = library.prompts.filterNot { it.id == prompt.id },
                                            selectedIds = library.selectedIds - prompt.id
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    helperSettings?.let { name ->
        HelperSettingsSheet(
            name = name,
            config = settings.system[name] ?: SystemToolConfig(),
            appPreferences = appPreferences,
            onSave = { config ->
                onSave(name, config)
                helperSettings = null
            },
            onAutoSummaryEnabledChange = onAutoSummaryEnabledChange,
            onSummarySuggestionEnabledChange = onSummarySuggestionEnabledChange,
            onAutoSummaryPercentChange = onAutoSummaryPercentChange,
            onDismiss = { helperSettings = null }
        )
    }

    choosing?.let { name ->
        val current = settings.system[name] ?: SystemToolConfig()
        ModelPickerSheet(
            providers = providers,
            providerGroups = providerGroups,
            collapsedGroupIdsState = collapsedGroupIds,
            collapsedProviderIdsState = collapsedProviderIds,
            onCollapsedGroupIdsChange = onCollapsedGroupIdsChange,
            onCollapsedProviderIdsChange = onCollapsedProviderIdsChange,
            selectedTabKeyState = selectedTabKey,
            onSelectedTabKeyChange = onSelectedTabKeyChange,
            selectedProviderId = current.providerId,
            selectedModelId = current.modelId,
            conversationReasoningEffort = null,
            onSetReasoningEffort = {},
            showReasoningEffort = false,
            modelFilter = { provider, id, config ->
                config.modelType == ModelType.TEXT_GENERATION &&
                    provider.config.modelConfigs.containsKey(id) &&
                    provider.config.modelIds?.contains(id) != false
            },
            onSelectModel = { providerId, modelId ->
                onSave(name, current.copy(providerId = providerId, modelId = modelId))
            },
            onEditProvider = onEditProvider,
            onDismiss = { choosing = null }
        )
    }
}
