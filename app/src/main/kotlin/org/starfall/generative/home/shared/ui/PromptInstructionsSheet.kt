package org.starfall.generative.home.shared.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.ui.LocalPromptControls
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.sheets.AppSheet

/** Bottom sheet that turns each saved prompt on or off for the open chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptInstructionsSheet(onDismiss: () -> Unit) {
    AppSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        PromptInstructionsSheetContent(onLeave = onDismiss)
    }
}

/** [onLeave] runs just before the sheet hands over to Chat Options, so the caller can close it. */
@Composable
internal fun PromptInstructionsSheetContent(
    onBack: (() -> Unit)? = null,
    onLeave: () -> Unit = {}
) {
    val controls = LocalPromptControls.current
    val library = controls.library
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        QuickActionsSubpageHeader(
            title = stringResource(R.string.quick_action_prompt_instructions),
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        onLeave()
                        controls.openInstructions()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    FluentIcon(
                        Icons.Outlined.Settings,
                        stringResource(R.string.prompt_open_instructions)
                    )
                }
            }
        )
        if (library.prompts.isEmpty()) {
            Text(
                stringResource(R.string.prompt_library_empty),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    library.prompts.forEach { prompt ->
                        PromptToggleRow(
                            prompt = prompt,
                            active = prompt.id in library.selectedIds,
                            onToggle = { controls.setPromptEnabled(prompt.id, it) },
                            onEdit = {
                                onLeave()
                                controls.editPrompt(prompt.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PromptToggleRow(
    prompt: SavedPrompt,
    active: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) { toggleableState = ToggleableState(active) }
            .combinedClickable(
                role = Role.Switch,
                onLongClickLabel = stringResource(R.string.prompt_edit_accessibility, prompt.name),
                onLongClick = onEdit,
                onClick = { onToggle(!active) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                prompt.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = active, onCheckedChange = null)
    }
}
