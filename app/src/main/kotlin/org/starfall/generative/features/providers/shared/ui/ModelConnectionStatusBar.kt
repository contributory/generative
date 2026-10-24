package org.starfall.generative.features.providers.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.starfall.generative.R
import org.starfall.generative.features.providers.shared.model.ModelConnectionProgress
import org.starfall.generative.features.providers.shared.model.ModelConnectionStatus
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.FluentUi
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

/** A third of the strip has to be dragged sideways before the finished notice is dismissed. */
private const val SwipeToDismissFraction = 1f / 3f

/**
 * Editor status strip for model connection testing: "tested of total" progress while probes run, then
 * the finished summary, which stays until it is swiped sideways or closed.
 */
@Composable
internal fun ModelConnectionStatusBar(
    progress: ModelConnectionProgress,
    testing: Boolean,
    showSummary: Boolean,
    onDismissSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = testing || showSummary,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val scope = rememberCoroutineScope()
        var stripWidth by remember { mutableFloatStateOf(0f) }
        // The strip's own horizontal offset doubles as the swipe gesture, so releasing before the
        // dismissal threshold can animate it back into place.
        val swipe = remember(showSummary) { Animatable(0f) }
        val summaryStatus = if (testing) null else when {
            progress.failures > 0 -> ModelConnectionStatus.FAILED
            progress.warnings > 0 -> ModelConnectionStatus.WARNING
            else -> ModelConnectionStatus.PASSED
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("model-connection-status")
                .onSizeChanged { stripWidth = it.width.toFloat() }
                .graphicsLayer {
                    translationX = swipe.value
                    alpha = if (stripWidth <= 0f) 1f else (1f - abs(swipe.value) / stripWidth).coerceIn(0f, 1f)
                }
                .pointerInput(testing) {
                    if (testing) return@pointerInput
                    // Only the finished notice can be swiped away; a running test has nothing to dismiss yet.
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val dismissed = stripWidth > 0f && abs(swipe.value) >= stripWidth * SwipeToDismissFraction
                            scope.launch {
                                if (dismissed) {
                                    swipe.snapTo(0f)
                                    onDismissSummary()
                                } else swipe.animateTo(0f)
                            }
                        },
                        onDragCancel = { scope.launch { swipe.snapTo(0f) } }
                    ) { _, dragAmount -> scope.launch { swipe.snapTo(swipe.value + dragAmount) } }
                },
            shape = RoundedCornerShape(12.dp),
            color = prominentContainerColor(),
            border = prominentContainerBorder()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (summaryStatus == null) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary)
                else {
                    FluentIcon(summaryStatus.icon(), null, tint = summaryStatus.accent(), modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.connection_tests_finished), style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium, color = summaryStatus.textColor())
                }
                // Tested/total plus one icon chip per outcome; the numbers speak for themselves.
                ModelConnectionCounts(progress, showProgress = true, modifier = Modifier.weight(1f))
                if (summaryStatus != null) IconButton(onClick = onDismissSummary) {
                    FluentIcon(FluentUi.Regular.Dismiss, stringResource(R.string.common_close), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
