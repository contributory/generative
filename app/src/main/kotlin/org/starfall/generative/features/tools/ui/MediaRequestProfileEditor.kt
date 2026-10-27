package org.starfall.generative.features.tools.ui

import org.starfall.generative.shared.ui.layout.scrollableScaffoldPadding
import org.starfall.generative.shared.ui.layout.edgeToEdgeTopAppBarColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.media.MediaGenerationMode
import org.starfall.generative.shared.model.media.defaultMediaProfile
import org.starfall.generative.shared.ui.ToolSwitch
import org.starfall.generative.shared.helpers.media.imageOptionFields
import org.starfall.generative.shared.helpers.media.videoOptionFields
import org.starfall.generative.shared.helpers.media.optionAt
import org.starfall.generative.shared.helpers.media.withOption
import org.starfall.generative.shared.helpers.media.validateImageOptions
import org.starfall.generative.shared.helpers.media.validateVideoOptions
import org.starfall.generative.shared.ui.menus.AppDropdownMenu
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem
import org.starfall.generative.R
import org.starfall.generative.shared.model.media.MediaFinalResponse
import org.starfall.generative.shared.model.media.MediaRequestProfile
import org.starfall.generative.shared.model.media.MediaRequestStep
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField
import org.starfall.generative.shared.ui.layout.nestedContainerBorder
import org.starfall.generative.shared.ui.layout.nestedContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaRequestProfileEditor(
    provider: ModelProvider,
    profile: MediaRequestProfile,
    video: Boolean,
    config: SystemToolConfig = SystemToolConfig(),
    onSave: (MediaRequestProfile) -> Unit,
    onAutoSave: (MediaRequestProfile) -> Unit = {},
    onBack: () -> Unit
) {
    var draft by remember(profile.id) { mutableStateOf(profile) }
    var modeMenu by remember { mutableStateOf(false) }
    var advancedOptions by remember { mutableStateOf(false) }
    var optionsJson by remember(profile.id) { mutableStateOf(profile.options.toString()) }
    val parsedOptions = remember(optionsJson) {
        runCatching { Json.parseToJsonElement(optionsJson) as? JsonObject }.getOrNull()
    }
    val optionsError = if (draft.mode == MediaGenerationMode.DEFAULT && parsedOptions != null) {
        runCatching {
            if (video) validateVideoOptions(provider, config.modelId, parsedOptions)
            else validateImageOptions(provider, config.modelId, parsedOptions)
        }.exceptionOrNull()?.message
    } else if (draft.mode == MediaGenerationMode.DEFAULT) stringResource(R.string.speech_json_invalid_hint) else null
    var editingStep by remember { mutableStateOf<Int?>(null) }
    var editingFinalResponse by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = draft != profile || (draft.mode == MediaGenerationMode.DEFAULT && parsedOptions != draft.options)

    fun leave() {
        if (dirty) confirmDiscard = true else onBack()
    }

    fun preparedDraft() = draft.copy(name = draft.name.trim(), options = parsedOptions ?: draft.options)
    fun autoSaveDraft() {
        onAutoSave(preparedDraft())
    }

    fun closeStepEditor() {
        autoSaveDraft()
        editingStep = null
    }

    fun removeStep(index: Int) {
        if (draft.chain.steps.size <= 1) return
        val updated = draft.copy(
            chain = draft.chain.copy(steps = draft.chain.steps.filterIndexed { stepIndex, _ -> stepIndex != index })
        )
        draft = updated
        onAutoSave(updated.copy(name = updated.name.trim()))
        editingStep = null
    }

    BackHandler(enabled = LocalScreenTransitionActive.current) { leave() }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = edgeToEdgeTopAppBarColors(),
                title = {
                    Text(stringResource(if (video) R.string.media_video_gen_profile else R.string.media_image_gen_profile))
                },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { modeMenu = true }, enabled = profile.id != defaultMediaProfile(video).id) {
                            Text(stringResource(if (draft.mode == MediaGenerationMode.DEFAULT) R.string.common_default else R.string.media_chain_custom))
                            FluentIcon(Icons.Default.ArrowDropDown, null)
                        }
                        AppDropdownMenu(modeMenu, { modeMenu = false }) {
                            MediaGenerationMode.entries.forEach { mode ->
                                RoundedDropdownMenuItem(
                                    text = { Text(stringResource(if (mode == MediaGenerationMode.DEFAULT) R.string.common_default else R.string.media_chain_custom)) },
                                    onClick = { draft = draft.copy(mode = mode); modeMenu = false }
                                )
                            }
                        }
                    }
                    IconButton(
                        enabled = draft.name.isNotBlank() &&
                            (draft.mode == MediaGenerationMode.DEFAULT && parsedOptions != null && optionsError == null ||
                                draft.mode == MediaGenerationMode.CUSTOM && chainValidationError(draft.chain) == null),
                        onClick = { onSave(preparedDraft()) }
                    ) {
                        FluentIcon(Icons.Outlined.Save, stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = scrollableScaffoldPadding(padding, horizontal = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SelectableOutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    provider.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (draft.mode == MediaGenerationMode.DEFAULT) {
                item {
                    Text(stringResource(R.string.image_tool_model_options_description), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    ToolSwitch(stringResource(R.string.image_tool_advanced_json), advancedOptions) { advancedOptions = it }
                }
                if (advancedOptions) {
                    item {
                        SelectableOutlinedTextField(
                            value = optionsJson, onValueChange = { if (it.length <= 65536) optionsJson = it },
                            label = { Text(stringResource(R.string.image_tool_additional_body)) },
                            isError = parsedOptions == null, minLines = 8, maxLines = 18,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (parsedOptions != null) {
                    items(if (video) videoOptionFields(provider, config.modelId) else imageOptionFields(provider, config.modelId), key = { it.path }) { field ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                ImageOptionInput(field, parsedOptions.optionAt(field.path)) { value ->
                                    val next = parsedOptions.withOption(field.path, value)
                                    optionsJson = next.toString()
                                }
                            }
                            Switch(
                                checked = field.path !in draft.disabledParams,
                                onCheckedChange = { enabled ->
                                    draft = draft.copy(disabledParams = if (enabled) draft.disabledParams - field.path else draft.disabledParams + field.path)
                                }
                            )
                        }
                    }
                }
                if (optionsError != null) item { Text(optionsError, color = MaterialTheme.colorScheme.error) }
            } else {
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.media_chain_steps), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.media_chain_steps_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            draft = draft.copy(chain = draft.chain.copy(steps = draft.chain.steps + MediaRequestStep()))
                            editingStep = draft.chain.steps.lastIndex
                        }) {
                            FluentIcon(Icons.Default.Add, stringResource(R.string.common_add))
                        }
                    }
                    draft.chain.steps.forEachIndexed { index, step ->
                        Surface(
                            onClick = { editingStep = index },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = nestedContainerColor(),
                            border = nestedContainerBorder()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.media_chain_step, index + 1))
                                    Text(
                                        "${step.method} ${step.endpoint.ifBlank { "/" }}",
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                FluentIcon(Icons.Outlined.Edit, stringResource(R.string.common_edit))
                            }
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.media_chain_final_response), style = MaterialTheme.typography.titleMedium)
                            Text(
                                finalResponseSummary(draft.chain.finalResponse),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editingFinalResponse = true }) {
                            FluentIcon(Icons.Outlined.Edit, stringResource(R.string.common_edit))
                        }
                    }
                }
            }
            }
        }
    }

    editingStep?.let { selectedIndex ->
        val index = selectedIndex.coerceIn(0, draft.chain.steps.lastIndex)
        AppSheet(onDismissRequest = ::closeStepEditor, fixedHeightFraction = 0.92f) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.media_chain_editing_step, index + 1),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        enabled = draft.chain.steps.size > 1,
                        onClick = { removeStep(index) }
                    ) {
                        FluentIcon(Icons.Outlined.Delete, stringResource(R.string.common_remove))
                    }
                    IconButton(onClick = ::closeStepEditor) {
                        FluentIcon(Icons.Outlined.Check, stringResource(R.string.common_done))
                    }
                }
                MediaRequestStepEditor(
                    stepNumber = index + 1,
                    step = draft.chain.steps[index],
                    onChange = { updated ->
                        draft = draft.copy(
                            chain = draft.chain.copy(
                                steps = draft.chain.steps.mapIndexed { stepIndex, current ->
                                    if (stepIndex == index) updated else current
                                }
                            )
                        )
                    }
                )
            }
        }
    }

    if (editingFinalResponse) {
        AppSheet(onDismissRequest = { editingFinalResponse = false }, fixedHeightFraction = 0.72f) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.media_chain_final_response),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { editingFinalResponse = false }) {
                        FluentIcon(Icons.Outlined.Check, stringResource(R.string.common_done))
                    }
                }
                MediaFinalResponseEditor(
                    finalResponse = draft.chain.finalResponse,
                    stepCount = draft.chain.steps.size,
                    onChange = { updated ->
                        draft = draft.copy(chain = draft.chain.copy(finalResponse = updated))
                    }
                )
            }
        }
    }

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.common_discard_changes)) },
        text = { Text(stringResource(R.string.media_chain_discard_message)) },
        confirmButton = {
            TextButton(onClick = { confirmDiscard = false; onBack() }) {
                Text(stringResource(R.string.common_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDiscard = false }) {
                Text(stringResource(R.string.common_keep_editing))
            }
        }
    )
}

@Composable
private fun finalResponseSummary(response: MediaFinalResponse): String = when (response.type) {
    org.starfall.generative.shared.model.media.MediaResponseType.JSON ->
        response.mediaPath.ifBlank { stringResource(R.string.media_chain_json_path_missing) }
    else -> response.type.name.lowercase().replace('_', '-')
}
