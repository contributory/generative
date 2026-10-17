package org.starfall.multigateway.features.defaultmodelsandtools.systemtools.screen

import org.starfall.multigateway.features.defaultmodelsandtools.defaultmodels.screen.DefaultModelsScreen

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.starfall.multigateway.R
import androidx.compose.ui.res.stringResource
import org.starfall.multigateway.features.defaultmodelsandtools.shared.ImageToolSettingsScreen
import org.starfall.multigateway.features.defaultmodelsandtools.shared.videoOptionFields
import org.starfall.multigateway.features.settings.shared.SettingsCard
import org.starfall.multigateway.home.modelpicker.sheet.ModelPickerSheet
import org.starfall.multigateway.shared.model.DEFAULT_CHAT_SUMMARY_PROMPT
import org.starfall.multigateway.shared.model.DEFAULT_TITLE_GENERATION_PROMPT
import org.starfall.multigateway.shared.model.SystemToolConfig
import org.starfall.multigateway.shared.model.ToolSettings
import org.starfall.multigateway.shared.model.api.ModelApiType
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.ProviderGroup
import org.starfall.multigateway.shared.model.systemMediaToolAvailable
import org.starfall.multigateway.shared.ui.sheets.AppBottomSheet
import org.starfall.multigateway.shared.ui.icons.EntityIcon
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.multigateway.shared.ui.text.SelectableOutlinedTextField
import org.starfall.multigateway.shared.ui.layout.SlideScreenContent
import org.starfall.multigateway.features.defaultmodelsandtools.shared.ToolSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemToolsScreen(
    providers: List<ModelProvider>,
    settings: ToolSettings,
    onSave: (String, SystemToolConfig) -> Unit,
    onSetSendFile: (Boolean) -> Unit = {},
    providerGroups: List<ProviderGroup> = emptyList(),
    collapsedGroupIds: Set<String> = emptySet(),
    collapsedProviderIds: Set<String> = emptySet(),
    onCollapsedGroupIdsChange: (Set<String>) -> Unit = {},
    onCollapsedProviderIdsChange: (Set<String>) -> Unit = {},
    selectedTabKey: String = "default",
    onSelectedTabKeyChange: (String) -> Unit = {},
    onEditProvider: (providerId: String) -> Unit = {},
    onBack: () -> Unit
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf<String?>(null) }
    var editingPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    var editingMedia by remember { mutableStateOf<String?>(null) }
    val mediaConfig = settings.system[editingMedia] ?: SystemToolConfig()
    val mediaProvider = providers.find { it.id == mediaConfig.providerId }

    SlideScreenContent(
        editor = editingMedia?.let { name -> mediaProvider?.let { Triple(name, it, mediaConfig) } },
        label = stringResource(R.string.tool_settings_media_label)
    ) { page ->
    if (page != null) {
        ImageToolSettingsScreen(
            page.second,
            page.third,
            onSave = { options ->
                onSave(page.first, if (page.first == "generate_video") page.third.withVideoOptions(options) else page.third.withImageOptions(options))
                editingMedia = null
            },
            onBack = { editingMedia = null },
            video = page.first == "generate_video"
        )
    } else {
    BackHandler(enabled = LocalScreenTransitionActive.current, onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_default_and_tools)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        TabRow(selectedTabIndex = pager.currentPage) {
            Tab(
                selected = pager.currentPage == 0,
                onClick = { coroutineScope.launch { pager.animateScrollToPage(0) } },
                text = { Text(stringResource(R.string.settings_default_models)) }
            )
            Tab(
                selected = pager.currentPage == 1,
                onClick = { coroutineScope.launch { pager.animateScrollToPage(1) } },
                text = { Text(stringResource(R.string.settings_tools_tab)) }
            )
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
        if (page == 1) LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(stringResource(R.string.tool_settings_media_tools_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            items(listOf("generate_image", "generate_video")) { name ->
                val config = settings.system[name] ?: SystemToolConfig()
                val image = name == "generate_image"
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(if (image) R.string.tool_image_generation_title else R.string.tool_video_generation_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(if (image) R.string.tool_generate_image_description else R.string.tool_generate_video_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { onSave(name, config.copy(enabled = it)) }
                        )
                    }
                }
            }
            item {
                Text(stringResource(R.string.tool_settings_other_tools_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.tool_send_file_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.tool_send_file_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.sendFileEnabled,
                            onCheckedChange = onSetSendFile
                        )
                    }
                }
            }
        } else DefaultModelsScreen(
            providers = providers,
            settings = settings,
            onChooseModel = { choosing = it },
            onOpenMediaSettings = { editingMedia = it },
            onEditPrompt = { editingPrompt = it }
        )
        }
        }
    }

    editingPrompt?.let { name ->
        val config = settings.system[name] ?: SystemToolConfig()
        val defaultPrompt = if (name == "title_generation") DEFAULT_TITLE_GENERATION_PROMPT else DEFAULT_CHAT_SUMMARY_PROMPT
        var prompt by rememberSaveable(name) { mutableStateOf(config.prompt.ifBlank { defaultPrompt }) }
        AppBottomSheet(
            onDismissRequest = { editingPrompt = null }
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(if (name == "title_generation") R.string.tool_title_generation_title else R.string.tool_chat_summary_title),
                    style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.tool_customize_instructions_description),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectableOutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.tool_instruction_prompt)) },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { prompt = defaultPrompt }) { Text(stringResource(R.string.tool_reset)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { editingPrompt = null }) { Text(stringResource(R.string.common_cancel)) }
                    Button(onClick = {
                        onSave(name, config.copy(prompt = prompt.ifBlank { defaultPrompt }))
                        editingPrompt = null
                    }) { Text(stringResource(R.string.common_save)) }
                }
            }
        }
    }

    choosing?.let { name ->
        val type = when (name) {
            "generate_image" -> ModelType.IMAGE_GENERATION
            "generate_video" -> ModelType.VIDEO_GENERATION
            else -> ModelType.TEXT_GENERATION
        }
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
                config.modelType == type &&
                    provider.config.modelConfigs.containsKey(id) &&
                    provider.config.modelIds?.contains(id) != false &&
                    (type == ModelType.TEXT_GENERATION || provider.type.isOpenAi || provider.type == ModelApiType.GOOGLE)
            },
            onSelectModel = { providerId, modelId ->
                onSave(name, current.copy(providerId = providerId, modelId = modelId))
            },
            onEditProvider = onEditProvider,
            onDismiss = { choosing = null }
        )
    }
    }
    }
}
