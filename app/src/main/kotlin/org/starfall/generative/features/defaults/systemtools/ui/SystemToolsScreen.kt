package org.starfall.generative.features.defaults.systemtools.ui


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
import kotlinx.coroutines.launch
import org.starfall.generative.R
import androidx.compose.ui.res.stringResource
import org.starfall.generative.shared.model.media.MediaGenerationMode
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.model.DEFAULT_CHAT_SUMMARY_PROMPT
import org.starfall.generative.shared.model.DEFAULT_TITLE_GENERATION_PROMPT
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.model.systemMediaToolAvailable
import org.starfall.generative.shared.ui.sheets.AppBottomSheet
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField
import org.starfall.generative.shared.ui.layout.SlideScreenContent
import org.starfall.generative.shared.ui.model.ModelPickerBottomSheet
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem

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
    onSaveModelConfiguration: (String, String, ModelConfiguration) -> Unit = { _, _, _ -> },
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
    var editingMediaCustom by remember { mutableStateOf(false) }
    val mediaConfig = settings.system[editingMedia] ?: SystemToolConfig()
    val mediaProvider = providers.find { it.id == mediaConfig.providerId }

    fun openMediaSettings(name: String, custom: Boolean) {
        editingMedia = name
        editingMediaCustom = custom
    }

    SlideScreenContent(
        editor = editingMedia?.let { name -> mediaProvider?.let { Triple(name, it, mediaConfig) } },
        label = stringResource(R.string.tool_settings_media_label)
    ) { page ->
    if (page != null) {
        if (editingMediaCustom) {
            MediaRequestChainSettingsScreen(
                page.second,
                page.third,
                onSave = { profiles, activeId ->
                    val updatedConfig = if (page.first == "generate_video") {
                        page.third.withVideoRequestProfiles(profiles, activeId)
                    } else {
                        page.third.withImageRequestProfiles(profiles, activeId)
                    }
                    onSave(page.first, updatedConfig)
                    page.second.config.modelConfigs[page.third.modelId]?.let { model ->
                        onSaveModelConfiguration(
                            page.third.providerId,
                            page.third.modelId,
                            model.copy(mediaApiType = MediaGenerationMode.CUSTOM, mediaRequestProfileId = activeId)
                        )
                    }
                    editingMedia = null
                },
                onAutoSave = { profiles, activeId ->
                    val updatedConfig = if (page.first == "generate_video") {
                        page.third.withVideoRequestProfiles(profiles, activeId)
                    } else {
                        page.third.withImageRequestProfiles(profiles, activeId)
                    }
                    onSave(page.first, updatedConfig)
                    page.second.config.modelConfigs[page.third.modelId]?.let { model ->
                        onSaveModelConfiguration(
                            page.third.providerId,
                            page.third.modelId,
                            model.copy(mediaApiType = MediaGenerationMode.CUSTOM, mediaRequestProfileId = activeId)
                        )
                    }
                },
                onBack = { editingMedia = null },
                video = page.first == "generate_video"
            )
        } else {
            ImageToolSettingsScreen(
                page.second,
                page.third,
                onSave = { options, disabledParams ->
                    val updatedConfig = if (page.first == "generate_video") {
                        page.third.withVideoOptions(options).copy(
                                disabledParams = disabledParams,
                                videoGenerationMode = MediaGenerationMode.DEFAULT
                        )
                    } else {
                        page.third.withImageOptions(options).copy(
                                disabledParams = disabledParams,
                                imageGenerationMode = MediaGenerationMode.DEFAULT
                        )
                    }
                    onSave(page.first, updatedConfig)
                    page.second.config.modelConfigs[page.third.modelId]?.let { model ->
                        onSaveModelConfiguration(
                            page.third.providerId,
                            page.third.modelId,
                            model.copy(mediaApiType = MediaGenerationMode.DEFAULT, mediaRequestProfileId = "")
                        )
                    }
                    editingMedia = null
                },
                onBack = { editingMedia = null },
                video = page.first == "generate_video"
            )
        }
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
        PrimaryTabRow(selectedTabIndex = pager.currentPage) {
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
                    if (config.enabled) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                         val providerForMode = providers.find { it.id == config.providerId }
                         val modelConfig = providerForMode?.config?.modelConfigs?.get(config.modelId)
                         val generationMode = modelConfig?.mediaApiType
                             ?: if (name == "generate_image") config.imageGenerationMode else config.videoGenerationMode
                         val defaultModeAvailable = providerForMode?.type?.isOpenAi == true ||
                             providerForMode?.type == org.starfall.generative.shared.model.api.ModelApiType.GOOGLE
                        val customProfile = if (generationMode == MediaGenerationMode.CUSTOM) {
                            if (name == "generate_image") config.activeImageRequestProfile else config.activeVideoRequestProfile
                        } else null
                          var apiTypeExpanded by remember(name, config.providerId, config.modelId) { mutableStateOf(false) }
                          ExposedDropdownMenuBox(
                              expanded = apiTypeExpanded,
                              onExpandedChange = { apiTypeExpanded = !apiTypeExpanded }
                          ) {
                              SelectableOutlinedTextField(
                                  value = stringResource(if (generationMode == MediaGenerationMode.DEFAULT) R.string.media_chain_default else R.string.media_chain_custom),
                                  onValueChange = {},
                                  readOnly = true,
                                  label = { Text(stringResource(R.string.media_api_type)) },
                                  trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(apiTypeExpanded) },
                                  modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                              )
                              ExposedDropdownMenu(
                                  expanded = apiTypeExpanded,
                                  onDismissRequest = { apiTypeExpanded = false }
                              ) {
                                  listOf(MediaGenerationMode.DEFAULT, MediaGenerationMode.CUSTOM).forEach { mode ->
                                      DropdownMenuItem(
                                          text = { Text(stringResource(if (mode == MediaGenerationMode.DEFAULT) R.string.media_chain_default else R.string.media_chain_custom)) },
                                          onClick = {
                                              if (mode != MediaGenerationMode.DEFAULT || defaultModeAvailable) {
                                                  val updatedModel = (modelConfig ?: ModelConfiguration()).copy(
                                                      mediaApiType = mode,
                                                      mediaRequestProfileId = if (mode == MediaGenerationMode.DEFAULT) "" else modelConfig?.mediaRequestProfileId.orEmpty()
                                                  )
                                                  if (providerForMode != null && config.modelId.isNotBlank()) {
                                                      onSaveModelConfiguration(config.providerId, config.modelId, updatedModel)
                                                  }
                                                  onSave(
                                                      name,
                                                      if (name == "generate_image") config.withImageGenerationMode(mode)
                                                      else config.withVideoGenerationMode(mode)
                                                  )
                                              }
                                              apiTypeExpanded = false
                                          }
                                      )
                                  }
                              }
                          }
                          if (generationMode == MediaGenerationMode.CUSTOM) {
                            Text(
                                if (customProfile == null) stringResource(R.string.media_chain_custom_not_configured)
                                else stringResource(R.string.media_chain_custom_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (customProfile == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                              IconButton(onClick = { openMediaSettings(name, true) }) {
                                  FluentIcon(Icons.Outlined.Settings, stringResource(R.string.media_chain_edit_custom))
                              }
                          } else {
                             Text(
                                 stringResource(R.string.media_chain_default_settings_hint),
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                              IconButton(onClick = { if (defaultModeAvailable) openMediaSettings(name, false) }, enabled = defaultModeAvailable) {
                                  FluentIcon(Icons.Outlined.Settings, stringResource(R.string.media_chain_open_default_settings))
                              }
                         }
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
        } else DefaultModelsTab(
             providers = providers,
             settings = settings,
             onChooseModel = { choosing = it },
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
        ModelPickerBottomSheet(
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
    }
}
