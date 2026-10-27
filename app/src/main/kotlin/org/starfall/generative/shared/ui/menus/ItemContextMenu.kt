package org.starfall.generative.shared.ui.menus
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.starfall.generative.R
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem

@Composable
fun ItemContextMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteColor: Color,
    firstActionText: String? = null,
    firstActionIcon: Int? = null,
    firstActionImageVector: ImageVector? = null,
    onFirstAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    showEdit: Boolean = true,
    showDelete: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(enabled = enabled, onClick = { expanded = true }) {
            FluentIcon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more_options))
        }

        AppDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (firstActionText != null && onFirstAction != null &&
                (firstActionIcon != null || firstActionImageVector != null)
            ) {
                DropdownMenuItem(
                    text = { Text(firstActionText) },
                    leadingIcon = {
                        if (firstActionImageVector != null) {
                            FluentIcon(firstActionImageVector, contentDescription = null)
                        } else {
                            FluentIcon(firstActionIcon!!, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onFirstAction()
                    }
                )
            }
            if (showEdit) DropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                leadingIcon = { FluentIcon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                }
            )
            if (showDelete) DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete), color = deleteColor) },
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
