package org.starfall.generative.home.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.shared.ui.LocalToolControls
import org.starfall.generative.shared.model.remoteMachineEnabled
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons

/** Bottom sheet that turns each Remote Machine on or off for the model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteMachinesSheet(onDismiss: () -> Unit) {
    AppSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        RemoteMachinesSheetContent()
    }
}

@Composable
internal fun RemoteMachinesSheetContent(
    onBack: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {}
) {
    val controls = LocalToolControls.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionsSubpageHeader(
            title = "Remote Machines",
            onBack = onBack,
            actions = {
                QuickActionsSettingsButton(
                    title = "Remote Machines",
                    onClick = onOpenSettings
                )
            }
        )
        if (controls.machines.isEmpty()) {
            Text(
                "No remote machines configured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        controls.machines.forEach { machine ->
            val icon = if (machine.type.name == "GCLOUD_SHELL") Icons.Outlined.Cloud else Icons.Outlined.Computer
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = prominentContainerColor(),
                border = prominentContainerBorder()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FluentIcon(
                        icon, null,
                        Modifier.size(44.dp).padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        machine.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Switch(
                        checked = remoteMachineEnabled(controls.settings, machine),
                        onCheckedChange = { controls.setMachine(machine.id, it) }
                    )
                }
            }
        }
    }
}
