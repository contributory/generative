package org.starfall.generative.features.chatoptions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptEditor(
    prompt: SavedPrompt?,
    onSave: (SavedPrompt) -> Unit,
    onBack: () -> Unit
) {
    val promptId = prompt?.id.orEmpty()
    var name by rememberSaveable(promptId) { mutableStateOf(prompt?.name.orEmpty()) }
    var content by rememberSaveable(promptId) { mutableStateOf(prompt?.content.orEmpty()) }
    var role by rememberSaveable(promptId) { mutableStateOf(prompt?.role ?: ChatRole.SYSTEM) }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (prompt == null) R.string.prompt_editor_new_title else R.string.prompt_editor_edit_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(
                        enabled = name.isNotBlank() && content.isNotBlank(),
                        onClick = {
                            onSave(
                                SavedPrompt(
                                    id = prompt?.id.orEmpty(),
                                    name = name.trim(),
                                    content = content.trim(),
                                    role = role
                                )
                            )
                        }
                    ) { Text(stringResource(R.string.common_save)) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.prompt_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.prompt_role),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val roles = listOf(ChatRole.SYSTEM, ChatRole.USER, ChatRole.MODEL)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    roles.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = role == option,
                            onClick = { role = option },
                            shape = SegmentedButtonDefaults.itemShape(index, roles.size)
                        ) { Text(promptRoleLabel(option)) }
                    }
                }
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.prompt_content)) },
                placeholder = { Text(stringResource(R.string.prompt_content_hint)) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 12,
                maxLines = Int.MAX_VALUE,
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
internal fun promptRoleLabel(role: ChatRole): String = stringResource(
    when (role) {
        ChatRole.SYSTEM -> R.string.prompt_role_system
        ChatRole.USER -> R.string.prompt_role_user
        ChatRole.MODEL -> R.string.prompt_role_assistant
    }
)
