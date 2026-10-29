package org.starfall.generative.features.home.chat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import org.starfall.generative.shared.ui.text.GatedSelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.LoadingIndicator
import org.starfall.generative.features.home.chat.ui.markdown.MarkdownRenderer
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@Composable
fun MessageBubble(
    message: StoredMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatRole.USER
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 12.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SharedIcon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = stringResource(R.string.message_ai_role),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Surface(
            shape = if (isUser) {
                RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            } else {
                RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            },
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                prominentContainerColor()
            },
            border = if (isUser) null else prominentContainerBorder(),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                if (message.content.isBlank()) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                } else {
                    GatedSelectionContainer {
                        MarkdownRenderer(content = message.content)
                    }
                }

                if (!isUser && message.content.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(context.getString(R.string.common_copy), message.content)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.common_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            SharedIcon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.common_copy),
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
