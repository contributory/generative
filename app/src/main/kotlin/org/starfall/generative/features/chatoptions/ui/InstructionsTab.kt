package org.starfall.generative.features.chatoptions.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
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
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    library.prompts.forEach { prompt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onPreviewPrompt(prompt) }),
                            shape = RectangleShape,
                            color = MaterialTheme.colorScheme.surface
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
                                IconButton(onClick = { onEditPrompt(prompt) }, modifier = Modifier.size(40.dp)) {
                                    FluentIcon(
                                        Icons.Outlined.Edit,
                                        stringResource(R.string.prompt_edit_accessibility, prompt.name)
                                    )
                                }
                                IconButton(onClick = { promptPendingDelete = prompt }, modifier = Modifier.size(40.dp)) {
                                    FluentIcon(
                                        Icons.Outlined.Delete,
                                        stringResource(R.string.prompt_delete_accessibility, prompt.name),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
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
