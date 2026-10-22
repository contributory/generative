package org.starfall.generative.features.tools.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.ToolSettings
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.media.MediaGenerationMode
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.model.systemMediaToolAvailable
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.ui.layout.SlideScreenContent
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.generative.shared.ui.model.DefaultModelSelector
import org.starfall.generative.shared.ui.model.ModelPickerSheet
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsEditor(
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
    var choosing by remember { mutableStateOf<String?>(null) }
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
                MediaRequestChainSettings(
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
                                model.copy(
                                    mediaApiType = MediaGenerationMode.CUSTOM,
                                    mediaRequestProfileId = activeId
                                )
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
                                model.copy(
                                    mediaApiType = MediaGenerationMode.CUSTOM,
                                    mediaRequestProfileId = activeId
                                )
                            )
                        }
                    },
                    onBack = { editingMedia = null },
                    video = page.first == "generate_video"
                )
            } else {
                ImageToolSettings(
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
                                model.copy(
                                    mediaApiType = MediaGenerationMode.DEFAULT,
                                    mediaRequestProfileId = ""
                                )
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
                        title = { Text(stringResource(R.string.settings_tools_editor)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                            }
                        }
                    )
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.tool_settings_media_tools_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(listOf("generate_image", "generate_video")) { name ->
                        val config = settings.system[name] ?: SystemToolConfig()
                        val provider = providers.find { it.id == config.providerId }
                        val model = provider?.config?.modelConfigs?.get(config.modelId)
                        val image = name == "generate_image"
                        val generationMode = model?.mediaApiType
                            ?: if (image) config.imageGenerationMode else config.videoGenerationMode
                        val available = systemMediaToolAvailable(name, config, providers, generationMode)
                        var apiTypeExpanded by remember(name, config.providerId, config.modelId) {
                            mutableStateOf(false)
                        }

                        SettingsCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(
                                            if (image) R.string.tool_image_generation_title
                                            else R.string.tool_video_generation_title
                                        ),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        stringResource(
                                            if (image) R.string.tool_generate_image_description
                                            else R.string.tool_generate_video_description
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = config.enabled,
                                    onCheckedChange = { onSave(name, config.copy(enabled = it)) }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            DefaultModelSelector(
                                provider = provider,
                                modelId = config.modelId,
                                model = model,
                                onClick = { choosing = name }
                            )
                            if (!available) {
                                Text(
                                    stringResource(R.string.tool_select_compatible_model_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            if (config.enabled) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                val defaultModeAvailable = provider?.type?.isOpenAi == true ||
                                    provider?.type == ModelApiType.GOOGLE
                                val customProfile = if (generationMode == MediaGenerationMode.CUSTOM) {
                                    if (image) config.activeImageRequestProfile else config.activeVideoRequestProfile
                                } else null
                                ExposedDropdownMenuBox(
                                    expanded = apiTypeExpanded,
                                    onExpandedChange = { apiTypeExpanded = !apiTypeExpanded }
                                ) {
                                    SelectableOutlinedTextField(
                                        value = stringResource(
                                            if (generationMode == MediaGenerationMode.DEFAULT) {
                                                R.string.media_chain_default
                                            } else {
                                                R.string.media_chain_custom
                                            }
                                        ),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.media_api_type)) },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(apiTypeExpanded)
                                        },
                                        modifier = Modifier.menuAnchor(
                                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                            enabled = true
                                        ).fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = apiTypeExpanded,
                                        onDismissRequest = { apiTypeExpanded = false }
                                    ) {
                                        listOf(
                                            MediaGenerationMode.DEFAULT,
                                            MediaGenerationMode.CUSTOM
                                        ).forEach { mode ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(
                                                            if (mode == MediaGenerationMode.DEFAULT) {
                                                                R.string.media_chain_default
                                                            } else {
                                                                R.string.media_chain_custom
                                                            }
                                                        )
                                                    )
                                                },
                                                onClick = {
                                                    if (mode != MediaGenerationMode.DEFAULT || defaultModeAvailable) {
                                                        val updatedModel = (model ?: ModelConfiguration()).copy(
                                                            mediaApiType = mode,
                                                            mediaRequestProfileId = if (mode == MediaGenerationMode.DEFAULT) {
                                                                ""
                                                            } else {
                                                                model?.mediaRequestProfileId.orEmpty()
                                                            }
                                                        )
                                                        if (provider != null && config.modelId.isNotBlank()) {
                                                            onSaveModelConfiguration(
                                                                config.providerId,
                                                                config.modelId,
                                                                updatedModel
                                                            )
                                                        }
                                                        onSave(
                                                            name,
                                                            if (image) config.withImageGenerationMode(mode)
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
                                        if (customProfile == null) {
                                            stringResource(R.string.media_chain_custom_not_configured)
                                        } else {
                                            stringResource(R.string.media_chain_custom_summary)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (customProfile == null) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    IconButton(onClick = { openMediaSettings(name, true) }) {
                                        FluentIcon(
                                            Icons.Outlined.Settings,
                                            stringResource(R.string.media_chain_edit_custom)
                                        )
                                    }
                                } else {
                                    Text(
                                        stringResource(R.string.media_chain_default_settings_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { if (defaultModeAvailable) openMediaSettings(name, false) },
                                        enabled = defaultModeAvailable
                                    ) {
                                        FluentIcon(
                                            Icons.Outlined.Settings,
                                            stringResource(R.string.media_chain_open_default_settings)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.tool_settings_other_tools_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    item {
                        SettingsCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.tool_send_file_title),
                                        style = MaterialTheme.typography.titleMedium
                                    )
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
