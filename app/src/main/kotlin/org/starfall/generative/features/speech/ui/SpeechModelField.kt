package org.starfall.generative.features.speech.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.starfall.generative.R
import org.starfall.generative.integrations.speech.SpeechSynthesisModel
import org.starfall.generative.integrations.speech.StandaloneSpeechClient
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.AppDropdownMenu
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

@Composable
internal fun SpeechModelField(
    service: SpeechService,
    onChange: (String) -> Unit,
    fetchModels: (suspend (SpeechService) -> List<SpeechSynthesisModel>)? = null
) {
    val client = remember { StandaloneSpeechClient() }
    val currentModelId by rememberUpdatedState(service.modelId)
    val currentOnChange by rememberUpdatedState(onChange)
    key(service.engine, service.apiKey) {
        var models by remember { mutableStateOf<List<SpeechSynthesisModel>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var expanded by remember { mutableStateOf(false) }
        var refresh by remember { mutableIntStateOf(0) }
        val canFetch = service.apiKey.isNotBlank()
        LaunchedEffect(refresh) {
            if (!canFetch) return@LaunchedEffect
            loading = true; error = null
            try {
                delay(600)
                val fetched = fetchModels?.invoke(service) ?: client.models(service)
                currentCoroutineContext().ensureActive()
                models = fetched
                // Never overwrite a saved model or one typed/chosen while the request was running.
                if (currentModelId.isNullOrBlank()) fetched.firstOrNull()?.id?.let(currentOnChange)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                error = failure.message.orEmpty().let { if (service.apiKey.isBlank()) it else it.replace(service.apiKey, "[redacted]") }.take(500)
            } finally { loading = false }
        }
        Box {
            SelectableOutlinedTextField(service.modelId.orEmpty(), onChange,
                label = { Text(stringResource(R.string.speech_engine_model)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row {
                        if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        else IconButton(enabled = canFetch, onClick = { refresh++ }) {
                            FluentIcon(Icons.Outlined.Refresh, stringResource(R.string.refresh_models))
                        }
                        IconButton(onClick = { expanded = !expanded }) {
                            FluentIcon(Icons.Default.KeyboardArrowDown, stringResource(R.string.speech_engine_model))
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("speech-model-id"))
            AppDropdownMenu(expanded, { expanded = false }) {
                error?.let { detail ->
                    Text(detail, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                    RoundedDropdownMenuItem(text = { Text(stringResource(R.string.common_retry)) }, onClick = { refresh++; expanded = false })
                }
                if (!loading && error == null && models.isEmpty()) {
                    RoundedDropdownMenuItem(text = { Text(stringResource(R.string.no_models_returned)) }, enabled = false, onClick = {})
                }
                models.forEach { model ->
                    RoundedDropdownMenuItem(text = {
                        Column {
                            Text(model.name)
                            if (model.name != model.id) Text(model.id, style = MaterialTheme.typography.bodySmall)
                        }
                    }, onClick = { onChange(model.id); expanded = false })
                }
            }
        }
    }
}
