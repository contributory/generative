package org.starfall.generative.features.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import org.starfall.generative.R
import org.starfall.generative.shared.ui.model.LocalThinkingScreenBurst
import org.starfall.generative.shared.ui.model.reasoningEffortOptions
import org.starfall.generative.shared.ui.model.reasoningEffortIndex
import org.starfall.generative.shared.ui.model.reasoningEffortTier
import org.starfall.generative.shared.ui.model.ThinkingScreenBurstLayer
import org.starfall.generative.shared.ui.model.rememberThinkingColorBurst
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.defaultNativeModelTools
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.language.ThinkingPassback
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import org.starfall.generative.features.home.shared.ui.FeatureToolsSheetContent
import org.starfall.generative.features.home.shared.ui.InstructionsSheetContent
import org.starfall.generative.features.home.shared.ui.McpConnectorsSheetContent
import org.starfall.generative.features.home.shared.ui.QuickActionsSheetContent
import org.starfall.generative.features.home.shared.ui.SelectedModelOverviewContent
import org.starfall.generative.features.home.shared.ui.rememberPreviewTier
import org.starfall.generative.features.home.shared.ui.selectedModelSheetStyle
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.model.ModelPickerSheetContent
import org.starfall.generative.shared.ui.model.chatModelPickerFilter
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.sheets.LocalBottomSheetMaxContentHeight
import org.starfall.generative.shared.ui.sheets.SlideSheetContent

internal enum class QuickActionsPage {
    INSTRUCTIONS,
    FEATURE_TOOLS,
    MCP_CONNECTORS,
}

internal enum class ModelSheetPage {
    SELECTED_MODEL,
    MODEL_PICKER,
}

internal class BottomSheetsLinkState {
    /** The selected-model overview and the model picker are two pages of one sheet. */
    var modelSheetPage by mutableStateOf<ModelSheetPage?>(null)
        private set
    /** True when the picker was reached from the overview, so Back returns there instead of closing. */
    private var pickerOpenedFromOverview by mutableStateOf(false)
    val showModelPicker: Boolean get() = modelSheetPage == ModelSheetPage.MODEL_PICKER
    val showSelectedModelOverview: Boolean get() = modelSheetPage == ModelSheetPage.SELECTED_MODEL
    var showAddMenu by mutableStateOf(false)
        private set
    var quickActionsPage by mutableStateOf<QuickActionsPage?>(null)
        private set
    var showFilesSheet by mutableStateOf(false)
        private set
    var skillsMenuRequest by mutableStateOf(0)
        private set

    val showInstructions: Boolean
        get() = showAddMenu && quickActionsPage == QuickActionsPage.INSTRUCTIONS
    val showFeatureTools: Boolean
        get() = showAddMenu && quickActionsPage == QuickActionsPage.FEATURE_TOOLS
    val showMcpConnectors: Boolean
        get() = showAddMenu && quickActionsPage == QuickActionsPage.MCP_CONNECTORS

