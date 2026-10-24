package org.starfall.generative.features.providers.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import org.starfall.generative.R
import org.starfall.generative.features.providers.shared.logic.isEmpty
import org.starfall.generative.features.providers.shared.logic.formatCatalogMetadata
import org.starfall.generative.shared.model.language.DiscoveredModel
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderModelCatalogSheet(
    provider: ModelProvider,
    selectedModels: Set<String>,
    onFetchModels: (suspend (ModelProvider) -> List<DiscoveredModel>)?,
    onCatalogLoaded: (List<DiscoveredModel>) -> Unit,
    onToggle: (String) -> Unit,
    onSetSelection: (List<String>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val discoveryIdentity = listOf(provider.id, provider.type, provider.baseUrl, provider.auth,
        provider.config.customListModelsUrl, provider.config.headers)
    var models by remember(discoveryIdentity) { mutableStateOf<List<DiscoveredModel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val discoveryUnavailable = stringResource(R.string.model_discovery_unavailable)
    LaunchedEffect(refresh, discoveryIdentity) {
        loading = true
        loadError = null
        try {
            val discovered = (onFetchModels ?: error(discoveryUnavailable))(provider).distinctBy { it.id }
            models = discovered
            onCatalogLoaded(discovered)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            loadError = e.localizedMessage ?: context.getString(R.string.unable_to_load_models)
        } finally { loading = false }
    }
    val modelsById = remember(models) { models.associateBy { it.id } }
    val visibleModels = (models.map { it.id } + selectedModels).distinct().filter {
        it.contains(query, ignoreCase = true) || modelsById[it]?.displayName?.contains(query, ignoreCase = true) == true
    }
    val allVisibleSelected = visibleModels.isNotEmpty() && visibleModels.all { it in selectedModels }
    val selectionDescription = stringResource(
        if (allVisibleSelected) R.string.provider_deselect_visible_models else R.string.select_all_visible_models
    )
    AppSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { refresh++ }, enabled = !loading) {
                    FluentIcon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh_models))
                }
                SelectableOutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_models)) },
                    singleLine = true, modifier = Modifier.weight(1f))
                TriStateCheckbox(
                    state = when {
                        allVisibleSelected -> ToggleableState.On
                        visibleModels.any { it in selectedModels } -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    onClick = { onSetSelection(visibleModels, !allVisibleSelected) },
                    enabled = visibleModels.isNotEmpty(),
                    modifier = Modifier.semantics {
                        contentDescription = selectionDescription
                    }
                )
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            loadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refresh++ }, enabled = !loading) { Text(stringResource(R.string.common_retry)) }
            }
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag("provider-model-catalog-list"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (visibleModels.isEmpty() && !loading && loadError == null) {
                    item { Text(stringResource(if (query.isBlank()) R.string.no_models_returned else R.string.no_matching_models), modifier = Modifier.padding(16.dp)) }
                }
                items(visibleModels, key = { it }) { id ->
                    var expanded by androidx.compose.runtime.saveable.rememberSaveable(id) { mutableStateOf(false) }
                    val added = id in selectedModels
                    val model = modelsById[id]
                    val title = model?.displayName?.ifBlank { id } ?: id
                    val modelSelectionDescription = stringResource(R.string.provider_select_model, id)
                    Surface(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = if (added) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        } else {
                            prominentContainerColor()
                        },
                        border = if (added) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        } else {
                            prominentContainerBorder()
                        }
                    ) {
                        Column {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EntityIcon(provider.config.modelConfigs[id]?.icon, Modifier.size(36.dp),
                                text = id.substringAfterLast('/').take(1).uppercase(), matchName = title, fallbackMatchName = id, model = true)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                    maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                                if (title != id) Text(id, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                            }
                             FluentIcon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                 stringResource(if (expanded) R.string.collapse else R.string.expand), modifier = Modifier.size(20.dp),
                                 tint = MaterialTheme.colorScheme.onSurfaceVariant)
                             Checkbox(checked = added, onCheckedChange = { onToggle(id) }, modifier = Modifier.semantics {
                                 contentDescription = modelSelectionDescription
                             })
                        }
                        if (expanded) {
                            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                model?.metadata?.filter { (key, value) ->
                                    key !in setOf("id", "name", "display_name", "displayName") && value != JsonNull
                                }?.forEach { (key, value) ->
                                    val label = key.replace('_', ' ').replaceFirstChar { it.uppercase() }
                                    val detail = formatCatalogMetadata(key, value)
                                    Text("$label: $detail", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (model == null) Text(stringResource(R.string.provider_model_not_returned),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}
