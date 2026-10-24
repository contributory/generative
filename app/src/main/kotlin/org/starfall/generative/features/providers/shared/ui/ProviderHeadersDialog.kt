package org.starfall.generative.features.providers.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.ui.dialogs.AppDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.text.SelectableOutlinedTextField

/** Manages a provider's custom HTTP headers; edits are applied to [rows] immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderHeadersDialog(
    rows: SnapshotStateList<Pair<String, String>>,
    valid: Boolean,
    onDismiss: () -> Unit
) {
    AppDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.headers), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        IconButton(onClick = { rows.add("" to "") }) { FluentIcon(Icons.Default.Add, stringResource(R.string.common_add)) }
                        IconButton(onClick = onDismiss) { FluentIcon(Icons.Default.Close, stringResource(R.string.common_close)) }
                    }
                )
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(rows) { index, row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectableOutlinedTextField(
                                row.first, { rows[index] = it to rows[index].second },
                                label = { Text(stringResource(R.string.common_key)) },
                                maxLines = 2, modifier = Modifier.weight(1f)
                            )
                            SelectableOutlinedTextField(
                                row.second, { rows[index] = rows[index].first to it },
                                label = { Text(stringResource(R.string.common_value)) },
                                maxLines = 2, modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { rows.removeAt(index) }) {
                                FluentIcon(Icons.Outlined.Delete, stringResource(R.string.common_delete))
                            }
                        }
                    }
                    if (!valid) item {
                        Text(
                            stringResource(R.string.provider_headers_invalid),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
