package org.starfall.multigateway.home.chat.screen.ChatAppbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.ui.res.stringResource
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons

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
            strokeWidth = 4.dp
        )
        Text(
            text = "${status.percentage}%",
            style = if (size < 50.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
            ContextWindowRing(status, 28.dp)
        }
    }
}
