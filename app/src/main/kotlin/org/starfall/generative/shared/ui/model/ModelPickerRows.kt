package org.starfall.generative.shared.ui.model

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

// Each lazy row draws its part of one continuous folder outline, including the
// spacing between rows. Keeping rows lazy preserves scrolling to selected models.
internal fun Modifier.folderOutline(first: Boolean, last: Boolean, color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val left = stroke / 2
    val right = size.width - stroke / 2
    val top = if (first) stroke / 2 else -4.dp.toPx()
    val bottom = if (last) size.height - stroke / 2 else size.height + 4.dp.toPx()
    val radius = 18.dp.toPx().coerceAtMost((bottom - top) / 2)
    // Draw the two sides independently. Joining the open segments with moveTo()
    // makes visible diagonal/kinked corners when LazyColumn spacing is present.
    drawLine(color, Offset(left, top + if (first) radius else 0f),
        Offset(left, bottom - if (last) radius else 0f), strokeWidth = stroke)
    drawLine(color, Offset(right, top + if (first) radius else 0f),
        Offset(right, bottom - if (last) radius else 0f), strokeWidth = stroke)
    if (first || last) {
        val path = Path().apply {
            if (first) {
                moveTo(left, top + radius)
                quadraticTo(left, top, left + radius, top)
                lineTo(right - radius, top)
                quadraticTo(right, top, right, top + radius)
            }
            if (last) {
                moveTo(left, bottom - radius)
                quadraticTo(left, bottom, left + radius, bottom)
                lineTo(right - radius, bottom)
                quadraticTo(right, bottom, right, bottom - radius)
            }
        }
        drawPath(path, color, style = Stroke(stroke))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ModelPickerProviderRow(
    provider: ModelProvider,
    collapsed: Boolean,
    hasSelectedModel: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (hasSelectedModel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else Color.Transparent,
        contentColor = if (hasSelectedModel) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
            .testTag("model-picker-provider_${provider.id}")
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FluentIcon(
                if (collapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(if (collapsed) R.string.expand_group else R.string.collapse_group, provider.name),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            ProviderMark(
                provider = provider,
                compact = true
            )
            Spacer(Modifier.width(8.dp))
            Text(
                provider.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                 color = if (hasSelectedModel) MaterialTheme.colorScheme.onPrimaryContainer
                     else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                 fontWeight = FontWeight.Medium
             )
             if (hasSelectedModel) {
                 FluentIcon(
                     Icons.Filled.Check,
                     contentDescription = null,
                     tint = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.size(18.dp)
                 )
             }
        }
    }
}
