package org.starfall.generative.features.chatoptions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.SavedPrompt
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PromptEditor(
    prompt: SavedPrompt?,
    onPreview: (SavedPrompt) -> Unit,
    onBack: (SavedPrompt) -> Unit
) {
    val promptId = prompt?.id.orEmpty()
    var name by rememberSaveable(promptId) { mutableStateOf(prompt?.name.orEmpty()) }
    var content by rememberSaveable(promptId) { mutableStateOf(prompt?.content.orEmpty()) }
    var role by rememberSaveable(promptId) { mutableStateOf(prompt?.role ?: ChatRole.SYSTEM) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

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
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .testTag("prompt_name_input"),
                        decorationBox = { innerTextField ->
                            if (name.isBlank()) {
                                Text(
                                    stringResource(R.string.prompt_name_hint),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                    Box {
                        AssistChip(
                            onClick = { roleMenuExpanded = true },
                            label = { Text(promptRoleLabel(role)) },
                            leadingIcon = {
                                FluentIcon(
                                    Icons.Outlined.AccountCircle,
                                    stringResource(R.string.prompt_role_accessibility, promptRoleLabel(role)),
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            trailingIcon = {
                                FluentIcon(
                                    Icons.Default.KeyboardArrowDown,
                                    null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
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
                        enabled = name.isNotBlank() && content.isNotBlank(),
                        onClick = { onPreview(draft()) }
                    ) {
                        FluentIcon(
                            Icons.Outlined.Visibility,
                            stringResource(R.string.prompt_preview_open)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
            )
        }
    ) { padding ->
        SelectableOutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(stringResource(R.string.prompt_content)) },
            placeholder = { Text(stringResource(R.string.prompt_content_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("prompt_content_input"),
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
