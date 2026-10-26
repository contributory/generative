package org.starfall.generative.features.home.chat.ui.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/** Renders complete fallback text as one selectable node; chunking here breaks Select all. */
@Composable
internal fun OverflowPlainText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    expandToWidth: Boolean = true
) {
    Text(
        text = text,
        style = style,
        modifier = if (expandToWidth) modifier.fillMaxWidth() else modifier
    )
}
