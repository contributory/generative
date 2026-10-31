package org.starfall.generative.features.home.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import org.starfall.generative.R
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.ui.dialogs.AlertDialog

/** Confirms that a response is regenerated, which removes every later message. */
@Composable
internal fun RegenerateResponseDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.regenerate_title)) },
        text = { Text(stringResource(R.string.regenerate_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.message_regenerate)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
    )
}

/**
 * Confirms a message deletion. A message with several versions offers deleting only the current version
 * or the whole message; a single-version message is deleted outright.
 */
@Composable
internal fun DeleteMessageDialog(
    message: StoredMessage,
    onDeleteVersion: () -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasMultipleVersions = message.versions.size > 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_message_title)) },
        text = {
            Text(
                if (hasMultipleVersions) {
                    stringResource(R.string.delete_version_message, message.versions.size)
                } else {
                    stringResource(R.string.delete_message_message)
                }
            )
        },
        confirmButton = {
            if (hasMultipleVersions) {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = onDeleteVersion,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete_current_version))
                    }
                    TextButton(
                        onClick = onDeleteAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete_all_versions))
                    }
                }
            } else {
                Button(
                    onClick = onDeleteAll,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}
