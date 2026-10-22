package org.starfall.generative.features.chatoptions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.DEFAULT_CHAT_SUMMARY_PROMPT
import org.starfall.generative.shared.model.DEFAULT_TITLE_GENERATION_PROMPT
import org.starfall.generative.shared.model.PromptLibrary
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
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatOptionsScreen(
    providers: List<ModelProvider>,
    settings: ToolSettings,
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
    var editingPrompt by rememberSaveable { mutableStateOf<String?>(null) }
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
    var library by remember(promptLibrary, defaultSystemPrompt) {
        mutableStateOf(promptLibrary ?: PromptLibrary.fromLegacy(defaultSystemPrompt))
    }
    fun updateLibrary(next: PromptLibrary) {
        library = next
        onPromptLibraryChange(next)
    }
    fun leavePromptEditor() {
        creatingPrompt = false
        editingPromptId = null
        coroutineScope.launch { pager.animateScrollToPage(1) }
    }
    val editorPrompt = library.prompts.firstOrNull { it.id == editingPromptId }
    val promptEditorOpen = creatingPrompt || editingPromptId != null

    SlideScreenContent(
        editor = promptEditorOpen.takeIf { it },
        label = "Prompt editor"
    ) { editorPage ->
        if (editorPage == true) {
            PromptEditor(
                prompt = editorPrompt,
                onSave = { saved ->
                    val prompt = if (saved.id.isBlank()) saved.copy(id = UUID.randomUUID().toString()) else saved
                    val exists = library.prompts.any { it.id == prompt.id }
                    updateLibrary(
                        library.copy(
                            prompts = if (exists) library.prompts.map { if (it.id == prompt.id) prompt else it }
                            else library.prompts + prompt,
                            selectedIds = if (exists) library.selectedIds else library.selectedIds + prompt.id
                        )
                    )
                    leavePromptEditor()
                },
                onBack = ::leavePromptEditor
            )
        } else {
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
                    HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
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
                                        Text(title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        DefaultModelSelector(
                                            provider = provider,
                                            modelId = config.modelId,
                                            model = model,
                                            onClick = { choosing = name }
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { editingPrompt = name }) {
                                                Text(stringResource(R.string.tool_instructions))
                                            }
                                        }
                                    }
                                }
                            }
                            else -> InstructionsTab(
                                library = library,
                                onAddPrompt = { creatingPrompt = true },
                                onEditPrompt = { editingPromptId = it.id },
                                onTogglePrompt = { prompt, enabled ->
                                    updateLibrary(
                                        library.copy(
                                            selectedIds = if (enabled) library.selectedIds + prompt.id
                                            else library.selectedIds - prompt.id
                                        )
                                    )
                                },
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

    editingPrompt?.let { name ->
        val config = settings.system[name] ?: SystemToolConfig()
        val defaultPrompt = if (name == "title_generation") {
            DEFAULT_TITLE_GENERATION_PROMPT
        } else {
            DEFAULT_CHAT_SUMMARY_PROMPT
        }
        var prompt by rememberSaveable(name) {
            mutableStateOf(config.prompt.ifBlank { defaultPrompt })
        }
        AppSheet(onDismissRequest = { editingPrompt = null }) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(
                        if (name == "title_generation") R.string.tool_title_generation_title
                        else R.string.tool_chat_summary_title
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(R.string.tool_customize_instructions_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectableOutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.tool_instruction_prompt)) },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { prompt = defaultPrompt }) {
                        Text(stringResource(R.string.tool_reset))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { editingPrompt = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(onClick = {
                        onSave(name, config.copy(prompt = prompt.ifBlank { defaultPrompt }))
                        editingPrompt = null
                    }) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
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
