package org.starfall.generative.shared.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.layout.appearanceContainerColor
import org.starfall.generative.shared.ui.text.GatedSelectionContainer

/** Consistent, selectable error details with optional retry/dismiss actions and built-in copying. */
@Composable
fun ErrorContainer(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    copyText: String = message,
    initiallyExpanded: Boolean = false,
    expandable: Boolean = true,
    collapsedLines: Int = 3,
    messageStyle: TextStyle? = null,
    copyButtonModifier: Modifier = Modifier.testTag("error-container-copy")
) {
    val context = LocalContext.current
    var expanded by remember(message, initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val foreground = MaterialTheme.colorScheme.onErrorContainer
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = appearanceContainerColor(MaterialTheme.colorScheme.errorContainer, opacityBoost = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            Modifier.fillMaxWidth().animateContentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.weight(1f)
                        .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SharedIcon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title ?: stringResource(R.string.common_error),
                        style = MaterialTheme.typography.labelLarge,
                        color = foreground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onRetry != null) {
                    TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = foreground)) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.message_error_clip_label), copyText))
                        Toast.makeText(context, context.getString(R.string.message_error_copied), Toast.LENGTH_SHORT).show()
                    },
                    modifier = copyButtonModifier.size(32.dp)
                ) {
                    SharedIcon(Icons.Outlined.ContentCopy, stringResource(R.string.message_copy_error),
                        modifier = Modifier.size(18.dp), tint = foreground)
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).testTag("error-container-dismiss")) {
                        SharedIcon(Icons.Outlined.Close, stringResource(R.string.chat_dismiss_error),
                            modifier = Modifier.size(18.dp), tint = foreground)
                    }
                }
                if (expandable) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(32.dp).testTag("error-container-toggle")
                    ) {
                        SharedIcon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            stringResource(if (expanded) R.string.message_collapse_error else R.string.message_expand_error),
                            modifier = Modifier.size(18.dp), tint = foreground
                        )
                    }
                }
            }
            GatedSelectionContainer {
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = foreground,
                    style = messageStyle ?: MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = if (expandable && !expanded) collapsedLines.coerceAtLeast(1) else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
