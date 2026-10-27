package org.starfall.generative.shared.ui.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.starfall.generative.shared.icons.AppImages

/** An image behind the content, with an opaque-enough themed veil to preserve text contrast. */
@Composable
fun ProfileBackground(image: String?, modifier: Modifier = Modifier) {
    if (image.isNullOrBlank()) return
    val overlay = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        Color.White.copy(alpha = 0.78f)
    } else {
        Color.Black.copy(alpha = 0.76f)
    }
    Box(modifier.fillMaxSize()) {
        AsyncImage(
            model = AppImages.source(image), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(overlay))
    }
}
