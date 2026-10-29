package org.starfall.generative.features.home.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.shared.ui.text.toComposeFontFamily

/** Displays reasoning verbatim, without Markdown parsing or formatting. */
@Composable
internal fun ThinkingPlainText(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    val preferences = LocalCodeRenderingPreferences.current
    val bodySize = preferences.effectiveMessageFontSize.sp
    Text(
        text = reasoning,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = preferences.messageFontFamily.toComposeFontFamily(),
            fontSize = bodySize,
            lineHeight = bodySize * 1.375f
        )
    )
}
