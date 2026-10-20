package org.starfall.multigateway.features.defaults.systemtools.ui

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
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.model.media.MediaFileTransport
import org.starfall.multigateway.shared.model.media.MediaFinalResponse
import org.starfall.multigateway.shared.model.media.MediaRequestMode
import org.starfall.multigateway.shared.model.media.MediaRequestChain
import org.starfall.multigateway.shared.model.media.MediaRequestStep
import org.starfall.multigateway.shared.model.media.MediaResponseObservation
import org.starfall.multigateway.shared.model.media.MediaResponseObservationMode
import org.starfall.multigateway.shared.model.media.MediaResponseType
import org.starfall.multigateway.shared.model.media.MediaRetryPolicy
import org.starfall.multigateway.shared.model.media.MediaValueKind
import org.starfall.multigateway.shared.helpers.media.isValidMediaEndpointTemplate
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.layout.SettingsCard
import org.starfall.multigateway.shared.ui.menus.RoundedDropdownMenuItem
import org.starfall.multigateway.shared.ui.text.SelectableOutlinedTextField

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
        Text(
            stringResource(R.string.media_chain_headers_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MediaChoiceField(
            label = stringResource(R.string.media_chain_request_mode),
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
        MediaTemplateAutocompleteField(
            value = step.body,
            onValueChange = { onChange(step.copy(body = it)) },
            stepNumber = stepNumber,
            label = { Text(stringResource(R.string.media_chain_request_body)) },
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.media_chain_headers), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { onChange(headers + ("" to "")) }) {
                FluentIcon(Icons.Default.Add, stringResource(R.string.add_header))
            }
        }
        headers.entries.toList().forEachIndexed { index, header ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaTemplateAutocompleteField(
                    value = header.key,
                    onValueChange = { value -> onChange(updateHeader(headers, index, value, header.value)) },
                    stepNumber = stepNumber,
                    label = { Text(stringResource(R.string.common_key)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                MediaTemplateAutocompleteField(
                    value = header.value,
                    onValueChange = { value -> onChange(updateHeader(headers, index, header.key, value)) },
                    stepNumber = stepNumber,
                    label = { Text(stringResource(R.string.common_value)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onChange(headers.entries.filterIndexed { headerIndex, _ -> headerIndex != index }.associate { it.key to it.value })
                }) {
                    FluentIcon(Icons.Outlined.Delete, stringResource(R.string.remove_header))
                }
            }
        }
        if (headers.isEmpty()) Text(
            stringResource(R.string.media_chain_no_headers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun updateHeader(headers: Map<String, String>, index: Int, key: String, value: String): Map<String, String> =
    headers.entries.mapIndexed { entryIndex, entry ->
        if (entryIndex == index) key to value else entry.key to entry.value
    }.associate { it.first to it.second }

@Composable
private fun ResponseObserverEditor(
    stepNumber: Int,
    observation: MediaResponseObservation,
    onChange: (MediaResponseObservation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.media_chain_observer), style = MaterialTheme.typography.titleSmall)
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
    onChange: (MediaFinalResponse) -> Unit
) {
    SettingsCard {
        Text(stringResource(R.string.media_chain_final_response), style = MaterialTheme.typography.titleMedium)
        MediaChoiceField(
            label = stringResource(R.string.media_chain_response_type),
            selected = responseTypeLabel(finalResponse.type),
            options = MediaResponseType.entries,
            optionLabel = { type -> responseTypeLabel(type) },
            onSelect = { onChange(finalResponse.copy(type = it)) }
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
    if (mode == MediaRequestMode.FORM) R.string.media_chain_form else R.string.media_chain_json
)

@Composable
private fun fileTransportLabel(transport: MediaFileTransport) = stringResource(
    if (transport == MediaFileTransport.FILE) R.string.media_chain_file else R.string.media_chain_base64
)

@Composable
private fun responseTypeLabel(type: MediaResponseType) = stringResource(
    when (type) {
        MediaResponseType.JSON -> R.string.media_chain_json
        MediaResponseType.IMAGE -> R.string.common_image
        MediaResponseType.VIDEO -> R.string.common_video
        MediaResponseType.OCTET_STREAM -> R.string.media_chain_octet_stream
    }
)

@Composable
private fun valueKindLabel(kind: MediaValueKind) = stringResource(
    if (kind == MediaValueKind.LINK) R.string.media_chain_link else R.string.media_chain_base64
)

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
                runCatching { Json.parseToJsonElement(step.body.ifBlank { "{}" }) }.getOrNull() == null ||
                (step.mode == MediaRequestMode.FORM && runCatching { Json.parseToJsonElement(step.body.ifBlank { "{}" }) }.getOrNull() !is JsonObject) ||
                (step.observation.statusPath.isNotBlank() &&
                    step.observation.mode == MediaResponseObservationMode.VALUE &&
                    step.observation.pendingValues.isEmpty()) ||
                step.observation.retry.maxAttempts !in 1..MAX_ATTEMPTS ||
                step.observation.retry.delayMillis !in 0..MAX_DELAY_MILLIS ||
                step.observation.retry.maxWaitMillis !in 0..MAX_WAIT_MILLIS
         }) return R.string.media_chain_invalid_configuration
    if (chain.finalResponse.type != MediaResponseType.JSON && chain.steps.last().observation.statusPath.isNotBlank()) {
        return R.string.media_chain_invalid_configuration
    }
    if (chain.finalResponse.type == MediaResponseType.JSON && chain.finalResponse.mediaPath.isBlank()) {
        return R.string.media_chain_media_path_required
    }
    return null
}
