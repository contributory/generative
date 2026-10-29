package org.starfall.generative.features.chatoptions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.lists.ItemCard
import org.starfall.generative.shared.ui.lists.EmptyState
import org.starfall.generative.shared.ui.lists.FadeGridListContent
import org.starfall.generative.shared.ui.dialogs.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.InstructionLibrary
import org.starfall.generative.shared.model.SavedInstruction
import org.starfall.generative.shared.ui.menus.ItemContextMenu

@Composable
internal fun InstructionsTab(
    library: InstructionLibrary,
    onEditInstruction: (SavedInstruction) -> Unit,
    onPreviewInstruction: (SavedInstruction) -> Unit,
    onDeleteInstruction: (SavedInstruction) -> Unit,
    isGrid: Boolean = false,
    bottomPadding: Dp = 0.dp
) {
    var instructionPendingDelete by remember { mutableStateOf<SavedInstruction?>(null) }

    if (library.instructions.isEmpty()) {
        EmptyState(Icons.Outlined.Description, stringResource(R.string.instruction_library_empty))
    } else {
        FadeGridListContent(isGrid = isGrid, modifier = Modifier.fillMaxSize()) { gridMode ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (gridMode) 2 else 1),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp + bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(library.instructions, key = { it.id }) { instruction ->
                    ItemCard(
                        isGrid = gridMode,
                        onClick = { onPreviewInstruction(instruction) },
                        icon = {
                            EntityIcon(null, Modifier.size(42.dp), fallback = Icons.Outlined.Description)
                        },
                        actions = {
                            ItemContextMenu(
                                onEdit = { onEditInstruction(instruction) },
                                onDelete = { instructionPendingDelete = instruction },
                                deleteColor = MaterialTheme.colorScheme.error
                            )
                        },
                        content = {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(instruction.name, style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.instruction_metadata, instructionRoleLabel(instruction.role),
                                        instruction.content.length, estimateInstructionTokens(instruction.content)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    instructionPendingDelete?.let { instruction ->
        AlertDialog(
            onDismissRequest = { instructionPendingDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_instruction_title)) },
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
