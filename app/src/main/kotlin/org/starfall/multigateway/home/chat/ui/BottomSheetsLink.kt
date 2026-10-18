package org.starfall.multigateway.home.chat.ui

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
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.ui.model.LocalThinkingScreenBurst
import org.starfall.multigateway.shared.ui.model.reasoningEffortOptions
import org.starfall.multigateway.shared.ui.model.reasoningEffortIndex
import org.starfall.multigateway.shared.ui.model.reasoningEffortTier
import org.starfall.multigateway.shared.ui.model.ThinkingScreenBurstLayer
import org.starfall.multigateway.shared.ui.model.rememberThinkingColorBurst
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.language.ThinkingPassback
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.ProviderGroup
import org.starfall.multigateway.shared.ui.dialogs.AppAlertDialog as AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.starfall.multigateway.home.sheets.ui.QuickActionsBottomSheet
import org.starfall.multigateway.home.sheets.ui.RemoteMachinesBottomSheet
import org.starfall.multigateway.home.sheets.ui.SelectedModelOverviewBottomSheet
import org.starfall.multigateway.home.sheets.ui.ToolsManageBottomSheet
import org.starfall.multigateway.shared.ui.model.ModelPickerBottomSheet
import org.starfall.multigateway.shared.ui.model.chatModelPickerFilter

internal class BottomSheetsLinkState {
    var showModelPicker by mutableStateOf(false)
        private set
    var showSelectedModelOverview by mutableStateOf(false)
        private set
    var showQuickActions by mutableStateOf(false)
        private set
    var showRemoteMachines by mutableStateOf(false)
        private set
    var showAddMenu by mutableStateOf(false)
        private set
    var showFilesSheet by mutableStateOf(false)
        private set

    fun openAddMenu() { showAddMenu = true }
    fun openModelPicker() { showModelPicker = true }
    fun openSelectedModelOverview() { showSelectedModelOverview = true }
    fun openToolsManage() { showQuickActions = true }
    fun openRemoteMachines() { showRemoteMachines = true }
    fun openFilesUnavailableDialog() { showFilesSheet = true }
    fun dismissAddMenu() { showAddMenu = false }
    fun dismissModelPicker() { showModelPicker = false }
    fun dismissSelectedModelOverview() { showSelectedModelOverview = false }
    fun dismissToolsManage() { showQuickActions = false }
    fun dismissRemoteMachines() { showRemoteMachines = false }
    fun dismissFilesUnavailableDialog() { showFilesSheet = false }
}

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
    onAttachmentsChange: (List<String>) -> Unit
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
        QuickActionsBottomSheet(
            onPickImage = {
                photoPicker.launch(PickVisualMediaRequest(
                    if (mediaKind == null) ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    else ActivityResultContracts.PickVisualMedia.ImageOnly
                ))
            },
            onPickDocument = {
                documentPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (mediaKind == null) "*/*" else "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                })
            },
            onTakePhoto = { sheets.openFilesUnavailableDialog() },
            onCreateImage = { onCancelEdit(); onMediaKindChange(ModelType.IMAGE_GENERATION) },
            onCreateVideo = { onCancelEdit(); onMediaKindChange(ModelType.VIDEO_GENERATION) },
            onOpenTools = { sheets.openToolsManage() },
            onOpenRemoteMachines = { sheets.openRemoteMachines() },
            onDismiss = { sheets.dismissAddMenu() }
        )
    }

    if (sheets.showSelectedModelOverview && mediaKind == null) {
        val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
        val selectedConfigEntry = selectedProvider?.config?.modelConfigs?.entries?.firstOrNull { it.key == selectedModelName }
            ?: selectedProvider?.config?.modelConfigs?.entries?.firstOrNull {
                it.value.displayName.isNotBlank() && it.value.displayName == selectedModelName
            }
        val selectedConfig = selectedConfigEntry?.value
            // Keep the same fallback as the model picker: discovered/custom models without
            // an explicit saved configuration still expose the reasoning control.
            ?: ModelConfiguration(displayName = selectedModelName.ifBlank { stringResource(R.string.select_model) })
        val selectedFolderName = selectedProvider?.groupId?.let { groupId ->
            providerGroups.firstOrNull { it.id == groupId }?.name
        }
        CompositionLocalProvider(LocalThinkingScreenBurst provides selectedModelScreenBurst) {
            ThinkingScreenBurstLayer(selectedModelScreenBurst)
            SelectedModelOverviewBottomSheet(
                provider = selectedProvider,
                folderName = selectedFolderName,
                modelId = selectedModelName.ifBlank { selectedConfig.displayName },
                config = selectedConfig,
                // A conversation override wins; otherwise show the level saved for this model.
                conversationReasoningEffort = conversationReasoningEffort ?: selectedConfig.reasoningEffort,
                onSetReasoningEffort = { effort ->
                    onSetReasoningEffort(effort)
                    if (selectedProvider != null && selectedConfigEntry != null) {
                        onSetModelReasoningEffort(selectedProvider.id, selectedConfigEntry.key, effort)
                    }
                },
                // Available as soon as the sheet opens; a model without a saved configuration gets one
                // created when the choice is stored.
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
                        sheets.dismissSelectedModelOverview()
                        onEditModel(provider.id, modelId)
                    }
                },
                onOpenModelPicker = {
                    sheets.openModelPicker()
                },
                onDismiss = { sheets.dismissSelectedModelOverview() }
            )
        }
    }

    if (sheets.showModelPicker) {
        ModelPickerBottomSheet(
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
            onDismiss = { sheets.dismissModelPicker() }
        )
    }

    if (sheets.showQuickActions) {
        ToolsManageBottomSheet(onDismiss = { sheets.dismissToolsManage() })
    }

    if (sheets.showRemoteMachines) {
        RemoteMachinesBottomSheet(onDismiss = { sheets.dismissRemoteMachines() })
    }

    if (sheets.showFilesSheet) {
        AlertDialog(
            onDismissRequest = { sheets.dismissFilesUnavailableDialog() },
            title = { Text("Camera unavailable") },
            text = { Text("Camera capture is not connected yet. Use Photos or Files to attach existing media.") },
            confirmButton = {
                TextButton(onClick = { sheets.dismissFilesUnavailableDialog() }) { Text("OK") }
            }
        )
    }
}
