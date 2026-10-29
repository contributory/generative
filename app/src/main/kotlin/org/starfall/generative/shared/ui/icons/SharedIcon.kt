package org.starfall.generative.shared.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.res.painterResource

/** Shared icon renderer for drawable resources and Compose image vectors. */
@Composable
fun SharedIcon(
    @DrawableRes imageVector: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    MaterialIcon(painter = painterResource(imageVector), contentDescription = contentDescription, modifier = modifier, tint = tint)
}

/** Renders image vectors without coupling the API to a specific icon set. */
@Composable
fun SharedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    MaterialIcon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier, tint = tint)
}
