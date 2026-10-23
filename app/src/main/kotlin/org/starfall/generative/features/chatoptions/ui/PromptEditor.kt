package org.starfall.generative.features.chatoptions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptEditor(
    prompt: SavedPrompt?,
    onSave: (SavedPrompt) -> Unit,
    onPreview: (SavedPrompt) -> Unit,
    onBack: (SavedPrompt) -> Unit
) {
    val promptId = prompt?.id.orEmpty()
    var name by rememberSaveable(promptId) { mutableStateOf(prompt?.name.orEmpty()) }
    var content by rememberSaveable(promptId) { mutableStateOf(prompt?.content.orEmpty()) }
    var role by rememberSaveable(promptId) { mutableStateOf(prompt?.role ?: ChatRole.SYSTEM) }
    var roleMenuExpanded by remember { mutableStateOf(false) }

    fun draft(): SavedPrompt = SavedPrompt(
        id = prompt?.id.orEmpty(),
        name = name.trim(),
        content = content.trim(),
        role = role
    )

    BackHandler(enabled = LocalScreenTransitionActive.current, onBack = { onBack(draft()) })
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (name.isBlank()) {
                                Text(
                                    stringResource(
                                        if (prompt == null) R.string.prompt_editor_new_title
                                        else R.string.prompt_name
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack(draft()) }) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(
                        enabled = name.isNotBlank() && content.isNotBlank(),
                        onClick = { onSave(draft()) }
                    ) { Text(stringResource(R.string.common_save)) }
                    Box {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            IconButton(onClick = { roleMenuExpanded = true }) {
                                FluentIcon(
                                    Icons.Outlined.AccountCircle,
                                    stringResource(R.string.prompt_role_accessibility, promptRoleLabel(role))
                                )
                            }
                            Text(
                                promptRoleLabel(role),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = roleMenuExpanded,
                            onDismissRequest = { roleMenuExpanded = false }
                        ) {
                            listOf(ChatRole.SYSTEM, ChatRole.USER, ChatRole.MODEL).forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(promptRoleLabel(option)) },
                                    onClick = {
                                        role = option
                                        roleMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { if (name.isNotBlank() && content.isNotBlank()) onPreview(draft()) }
                    ) {
                        FluentIcon(
                            Icons.Outlined.Visibility,
                            stringResource(R.string.prompt_preview_open)
                        )
                    }
                }
            )
        }
    ) { padding ->
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(stringResource(R.string.prompt_content)) },
            placeholder = { Text(stringResource(R.string.prompt_content_hint)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            maxLines = Int.MAX_VALUE,
            textStyle = MaterialTheme.typography.bodyLarge
        )
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
