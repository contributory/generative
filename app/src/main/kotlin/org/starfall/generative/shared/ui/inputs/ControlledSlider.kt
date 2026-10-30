package org.starfall.generative.shared.ui.inputs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier

/**
 * Controlled Material 3 slider with stable SliderState. The parent remains the
 * source of truth, including programmatic reset and external preference changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlledSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val state = remember(valueRange.start, valueRange.endInclusive, steps) {
        SliderState(value = value, steps = steps, trackRange = valueRange)
    }
    // Updating snapshot state during composition can cause unbounded recomposition
    // when a dialog changes theme/layout. Reconcile external changes afterward.
    SideEffect {
        if (state.value != value) state.value = value
    }
    Slider(
        state = state,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished
    )
}
