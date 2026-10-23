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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
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
        title = { Text("Delete Speech Service") },
        text = { Text("Are you sure you want to remove this TTS configuration?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
        title = { Text(if (isNew) "Add Speech Service" else "Edit Speech Service") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                SelectableOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Service name") },
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
                                "Android System TTS"
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
                            text = { Text("Android System TTS") },
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
                            Text(modelId ?: "Select TTS model", modifier = Modifier.weight(1f))
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
                        "No Provider models are marked as Text to speech yet. Configure a model type in Providers to use it here.",
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
                                "Voice"
                            } else {
                                 if (google) "Gemini voice (for example: Kore)" else "Voice ID (for example: alloy)"
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
                Text("Speed: ${String.format("%.1f", speed)}x")
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = if (system) 0.5f..2.0f else 0.25f..4f
                )
                }

                if (!system) {
                    if (supportsInstructions) {
                        SelectableOutlinedTextField(
                            value = instructions, onValueChange = { instructions = it },
                            label = { Text("Voice instructions") },
                            supportingText = { Text(stringResource(R.string.speech_voice_instructions_help, if (google) " and speaking speed" else "")) },
                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
                        )
                    }
                    if (google) {
                        SelectableOutlinedTextField(
                            value = languageCode, onValueChange = { languageCode = it },
                            label = { Text("Language code (optional)") },
                            placeholder = { Text("vi-VN") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.speech_gemini_audio_description), style = MaterialTheme.typography.bodySmall)
                    } else {
                        var formatExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { formatExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Audio format: $responseFormat", Modifier.weight(1f))
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
                        label = { Text("Additional API parameters (JSON)") },
                        isError = extraBody == null,
                         supportingText = { Text(stringResource(if (extraBody == null) R.string.speech_json_invalid_hint else R.string.speech_provider_parameters_help)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5
                    )
                    SelectableOutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        label = { Text("API key override (optional)") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                         supportingText = { Text(stringResource(R.string.speech_api_key_help)) },
                        visualTransformation = ApiKeyMask, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (providerId.equals("system", true)) {
                    Text("Pitch: ${String.format("%.1f", pitch)}x")
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it },
                        valueRange = 0.5f..2.0f
                    )
                }

                OutlinedButton(
                    onClick = {
                        onTest(currentService(), "Testing speech voice output.")
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
