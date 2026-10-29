package org.starfall.generative.features.speech.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
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
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.sheets.BottomSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import org.starfall.generative.shared.ui.text.OutlinedTextField

@Composable
internal fun SpeechModelField(
    service: SpeechService,
    onChange: (String) -> Unit,
    fetchModels: (suspend (SpeechService) -> List<SpeechSynthesisModel>)? = null
) {
    val client = remember { StandaloneSpeechClient() }
    val currentModelId by rememberUpdatedState(service.modelId)
    val currentOnChange by rememberUpdatedState(onChange)
    key(service.id, service.engine, service.apiKey) {
        var models by remember { mutableStateOf<List<SpeechSynthesisModel>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var expanded by remember { mutableStateOf(false) }
        // A saved model needs no catalog request until the user opens the picker.
        // Remember this per engine/key so selecting a model never triggers another fetch.
        var fetchRequested by remember { mutableStateOf(service.modelId.isNullOrBlank()) }
        var fetchedOnce by remember { mutableStateOf(false) }
        val canFetch = service.apiKey.isNotBlank()
        LaunchedEffect(fetchRequested) {
            if (!canFetch || !fetchRequested) return@LaunchedEffect
            loading = true; error = null
            try {
                delay(600)
                val fetched = fetchModels?.invoke(service) ?: client.models(service)
                currentCoroutineContext().ensureActive()
                models = fetched
                fetchedOnce = true
                // Never overwrite a saved model or one typed/chosen while the request was running.
                if (currentModelId.isNullOrBlank()) fetched.firstOrNull()?.id?.let(currentOnChange)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                error = failure.message.orEmpty().let { if (service.apiKey.isBlank()) it else it.replace(service.apiKey, "[redacted]") }.take(500)
            } finally {
                loading = false
                fetchRequested = false
            }
        }
        Box {
            OutlinedTextField(service.modelId.orEmpty(), onChange,
                label = { Text(stringResource(R.string.speech_engine_model)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row {
                        if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        IconButton(
                            onClick = {
                                if (!expanded && canFetch && !loading && !fetchedOnce && error == null) {
                                    fetchRequested = true
                                }
                                expanded = !expanded
                            },
                            modifier = Modifier.testTag("speech-model-dropdown")
                        ) {
                            SharedIcon(Icons.Default.KeyboardArrowDown, stringResource(R.string.speech_engine_model))
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("speech-model-id"))
            if (expanded) {
                BottomSheet(onDismissRequest = { expanded = false }) {
                    Text(stringResource(R.string.speech_engine_model), Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge)
                    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        error?.let { detail ->
                            item {
                                Text(detail, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { fetchRequested = true }) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            }
                        }
                        if (loading || fetchRequested) item {
                            CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), strokeWidth = 2.dp)
                        }
                        if (!loading && !fetchRequested && error == null && models.isEmpty()) item {
                            Text(stringResource(R.string.no_models_returned), Modifier.padding(16.dp))
                        }
                        items(models, key = { it.id }) { model ->
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(model.name) },
                                supportingContent = {
                                    if (model.name != model.id) Text(model.id)
                                },
                                trailingContent = {
                                    if (model.id == service.modelId) SharedIcon(Icons.Default.Check, stringResource(R.string.common_selected))
                                },
                                modifier = Modifier.clickable { onChange(model.id); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
