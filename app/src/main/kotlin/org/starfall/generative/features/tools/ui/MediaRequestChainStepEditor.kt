package org.starfall.generative.features.tools.ui

import org.starfall.generative.shared.ui.menus.AppDropdownMenu as DropdownMenu
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.starfall.generative.R
import org.starfall.generative.shared.model.media.MediaFileTransport
import org.starfall.generative.shared.model.media.MediaFinalResponse
import org.starfall.generative.shared.model.media.MediaRequestMode
import org.starfall.generative.shared.model.media.MediaRequestChain
import org.starfall.generative.shared.model.media.MediaRequestStep
import org.starfall.generative.shared.model.media.MediaResponseObservation
import org.starfall.generative.shared.model.media.MediaResponseObservationMode
import org.starfall.generative.shared.model.media.MediaResponseType
import org.starfall.generative.shared.model.media.MediaRetryPolicy
import org.starfall.generative.shared.model.media.MediaValueKind
import org.starfall.generative.shared.helpers.media.isValidMediaEndpointTemplate
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

private const val MAX_ATTEMPTS = 20
private const val MAX_DELAY_MILLIS = 300_000L
private const val MAX_WAIT_MILLIS = 3_600_000L
private val requestMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")

@Composable
internal fun MediaRequestStepEditor(
    stepNumber: Int,
    step: MediaRequestStep,
    onChange: (MediaRequestStep) -> Unit
) {
    SettingsCard {
        Text(stringResource(R.string.media_chain_editing_step, stepNumber), style = MaterialTheme.typography.titleMedium)
        MediaTemplateAutocompleteField(
            value = step.endpoint,
            onValueChange = { onChange(step.copy(endpoint = it)) },
            stepNumber = stepNumber,
            label = { Text(stringResource(R.string.media_chain_endpoint)) },
            supportingText = { Text(stringResource(R.string.media_chain_endpoint_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        MediaChoiceField(
            label = stringResource(R.string.media_chain_method),
            selected = step.method,
            options = requestMethods,
            optionLabel = { it },
            onSelect = { onChange(step.copy(method = it)) }
        )
        HeaderEditor(stepNumber, step.headers) { headers -> onChange(step.copy(headers = headers)) }
        MediaChoiceField(
            label = stringResource(R.string.media_chain_content_type),
            selected = modeLabel(step.mode),
            options = MediaRequestMode.entries,
            optionLabel = { mode -> modeLabel(mode) },
            onSelect = { onChange(step.copy(mode = it)) }
        )
        MediaChoiceField(
            label = stringResource(R.string.media_chain_file_transport),
            selected = fileTransportLabel(step.fileTransport),
            options = MediaFileTransport.entries,
            optionLabel = { transport -> fileTransportLabel(transport) },
            onSelect = { onChange(step.copy(fileTransport = it)) }
        )
        SelectableOutlinedTextField(
            value = step.contentType,
            onValueChange = { onChange(step.copy(contentType = it)) },
            label = { Text(stringResource(R.string.media_chain_custom_content_type)) },
            supportingText = { Text(stringResource(R.string.media_chain_custom_content_type_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        MediaTemplateAutocompleteField(
            value = step.body,
            onValueChange = { onChange(step.copy(body = it)) },
            stepNumber = stepNumber,
            label = { Text(stringResource(R.string.media_chain_body_editor)) },
            supportingText = { Text(stringResource(R.string.media_chain_request_body_help)) },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            minLines = 8,
            maxLines = 18,
            modifier = Modifier.fillMaxWidth()
        )
        ResponseObserverEditor(stepNumber, step.observation) { observation ->
            onChange(step.copy(observation = observation))
        }
    }
}

@Composable
private fun HeaderEditor(stepNumber: Int, headers: Map<String, String>, onChange: (Map<String, String>) -> Unit) {
    val prettyJson = remember { Json { prettyPrint = true } }
    var text by remember(stepNumber) { mutableStateOf(formatHeaders(prettyJson, headers)) }
    var invalid by remember(stepNumber) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.media_chain_headers), style = MaterialTheme.typography.titleSmall)
        MediaTemplateAutocompleteField(
            value = text,
            onValueChange = { value ->
                text = value
                parseHeaders(value)?.let {
                    invalid = false
                    onChange(it)
                } ?: run { invalid = true }
            },
            stepNumber = stepNumber,
            label = { Text(stringResource(R.string.media_chain_headers_editor)) },
            supportingText = {
                Text(stringResource(if (invalid) R.string.media_chain_headers_json_invalid else R.string.media_chain_headers_help))
            },
            isError = invalid,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatHeaders(json: Json, headers: Map<String, String>): String = json.encodeToString(
    JsonObject.serializer(),
    buildJsonObject { headers.forEach { (key, value) -> put(key, JsonPrimitive(value)) } }
)

private fun parseHeaders(value: String): Map<String, String>? = runCatching {
    Json.parseToJsonElement(value) as? JsonObject
}.getOrNull()?.let { objectValue ->
    val result = linkedMapOf<String, String>()
    objectValue.forEach { (key, child) ->
        val primitive = child as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        result[key] = primitive.content
    }
    result
}

@Composable
private fun ResponseObserverEditor(
    stepNumber: Int,
    observation: MediaResponseObservation,
    onChange: (MediaResponseObservation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.media_chain_observer), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(
                checked = observation.isEnabled,
                onCheckedChange = { onChange(observation.copy(enabled = it)) }
            )
        }
        if (observation.isEnabled) {
            SelectableOutlinedTextField(
                value = observation.statusPath,
                onValueChange = { onChange(observation.copy(statusPath = it)) },
                label = { Text(stringResource(R.string.media_chain_status_path)) },
                supportingText = { Text(stringResource(R.string.media_chain_status_path_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            MediaChoiceField(
                label = stringResource(R.string.media_chain_observer_mode),
                selected = observationModeLabel(observation.mode),
                options = MediaResponseObservationMode.entries,
                optionLabel = { mode -> observationModeLabel(mode) },
                onSelect = { onChange(observation.copy(mode = it)) }
            )
            if (observation.mode == MediaResponseObservationMode.VALUE) {
                ObservationValuesField(stepNumber, "pending", R.string.media_chain_pending_values, observation.pendingValues) {
                    onChange(observation.copy(pendingValues = it))
                }
            }
            RetryPolicyEditor(stepNumber, observation.retry) { onChange(observation.copy(retry = it)) }
        } else {
            Text(
                stringResource(R.string.media_chain_observer_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ObservationValuesField(
    stepNumber: Int,
    kind: String,
    label: Int,
    values: List<String>,
    onChange: (List<String>) -> Unit
) {
    var text by remember(stepNumber, kind) { mutableStateOf(values.joinToString(", ")) }
    SelectableOutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it.split(',').map(String::trim).filter(String::isNotBlank))
        },
        label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(R.string.media_chain_values_help)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RetryPolicyEditor(
    stepNumber: Int,
    retry: MediaRetryPolicy,
    onChange: (MediaRetryPolicy) -> Unit
) {
    var attempts by remember(stepNumber) { mutableStateOf(retry.maxAttempts.toString()) }
    var delay by remember(stepNumber) { mutableStateOf(retry.delayMillis.toString()) }
    var wait by remember(stepNumber) { mutableStateOf(retry.maxWaitMillis.toString()) }
    Text(stringResource(R.string.media_chain_retry_policy), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RetryNumberField(
            value = attempts,
            label = stringResource(R.string.media_chain_max_attempts),
            bounds = "1–$MAX_ATTEMPTS",
            modifier = Modifier.weight(1f),
            onValueChange = {
                attempts = boundedNumberText(it, 1, MAX_ATTEMPTS.toLong())
                onChange(retry.copy(maxAttempts = attempts.toIntOrNull() ?: 1))
            }
        )
        RetryNumberField(
            value = delay,
            label = stringResource(R.string.media_chain_delay_millis),
            bounds = "0–$MAX_DELAY_MILLIS",
            modifier = Modifier.weight(1f),
            onValueChange = {
                delay = boundedNumberText(it, 0, MAX_DELAY_MILLIS)
                onChange(retry.copy(delayMillis = delay.toLongOrNull() ?: 0L))
            }
        )
    }
    RetryNumberField(
        value = wait,
        label = stringResource(R.string.media_chain_max_wait_millis),
        bounds = "0–$MAX_WAIT_MILLIS",
        modifier = Modifier.fillMaxWidth(),
        onValueChange = {
            wait = boundedNumberText(it, 0, MAX_WAIT_MILLIS)
            onChange(retry.copy(maxWaitMillis = wait.toLongOrNull() ?: 0L))
        }
    )
}

@Composable
private fun RetryNumberField(
    value: String,
    label: String,
    bounds: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    SelectableOutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit)) },
        label = { Text(label) },
        supportingText = { Text(bounds) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

private fun boundedNumberText(value: String, minimum: Long, maximum: Long): String =
    if (value.isBlank()) "" else value.toLongOrNull()?.coerceIn(minimum, maximum)?.toString() ?: maximum.toString()

@Composable
internal fun MediaFinalResponseEditor(
    finalResponse: MediaFinalResponse,
    stepCount: Int,
    onChange: (MediaFinalResponse) -> Unit,
    music: Boolean = false
) {
    SettingsCard {
        Text(stringResource(R.string.media_chain_final_response), style = MaterialTheme.typography.titleMedium)
        val sourceSteps = listOf<Int?>(null) + (1..stepCount).toList()
        MediaChoiceField(
            label = stringResource(R.string.media_chain_final_source_step),
            selected = finalSourceStepLabel(finalResponse.sourceStep),
            options = sourceSteps,
            optionLabel = { step -> finalSourceStepLabel(step) },
            onSelect = { onChange(finalResponse.copy(sourceStep = it)) }
        )
        MediaChoiceField(
            label = stringResource(R.string.media_chain_response_type),
            selected = responseTypeLabel(finalResponse.type),
            options = if (music) listOf(MediaResponseType.JSON, MediaResponseType.AUDIO) else MediaResponseType.entries,
            optionLabel = { type -> responseTypeLabel(type) },
            onSelect = { type ->
                onChange(finalResponse.copy(
                    type = type,
                    sourceStep = finalResponse.sourceStep.takeIf { type == MediaResponseType.JSON }
                ))
            }
        )
        if (finalResponse.type == MediaResponseType.JSON) {
            SelectableOutlinedTextField(
                value = finalResponse.mediaPath,
                onValueChange = { onChange(finalResponse.copy(mediaPath = it)) },
                label = { Text(stringResource(R.string.media_chain_media_path)) },
                supportingText = { Text(stringResource(R.string.media_chain_media_path_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            MediaChoiceField(
                label = stringResource(R.string.media_chain_value_kind),
                selected = valueKindLabel(finalResponse.valueKind),
                options = MediaValueKind.entries,
                optionLabel = { kind -> valueKindLabel(kind) },
                onSelect = { onChange(finalResponse.copy(valueKind = it)) }
            )
        }
    }
}

@Composable
private fun <T> MediaChoiceField(
    label: String,
    selected: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                RoundedDropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun modeLabel(mode: MediaRequestMode) = stringResource(
    when (mode) {
        MediaRequestMode.JSON -> R.string.media_chain_content_type_json
        MediaRequestMode.FORM -> R.string.media_chain_content_type_form
        MediaRequestMode.RAW -> R.string.media_chain_content_type_raw
    }
)

@Composable
private fun fileTransportLabel(transport: MediaFileTransport) = stringResource(
    when (transport) {
        MediaFileTransport.FILE -> R.string.media_chain_file
        MediaFileTransport.BASE64 -> R.string.media_chain_base64
        MediaFileTransport.RAW -> R.string.common_raw
    }
)

@Composable
private fun responseTypeLabel(type: MediaResponseType) = stringResource(
    when (type) {
        MediaResponseType.JSON -> R.string.media_chain_json
        MediaResponseType.IMAGE -> R.string.common_image
        MediaResponseType.VIDEO -> R.string.common_video
        MediaResponseType.AUDIO -> R.string.music_audio
        MediaResponseType.OCTET_STREAM -> R.string.media_chain_octet_stream
    }
)

@Composable
private fun valueKindLabel(kind: MediaValueKind) = stringResource(
    if (kind == MediaValueKind.LINK) R.string.media_chain_link else R.string.media_chain_base64
)

@Composable
private fun finalSourceStepLabel(step: Int?): String = if (step == null) {
    stringResource(R.string.media_chain_last_step)
} else {
    stringResource(R.string.media_chain_step, step)
}

@Composable
private fun observationModeLabel(mode: MediaResponseObservationMode) = stringResource(
    if (mode == MediaResponseObservationMode.PATH_EXISTS) {
        R.string.media_chain_observer_path_exists
    } else {
        R.string.media_chain_observer_read_value
    }
)

internal fun chainValidationError(chain: MediaRequestChain): Int? {
    if (chain.steps.isEmpty()) return R.string.media_chain_one_step_required
    if (chain.steps.any { step ->
            !isValidMediaEndpointTemplate(step.endpoint) ||
                step.method !in requestMethods ||
                step.headers.keys.any { key -> key.trim().isBlank() || key.any { it <= ' ' || it == ':' || it.code >= 127 } } ||
                step.headers.values.any { value -> value.contains('\r') || value.contains('\n') } ||
                (step.mode != MediaRequestMode.RAW && runCatching { Json.parseToJsonElement(step.body.ifBlank { "{}" }) }.getOrNull() == null) ||
                (step.mode == MediaRequestMode.FORM && runCatching { Json.parseToJsonElement(step.body.ifBlank { "{}" }) }.getOrNull() !is JsonObject) ||
                (step.observation.isEnabled && step.observation.statusPath.isBlank()) ||
                (step.observation.isEnabled &&
                    step.observation.mode == MediaResponseObservationMode.VALUE &&
                    step.observation.pendingValues.isEmpty()) ||
                step.observation.retry.maxAttempts !in 1..MAX_ATTEMPTS ||
                step.observation.retry.delayMillis !in 0..MAX_DELAY_MILLIS ||
                step.observation.retry.maxWaitMillis !in 0..MAX_WAIT_MILLIS
         }) return R.string.media_chain_invalid_configuration
    if (chain.finalResponse.type != MediaResponseType.JSON && chain.steps.last().observation.isEnabled) {
        return R.string.media_chain_invalid_configuration
    }
    if (chain.finalResponse.sourceStep != null && chain.finalResponse.sourceStep !in 1..chain.steps.size) {
        return R.string.media_chain_invalid_configuration
    }
    if (chain.finalResponse.type != MediaResponseType.JSON &&
        chain.finalResponse.sourceStep != null &&
        chain.finalResponse.sourceStep != chain.steps.size
    ) {
        return R.string.media_chain_invalid_configuration
    }
    if (chain.finalResponse.type == MediaResponseType.JSON && chain.finalResponse.mediaPath.isBlank()) {
        return R.string.media_chain_media_path_required
    }
    return null
}
