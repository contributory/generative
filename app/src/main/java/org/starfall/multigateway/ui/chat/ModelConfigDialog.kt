package org.starfall.multigateway.ui.chat

import org.starfall.multigateway.ui.components.AppAlertDialog as AlertDialog
import org.starfall.multigateway.ui.components.SelectableOutlinedTextField

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.data.model.*
import org.starfall.multigateway.R
import androidx.compose.ui.res.stringResource

@Composable
fun ModelConfigDialog(
    provider: LlmProviderInfo,
    modelId: String,
    onSave: (ModelConfiguration) -> Unit,
    onDismiss: () -> Unit
) {
    val config = provider.config.modelConfigs[modelId] ?: ModelConfiguration()
    var temperature by remember { mutableStateOf(config.temperature?.toString().orEmpty()) }
    var topP by remember { mutableStateOf(config.topP?.toString().orEmpty()) }
    var topK by remember { mutableStateOf(config.topK?.toString().orEmpty()) }
    val supportsTopK = !provider.type.isOpenAi
    val tempMax = if (provider.type == ProviderType.ANTHROPIC) 1.0 else 2.0
    val tempValid = temperature.isBlank() || temperature.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..tempMax } == true
    val topPValid = topP.isBlank() || topP.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..1.0 } == true
    val topKValid = !supportsTopK || topK.isBlank() || topK.toIntOrNull()?.let { it > 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(modelId, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(provider.name, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.model_stream_settings_description))
                SelectableOutlinedTextField(temperature, { temperature = it }, label = { Text("Temperature (0–$tempMax)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                     supportingText = { if (!tempValid) Text(stringResource(R.string.model_temperature_invalid, tempMax)) },
                    isError = !tempValid, singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectableOutlinedTextField(topP, { topP = it }, label = { Text("Top-p (0–1)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                     supportingText = { if (!topPValid) Text(stringResource(R.string.model_top_p_invalid)) },
                    isError = !topPValid, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (supportsTopK) {
                    SelectableOutlinedTextField(topK, { topK = it }, label = { Text("Top-k (positive integer)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                         supportingText = { if (!topKValid) Text(stringResource(R.string.model_top_k_invalid)) },
                        isError = !topKValid, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { temperature = ""; topP = ""; topK = "" }) { Text("Reset sampling to API defaults") }
            }
        },
        confirmButton = {
            Button(enabled = tempValid && topPValid && topKValid, onClick = {
                onSave(config.copy(temperature = temperature.toDoubleOrNull(), topP = topP.toDoubleOrNull(),
                    topK = if (supportsTopK) topK.toIntOrNull() else null))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
