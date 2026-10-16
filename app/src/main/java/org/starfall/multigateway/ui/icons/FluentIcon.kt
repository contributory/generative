package org.starfall.multigateway.ui.icons

import org.starfall.multigateway.ui.icons.FluentIcon

import androidx.annotation.DrawableRes
import androidx.compose.material.Icon as MaterialIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material.LocalContentColor
import androidx.compose.ui.res.painterResource

/** Compose adapter for Microsoft's official Fluent System Icons Android VectorDrawables. */
@Composable
fun FluentIcon(
    @DrawableRes imageVector: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    MaterialIcon(painter = painterResource(imageVector), contentDescription = contentDescription, modifier = modifier, tint = tint)
}

/** Keeps support for non-Fluent ImageVector icons used by a few unrelated UI elements. */
@Composable
fun FluentIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    MaterialIcon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier, tint = tint)
}
