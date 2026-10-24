package org.starfall.generative.home.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.starfall.generative.R
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.MessageVersion
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.SummaryRole
import org.starfall.generative.shared.model.generation.hasToolCalls
import org.starfall.generative.shared.model.language.DEFAULT_CONTEXT_WINDOW_TOKENS
import org.starfall.generative.shared.model.language.ModelConfiguration
import org.starfall.generative.shared.model.language.sendsThinkingBack
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.FeaturesLogoImage
import org.starfall.generative.home.chat.model.ContextWindowStatus
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@Composable
fun ChatAppBar(
    modelName: String,
    providerName: String,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    featuresLogoImage: String? = null,
    onOpenModelPicker: () -> Unit = {},
    /** True while at least one remote machine holds a live connection. */
    showTerminalButton: Boolean = false,
    onOpenTerminal: () -> Unit = {}
) {
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
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
        val buttonColor = prominentContainerColor().copy(alpha = 0.94f)
        val buttonBorder = prominentContainerBorder()
        Surface(
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp),
            shape = CircleShape,
            color = buttonColor,
            border = buttonBorder,
            shadowElevation = if (buttonBorder == null) 4.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                FluentIcon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.chat_open_side_navigation),
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
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenModelPicker)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
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
                if (showTerminalButton) {
                    Surface(
                        onClick = onOpenTerminal,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = buttonColor,
                        border = buttonBorder,
                        shadowElevation = if (buttonBorder == null) 4.dp else 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            FluentIcon(
                                imageVector = Icons.Outlined.Terminal,
                                contentDescription = stringResource(R.string.remote_machine_open_terminal),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            // Live-connection dot, matching the machines list status indicator.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp, end = 10.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                Surface(
                    onClick = onNewChat,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = buttonColor,
                    border = buttonBorder,
                    shadowElevation = if (buttonBorder == null) 4.dp else 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FluentIcon(
                            imageVector = Icons.Outlined.Compose,
                            contentDescription = stringResource(R.string.sidebar_new_chat),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = buttonColor,
                    border = buttonBorder,
                    shadowElevation = if (buttonBorder == null) 4.dp else 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FeaturesLogoImage(featuresLogoImage, Modifier.size(40.dp).clip(CircleShape))
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
            Text(stringResource(R.string.chat_context_window), style = MaterialTheme.typography.titleSmall)
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
            text = stringResource(R.string.chat_context_window_tokens, formatContextTokens(status.usedTokens), formatContextTokens(status.limit.toLong())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(when {
                status.apiUsage == null -> R.string.context_window_source_estimated
                status.estimatedAdditionalTokens > 0 -> R.string.context_window_source_mixed
                else -> R.string.context_window_source_api
            }),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        status.apiUsage?.let { usage ->
            Text(
                text = if (usage.outputTokens != null) stringResource(R.string.context_window_api_breakdown,
                    formatContextTokens(usage.inputTokens), formatContextTokens(usage.outputTokens))
                else stringResource(R.string.context_window_api_input_only, formatContextTokens(usage.inputTokens)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
@Composable
internal fun ContextWindowRing(status: ContextWindowStatus, size: androidx.compose.ui.unit.Dp) {
    val progress = (status.usedTokens.toFloat() / status.limit.coerceAtLeast(1)).coerceIn(0f, 1f)
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
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = prominentContainerColor(),
        border = prominentContainerBorder()
    ) {
        Box(contentAlignment = Alignment.Center) {
            ContextWindowRing(status, 32.dp)
        }
    }
}

internal fun formatContextTokens(tokens: Long): String =
    String.format(Locale.US, "%,d", tokens.coerceAtLeast(0L))
