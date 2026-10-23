package org.starfall.generative.shared.ui.menus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.generative.shared.theme.LocalAmoledMode
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@Composable
fun ItemContextMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteColor: Color,
    firstActionText: String? = null,
    firstActionIcon: Int? = null,
    onFirstAction: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(enabled = enabled, onClick = { expanded = true }) {
            FluentIcon(Icons.Default.MoreVert, contentDescription = "More options")
        }

        MaterialTheme(
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp))
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = prominentContainerColor(),
                tonalElevation = if (LocalAmoledMode.current) 0.dp else 6.dp,
                shadowElevation = if (LocalAmoledMode.current) 0.dp else 8.dp,
                border = prominentContainerBorder()
            ) {
                if (firstActionText != null && firstActionIcon != null && onFirstAction != null) {
                    DropdownMenuItem(
                        text = { Text(firstActionText) },
                        leadingIcon = { FluentIcon(firstActionIcon, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onFirstAction()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { FluentIcon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = deleteColor) },
                    leadingIcon = {
                        FluentIcon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = deleteColor
                        )
                    },
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}
