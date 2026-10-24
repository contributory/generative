package org.starfall.generative.shared.ui.model

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

/** Search field with the expand/collapse-all button and, when available, the selected-model shortcut. */
@Composable
internal fun ModelPickerToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    toggleEnabled: Boolean,
    allCollapsed: Boolean,
    onToggleAllCollapsed: () -> Unit,
    onOpenSelectedModel: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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
        SelectableOutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            leadingIcon = { FluentIcon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_models)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f)
        )
        if (onOpenSelectedModel != null) {
            FilledTonalIconButton(
                onClick = onOpenSelectedModel,
                modifier = Modifier.size(48.dp).testTag("open-selected-model-button")
            ) {
                FluentIcon(
                    Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.open_selected_model)
                )
            }
        }
    }
}
