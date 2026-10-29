package org.starfall.generative.features.customization.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.storage.preferences.AppPreferences
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import kotlin.math.roundToInt

@Composable
internal fun InputAreaSettings(
    appearance: AppPreferences,
    onTransparencyChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit
) {
    var transparency by remember(appearance.inputAreaTransparency) {
        mutableFloatStateOf(appearance.inputAreaTransparency.coerceIn(0f, 1f))
    }
    var blur by remember(appearance.inputAreaBlur) {
        mutableFloatStateOf(appearance.inputAreaBlur.coerceIn(0f, 32f))
    }
    val transparencyLabel = stringResource(R.string.customization_input_transparency)
    val blurLabel = stringResource(R.string.customization_input_blur)
    Surface(shape = RoundedCornerShape(28.dp), color = prominentContainerColor()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.customization_input_title), style = MaterialTheme.typography.titleMedium)
            Text("$transparencyLabel · ${(transparency * 100).roundToInt()}%")
            Slider(
                value = transparency, onValueChange = { transparency = it },
                onValueChangeFinished = { onTransparencyChange(transparency) },
                modifier = Modifier.testTag("input-area-transparency-slider")
                    .semantics { contentDescription = transparencyLabel }
            )
            Text("$blurLabel · ${blur.roundToInt()} dp")
            Slider(
                value = blur, onValueChange = { blur = it }, valueRange = 0f..32f,
                onValueChangeFinished = { onBlurChange(blur) },
                modifier = Modifier.testTag("input-area-blur-slider")
                    .semantics { contentDescription = blurLabel }
            )
            Text(stringResource(R.string.customization_input_blur_description),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                transparency = 0.08f
                blur = 0f
                onTransparencyChange(transparency)
                onBlurChange(blur)
            }, modifier = Modifier.testTag("input-area-appearance-reset")) {
                Text(stringResource(R.string.common_reset))
            }
        }
    }
}
