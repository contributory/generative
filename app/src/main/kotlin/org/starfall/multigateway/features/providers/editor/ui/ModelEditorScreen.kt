package org.starfall.multigateway.features.providers.editor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.R
import org.starfall.multigateway.features.providers.shared.ui.ModelConfigDialog
import org.starfall.multigateway.features.providers.shared.ui.ModelToolsDialog
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.ModelType
import org.starfall.multigateway.shared.model.language.SELECTABLE_REASONING_EFFORTS
import org.starfall.multigateway.shared.model.language.ThinkingPassback
import org.starfall.multigateway.shared.model.language.passbackMode
import org.starfall.multigateway.shared.model.language.supportedReasoningEfforts
import org.starfall.multigateway.shared.model.language.withThinkingPassback
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.storage.preferences.ModelConfigurationMemory
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.IconPickerRow
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.multigateway.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.multigateway.shared.ui.text.SelectableOutlinedTextField
import org.starfall.multigateway.shared.ui.code.CodeBlockSurface
import org.starfall.multigateway.shared.ui.model.modelInitial

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEditorScreen(
    provider: ModelProvider,
    initialModelId: String,
    existingModelIds: Set<String>,
    onSave: (String, ModelConfiguration) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val modelMemory = remember(context) { ModelConfigurationMemory(context) }
    var modelId by remember { mutableStateOf(initialModelId) }
    var config by remember { mutableStateOf(provider.config.modelConfigs[initialModelId] ?: ModelConfiguration()) }
    var contextWindowText by remember { mutableStateOf(config.contextWindowTokens.toString()) }
    val contextWindow = contextWindowText.toIntOrNull()?.takeIf { it > 0 }
    var iconImporting by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var showSampling by remember { mutableStateOf(false) }
    var showModelTools by remember { mutableStateOf(false) }
    var modelJsonExpanded by remember { mutableStateOf(false) }
    val trimmedId = modelId.trim()
    val modelJson = if (initialModelId.isNotBlank() && trimmedId == initialModelId) config.modelJson else JsonObject(emptyMap())
    val prettyJson = remember { Json { prettyPrint = true } }
    val modelJsonCode = remember(modelJson) { prettyJson.encodeToString(JsonObject.serializer(), modelJson) }
    LaunchedEffect(trimmedId) {
        if (initialModelId.isBlank()) modelMemory.get(trimmedId)?.let { remembered ->
            config = remembered.copy(displayName = config.displayName, icon = config.icon)
            contextWindowText = remembered.contextWindowTokens.toString()
        }
    }
    val idRequired = stringResource(R.string.model_id_required)
    val idWhitespace = stringResource(R.string.model_id_no_whitespace)
    val idDuplicate = stringResource(R.string.model_id_duplicate)
    val idApiHint = stringResource(R.string.model_id_api_hint)
    val idError = when {
        trimmedId.isBlank() -> idRequired
        trimmedId.any { it.isWhitespace() } -> idWhitespace
        trimmedId != initialModelId && trimmedId in existingModelIds -> idDuplicate
        else -> null
    }

    fun saveAndBack() {
        if (idError == null && contextWindow != null) {
            modelMemory.remember(trimmedId, config.copy(contextWindowTokens = contextWindow))
            onSave(
                trimmedId,
                config.copy(
                    contextWindowTokens = contextWindow,
                    modelJson = modelJson,
                    displayName = config.displayName.trim(),
                    reasoningEffort = config.reasoningEffort?.trim()?.ifEmpty { null },
                    reasoningEfforts = config.supportedReasoningEfforts
                )
            )
            onBack()
        }
    }

    BackHandler(enabled = LocalScreenTransitionActive.current, onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (initialModelId.isBlank()) R.string.add_model_title else R.string.edit_model_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = ::saveAndBack, enabled = idError == null && contextWindow != null && !iconImporting) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconPickerRow(
                config.icon,
                onChange = { config = config.copy(icon = it) },
                text = modelInitial(modelId),
                onBusyChange = { iconImporting = it },
                matchName = config.displayName.ifBlank { modelId },
                model = true,
                faviconBaseUrl = provider.baseUrl
            )
            SelectableOutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text(stringResource(R.string.model_id)) },
                supportingText = { Text(idError ?: idApiHint) },
                isError = idError != null,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            SelectableOutlinedTextField(
                value = config.displayName,
                onValueChange = { config = config.copy(displayName = it) },
                label = { Text(stringResource(R.string.display_name)) },
                supportingText = { Text(stringResource(R.string.model_display_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                SelectableOutlinedTextField(
                    value = config.modelType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.model_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    ModelType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                config = config.copy(modelType = type)
                                typeExpanded = false
                            }
                        )
                    }
                }
            }
            if (config.modelType == ModelType.TEXT_GENERATION) {
                SelectableOutlinedTextField(
                    value = contextWindowText,
                    onValueChange = { if (it.all(Char::isDigit)) contextWindowText = it },
                    label = { Text(stringResource(R.string.context_window)) },
                    supportingText = if (contextWindow == null) ({ Text(stringResource(R.string.context_window_invalid)) }) else null,
                    isError = contextWindow == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                Text(stringResource(R.string.capabilities), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.input_media), style = MaterialTheme.typography.bodyMedium)
                MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        checked = config.supportsVision,
                        onCheckedChange = { config = config.copy(supportsVision = it) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = { FluentIcon(Icons.Outlined.Image, contentDescription = null) },
                        label = { Text(stringResource(R.string.common_image)) }
                    )
                    SegmentedButton(
                        checked = config.supportsVideoInput,
                        onCheckedChange = { config = config.copy(supportsVideoInput = it) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = { FluentIcon(Icons.Outlined.Videocam, contentDescription = null) },
                        label = { Text(stringResource(R.string.common_video)) }
                    )
                    SegmentedButton(
                        checked = config.supportsAudioInput,
                        onCheckedChange = { config = config.copy(supportsAudioInput = it) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = { FluentIcon(Icons.Outlined.Audiotrack, contentDescription = null) },
                        label = { Text(stringResource(R.string.common_audio)) }
                    )
                }
                ModelCapabilitySwitch(stringResource(R.string.model_thinking), config.supportsThinking) {
                    config = config.copy(supportsThinking = it)
                }
                if (config.supportsThinking) {
                    // Which levels exist is marked here; the one in use is chosen in the chat's selected-model sheet.
                    Text(stringResource(R.string.model_reasoning_levels), style = MaterialTheme.typography.titleSmall)
                    val markedEfforts = config.supportedReasoningEfforts
                    MultiChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("reasoning-levels")) {
                        SELECTABLE_REASONING_EFFORTS.forEachIndexed { index, level ->
                            SegmentedButton(
                                checked = level in markedEfforts,
                                onCheckedChange = { checked ->
                                    val next = if (checked) markedEfforts + level else markedEfforts - level
                                    config = config.copy(
                                        reasoningEfforts = SELECTABLE_REASONING_EFFORTS.filter { it in next }
                                    )
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, SELECTABLE_REASONING_EFFORTS.size),
                                icon = {},
                                modifier = Modifier.testTag("reasoning-level-$level")
                            ) {
                                Text(
                                    when (level) {
                                        "minimal" -> "Min"
                                        "medium" -> "Med"
                                        "xhigh" -> "XHigh"
                                        else -> level.replaceFirstChar { it.uppercase() }
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    Text(stringResource(R.string.send_thinking_content_back), style = MaterialTheme.typography.titleSmall)
                    val passbackModes = listOf(ThinkingPassback.AUTO, ThinkingPassback.ON, ThinkingPassback.OFF)
                    val passbackLabels = listOf(
                        stringResource(R.string.common_auto),
                        stringResource(R.string.common_on),
                        stringResource(R.string.common_off)
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("passback-thinking-mode")) {
                        passbackModes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = config.passbackMode == mode,
                                onClick = { config = config.withThinkingPassback(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, passbackModes.size),
                                modifier = Modifier.testTag("passback-thinking-${mode.name.lowercase()}")
                            ) {
                                Text(passbackLabels[index], maxLines = 1)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.send_thinking_content_back_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ModelCapabilitySwitch(stringResource(R.string.tool_calls), config.supportsToolCalls) {
                    config = config.copy(supportsToolCalls = it)
                }
                HorizontalDivider()
                Text(stringResource(R.string.streaming), style = MaterialTheme.typography.titleMedium)
                val streamOptions = listOf(null, true, false)
                val streamLabels = listOf(
                    stringResource(R.string.common_default),
                    stringResource(R.string.common_on),
                    stringResource(R.string.common_off)
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("streaming-mode")) {
                    streamOptions.forEachIndexed { index, value ->
                        SegmentedButton(
                            selected = config.supportStream == value,
                            onClick = { config = config.copy(supportStream = value) },
                            shape = SegmentedButtonDefaults.itemShape(index, streamOptions.size)
                        ) {
                            Text(streamLabels[index])
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showModelTools = true },
                        modifier = Modifier.weight(1f).testTag("model-tools-button")
                    ) {
                        Text(stringResource(R.string.model_tools_title), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showSampling = true },
                        modifier = Modifier.weight(1f).testTag("model-arguments-button")
                    ) {
                        Text(stringResource(R.string.model_arguments), maxLines = 1)
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().testTag("model-json-toggle")
                            .clickable { modelJsonExpanded = !modelJsonExpanded }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FluentIcon(
                            if (modelJsonExpanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.model_json), style = MaterialTheme.typography.titleMedium)
                    }
                    if (modelJsonExpanded) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp).testTag("model-json-code")) {
                            CodeBlockSurface(language = "json", code = modelJsonCode)
                        }
                    }
                }
            }
        }
    }
    if (showModelTools) {
        ModelToolsDialog(
            provider = provider,
            modelId = initialModelId.ifBlank { trimmedId },
            config = config,
            onSave = { config = it },
            onDismiss = { showModelTools = false }
        )
    }
    if (showSampling) {
        ModelConfigDialog(
            provider = provider.copy(
                config = provider.config.copy(
                    modelConfigs = provider.config.modelConfigs + (initialModelId to config)
                )
            ),
            modelId = initialModelId,
            onSave = { config = it },
            onDismiss = { showSampling = false }
        )
    }
}

@Composable
private fun ModelCapabilitySwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
