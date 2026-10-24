package org.starfall.generative.shared.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.theme.LocalAmoledMode
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem

internal data class MediaViewerAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val tint: Color? = null
)

@Composable
internal fun MediaViewerMenu(
    actions: List<MediaViewerAction>,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(8.dp)
    ) {
        Box(Modifier.align(AbsoluteAlignment.TopLeft)) {
            ViewerOverlayButton {
                IconButton(onClick = { expanded = true }) {
                    FluentIcon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.common_more_options),
                        tint = Color.White
                    )
                }
            }
            MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp))) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = prominentContainerColor(),
                    tonalElevation = if (LocalAmoledMode.current) 0.dp else 6.dp,
                    shadowElevation = if (LocalAmoledMode.current) 0.dp else 8.dp,
                    border = prominentContainerBorder()
                ) {
                    actions.forEach { action ->
                        val contentColor = action.tint ?: Color.Unspecified
                        RoundedDropdownMenuItem(
                            text = { Text(action.label, color = contentColor) },
                            leadingIcon = {
                                FluentIcon(
                                    action.icon,
                                    contentDescription = null,
                                    tint = action.tint ?: MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                expanded = false
                                action.onClick()
                            }
                        )
                    }
                }
            }
        }
        ViewerOverlayButton(modifier = Modifier.align(AbsoluteAlignment.TopRight)) {
            IconButton(onClick = onDismiss) {
                FluentIcon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.media_close_viewer),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ViewerOverlayButton(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.48f),
        content = content
    )
}
