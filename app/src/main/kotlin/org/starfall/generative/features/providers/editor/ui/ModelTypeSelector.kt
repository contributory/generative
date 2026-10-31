package org.starfall.generative.features.providers.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.ui.dialogs.RadioSelectionDialog
import org.starfall.generative.shared.ui.dialogs.RadioSelectionOption
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@Composable
internal fun ModelTypeSelector(selected: ModelType, onSelected: (ModelType) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.model_type)

    Surface(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().testTag("model-type-selector"),
        shape = RoundedCornerShape(16.dp),
        color = prominentContainerColor(),
        border = prominentContainerBorder()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(selected.displayName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (open) RadioSelectionDialog(
        title = title,
        selected = selected,
        options = ModelType.entries.map { type ->
            RadioSelectionOption(type, type.displayName, testTag = "model-type-${type.name.lowercase()}")
        },
        onDismissRequest = { open = false },
        onSelected = onSelected
    )
}
