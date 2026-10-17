package org.starfall.multigateway.ui.providers

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.data.model.LlmProviderInfo
import org.starfall.multigateway.data.model.ProviderGroup
import org.starfall.multigateway.R
import org.starfall.multigateway.ui.components.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.multigateway.ui.icons.FluentIcon
import org.starfall.multigateway.ui.icons.Icons

/**
 * The providers app bar. It cross-fades between three modes: the search field, an open folder's
 * header with folder actions, and the default title with search / view / add actions.
 *
 * [visibleGroupId] is the folder the bar is currently showing; [onAddProvider] adds to whichever
 * folder is open at click time, while [onAddProviderToGroup] targets the folder shown in the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderScreenTopBar(
    visibleGroupId: String?,
    sortedGroups: List<ProviderGroup>,
    orderedProviders: List<LlmProviderInfo>,
    isGridView: Boolean,
    searching: Boolean,
    searchQuery: String,
    searchFields: Set<ProviderSearchField>,
    searchFilterOpen: Boolean,
    searchFocus: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onSearchFieldsChange: (Set<ProviderSearchField>) -> Unit,
    onSearchFilterOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onToggleGridView: ((Boolean) -> Unit)?,
    onAddProvider: () -> Unit,
    onAddProviderToGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onCloseGroup: () -> Unit,
    onEditGroup: (ProviderGroup) -> Unit,
    onDeleteGroup: (ProviderGroup) -> Unit
) {
    Crossfade(targetState = visibleGroupId, label = "provider_app_bar") { targetGroupId ->
        val appBarGroup = sortedGroups.firstOrNull { it.id == targetGroupId }
        TopAppBar(
            title = {
                if (searching) {
                    LaunchedEffect(Unit) { searchFocus.requestFocus() }
                    TextField(
                        value = searchQuery,
                        onValueChange = { onSearchQueryChange(it) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        placeholder = { Text(stringResource(R.string.search_providers)) },
                        singleLine = true,
                        trailingIcon = {
                            Box {
                                IconButton(onClick = { onSearchFilterOpenChange(true) }) {
                                    FluentIcon(Icons.Outlined.FilterList, stringResource(R.string.provider_search_filter),
                                        tint = if (searchFields == setOf(ProviderSearchField.PROVIDER_NAME))
                                            MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(expanded = searchFilterOpen, onDismissRequest = { onSearchFilterOpenChange(false) }) {
                                    listOf(
                                        ProviderSearchField.PROVIDER_NAME to R.string.provider_search_name,
                                        ProviderSearchField.FOLDER_NAME to R.string.provider_search_folder,
                                        ProviderSearchField.MODEL_NAME to R.string.provider_search_model
                                    ).forEach { (field, label) ->
                                        val selected = field in searchFields
                                        DropdownMenuItem(
                                            text = { Text(stringResource(label)) },
                                            leadingIcon = { Checkbox(checked = selected, onCheckedChange = null) },
                                            enabled = !selected || searchFields.size > 1,
                                            onClick = {
                                                onSearchFieldsChange(if (selected) searchFields - field else searchFields + field)
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus).testTag("provider_search"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                } else if (appBarGroup != null) {
                    val members = orderedProviders.filter { it.groupId == appBarGroup.id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderFolderPreview(group = appBarGroup, providers = members, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = appBarGroup.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.provider_count, members.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        }
                    }
                } else Column {
                    Text(
                        text = stringResource(R.string.providers_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.providers_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                if (appBarGroup == null || searching) IconButton(onClick = onBack) {
                    FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
            },
            actions = {
                if (appBarGroup != null && !searching) {
                    IconButton(
                        onClick = { onAddProviderToGroup(appBarGroup.id) },
                        modifier = Modifier.testTag("add_provider")
                    ) {
                        FluentIcon(Icons.Default.Add, stringResource(R.string.add_provider))
                    }
                    IconButton(onClick = { onToggleGridView?.invoke(!isGridView) }) {
                        FluentIcon(
                            imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = stringResource(
                                if (isGridView) R.string.switch_to_list_view else R.string.switch_to_grid_view
                            )
                        )
                    }
                    ProviderGroupOverflowMenu(
                        onEdit = { onEditGroup(appBarGroup) },
                        onDelete = { onDeleteGroup(appBarGroup) },
                        compact = false
                    )
                    IconButton(onClick = onCloseGroup) {
                        FluentIcon(Icons.Default.Close, stringResource(R.string.common_close))
                    }
                } else if (searching) {
                    IconButton(onClick = onCloseSearch) {
                        FluentIcon(Icons.Default.Close, stringResource(R.string.close_provider_search))
                    }
                } else {
                    IconButton(onClick = onOpenSearch) {
                        FluentIcon(Icons.Default.Search, stringResource(R.string.search_providers))
                    }
                    IconButton(onClick = { onToggleGridView?.invoke(!isGridView) }) {
                        FluentIcon(
                            imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = stringResource(
                                if (isGridView) R.string.switch_to_list_view else R.string.switch_to_grid_view
                            )
                        )
                    }
                    IconButton(onClick = onCreateGroup) {
                        FluentIcon(Icons.Outlined.CreateNewFolder, stringResource(R.string.add_provider_group))
                    }
                    IconButton(
                        onClick = onAddProvider,
                        modifier = Modifier.testTag("add_provider")
                    ) {
                        FluentIcon(Icons.Default.Add, stringResource(R.string.add_provider))
                    }
                }
            }
        )
    }
}
