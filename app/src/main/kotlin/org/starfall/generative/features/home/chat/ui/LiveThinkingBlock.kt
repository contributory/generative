package org.starfall.generative.features.home.chat.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.starfall.generative.R
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.GenerativeLoadingIndicator
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences

/** Follow the measured scroll range without per-line animations or distance thresholds. */
@Composable
internal fun rememberLiveThinkingScrollState(active: Boolean): ScrollState {
    val state = rememberScrollState()
    LiveThinkingScrollEffect(active, state)
    return state
}

@Composable
private fun LiveThinkingScrollEffect(active: Boolean, state: ScrollState) {
    LaunchedEffect(active, state) {
        if (active) snapshotFlow { state.maxValue }.collectLatest { target ->
            // scrollTo() still starts a native scroll mutation when already at the end.
            // Skip it until layout has actually left new content below the visible edge.
            if (target in 1 until Int.MAX_VALUE && state.value < target) state.scrollTo(target)
        }
    }
}

@Composable
internal fun LiveThinkingBlock(reasoning: String, active: Boolean, showHeader: Boolean = true) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val dragging by scrollState.interactionSource.collectIsDraggedAsState()
    var touchingPreview by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableIntStateOf(0) }
    var followPaused by remember { mutableStateOf(false) }
    LaunchedEffect(dragging, touchingPreview, lastInteraction) {
        if (dragging) followPaused = true
        else if (followPaused && !touchingPreview) {
            delay(3_000)
            followPaused = false
        }
    }
    val following = active && !expanded && !followPaused && LocalChatForeground.current
    LiveThinkingScrollEffect(following, scrollState)
    // The ScrollState remains authoritative (including gestures and hit testing). Smooth only
    // the drawn position after native auto-follow jumps; never animate or intercept scroll input.
    val drawnScroll = remember { Animatable(0f) }
    LaunchedEffect(scrollState, following) {
        snapshotFlow { scrollState.value }.collectLatest { offset ->
            if (following) drawnScroll.animateTo(offset.toFloat(), tween(180, easing = FastOutSlowInEasing))
            else drawnScroll.snapTo(offset.toFloat()) // Manual scroll remains visually exact.
        }
    }
    val density = LocalDensity.current
    val preferences = LocalCodeRenderingPreferences.current
    val bodySize = preferences.effectiveMessageFontSize.sp
    val bodyLineHeight = bodySize * 1.375f
    val previewHeight = with(density) { (bodyLineHeight * 3.5f).toDp() }
    val fadeHeight = with(density) { bodyLineHeight.toPx() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        if (showHeader) Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(if (active) R.string.thinking_streaming else R.string.processed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.weight(1f))
            if (active) {
                Spacer(Modifier.width(6.dp))
                GenerativeLoadingIndicator(
                    Modifier.size(12.dp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.width(6.dp))
            FluentIcon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
        if (showHeader) Spacer(Modifier.height(4.dp))
        // The edge mask belongs to the fixed preview viewport, never the scrolling text.
        val fadingEdges = if (!active) Modifier else Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (size.height > 0f && scrollState.maxValue in 1 until Int.MAX_VALUE) {
                    val edge = fadeHeight.coerceAtMost(size.height / 3f).coerceAtLeast(1f)
                    val topAlpha = 1f - (scrollState.value / edge).coerceIn(0f, 1f)
                    val bottomAlpha = 1f - ((scrollState.maxValue - scrollState.value) / edge).coerceIn(0f, 1f)
                    val mask = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = topAlpha),
                        (edge / size.height) to Color.Black,
                        (1f - edge / size.height) to Color.Black,
                        1f to Color.Black.copy(alpha = bottomAlpha)
                    )
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            }
        val previewModifier = if (expanded) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().heightIn(max = previewHeight)
        }
        val scrollModifier = if (expanded) Modifier else Modifier.verticalScroll(scrollState)
        // While the preview is live and collapsed, animating its height would run at the same time
        // as the scroll follower (the viewport is briefly smaller than the content), so the two
        // fight and the text shudders. Size animation is kept for expand/collapse and when idle.
        val animateSize = !active || expanded
        Box(
            Modifier
                .then(if (animateSize) Modifier.animateContentSize() else Modifier)
                .then(previewModifier)
                .testTag("live-thinking-viewport")
                .clip(RoundedCornerShape(8.dp))
                .then(fadingEdges)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .testTag("live-thinking-preview")
                    .pointerInput(Unit) {
                        // Observe touches without consuming them; verticalScroll owns the gesture.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            touchingPreview = true
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                touchingPreview = false
                                lastInteraction++
                            }
                        }
                    }
                    .then(scrollModifier)
                    .clickable { expanded = !expanded }
            ) {
                // Show the original reasoning text. Only the outer viewport applies the edge fade.
                ThinkingPlainText(
                    reasoning = reasoning,
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        translationY = if (following) scrollState.value - drawnScroll.value else 0f
                    }
                )
            }
        }
    }
}
