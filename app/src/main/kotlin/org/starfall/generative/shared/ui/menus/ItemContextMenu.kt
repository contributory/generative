package org.starfall.generative.shared.ui.menus
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.starfall.generative.R
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.DropdownMenuItem

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
    showDelete: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(enabled = enabled, onClick = { expanded = true }, modifier = modifier) {
            SharedIcon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more_options))
        }

        DropdownMenu(
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
                            SharedIcon(firstActionImageVector, contentDescription = null)
                        } else {
                            SharedIcon(firstActionIcon!!, contentDescription = null)
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
                leadingIcon = { SharedIcon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                }
            )
            if (showDelete) DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete), color = deleteColor) },
                leadingIcon = {
                    SharedIcon(
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
