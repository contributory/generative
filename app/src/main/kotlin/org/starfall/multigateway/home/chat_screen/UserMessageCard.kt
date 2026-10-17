package org.starfall.multigateway.home.chat_screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.theme.LocalAmoledMode
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessageCard(
    message: StoredMessage,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSwitchVersion: (Int) -> Unit,
    selectedImageAttachments: List<String> = emptyList(),
    onToggleChatImage: ((String) -> Unit)? = null,
    onResend: (() -> Unit)? = null,
    onResendFromMenu: (() -> Unit)? = null,
    resendEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = maxWidth * 0.85f),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (LocalAmoledMode.current) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                } else null,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .combinedClickable(
                        onClick = { if (message.isQueued) showMenu = true },
                        onLongClick = { showMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (message.files.isNotEmpty()) {
                        AttachmentStrip(
                            references = message.files,
                            removable = false,
                            modifier = Modifier.padding(bottom = 8.dp),
                            compact = false,
                            selectedImageAttachments = selectedImageAttachments,
                            onToggleChatImage = onToggleChatImage
                        )
                    }

                    // Text Content
                    // Same renderer (markdown, LaTeX, code, tables, font and size) as AI messages.
                    val messageText: @Composable () -> Unit = {
                        MarkdownRenderer(content = message.content, fillMaxWidth = false, trimLeadingBlockSpace = true)
                    }
                    if (message.isQueued) {
                        messageText()
                    } else {
                        // Selection only starts from a long press that lands on the text itself; a press on blank
                        // space inside the bubble is left to the message menu instead of selecting a nearby word.
                        val glyphRegistry = remember { GlyphHitRegistry() }
                        var selectionBlocked by remember { mutableStateOf(false) }
                        val latestText by rememberUpdatedState(messageText)
                        val movableText = remember { movableContentOf { latestText() } }
                        CompositionLocalProvider(
                            LocalGlyphHitRegistry provides glyphRegistry,
                            LocalSelectionBlocked provides selectionBlocked
                        ) {
                            Box(
                                Modifier
                                    .onGloballyPositioned { glyphRegistry.owner = it }
                                    .glyphSelectionGate(glyphRegistry) { selectionBlocked = it }
                            ) {
                                GatedSelectionContainer { movableText() }
                            }
                        }
                    }

                    // Version switcher if multiple versions exist
                    if (!message.isQueued && message.versions.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    if (message.activeVersionIndex > 0) {
                                        onSwitchVersion(message.activeVersionIndex - 1)
                                    }
                                },
                                enabled = message.activeVersionIndex > 0,
                                modifier = Modifier.size(24.dp)
                            ) {
                                FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev Version", modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = "${message.activeVersionIndex + 1}/${message.versions.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (message.activeVersionIndex < message.versions.size - 1) {
                                        onSwitchVersion(message.activeVersionIndex + 1)
                                    }
                                },
                                enabled = message.activeVersionIndex < message.versions.size - 1,
                                modifier = Modifier.size(24.dp)
                            ) {
                                FluentIcon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Version", modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Dropdown Context Menu
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (!message.isQueued) DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { FluentIcon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Copied", message.content))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                        if (onResendFromMenu != null) DropdownMenuItem(
                            text = { Text(stringResource(org.starfall.multigateway.R.string.resend_message)) },
                            leadingIcon = { FluentIcon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                            enabled = resendEnabled,
                            onClick = {
                                showMenu = false
                                onResendFromMenu()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { FluentIcon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit(message.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (message.isQueued) "Cancel" else "Delete") },
                            leadingIcon = { FluentIcon(if (message.isQueued) Icons.Outlined.Close else Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onDelete(message.id)
                            }
                        )
                    }
                }
            }
            if (onResend != null) {
                IconButton(onClick = onResend, enabled = resendEnabled, modifier = Modifier.size(40.dp)) {
                    FluentIcon(Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(org.starfall.multigateway.R.string.resend_message),
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
