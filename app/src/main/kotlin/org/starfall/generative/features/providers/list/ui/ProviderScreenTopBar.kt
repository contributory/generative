package org.starfall.generative.features.providers.list.ui

import org.starfall.generative.shared.ui.layout.edgeToEdgeTopAppBarColors
import org.starfall.generative.shared.ui.menus.AppDropdownMenu as DropdownMenu
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import org.starfall.generative.R
import org.starfall.generative.features.providers.list.logic.ProviderSearchField
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.lists.ListViewToggle
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem

/** App bar for the providers root screen only. Open folders draw their own full-screen header. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderScreenTopBar(
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
    onCreateGroup: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit
) {
    TopAppBar(
        colors = edgeToEdgeTopAppBarColors(),
        title = {
            if (searching) {
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    placeholder = { Text(stringResource(R.string.search_providers)) },
                    singleLine = true,
                    trailingIcon = {
                        Box {
                            IconButton(onClick = { onSearchFilterOpenChange(true) }) {
                                FluentIcon(
                                    Icons.Outlined.FilterList,
                                    stringResource(R.string.provider_search_filter),
                                    tint = if (searchFields == setOf(ProviderSearchField.PROVIDER_NAME))
                                        MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(expanded = searchFilterOpen, onDismissRequest = { onSearchFilterOpenChange(false) }) {
                                listOf(
                                    ProviderSearchField.PROVIDER_NAME to R.string.provider_search_name,
                                    ProviderSearchField.FOLDER_NAME to R.string.common_folder_name,
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
            IconButton(onClick = onBack) {
                FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        },
        actions = {
            if (searching) {
                IconButton(onClick = onCloseSearch) {
                    FluentIcon(Icons.Default.Close, stringResource(R.string.close_provider_search))
                }
            } else {
                IconButton(onClick = onOpenSearch) {
                    FluentIcon(Icons.Default.Search, stringResource(R.string.search_providers))
                }
                ListViewToggle(isGridView) { onToggleGridView?.invoke(!isGridView) }
                IconButton(onClick = onCreateGroup, modifier = Modifier.testTag("add_provider_group")) {
                    FluentIcon(Icons.Outlined.CreateNewFolder, stringResource(R.string.common_add))
                }
                IconButton(onClick = onAddProvider, modifier = Modifier.testTag("add_provider")) {
                    FluentIcon(Icons.Default.Add, stringResource(R.string.common_add))
                }
            }
        }
    )
}
