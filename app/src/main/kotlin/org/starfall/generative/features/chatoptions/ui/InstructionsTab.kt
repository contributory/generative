package org.starfall.generative.features.chatoptions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.PromptLibrary
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.groupedItemShape
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.menus.ItemContextMenu

@Composable
internal fun InstructionsTab(
    library: PromptLibrary,
    onAddPrompt: () -> Unit,
    onEditPrompt: (SavedPrompt) -> Unit,
    onPreviewPrompt: (SavedPrompt) -> Unit,
    onDeletePrompt: (SavedPrompt) -> Unit
) {
    var promptPendingDelete by remember { mutableStateOf<SavedPrompt?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(onClick = onAddPrompt, modifier = Modifier.fillMaxWidth()) {
                FluentIcon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.prompt_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (library.prompts.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.prompt_library_empty),
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (library.prompts.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    library.prompts.forEachIndexed { index, prompt ->
                        Surface(
                            onClick = { onPreviewPrompt(prompt) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = groupedItemShape(index, library.prompts.size),
                            color = prominentContainerColor(),
                            border = prominentContainerBorder()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(prompt.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(
                                            R.string.prompt_metadata,
                                            promptRoleLabel(prompt.role),
                                            prompt.content.length,
                                            estimatePromptTokens(prompt.content)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ItemContextMenu(
                                    onEdit = { onEditPrompt(prompt) },
                                    onDelete = { promptPendingDelete = prompt },
                                    deleteColor = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    promptPendingDelete?.let { prompt ->
        AlertDialog(
            onDismissRequest = { promptPendingDelete = null },
            title = { Text(stringResource(R.string.prompt_delete_title)) },
            text = { Text(stringResource(R.string.prompt_delete_confirmation, prompt.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePrompt(prompt)
                    promptPendingDelete = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { promptPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

private fun estimatePromptTokens(content: String): Int =
    if (content.isBlank()) 0 else ((content.length + 3) / 4).coerceAtLeast(1)
