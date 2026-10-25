package org.starfall.generative.features.speech.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.starfall.generative.R
import org.starfall.generative.shared.media.supportsSpeechProvider
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.IconPickerRow
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.icons.providerInitials
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.generative.shared.ui.text.ApiKeyMask
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

/** Confirms removal of a speech service. The caller clears the selection and deletes the saved service. */
@Composable
internal fun DeleteSpeechServiceDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.speech_delete_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.common_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/** Add or edit a speech service: name, icon, provider, model, voice and delivery options. */
@Composable
internal fun AddOrEditSpeechDialog(
    initialService: SpeechService,
    isNew: Boolean,
    providers: List<ModelProvider>,
    isPlaying: Boolean,
    onTest: (SpeechService, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (SpeechService) -> Unit
) {
    val context = LocalContext.current
    val ttsModelsByProvider = remember(providers) {
        providers.filter(::supportsSpeechProvider).associate { provider ->
            provider.id to provider.config.modelConfigs
                .filter { (id, config) -> config.modelType == ModelType.TEXT_TO_SPEECH && provider.config.modelIds?.contains(id) != false }
                .keys
                .toList()
        }.filterValues { it.isNotEmpty() }
    }

    var name by remember(initialService.id) { mutableStateOf(initialService.name) }
    var serviceIcon by remember(initialService.id) { mutableStateOf(initialService.icon) }
    var iconImporting by remember { mutableStateOf(false) }
    var providerId by remember(initialService.id) {
        mutableStateOf(
            initialService.provider
        )
    }
    var modelId by remember(initialService.id) {
        mutableStateOf(initialService.modelId)
    }
    var voice by remember(initialService.id) { mutableStateOf(initialService.voice) }
    var speed by remember(initialService.id) { mutableFloatStateOf(initialService.speed) }
    var pitch by remember(initialService.id) { mutableFloatStateOf(initialService.pitch) }
    var instructions by remember(initialService.id) { mutableStateOf(initialService.instructions) }
    var responseFormat by remember(initialService.id) { mutableStateOf(initialService.responseFormat) }
    var languageCode by remember(initialService.id) { mutableStateOf(initialService.languageCode) }
    var extraBodyText by remember(initialService.id) { mutableStateOf(initialService.extraBody.toString()) }
    var apiKey by remember(initialService.id) { mutableStateOf(initialService.apiKey) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    val availableModels = ttsModelsByProvider[providerId].orEmpty()
    val system = providerId.equals("system", true)
    val selectedProvider = providers.firstOrNull { it.id == providerId }
    val google = providers.find { it.id == providerId }?.type == ModelApiType.GOOGLE
    val providerBaseUrl = providers.find { it.id == providerId }?.baseUrl.orEmpty()
    val supportsInstructions = google || modelId !in listOf("tts-1", "tts-1-hd")
    val extraBody = remember(extraBodyText) { runCatching { Json.parseToJsonElement(extraBodyText.ifBlank { "{}" }) as? JsonObject }.getOrNull() }
    LaunchedEffect(providerId) {
        if (!providerId.equals("system", true) && modelId !in availableModels) {
            modelId = availableModels.firstOrNull()
        }
        if (providerId.equals("system", true)) modelId = null
    }
    LaunchedEffect(providerId, modelId) {
        if (!system && voice.equals("Default", true)) voice = if (google) "Kore" else "alloy"
        if (system && voice in listOf("alloy", "Kore")) voice = "Default"
    }

    fun currentService() = initialService.copy(
        name = name.trim(),
        // A provider icon is only the default. A saved service icon always wins.
        icon = serviceIcon,
        provider = providerId,
        modelId = modelId,
        voice = voice.trim(),
        speed = speed,
        pitch = pitch,
        instructions = if (supportsInstructions) instructions else "",
        responseFormat = responseFormat,
        languageCode = languageCode.trim(),
        apiKey = apiKey.trim(),
        extraBody = extraBody ?: JsonObject(emptyMap())
    )

    val canSave = name.isNotBlank() &&
        (system || (modelId in availableModels && extraBody != null))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.speech_add_service else R.string.speech_edit_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                SelectableOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.speech_service_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                IconPickerRow(
                    // Keep the provider icon visible while no service-specific icon
                    // is saved. Choosing or importing an icon replaces it; removing
                    // that icon falls back to the provider again.
                    image = serviceIcon,
                    onChange = { serviceIcon = it },
                    text = providerInitials(name),
                    fallback = Icons.Outlined.RecordVoiceOver,
                    onBusyChange = { iconImporting = it },
                    matchName = name,
                    faviconBaseUrl = providerBaseUrl,
                    defaultImage = selectedProvider?.icon
                )

                Box {
                    OutlinedButton(
                        onClick = { providerExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (providerId.equals("system", true)) {
                                stringResource(R.string.speech_system_tts)
                            } else {
                                providers.find { it.id == providerId }?.name ?: providerId
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FluentIcon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.speech_system_tts)) },
                            onClick = {
                                providerId = "system"
                                providerExpanded = false
                            }
                        )
                        providers.filter { it.id in ttsModelsByProvider }.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name) },
                                onClick = {
                                     providerId = provider.id
                                    voice = if (provider.type == ModelApiType.GOOGLE) "Kore" else "alloy"
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                if (!providerId.equals("system", true)) {
                    Box {
                        OutlinedButton(
                            onClick = { modelExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(modelId ?: stringResource(R.string.speech_select_tts_model), modifier = Modifier.weight(1f))
                            FluentIcon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            availableModels.forEach { id ->
                                val displayName = providers
                                    .find { it.id == providerId }
                                    ?.config
                                    ?.modelConfigs
                                    ?.get(id)
                                    ?.displayName
                                    ?.takeIf { it.isNotBlank() }
                                DropdownMenuItem(
                                    text = { Text(displayName ?: id) },
                                    onClick = {
                                        modelId = id
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else if (ttsModelsByProvider.isEmpty()) {
                    Text(
                        stringResource(R.string.speech_no_tts_models),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SelectableOutlinedTextField(
                    value = voice,
                    onValueChange = { voice = it },
                    label = {
                        Text(
                            if (providerId.equals("system", true)) {
                                stringResource(R.string.speech_voice)
                            } else {
                                 if (google) stringResource(R.string.speech_voice_gemini_hint) else stringResource(R.string.speech_voice_id_hint)
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (voice.isBlank()) Text(
                    stringResource(R.string.speech_voice_optional_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                if (!google) {
                    val speedRange = if (system) 0.5f..2.0f else 0.25f..4f
                    val speedSliderState = remember(speedRange) {
                        SliderState(value = speed, trackRange = speedRange)
                    }
                    speedSliderState.value = speed
                    Text(stringResource(R.string.speech_speed_value, String.format("%.1f", speed)))
                    Slider(
                        state = speedSliderState,
                        onValueChange = { speed = it }
                    )
                }

                if (!system) {
                    if (supportsInstructions) {
                        SelectableOutlinedTextField(
                            value = instructions, onValueChange = { instructions = it },
                            label = { Text(stringResource(R.string.speech_voice_instructions)) },
                            supportingText = { Text(stringResource(if (google) R.string.speech_voice_instructions_help_google else R.string.speech_voice_instructions_help, "")) },
                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
                        )
                    }
                    if (google) {
                        SelectableOutlinedTextField(
                            value = languageCode, onValueChange = { languageCode = it },
                            label = { Text(stringResource(R.string.speech_language_code)) },
                            placeholder = { Text("vi-VN") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.speech_gemini_audio_description), style = MaterialTheme.typography.bodySmall)
                    } else {
                        var formatExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { formatExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.speech_audio_format, responseFormat), Modifier.weight(1f))
                                FluentIcon(Icons.Default.KeyboardArrowDown, null)
                            }
                            DropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                                listOf("mp3", "wav", "aac", "flac", "opus", "pcm").forEach { format ->
                                    DropdownMenuItem(text = { Text(format) }, onClick = { responseFormat = format; formatExpanded = false })
                                }
                            }
                        }
                    }
                    SelectableOutlinedTextField(
                        value = extraBodyText, onValueChange = { extraBodyText = it },
                        label = { Text(stringResource(R.string.speech_additional_parameters)) },
                        isError = extraBody == null,
                         supportingText = { Text(stringResource(if (extraBody == null) R.string.speech_json_invalid_hint else R.string.speech_provider_parameters_help)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5
                    )
                    SelectableOutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.speech_api_key_override)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                         supportingText = { Text(stringResource(R.string.speech_api_key_help)) },
                        visualTransformation = ApiKeyMask, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (providerId.equals("system", true)) {
                    val pitchSliderState = remember {
                        SliderState(value = pitch, trackRange = 0.5f..2.0f)
                    }
                    pitchSliderState.value = pitch
                    Text(stringResource(R.string.speech_pitch_value, String.format("%.1f", pitch)))
                    Slider(
                        state = pitchSliderState,
                        onValueChange = { pitch = it }
                    )
                }

                OutlinedButton(
                    onClick = {
                        onTest(currentService(), context.getString(R.string.speech_test_announcement))
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FluentIcon(
                        if (isPlaying) Icons.Outlined.StopCircle else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (isPlaying) R.string.speech_stop else R.string.speech_test_voice))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(currentService()) },
                enabled = canSave && !iconImporting
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
