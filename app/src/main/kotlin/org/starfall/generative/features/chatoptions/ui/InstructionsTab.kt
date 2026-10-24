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
import org.starfall.generative.shared.model.InstructionLibrary
import org.starfall.generative.shared.model.SavedInstruction
import org.starfall.generative.shared.ui.layout.groupedItemShape
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.menus.ItemContextMenu

@Composable
internal fun InstructionsTab(
    library: InstructionLibrary,
    onEditInstruction: (SavedInstruction) -> Unit,
    onPreviewInstruction: (SavedInstruction) -> Unit,
    onDeleteInstruction: (SavedInstruction) -> Unit
) {
    var instructionPendingDelete by remember { mutableStateOf<SavedInstruction?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (library.instructions.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.instruction_library_empty),
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (library.instructions.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    library.instructions.forEachIndexed { index, instruction ->
                        Surface(
                            onClick = { onPreviewInstruction(instruction) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = groupedItemShape(index, library.instructions.size),
                            color = prominentContainerColor(),
                            border = prominentContainerBorder()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(instruction.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(
                                            R.string.instruction_metadata,
                                            instructionRoleLabel(instruction.role),
                                            instruction.content.length,
                                            estimateInstructionTokens(instruction.content)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ItemContextMenu(
                                    onEdit = { onEditInstruction(instruction) },
                                    onDelete = { instructionPendingDelete = instruction },
                                    deleteColor = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    instructionPendingDelete?.let { instruction ->
        AlertDialog(
            onDismissRequest = { instructionPendingDelete = null },
            text = { Text(stringResource(R.string.instruction_delete_confirmation, instruction.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteInstruction(instruction)
                    instructionPendingDelete = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { instructionPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

private fun estimateInstructionTokens(content: String): Int =
    if (content.isBlank()) 0 else ((content.length + 3) / 4).coerceAtLeast(1)
