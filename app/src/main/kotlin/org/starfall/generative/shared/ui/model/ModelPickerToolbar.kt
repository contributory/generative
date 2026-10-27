package org.starfall.generative.shared.ui.model

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

/** Expandable search, section controls, and an optional shortcut to the selected model. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelPickerToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    toggleEnabled: Boolean,
    allCollapsed: Boolean,
    onToggleAllCollapsed: () -> Unit,
    selectedModelId: String,
    selectedModel: ModelConfiguration?,
    onOpenSelectedModel: (() -> Unit)?,
    onSearchBoundsChange: (Rect?) -> Unit = {}
) {
    var searchExpanded by remember { mutableStateOf(false) }
    var searchWasFocused by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) imeWasVisible = true
        else if (imeWasVisible) {
            imeWasVisible = false
            if (searchExpanded) focusManager.clearFocus(force = true)
        }
    }
    LaunchedEffect(searchExpanded) {
        if (!searchExpanded) onSearchBoundsChange(null)
    }
    val modelName = selectedModel?.displayName?.takeIf { it.isNotBlank() }
        ?: selectedModelId.ifBlank { stringResource(R.string.select_model) }
    val openSelectedDescription = stringResource(R.string.open_selected_model)

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        val reservedWidth = 48.dp + 8.dp + (if (onOpenSelectedModel != null) 56.dp else 0.dp)
        val searchWidth by animateDpAsState(
            targetValue = if (searchExpanded) (maxWidth - reservedWidth).coerceAtLeast(48.dp) else 48.dp,
            label = "modelPickerSearchWidth"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                enabled = toggleEnabled,
                onClick = onToggleAllCollapsed,
                modifier = Modifier.size(48.dp)
            ) {
                FluentIcon(
                    if (allCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = stringResource(
                        if (allCollapsed) R.string.expand_all_model_sections else R.string.collapse_all_model_sections
                    )
                )
            }
            Box(Modifier.width(searchWidth), contentAlignment = Alignment.CenterStart) {
                if (searchExpanded) {
                    LaunchedEffect(Unit) { searchFocus.requestFocus() }
                    SelectableOutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        leadingIcon = { FluentIcon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { onQueryChange("") }) {
                                    FluentIcon(Icons.Default.Close, stringResource(R.string.common_clear))
                                }
                            }
                        } else null,
                        placeholder = { Text(stringResource(R.string.search_models), maxLines = 1) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); keyboard?.hide() }),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(searchFocus)
                            .onGloballyPositioned { onSearchBoundsChange(it.boundsInRoot()) }
                            .onFocusChanged { focus ->
                                if (focus.isFocused) searchWasFocused = true
                                else if (searchWasFocused) {
                                    searchWasFocused = false
                                    searchExpanded = false
                                }
                            }
                            .testTag("model-picker-search")
                    )
                } else {
                    FilledTonalIconButton(
                        onClick = { searchExpanded = true },
                        modifier = Modifier.size(48.dp).testTag("model-picker-search-button")
                    ) {
                        FluentIcon(
                            Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.search_models),
                            tint = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            if (onOpenSelectedModel != null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Surface(
                        onClick = {
                            if (searchExpanded) focusManager.clearFocus()
                            onOpenSelectedModel()
                        },
                        modifier = Modifier.height(48.dp)
                            .testTag("open-selected-model-button")
                            .semantics { contentDescription = openSelectedDescription },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, end = if (searchExpanded) 8.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (searchExpanded) 0.dp else 8.dp)
                        ) {
                            EntityIcon(
                                image = selectedModel?.icon,
                                modifier = Modifier.size(32.dp).testTag("selected-model-icon"),
                                text = modelInitial(selectedModelId),
                                fallback = if (selectedModelId.isBlank()) Icons.Outlined.Add else null,
                                matchName = modelName,
                                fallbackMatchName = selectedModelId,
                                model = true
                            )
                            if (!searchExpanded) {
                                Text(
                                    text = modelName,
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
