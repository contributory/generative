package org.starfall.generative.features.providers.list.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.UUID
import org.starfall.generative.R
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup

/**
 * Which provider-list dialog is open and for what. The screen's menus and folder overlay open a dialog by
 * setting one of these; [ProviderDialogHost] shows it and clears it when it closes.
 */
internal class ProviderListDialogs {
    var creatingGroup by mutableStateOf(false)
    var groupToRename by mutableStateOf<ProviderGroup?>(null)
    var deletingGroup by mutableStateOf<ProviderGroup?>(null)
    var movingProvider by mutableStateOf<ModelProvider?>(null)
    var deletingProviderId by mutableStateOf<String?>(null)
}

@Composable
internal fun rememberProviderListDialogs(): ProviderListDialogs = remember { ProviderListDialogs() }

/**
 * Shows the open provider-list dialog. Each confirmation goes through a callback supplied by the screen,
 * which owns the data changes (saving groups, moving or deleting providers).
 */
@Composable
internal fun ProviderDialogHost(
    dialogs: ProviderListDialogs,
    sortedGroups: List<ProviderGroup>,
    nextRootSortOrder: () -> Int,
    onSaveGroup: (ProviderGroup) -> Unit,
    onGroupDeleteConfirmed: (ProviderGroup) -> Unit,
    onMoveProviderToGroup: (String, String?) -> Unit,
    onProviderDeleteConfirmed: (String) -> Unit
) {
    if (dialogs.creatingGroup) {
        ProviderGroupNameDialog(
            title = stringResource(R.string.new_provider_group),
            initialName = "",
            initialIcon = null,
            onDismiss = { dialogs.creatingGroup = false },
            onSave = { name, icon ->
                onSaveGroup(
                    ProviderGroup(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        sortOrder = nextRootSortOrder(),
                        icon = icon
                    )
                )
                dialogs.creatingGroup = false
            }
        )
    }

    dialogs.groupToRename?.let { group ->
        ProviderGroupNameDialog(
            title = stringResource(R.string.edit_provider_group),
            initialName = group.name,
            initialIcon = group.icon,
            onDismiss = { dialogs.groupToRename = null },
            onSave = { name, icon ->
                onSaveGroup(group.copy(name = name, icon = icon))
                dialogs.groupToRename = null
            }
        )
    }

    dialogs.deletingGroup?.let { group ->
        ProviderDeleteGroupDialog(
            onDismiss = { dialogs.deletingGroup = null },
            onConfirm = {
                onGroupDeleteConfirmed(group)
                dialogs.deletingGroup = null
            }
        )
    }

    dialogs.movingProvider?.let { provider ->
        ProviderGroupPickerDialog(
            provider = provider,
            groups = sortedGroups,
            onDismiss = { dialogs.movingProvider = null },
            onSelect = { groupId ->
                onMoveProviderToGroup(provider.id, groupId)
                dialogs.movingProvider = null
            }
        )
    }

    if (dialogs.deletingProviderId != null) {
        ProviderDeleteDialog(
            onDismiss = { dialogs.deletingProviderId = null },
            onConfirm = {
                dialogs.deletingProviderId?.let { onProviderDeleteConfirmed(it) }
                dialogs.deletingProviderId = null
            }
        )
    }
}