    fun openAddMenu() {
        quickActionsPage = null
        showAddMenu = true
    }
    fun openSkillsMenu() {
        dismissAddMenu()
        skillsMenuRequest++
    }
    fun openModelPicker() {
        pickerOpenedFromOverview = false
        modelSheetPage = ModelSheetPage.MODEL_PICKER
    }
    fun openSelectedModelOverview() { modelSheetPage = ModelSheetPage.SELECTED_MODEL }
    /** Moves the open sheet from the overview to the picker in place. */
    fun switchToModelPicker() {
        pickerOpenedFromOverview = true
        modelSheetPage = ModelSheetPage.MODEL_PICKER
    }
    fun backFromModelPicker() {
        if (pickerOpenedFromOverview) openSelectedModelOverview() else dismissModelSheet()
    }
    fun openInstructions() {
        quickActionsPage = QuickActionsPage.INSTRUCTIONS
        showAddMenu = true
    }
    fun openFeatureTools() {
        quickActionsPage = QuickActionsPage.FEATURE_TOOLS
        showAddMenu = true
    }
    fun openMcpConnectors() {
        quickActionsPage = QuickActionsPage.MCP_CONNECTORS
        showAddMenu = true
    }
    fun backToQuickActions() {
        quickActionsPage = null
    }
    fun openFilesUnavailableDialog() { showFilesSheet = true }
    fun dismissAddMenu() {
        showAddMenu = false
        quickActionsPage = null
    }
    fun dismissModelSheet() { modelSheetPage = null }
    fun dismissModelPicker() { if (showModelPicker) modelSheetPage = null }
    fun dismissSelectedModelOverview() { if (showSelectedModelOverview) modelSheetPage = null }
    fun dismissInstructions() {
        if (quickActionsPage == QuickActionsPage.INSTRUCTIONS) quickActionsPage = null
    }
    fun dismissFeatureTools() {
        if (quickActionsPage == QuickActionsPage.FEATURE_TOOLS) quickActionsPage = null
    }
    fun dismissMcpConnectors() {
        if (quickActionsPage == QuickActionsPage.MCP_CONNECTORS) quickActionsPage = null
    }
    fun dismissFilesUnavailableDialog() { showFilesSheet = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomSheetsLink(
    sheets: BottomSheetsLinkState,
    mediaKind: ModelType?,
    onMediaKindChange: (ModelType?) -> Unit,
    onCancelEdit: () -> Unit,
    providers: List<ModelProvider>,
    providerGroups: List<ProviderGroup>,
    selectedProviderId: String,
    selectedModelName: String,
    conversationReasoningEffort: String?,
    onSetReasoningEffort: (String?) -> Unit,
    onSelectModel: (providerId: String, modelId: String) -> Unit,
    onSetThinkingPassback: (providerId: String, modelId: String, mode: ThinkingPassback) -> Unit,
    onSetModelReasoningEffort: (providerId: String, modelId: String, effort: String?) -> Unit,
    onSetModelTools: (providerId: String, modelId: String, tools: List<String>) -> Unit,
    onEditModel: (providerId: String, modelId: String) -> Unit,
    onEditProvider: (providerId: String) -> Unit,
    modelPickerCollapsedGroups: Set<String>,
    modelPickerCollapsedProviders: Set<String>,
    onModelPickerCollapsedGroupsChange: (Set<String>) -> Unit,
    onModelPickerCollapsedProvidersChange: (Set<String>) -> Unit,
    modelPickerSelectedTab: String,
    onModelPickerSelectedTabChange: (String) -> Unit,
    modelPickerOpenRequest: Int,
    dynamicModelsMap: Map<String, List<String>>,
    mediaProviders: List<ModelProvider>,
    mediaProviderId: String,
    mediaModelId: String,
    onMediaModelChange: (providerId: String, modelId: String) -> Unit,
    attachments: List<String>,
    onAttachmentsChange: (List<String>) -> Unit,
    onOpenFeatureToolsSettings: () -> Unit = {},
    onOpenMcpConnectorsSettings: () -> Unit = {},
    /** Context window indicator shown above the model card; null hides it. */
    contextWindowContent: (@Composable (onDarkBackground: Boolean) -> Unit)? = null,
    /** Opens the conversation summarization sheet from the model sheet; null hides the Summarize button. */
    onOpenSummary: (() -> Unit)? = null
) {
    val context = LocalContext.current
    fun retainReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        uris.forEach(::retainReadPermission)
        onAttachmentsChange((attachments + uris.map(Uri::toString)).distinct())
    }
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val picked = buildList {
            data?.data?.let(::add)
            data?.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) add(clip.getItemAt(index).uri)
            }
        }.distinct()
        picked.forEach(::retainReadPermission)
        onAttachmentsChange((attachments + picked.map(Uri::toString)).distinct())
    }
    val selectedModelScreenBurst = rememberThinkingColorBurst()
    LaunchedEffect(modelPickerOpenRequest) {
        if (modelPickerOpenRequest > 0) sheets.openModelPicker()
    }
    LaunchedEffect(sheets.showSelectedModelOverview) {
        if (!sheets.showSelectedModelOverview) selectedModelScreenBurst.hide()
    }
    if (sheets.showAddMenu) {
        AppSheet(
            onDismissRequest = { sheets.dismissAddMenu() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            contentKey = sheets.quickActionsPage
        ) {
            SlideSheetContent(
                page = sheets.quickActionsPage,
                onBack = { sheets.backToQuickActions() },
                label = "Quick actions sheet"
            ) { page ->
                val pageActive = LocalScreenTransitionActive.current
                when (page) {
                    null -> QuickActionsSheetContent(
                        onPickImage = {
                            if (!pageActive) return@QuickActionsSheetContent
                            photoPicker.launch(PickVisualMediaRequest(
                                if (mediaKind == null || mediaKind == ModelType.VIDEO_GENERATION) ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                else ActivityResultContracts.PickVisualMedia.ImageOnly
                            ))
                        },
                        onPickDocument = {
                            if (!pageActive) return@QuickActionsSheetContent
                            documentPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = if (mediaKind == null || mediaKind == ModelType.VIDEO_GENERATION) "*/*" else "image/*"
                                if (mediaKind == ModelType.VIDEO_GENERATION) putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            })
                        },
                        onTakePhoto = {
                            if (pageActive) sheets.openFilesUnavailableDialog()
                        },
                        onCreateImage = {
                            if (pageActive) {
                                onCancelEdit()
                                onMediaKindChange(ModelType.IMAGE_GENERATION)
                            }
                        },
                        onCreateVideo = {
                            if (pageActive) {
                                onCancelEdit()
                                onMediaKindChange(ModelType.VIDEO_GENERATION)
                            }
                        },
                        onOpenInstructions = {
                            if (pageActive) sheets.openInstructions()
                        },
                        onOpenSkills = {
                            if (pageActive) sheets.openSkillsMenu()
                        },
                        onOpenFeatureTools = {
                            if (pageActive) sheets.openFeatureTools()
                        },
                        onOpenMcpConnectors = {
                            if (pageActive) sheets.openMcpConnectors()
                        },
                        onDismiss = {
                            if (pageActive) sheets.dismissAddMenu()
                        }
                    )
                    QuickActionsPage.INSTRUCTIONS -> InstructionsSheetContent(
                        onBack = { if (pageActive) sheets.backToQuickActions() },
                        onLeave = { if (pageActive) sheets.dismissAddMenu() }
                    )
                    QuickActionsPage.FEATURE_TOOLS -> FeatureToolsSheetContent(
                        onBack = { if (pageActive) sheets.backToQuickActions() },
                        onOpenSettings = {
                            if (pageActive) {
                                sheets.dismissAddMenu()
                                onOpenFeatureToolsSettings()
                            }
                        }
                    )
                    QuickActionsPage.MCP_CONNECTORS -> McpConnectorsSheetContent(
                        onBack = { if (pageActive) sheets.backToQuickActions() },
                        onOpenSettings = {
                            if (pageActive) {
                                sheets.dismissAddMenu()
                                onOpenMcpConnectorsSettings()
                            }
                        }
                    )
                }
            }
        }
    }

    val modelSheetPage = sheets.modelSheetPage
    if (modelSheetPage != null && (modelSheetPage == ModelSheetPage.MODEL_PICKER || mediaKind == null)) {
        val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
        val selectedConfigEntry = selectedProvider?.config?.modelConfigs?.entries?.firstOrNull { it.key == selectedModelName }
            ?: selectedProvider?.config?.modelConfigs?.entries?.firstOrNull {
                it.value.displayName.isNotBlank() && it.value.displayName == selectedModelName
            }
        val rawSelectedConfig = selectedConfigEntry?.value
            // Keep the same fallback as the model picker: discovered/custom models without
            // an explicit saved configuration still expose the reasoning control.
            ?: ModelConfiguration(displayName = selectedModelName.ifBlank { stringResource(R.string.select_model) })
        // Some provider catalogs omit native-tool metadata even for models that support the
        // provider's built-in tools. Keep explicit metadata authoritative, and use known native
        // presets only when the model has no tool list at all.
        val selectedConfig = if (rawSelectedConfig.supportedModelTools.isEmpty() && selectedProvider != null) {
            rawSelectedConfig.copy(modelTools = defaultNativeModelTools(selectedProvider.type, selectedModelName))
        } else rawSelectedConfig
        val selectedFolderName = selectedProvider?.groupId?.let { groupId ->
            providerGroups.firstOrNull { it.id == groupId }?.name
        }
        // A conversation override wins; otherwise show the level saved for this model.
        val effectiveReasoningEffort = conversationReasoningEffort ?: selectedConfig.reasoningEffort
        val previewTier = rememberPreviewTier(selectedConfig, effectiveReasoningEffort)
        val onPicker = modelSheetPage == ModelSheetPage.MODEL_PICKER
        val style = selectedModelSheetStyle(!onPicker && previewTier.intValue == 6 && selectedConfig.supportsThinking)
        val maxContentHeight = LocalBottomSheetMaxContentHeight
        CompositionLocalProvider(LocalThinkingScreenBurst provides selectedModelScreenBurst) {
            ThinkingScreenBurstLayer(selectedModelScreenBurst)
            // One sheet for both pages: switching slides the content and animates the height in place.
            AppSheet(
                onDismissRequest = {
                    selectedModelScreenBurst.hide()
                    sheets.dismissModelSheet()
                },
                containerColor = style.color,
                containerBrush = style.brush,
                navigationBarColor = style.navigationBarColor,
                // Read in the draw phase: the reveal animates every frame and must not recompose the sheet.
                containerBrushRevealOrigin = { selectedModelScreenBurst.origin.takeIf { it != Offset.Zero } },
                containerBrushRevealProgress = { selectedModelScreenBurst.progress.value },
                containerOverlay = style.overlay,
                contentKey = modelSheetPage
            ) {
                // The sheet is its own window, so the screen-owned star overlay must also be attached to it.
                ThinkingScreenBurstLayer(selectedModelScreenBurst)
                SlideSheetContent(
                    page = if (onPicker) ModelSheetPage.MODEL_PICKER else null,
                    onBack = { sheets.backFromModelPicker() },
                    label = "Model sheet"
                ) { target ->
                    val pageActive = LocalScreenTransitionActive.current
                    if (target == ModelSheetPage.MODEL_PICKER) {
                        ModelPickerSheetContent(
                            providers = if (mediaKind != null) mediaProviders else providers,
                            providerGroups = providerGroups,
                            collapsedGroupIdsState = modelPickerCollapsedGroups,
                            collapsedProviderIdsState = modelPickerCollapsedProviders,
                            onCollapsedGroupIdsChange = onModelPickerCollapsedGroupsChange,
                            onCollapsedProviderIdsChange = onModelPickerCollapsedProvidersChange,
                            selectedTabKeyState = modelPickerSelectedTab,
                            onSelectedTabKeyChange = onModelPickerSelectedTabChange,
                            selectedProviderId = if (mediaKind != null) mediaProviderId else selectedProviderId,
                            selectedModelId = if (mediaKind != null) mediaModelId else selectedModelName,
                            conversationReasoningEffort = null,
                            onSelectModel = { provider, model ->
                                if (mediaKind != null) { onMediaModelChange(provider, model) }
                                else onSelectModel(provider, model)
                            },
                            onSetReasoningEffort = {},
                            showReasoningEffort = false,
                            dynamicModelsMap = if (mediaKind != null) emptyMap() else dynamicModelsMap.toMap(),
                            modelFilter = if (mediaKind == null) chatModelPickerFilter else { _, _, config -> config.modelType == mediaKind },
                            onEditProvider = onEditProvider,
                            onOpenSelectedModel = if (mediaKind == null) {
                                { if (pageActive) sheets.openSelectedModelOverview() }
                            } else null,
                            // The picker keeps the tall size it had as its own sheet; the height animates
                            // from the overview's size because this page's size is what the sheet adopts.
                            modifier = Modifier.height(maxContentHeight.current * 0.9f),
                            onDismiss = { sheets.dismissModelSheet() }
                        )
                    } else {
                        SelectedModelOverviewContent(
                            provider = selectedProvider,
                            folderName = selectedFolderName,
                            modelId = selectedModelName.ifBlank { selectedConfig.displayName },
                            config = selectedConfig,
                            conversationReasoningEffort = effectiveReasoningEffort,
                            previewTier = previewTier.intValue,
                            onPreviewTierChange = { previewTier.intValue = it },
                            onSetReasoningEffort = { effort ->
                                onSetReasoningEffort(effort)
                                if (selectedProvider != null && selectedConfigEntry != null) {
                                    onSetModelReasoningEffort(selectedProvider.id, selectedConfigEntry.key, effort)
                                }
                            },
                            // Available as soon as the sheet opens; a model without a saved configuration gets one
                            // created when the choice is stored.
                            onSetModelTools = (selectedConfigEntry?.key ?: selectedModelName).takeIf {
                                selectedProvider != null && it.isNotBlank() && selectedConfig.supportedModelTools.isNotEmpty()
                            }?.let { configKey ->
                                val providerId = selectedProvider!!.id
                                val setTools: (List<String>) -> Unit = { tools -> onSetModelTools(providerId, configKey, tools) }
                                setTools
                            },
                            onSetThinkingPassback = (selectedConfigEntry?.key ?: selectedModelName).takeIf {
                                selectedProvider != null && it.isNotBlank()
                            }?.let { configKey ->
                                val providerId = selectedProvider!!.id
                                val setPassback: (ThinkingPassback) -> Unit = { mode -> onSetThinkingPassback(providerId, configKey, mode) }
                                setPassback
                            },
                            onEditModel = selectedProvider?.let { provider ->
                                val modelId = selectedConfigEntry?.key ?: selectedModelName
                                {
                                    sheets.dismissModelSheet()
                                    onEditModel(provider.id, modelId)
                                }
                            },
                            onOpenModelPicker = { if (pageActive) sheets.switchToModelPicker() },
                            contextWindowContent = contextWindowContent,
                            onSummarize = onOpenSummary?.let { openSummary ->
                                {
                                    if (pageActive) {
                                        sheets.dismissModelSheet()
                                        openSummary()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (sheets.showFilesSheet) {
        AlertDialog(
            onDismissRequest = { sheets.dismissFilesUnavailableDialog() },
            title = { Text(stringResource(R.string.camera_unavailable_title)) },
            text = { Text(stringResource(R.string.camera_unavailable_message)) },
            confirmButton = {
                TextButton(onClick = { sheets.dismissFilesUnavailableDialog() }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }
}
