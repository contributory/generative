package org.starfall.generative.shared.ui.model

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.theme.LocalAmoledMode
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

/** Muted divider between the folder sections in the shared picker list. */
@Composable
internal fun ModelPickerSection(label: String, tag: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    val rowShape = RoundedCornerShape(12.dp)
    val amoledSelected = LocalAmoledMode.current && hasSelectedModel
    Surface(
        shape = rowShape,
        color = if (hasSelectedModel && !amoledSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            Color.Transparent
        },
        contentColor = if (hasSelectedModel) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (amoledSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
            .testTag("model-picker-provider_${provider.id}")
            .clip(rowShape)
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
