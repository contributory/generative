package org.starfall.generative.shared.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class RadioSelectionOption<T>(
    val value: T,
    val label: String,
    val description: String? = null,
    val testTag: String? = null
)

/** Immediate, single-choice selection with no footer. The dialog owns dismissal and scrolling. */
@Composable
fun <T> RadioSelectionDialog(
    title: String,
    selected: T,
    options: List<RadioSelectionOption<T>>,
    onDismissRequest: () -> Unit,
    onSelected: (T) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        scrollableContent = true,
        content = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = isSelected, role = Role.RadioButton) {
                                onDismissRequest()
                                onSelected(option.value)
                            }
                            .then(option.testTag?.let { Modifier.testTag(it) } ?: Modifier)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                            option.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    )
}
