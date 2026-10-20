package org.starfall.generative.features.defaults.systemtools.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
import org.starfall.generative.shared.ui.sheets.AppBottomSheet
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaRequestProfileEditorScreen(
    provider: ModelProvider,
    profile: MediaRequestProfile,
    video: Boolean,
    onSave: (MediaRequestProfile) -> Unit,
    onAutoSave: (MediaRequestProfile) -> Unit = {},
    onBack: () -> Unit
) {
    var draft by remember(profile.id) { mutableStateOf(profile) }
    var editingStep by remember { mutableStateOf<Int?>(null) }
    var editingFinalResponse by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = draft != profile

    fun leave() {
        if (dirty) confirmDiscard = true else onBack()
    }

    fun autoSaveDraft() {
        onAutoSave(draft.copy(name = draft.name.trim()))
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
                title = {
                    Text(stringResource(if (video) R.string.tool_video_settings else R.string.tool_image_settings))
                },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = draft.name.isNotBlank() && chainValidationError(draft.chain) == null,
                        onClick = { onSave(draft.copy(name = draft.name.trim())) },
                    ) {
                        FluentIcon(Icons.Outlined.Save, stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SelectableOutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.media_chain_profile_name)) },
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
                            FluentIcon(Icons.Default.Add, stringResource(R.string.media_chain_add_step))
                        }
                    }
                    draft.chain.steps.forEachIndexed { index, step ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { editingStep = index },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
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
                                FluentIcon(Icons.Outlined.Edit, stringResource(R.string.media_chain_edit_step))
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
                            FluentIcon(Icons.Outlined.Edit, stringResource(R.string.media_chain_edit_final_response))
                        }
                    }
                }
            }
        }
    }

    editingStep?.let { selectedIndex ->
        val index = selectedIndex.coerceIn(0, draft.chain.steps.lastIndex)
        AppBottomSheet(onDismissRequest = ::closeStepEditor, fixedHeightFraction = 0.92f) {
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
                        FluentIcon(Icons.Outlined.Delete, stringResource(R.string.media_chain_remove_step))
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
        AppBottomSheet(onDismissRequest = { editingFinalResponse = false }, fixedHeightFraction = 0.72f) {
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
        title = { Text(stringResource(R.string.media_chain_discard_title)) },
        text = { Text(stringResource(R.string.media_chain_discard_message)) },
        confirmButton = {
            TextButton(onClick = { confirmDiscard = false; onBack() }) {
                Text(stringResource(R.string.common_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDiscard = false }) {
                Text(stringResource(R.string.media_chain_keep_editing))
            }
        }
    )
}

private fun finalResponseSummary(response: MediaFinalResponse): String = when (response.type) {
    org.starfall.generative.shared.model.media.MediaResponseType.JSON ->
        response.mediaPath.ifBlank { "JSON path not configured" }
    else -> response.type.name.lowercase().replace('_', '-')
}
