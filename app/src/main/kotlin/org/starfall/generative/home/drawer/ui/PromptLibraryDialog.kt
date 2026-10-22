package org.starfall.generative.home.drawer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.PromptLibrary
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.ui.dialogs.AppDialog as Dialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.windowHeightIn

@Composable
fun PromptLibraryDialog(
    initialLibrary: PromptLibrary,
    onChange: (PromptLibrary) -> Unit,
    onOpenInstructions: () -> Unit,
    onEditPrompt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var library by remember { mutableStateOf(initialLibrary) }
    fun update(next: PromptLibrary) {
        library = next
        onChange(next)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().windowHeightIn(maxFraction = 0.85f).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.prompt_library_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onOpenInstructions) {
                        FluentIcon(
                            Icons.Outlined.Settings,
                            stringResource(R.string.prompt_open_instructions)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        FluentIcon(Icons.Outlined.Close, stringResource(R.string.common_close))
                    }
                }
                Text(
                    stringResource(R.string.prompt_toggle_description),
                    Modifier.padding(top = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    if (library.prompts.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.prompt_library_empty),
                                Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(library.prompts, key = { it.id }) { prompt ->
                        val active = prompt.id in library.selectedIds
                        PromptToggleRow(
                            prompt = prompt,
                            active = active,
                            onToggle = {
                                update(
                                    library.copy(
                                        selectedIds = if (active) library.selectedIds - prompt.id
                                        else library.selectedIds + prompt.id
                                    )
                                )
                            },
                            onEdit = { onEditPrompt(prompt.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptToggleRow(
    prompt: SavedPrompt,
    active: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().toggleable(
            value = active,
            role = Role.Checkbox,
            onValueChange = { onToggle() }
        )
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = active, onCheckedChange = null)
            Column(Modifier.weight(1f)) {
                Text(prompt.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    promptRoleLabel(prompt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    prompt.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                FluentIcon(
                    Icons.Outlined.Edit,
                    stringResource(R.string.prompt_edit_accessibility, prompt.name)
                )
            }
        }
    }
}

@Composable
private fun promptRoleLabel(prompt: SavedPrompt): String = stringResource(
    when (prompt.role) {
        org.starfall.generative.shared.model.ChatRole.SYSTEM -> R.string.prompt_role_system
        org.starfall.generative.shared.model.ChatRole.USER -> R.string.prompt_role_user
        org.starfall.generative.shared.model.ChatRole.MODEL -> R.string.prompt_role_assistant
    }
)
