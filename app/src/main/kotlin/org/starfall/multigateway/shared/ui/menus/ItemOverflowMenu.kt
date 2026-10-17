package org.starfall.multigateway.shared.ui.menus
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
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem

@Composable
fun ItemOverflowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteColor: Color,
    firstActionText: String? = null,
    firstActionIcon: Int? = null,
    onFirstAction: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            FluentIcon(Icons.Default.MoreVert, contentDescription = "More options")
        }

        MaterialTheme(
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp))
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
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
