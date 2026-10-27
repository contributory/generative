package org.starfall.generative.features.chatoptions.ui

import org.starfall.generative.shared.ui.layout.edgeToEdgeTopAppBarColors
import org.starfall.generative.shared.ui.menus.AppDropdownMenu as DropdownMenu
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.starfall.generative.R
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.SavedInstruction
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.text.FullScreenEditorTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstructionEditor(
    instruction: SavedInstruction?,
    onPreview: (SavedInstruction) -> Unit,
    onBack: (SavedInstruction) -> Unit
) {
    val instructionId = instruction?.id.orEmpty()
    var name by rememberSaveable(instructionId) { mutableStateOf(instruction?.name.orEmpty()) }
    var content by rememberSaveable(instructionId) { mutableStateOf(instruction?.content.orEmpty()) }
    var role by rememberSaveable(instructionId) { mutableStateOf(instruction?.role ?: ChatRole.SYSTEM) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun draft(): SavedInstruction = SavedInstruction(
        id = instruction?.id.orEmpty(),
        name = name.trim(),
        content = content.trim(),
        role = role
    )

    BackHandler(enabled = LocalScreenTransitionActive.current, onBack = { onBack(draft()) })
    Scaffold(
        topBar = {
            TopAppBar(
                colors = edgeToEdgeTopAppBarColors(),
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
                            .testTag("instruction_name_input"),
                        decorationBox = { innerTextField ->
                            if (name.isBlank()) {
                                Text(
                                    stringResource(R.string.common_name),
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
                            label = { Text(instructionRoleLabel(role)) },
                            leadingIcon = {
                                FluentIcon(
                                    Icons.Outlined.AccountCircle,
                                    stringResource(R.string.instruction_role_accessibility, instructionRoleLabel(role)),
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
                                    text = { Text(instructionRoleLabel(option)) },
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
                            stringResource(R.string.instruction_preview_open)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
            )
        }
    ) { padding ->
        FullScreenEditorTextField(
            value = content,
            onValueChange = { content = it },
            placeholder = stringResource(R.string.instruction_content_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .testTag("instruction_content_input"),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        )
    }
}

@Composable
internal fun instructionRoleLabel(role: ChatRole): String = stringResource(
    when (role) {
        ChatRole.SYSTEM -> R.string.common_system
        ChatRole.USER -> R.string.common_user
        ChatRole.MODEL -> R.string.common_assistant
    }
)
