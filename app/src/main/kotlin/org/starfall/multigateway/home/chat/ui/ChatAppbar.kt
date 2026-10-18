package org.starfall.multigateway.home.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.model.SummaryRole
import org.starfall.multigateway.shared.model.generation.hasToolCalls
import org.starfall.multigateway.shared.model.language.DEFAULT_CONTEXT_WINDOW_TOKENS
import org.starfall.multigateway.shared.model.language.ModelConfiguration
import org.starfall.multigateway.shared.model.language.sendsThinkingBack
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.home.chat.model.ContextWindowStatus

@Composable
fun ChatAppBar(
    modelName: String,
    providerName: String,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelPicker: () -> Unit = {},
    onOpenSummary: () -> Unit = {},
    contextWindowStatus: ContextWindowStatus? = null,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    var contextMenuVisible by remember { mutableStateOf(false) }
    val displayedModelName = modelName.substringAfterLast('/').ifBlank { modelName }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp + statusBarHeight)
            // Background first so it extends under the status bar. It fades from fully
            // transparent at the bottom to dark at the top.
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    )
                )
            )
            .padding(top = statusBarHeight)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenDrawer),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 4.dp,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                FluentIcon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open side navigation",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(start = 68.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenModelPicker)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = displayedModelName.ifBlank { stringResource(R.string.select_model) },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (providerName.isNotBlank()) {
                    Text(
                        text = providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                contextWindowStatus?.let { status ->
                    Box {
                        ContextWindowIndicator(
                            status = status,
                            onClick = { contextMenuVisible = true }
                        )
                        DropdownMenu(
                            expanded = contextMenuVisible,
                            onDismissRequest = { contextMenuVisible = false },
                            modifier = Modifier.width(228.dp)
                        ) {
                            ContextWindowDetails(
                                status = status,
                                onCompact = {
                                    contextMenuVisible = false
                                    onOpenSummary()
                                }
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp
                ) {
                    IconButton(onClick = onNewChat, modifier = Modifier.fillMaxSize()) {
                        FluentIcon(
                            imageVector = Icons.Outlined.Compose,
                            contentDescription = "New chat",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.multigateway_launcher_round),
                            contentDescription = "Features",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContextWindowDetails(status: ContextWindowStatus, onCompact: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Context window", style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.context_window_compact_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onCompact)
        ) {
            ContextWindowRing(status, 96.dp)
        }
        Text(
            text = "${formatContextTokens(status.estimatedTokens)} / ${formatContextTokens(status.limit.toLong())} tokens",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
internal fun ContextWindowRing(status: ContextWindowStatus, size: androidx.compose.ui.unit.Dp) {
    val progress = (status.estimatedTokens.toFloat() / status.limit.coerceAtLeast(1)).coerceIn(0f, 1f)
    val color = when {
        status.percentage >= 90 -> MaterialTheme.colorScheme.error
        status.percentage >= 75 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            strokeWidth = if (size < 50.dp) 3.dp else 4.dp
        )
        if (size < 50.dp) {
            // Fixed size in dp (ignores the system font scale) so "100%" always fits inside the ring.
            val digitSize = with(androidx.compose.ui.platform.LocalDensity.current) {
                (if (status.percentage >= 100) 7.dp else 8.dp).toSp()
            }
            Text(
                text = "${status.percentage}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = digitSize, lineHeight = digitSize),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        } else {
            Text(
                text = "${status.percentage}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
@Composable
internal fun ContextWindowIndicator(
    status: ContextWindowStatus,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ContextWindowRing(status, 32.dp)
        }
    }
}

internal fun formatContextTokens(tokens: Long): String =
    String.format(Locale.US, "%,d", tokens.coerceAtLeast(0L))
