package org.starfall.generative.home.drawer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.PromptLibrary
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.ui.dialogs.AppDialog as Dialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField
import org.starfall.generative.shared.ui.layout.windowHeightIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryDialog(initialLibrary: PromptLibrary, onChange: (PromptLibrary) -> Unit, onDismiss: () -> Unit) {
    var library by remember { mutableStateOf(initialLibrary) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedPrompt?>(null) }
    fun update(next: PromptLibrary) { library = next; onChange(next) }
    val showEditor = creating || editing != null
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().windowHeightIn(maxFraction = 0.85f).padding(16.dp)) {
                if (showEditor) {
                    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
                    var content by remember(editing?.id) { mutableStateOf(editing?.content.orEmpty()) }
                    var role by remember(editing?.id) { mutableStateOf(editing?.role ?: ChatRole.SYSTEM) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { creating = false; editing = null }) {
                            FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back to prompt list")
                        }
                        Text(if (creating) "New prompt" else "Edit prompt", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(enabled = name.isNotBlank() && content.isNotBlank(), onClick = {
                            val id = editing?.id ?: UUID.randomUUID().toString()
                            val prompt = SavedPrompt(id, name.trim(), content.trim(), role)
                            update(library.copy(
                                prompts = if (editing == null) library.prompts + prompt else library.prompts.map { if (it.id == id) prompt else it },
                                selectedIds = if (editing == null) library.selectedIds + id else library.selectedIds))
                            creating = false; editing = null
                        }) { FluentIcon(Icons.Outlined.Save, "Save prompt") }
                        IconButton(onClick = onDismiss) { FluentIcon(Icons.Outlined.Close, "Close prompts") }
                    }
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectableOutlinedTextField(name, { name = it }, label = { Text("Prompt name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val roles = listOf(ChatRole.SYSTEM, ChatRole.USER, ChatRole.MODEL)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            roles.forEachIndexed { index, value ->
                                SegmentedButton(selected = role == value, onClick = { role = value },
                                    shape = SegmentedButtonDefaults.itemShape(index, roles.size)) { Text(promptRoleName(value)) }
                            }
                        }
                        SelectableOutlinedTextField(content, { content = it }, label = { Text("Prompt") },
                            minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    val allSelected = library.prompts.isNotEmpty() && library.prompts.all { it.id in library.selectedIds }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Prompts", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { creating = true }) { FluentIcon(Icons.Outlined.Add, "Add prompt") }
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                update(library.copy(selectedIds = if (checked) library.prompts.map { it.id }.toSet() else emptySet()))
                            },
                            enabled = library.prompts.isNotEmpty()
                        )
                        IconButton(onClick = onDismiss) { FluentIcon(Icons.Outlined.Close, "Close prompts") }
                    }
                    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp)) {
                        if (library.prompts.isEmpty()) item { Text("No saved prompts", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.outline) }
                        items(library.prompts, key = { it.id }) { prompt ->
                            val selected = prompt.id in library.selectedIds
                            Surface(shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth().toggleable(selected, role = Role.Checkbox, onValueChange = {
                                    update(library.copy(selectedIds = if (selected) library.selectedIds - prompt.id else library.selectedIds + prompt.id))
                                })) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(selected, onCheckedChange = null)
                                    Column(Modifier.weight(1f)) {
                                        Text(prompt.name, style = MaterialTheme.typography.titleSmall)
                                        Text(promptRoleName(prompt.role), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Text(prompt.content, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column {
                                        IconButton(onClick = { editing = prompt }, modifier = Modifier.size(36.dp)) { FluentIcon(Icons.Outlined.Edit, "Edit ${prompt.name}") }
                                        IconButton(onClick = { update(library.copy(prompts = library.prompts.filterNot { it.id == prompt.id },
                                            selectedIds = library.selectedIds - prompt.id)) }, modifier = Modifier.size(36.dp)) {
                                            FluentIcon(Icons.Outlined.Delete, "Delete ${prompt.name}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun promptRoleName(role: ChatRole): String = when (role) {
    ChatRole.SYSTEM -> "System"
    ChatRole.USER -> "User"
    ChatRole.MODEL -> "Assistant"
}
