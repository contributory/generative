package org.starfall.generative.home.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.starfall.generative.R
import org.starfall.generative.shared.model.ConversationSummaryRequest
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSummarySheet(
    onStart: (ConversationSummaryRequest) -> Boolean,
    onDismiss: () -> Unit
) {
    var targetSlider by remember { mutableFloatStateOf(1200f) }
    var customTarget by remember { mutableStateOf(false) }
    var customTargetText by remember { mutableStateOf("1200") }
    var chunked by remember { mutableStateOf(false) }
    var chunkSlider by remember { mutableFloatStateOf(20_000f) }
    var customChunk by remember { mutableStateOf(false) }
    var customChunkText by remember { mutableStateOf("20000") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val targetTokens = if (customTarget) customTargetText.toIntOrNull() else targetSlider.roundToInt()
    val chunkTokens = if (customChunk) customChunkText.toIntOrNull() else chunkSlider.roundToInt()
    val targetValid = targetTokens != null && targetTokens > 0
    val chunkValid = !chunked || (chunkTokens != null && chunkTokens in 1..100_000)
    val validationError = when {
        !targetValid -> stringResource(R.string.conversation_summary_invalid_target)
        !chunkValid -> stringResource(R.string.conversation_summary_invalid_chunk)
        else -> null
    }

    AppSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.conversation_summary_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.conversation_summary_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.conversation_summary_tokens_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(stringResource(R.string.conversation_summary_target_size, targetTokens ?: 0), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = targetSlider,
                onValueChange = { targetSlider = it },
                valueRange = 1f..6000f,
                enabled = !customTarget
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = customTarget, onCheckedChange = { customTarget = it })
                Text(stringResource(R.string.conversation_summary_custom_target))
            }
            if (customTarget) {
                SelectableOutlinedTextField(
                    value = customTargetText,
                    onValueChange = { customTargetText = it; error = null },
                    label = { Text(stringResource(R.string.conversation_summary_target_tokens)) },
                    isError = !targetValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(stringResource(R.string.conversation_summary_mode), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !chunked,
                    onClick = { chunked = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text(stringResource(R.string.conversation_summary_mode_all)) }
                )
                SegmentedButton(
                    selected = chunked,
                    onClick = { chunked = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text(stringResource(R.string.conversation_summary_mode_passes)) }
                )
            }

            if (chunked) {
                Text(stringResource(R.string.conversation_summary_token_limit, chunkTokens ?: 0), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = chunkSlider,
                    onValueChange = { chunkSlider = it },
                    valueRange = 1f..100_000f,
                    enabled = !customChunk
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = customChunk, onCheckedChange = { customChunk = it })
                    Text(stringResource(R.string.conversation_summary_custom_pass))
                }
                if (customChunk) {
                    SelectableOutlinedTextField(
                        value = customChunkText,
                        onValueChange = { customChunkText = it; error = null },
                        label = { Text(stringResource(R.string.conversation_summary_tokens_per_pass)) },
                        isError = !chunkValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val displayedError = validationError ?: error
            if (displayedError != null) {
                Text(displayedError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val request = ConversationSummaryRequest(
                        targetTokens = targetTokens ?: 1,
                        chunked = chunked,
                        tokensPerChunk = chunkTokens ?: 20_000
                    )
                    if (onStart(request)) onDismiss()
                    else error = context.getString(R.string.conversation_summary_model_missing)
                },
                enabled = targetValid && chunkValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.conversation_summary_start))
            }
        }
    }
}
