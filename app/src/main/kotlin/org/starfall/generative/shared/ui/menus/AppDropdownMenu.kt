package org.starfall.generative.shared.ui.menus

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.starfall.generative.shared.theme.LocalAmoledMode
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

/** Shared appearance for anchored action menus. */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        containerColor = prominentContainerColor(),
        tonalElevation = if (LocalAmoledMode.current) 0.dp else 6.dp,
        shadowElevation = if (LocalAmoledMode.current) 0.dp else 8.dp,
        border = prominentContainerBorder(),
        content = content
    )
}

/** Exposed menus retain Material's anchor sizing and keyboard behaviour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.AppExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        containerColor = prominentContainerColor(),
        tonalElevation = if (LocalAmoledMode.current) 0.dp else 6.dp,
        shadowElevation = if (LocalAmoledMode.current) 0.dp else 8.dp,
        border = prominentContainerBorder(),
        content = content
    )
}
