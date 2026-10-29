package org.starfall.generative.shared.ui.layout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Full-width horizontal transition, drawn on a graphics layer without changing layout. */
internal fun screenSlideSpec(): FiniteAnimationSpec<Float> = tween(380, easing = FastOutSlowInEasing)

/** Forward: incoming from right, outgoing to left. Pop: the opposite. */
internal fun screenSlideOffset(active: Boolean, incoming: Boolean, popping: Boolean): Float = when {
    active -> 0f
    incoming -> if (popping) -1f else 1f
    else -> if (popping) 1f else -1f
}

internal fun Modifier.screenSlide(offset: () -> Float): Modifier = graphicsLayer {
    translationX = size.width * offset()
}
