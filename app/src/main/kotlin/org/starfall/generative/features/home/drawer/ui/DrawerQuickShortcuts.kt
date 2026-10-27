package org.starfall.generative.features.home.drawer.ui

import org.starfall.generative.shared.ui.menus.AppDropdownMenu as DropdownMenu
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.model.remoteMachineEnabled
import org.starfall.generative.shared.ui.LocalToolControls
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.FluentUi
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.machines.MachineToolsDialog
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem as DropdownMenuItem

/**
 * Single combined card at the top of the left drawer.
 * - Remote: expandable list with per-machine context menus, title is "Remote"
 * - Media: navigates to StorageScreen, title is "Media"
 * Both rows share the same [prominentContainerColor] block.
 */
@Composable
internal fun DrawerQuickShortcuts(
    onOpenMediaStorage: () -> Unit,
    onOpenRemoteMachines: () -> Unit,
    onEditRemoteMachine: (RemoteMachine) -> Unit = {},
    onSaveMachineTools: (RemoteMachine) -> Unit,
    modifier: Modifier = Modifier,
    maxExpandedHeight: Dp = 280.dp
) {
    val controls = LocalToolControls.current
    val machines = controls.machines
    val enabledCount = machines.count { remoteMachineEnabled(controls.settings, it) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var toolsMachine by remember { mutableStateOf<RemoteMachine?>(null) }

    val subtitle = if (machines.isEmpty()) {
        stringResource(R.string.remote_machine_none_configured)
    } else {
        stringResource(R.string.remote_machine_counts, machines.size, enabledCount)
    }

    // Header (~72dp) + Media row (~66dp) + dividers ≈ 150dp. Rest is scrollable list.
    // On short screens preserve those rows instead of forcing the card beyond its half-drawer cap.
    val listMaxHeight = (maxExpandedHeight - 150.dp).coerceAtLeast(0.dp)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = prominentContainerColor(),
        border = prominentContainerBorder(),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = if (expanded) maxExpandedHeight else Dp.Unspecified)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Remote row — expands/collapses the machine list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { expanded = !expanded }
                    .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FluentIcon(
                            imageVector = FluentUi.Regular.Desktop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.remote_machine_short_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.heightIn(min = 2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onOpenRemoteMachines,
                    modifier = Modifier.size(36.dp)
                ) {
                    FluentIcon(
                        imageVector = FluentUi.Regular.Settings,
                        contentDescription = stringResource(
                            R.string.tool_settings_accessibility,
                            stringResource(R.string.remote_machines_title)
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    FluentIcon(
                        imageVector = if (expanded) FluentUi.Regular.ChevronUp else FluentUi.Regular.ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    if (machines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.remote_machine_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = listMaxHeight)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            machines.forEach { machine ->
                                val icon = if (machine.type.name == "GCLOUD_SHELL") Icons.Outlined.Cloud else Icons.Outlined.Computer
                                val checked = remoteMachineEnabled(controls.settings, machine)
                                var menuExpanded by remember(machine.id) { mutableStateOf(false) }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)
                                            .testTag("drawer_machine_row_${machine.id}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FluentIcon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = if (checked) 1f else 0.55f
                                            )
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = machine.name.ifBlank { stringResource(R.string.remote_machine_unnamed) },
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (checked) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box {
                                            IconButton(
                                                onClick = { menuExpanded = true },
                                                modifier = Modifier.size(36.dp)
                                                    .testTag("drawer_machine_options_${machine.id}")
                                            ) {
                                                FluentIcon(
                                                    Icons.Default.MoreVert,
                                                    stringResource(R.string.common_more_options),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = menuExpanded,
                                                onDismissRequest = { menuExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(
                                                        if (checked) R.string.remote_machine_disable_action
                                                        else R.string.remote_machine_enable_action
                                                    )) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        controls.setMachine(machine.id, !checked)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.common_tools)) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        toolsMachine = machine
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.common_edit)) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        onEditRemoteMachine(machine)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            // Media row — navigates to StorageScreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenMediaStorage)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FluentIcon(
                            imageVector = FluentUi.Regular.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.storage_short_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.heightIn(min = 2.dp))
                    Text(
                        text = stringResource(R.string.settings_storage_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                FluentIcon(
                    imageVector = FluentUi.Regular.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    toolsMachine?.let { machine ->
        MachineToolsDialog(
            machine = machine,
            onDismiss = { toolsMachine = null },
            onSave = { updated ->
                onSaveMachineTools(updated)
                toolsMachine = null
            }
        )
    }
}
