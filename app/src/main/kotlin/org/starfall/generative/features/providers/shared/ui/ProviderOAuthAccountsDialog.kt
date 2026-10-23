package org.starfall.generative.features.providers.shared.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.starfall.generative.R
import org.starfall.generative.features.providers.shared.logic.isEmpty
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderOAuthAccount
import org.starfall.generative.shared.ui.dialogs.AppDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField
import org.starfall.generative.shared.ui.auth.rememberOAuthStart
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderOAuthAccountsDialog(
    accounts: List<ProviderOAuthAccount>, selectedId: String?, canAuthorize: Boolean, canDelete: Boolean,
    onSelect: (ProviderOAuthAccount) -> Unit, onSaveLabel: (String, String) -> Unit,
    onAuthorize: suspend (String?, String) -> Result<ModelProvider>, onCancelAuthorization: () -> Unit,
    onDelete: suspend (String) -> Result<ModelProvider>, onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val cancelled = stringResource(R.string.oauth_sign_in_cancelled)
    fun edit(account: ProviderOAuthAccount?) {
        editingId = account?.id; label = account?.label.orEmpty(); error = null; editing = true
    }
    fun dismiss() { if (busy) onCancelAuthorization(); onDismiss() }
    val signIn = rememberOAuthStart {
        scope.launch {
            busy = true; error = null
            try {
                onAuthorize(editingId, label).onSuccess { editing = false }.onFailure { error = it.message }
            } catch (_: CancellationException) { error = cancelled }
            finally { busy = false }
        }
    }
    AppDialog(onDismissRequest = { if (editing && !busy) editing = false else dismiss() }) {
        BackHandler(enabled = editing && !busy) { editing = false }
        Surface(shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column {
                TopAppBar(title = { Text(stringResource(if (editing) {
                    if (editingId == null) R.string.provider_add_account else R.string.provider_edit_account
                } else R.string.provider_manage_accounts)) },
                    navigationIcon = { if (editing) IconButton(enabled = !busy, onClick = { editing = false }) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    } },
                    actions = {
                        if (editing) IconButton(enabled = !busy && (editingId != null || canAuthorize), onClick = {
                            if (editingId == null) signIn() else { onSaveLabel(editingId!!, label); editing = false }
                        }) { FluentIcon(Icons.Outlined.Save, stringResource(R.string.common_save)) }
                        if (!editing) IconButton(enabled = !busy && canAuthorize, onClick = { edit(null) }) {
                            FluentIcon(Icons.Default.Add, stringResource(R.string.provider_add_account))
                        }
                        IconButton(onClick = ::dismiss) { FluentIcon(Icons.Default.Close, stringResource(R.string.common_close)) }
                    })
                error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                if (editing) Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectableOutlinedTextField(label, { label = it }, singleLine = true, enabled = !busy,
                        label = { Text(stringResource(R.string.provider_account_label)) },
                        modifier = Modifier.fillMaxWidth().testTag("oauth-account-label"))
                    accounts.firstOrNull { it.id == editingId }?.identity?.let { Text(it) }
                    Button(enabled = !busy && canAuthorize, onClick = signIn, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (busy) R.string.oauth_signing_in else if (editingId == null)
                            R.string.oauth_sign_in else R.string.oauth_sign_in_again))
                    }
                    if (busy) TextButton(onClick = onCancelAuthorization) { Text(stringResource(R.string.common_cancel)) }
                } else if (accounts.isEmpty()) Text(stringResource(R.string.provider_no_accounts), Modifier.padding(24.dp))
                else LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts.sortedBy { it.id != selectedId }, key = { it.id }) { account ->
                        val selected = account.id == selectedId
                        var menu by remember(account.id) { mutableStateOf(false) }
                        val itemShape = RoundedCornerShape(16.dp)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = itemShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                prominentContainerColor()
                            },
                            border = if (selected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                prominentContainerBorder()
                            }
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(itemShape)
                                    .selectable(selected, enabled = !busy, role = Role.RadioButton,
                                        onClick = { onSelect(account) })
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                    Text(account.label.ifBlank { account.identity ?: "Account ${accounts.indexOf(account) + 1}" })
                                    if (account.label.isNotBlank() && !account.identity.isNullOrBlank()) Text(account.identity,
                                        style = MaterialTheme.typography.bodySmall)
                                    if (selected) Text(stringResource(R.string.provider_selected_account),
                                        style = MaterialTheme.typography.labelMedium)
                                }
                                if (selected) FluentIcon(Icons.Default.Check, stringResource(R.string.provider_selected_account))
                                Box {
                                    IconButton(enabled = !busy, onClick = { menu = true }) {
                                        FluentIcon(Icons.Default.MoreVert, stringResource(R.string.oauth_account_actions))
                                    }
                                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                        RoundedDropdownMenuItem(text = { Text(stringResource(R.string.common_edit)) },
                                            onClick = { menu = false; edit(account) })
                                        RoundedDropdownMenuItem(enabled = canDelete, text = { Text(stringResource(R.string.common_delete)) },
                                            onClick = {
                                                menu = false
                                                scope.launch {
                                                    busy = true; error = null
                                                    try { onDelete(account.id).onFailure { error = it.message } }
                                                    finally { busy = false }
                                                }
                                            })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
