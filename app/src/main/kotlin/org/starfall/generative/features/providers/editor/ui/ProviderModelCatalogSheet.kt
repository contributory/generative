package org.starfall.generative.features.providers.editor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import org.starfall.generative.shared.ui.GenerativeLinearWavyProgressIndicator
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProviderModelCatalogSheet(
    provider: ModelProvider,
    selectedModels: Set<String>,
    visible: Boolean = true,
    onFetchModels: (suspend (ModelProvider) -> List<DiscoveredModel>)?,
    onCatalogLoaded: (List<DiscoveredModel>) -> Unit,
    onToggle: (String) -> Unit,
    onSetSelection: (List<String>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val discoveryIdentity = listOf(provider.id, provider.type, provider.baseUrl, provider.auth,
        provider.config.customListModelsUrl, provider.config.codexClientVersion,
        provider.config.antigravityVersion, provider.config.antigravityChangelist,
        provider.config.antigravityProjectIds, provider.config.headers)
    var models by remember(discoveryIdentity) { mutableStateOf<List<DiscoveredModel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Keep the final selectable model clear of the system gesture region.
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(this).toDp()
    }
    fun collapseSearch() {
        // Losing focus only closes the search editor; the query keeps filtering the catalog.
        // The trailing clear icon is the only action that resets the filter.
        searchExpanded = false
        searchFocused = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    LaunchedEffect(searchExpanded, visible) {
        if (searchExpanded && visible) {
            withFrameNanos { }
            searchFocus.requestFocus()
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) imeWasVisible = true
        else if (imeWasVisible) {
            imeWasVisible = false
            if (searchExpanded) collapseSearch()
        }
    }
    LaunchedEffect(visible) {
        // This composable also exists while closed: never clear an editor field's focus
        // just because the model catalog has not been opened yet.
        if (!visible && (searchExpanded || query.isNotEmpty())) collapseSearch()
    }
    val context = LocalContext.current
    val discoveryUnavailable = stringResource(R.string.model_discovery_unavailable)
    LaunchedEffect(visible, refresh, discoveryIdentity) {
        if (!visible) return@LaunchedEffect
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
    val selectionState = when {
        allVisibleSelected -> ToggleableState.On
        visibleModels.any { it in selectedModels } -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }
    val selectionDescription = stringResource(
        if (allVisibleSelected) R.string.provider_deselect_visible_models else R.string.select_all_visible_models
    )
    if (!visible) return
    AppSheet(
        onDismissRequest = onDismiss,
        contentBehindNavigationBar = true,
        // Keep the original opening height while fetching. Once the results arrive, let the
        // actual list content determine the sheet height (capped below the status bar).
        fixedHeightFraction = if (loading) 0.78f else null,
        // Release a drag made during loading when transitioning to content-based sizing.
        // After loading, normal manual resizing stays in the user's control.
        contentKey = loading
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (loading) Modifier.fillMaxHeight() else Modifier)
                .imePadding()
                .pointerInput(searchExpanded) {
                    // Like ConversationSideDrawer, tapping an unoccupied area dismisses search.
                    if (searchExpanded) detectTapGestures(onTap = { collapseSearch() })
                }
        ) {
            // Match ModelPickerSheet: the compact toolbar stays above the results.
            BoxWithConstraints(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("provider-model-catalog-toolbar")
            ) {
                val searchWidth by animateDpAsState(
                    targetValue = if (searchExpanded) (maxWidth - 104.dp).coerceAtLeast(40.dp) else 40.dp,
                    label = "catalogSearchWidth"
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { onSetSelection(visibleModels, !allVisibleSelected) },
                        enabled = visibleModels.isNotEmpty(),
                        modifier = Modifier.size(40.dp).semantics {
                            contentDescription = selectionDescription
                            toggleableState = selectionState
                        }
                    ) {
                        FluentIcon(
                            when (selectionState) {
                                ToggleableState.On -> Icons.Outlined.CheckCircle
                                ToggleableState.Indeterminate -> Icons.Outlined.Check
                                ToggleableState.Off -> Icons.Outlined.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = if (selectionState == ToggleableState.Off || visibleModels.isEmpty())
                                LocalContentColor.current else MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(Modifier.width(searchWidth), contentAlignment = Alignment.CenterStart) {
                        if (searchExpanded) {
                            SelectableOutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                placeholder = {
                                    Text(stringResource(R.string.search_models), maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                },
                                leadingIcon = { FluentIcon(Icons.Outlined.Search, contentDescription = null) },
                                trailingIcon = if (query.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { query = "" }) {
                                            FluentIcon(Icons.Default.Close, stringResource(R.string.common_clear))
                                        }
                                    }
                                } else null,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                }),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.fillMaxWidth()
                                    .focusRequester(searchFocus)
                                    .testTag("provider_model_catalog_search")
                                    .onFocusChanged {
                                        if (!it.isFocused && searchFocused && searchExpanded) collapseSearch()
                                        searchFocused = it.isFocused
                                    }
                            )
                        } else {
                            FilledTonalIconButton(
                                onClick = { searchExpanded = true },
                                modifier = Modifier.size(40.dp)
                                    .testTag("provider_model_catalog_search_button")
                            ) {
                                FluentIcon(
                                    Icons.Outlined.Search,
                                    stringResource(R.string.search_models),
                                    tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(
                        onClick = { refresh++ },
                        enabled = !loading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        FluentIcon(Icons.Outlined.Refresh, stringResource(R.string.refresh_models))
                    }
                }
            }
            if (loading) GenerativeLinearWavyProgressIndicator(Modifier.fillMaxWidth())
            loadError?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("provider-model-catalog-error"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(error, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { refresh++ }, enabled = !loading) {
                                Text(stringResource(R.string.common_retry))
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.unable_to_load_models), error))
                            }, modifier = Modifier.testTag("provider-model-catalog-copy-error")) {
                                FluentIcon(Icons.Outlined.ContentCopy, stringResource(R.string.common_copy),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            LazyColumn(
                Modifier
                    // Wrap short results; long lists consume the maximum available height.
                    .weight(1f, fill = loading)
                    .fillMaxWidth()
                    .testTag("provider-model-catalog-list"),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = 16.dp + navigationBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        shape = RoundedCornerShape(12.dp),
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
                        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EntityIcon(provider.config.modelConfigs[id]?.icon, Modifier.size(32.dp),
                                text = id.substringAfterLast('/').take(1).uppercase(), matchName = title, fallbackMatchName = id, model = true)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                                if (title != id) Text(id, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                            }
                             FluentIcon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                 stringResource(if (expanded) R.string.collapse else R.string.expand), modifier = Modifier.size(20.dp),
                                 tint = MaterialTheme.colorScheme.onSurfaceVariant)
                             Checkbox(checked = added, onCheckedChange = { onToggle(id) }, modifier = Modifier.size(32.dp).semantics {
                                 contentDescription = modelSelectionDescription
                             })
                        }
                        if (expanded) {
                            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val metadata = model?.metadata?.filter { (key, value) ->
                                    key !in setOf("id", "name", "display_name", "displayName") && value != JsonNull
                                }.orEmpty()
                                for ((key, value) in metadata) {
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
