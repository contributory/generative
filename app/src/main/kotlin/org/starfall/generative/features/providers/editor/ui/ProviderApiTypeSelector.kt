package org.starfall.generative.features.providers.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.ui.dialogs.RadioSelectionDialog
import org.starfall.generative.shared.ui.dialogs.RadioSelectionOption
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@Composable
internal fun ProviderApiTypeSelector(
    selected: ModelApiType,
    onSelected: (ModelApiType) -> Unit
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.provider_api_type)

    Surface(
        onClick = { dialogOpen = true },
        modifier = Modifier.fillMaxWidth().testTag("provider-type-selector"),
        shape = RoundedCornerShape(16.dp),
        color = prominentContainerColor(),
        border = prominentContainerBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(selected.displayName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SharedIcon(Icons.Outlined.ChevronRight, contentDescription = null,
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (dialogOpen) RadioSelectionDialog(
        title = title,
        selected = selected,
        options = ModelApiType.entries.map { api ->
            RadioSelectionOption(api, api.displayName, testTag = "provider-api-type-${api.name.lowercase()}")
        },
        onDismissRequest = { dialogOpen = false },
        onSelected = onSelected
    )
}
