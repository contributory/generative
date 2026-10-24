package org.starfall.generative.shared.ui.logging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.starfall.generative.R
import org.starfall.generative.shared.logging.DebugLog
import org.starfall.generative.shared.logging.DebugLogEntry
import org.starfall.generative.shared.model.logging.DebugLogLevel
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.nestedContainerBorder
import org.starfall.generative.shared.ui.layout.nestedContainerColor
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

private const val COLLAPSED_LINES = 6

/** Live view of [DebugLog]: one block per entry, with copy-block, copy-all and clear-all icon buttons. */
@Composable
internal fun DebugLogPanel(modifier: Modifier = Modifier) {
    val entries by DebugLog.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Follow new entries only while the reader is already at the bottom; scrolling up pauses following.
    LaunchedEffect(entries.lastOrNull()?.id) {
        if (entries.isNotEmpty() && !listState.canScrollForward) listState.scrollToItem(entries.lastIndex)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = prominentContainerColor().copy(alpha = 0.97f),
        border = prominentContainerBorder(),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${stringResource(R.string.debug_log_title)} (${entries.size})",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { copyLog(context, DebugLog.exportAll(entries)) },
                    enabled = entries.isNotEmpty()
                ) {
                    FluentIcon(Icons.Outlined.ContentCopy, stringResource(R.string.debug_log_copy_all), Modifier.size(22.dp))
                }
                IconButton(onClick = DebugLog::clear, enabled = entries.isNotEmpty()) {
                    FluentIcon(Icons.Outlined.Delete, stringResource(R.string.debug_log_clear_all), Modifier.size(22.dp))
                }
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.debug_log_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        DebugLogEntryBlock(entry, onCopy = { copyLog(context, entry.format()) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugLogEntryBlock(entry: DebugLogEntry, onCopy: () -> Unit) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val accent = when (entry.level) {
        DebugLogLevel.ERROR -> MaterialTheme.colorScheme.error
        DebugLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        DebugLogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = nestedContainerColor(),
        border = nestedContainerBorder()
    ) {
        Column(Modifier.padding(start = 12.dp, top = 2.dp, end = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${stringResource(entry.category.labelRes())} · ${entry.tag}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Text(
                        entry.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                    FluentIcon(Icons.Outlined.ContentCopy, stringResource(R.string.debug_log_copy_entry), Modifier.size(18.dp))
                }
            }
            Text(
                entry.message,
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun copyLog(context: Context, text: String) {
    val copied = runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: error(context.getString(R.string.debug_clipboard_unavailable))
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.debug_log_clip_label), text))
    }.isSuccess
    // Android 13+ shows its own confirmation after a copy.
    if (!copied) Toast.makeText(context, R.string.debug_log_copy_failed, Toast.LENGTH_SHORT).show()
    else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.common_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
