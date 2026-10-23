package org.starfall.generative.home.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog

/** Confirms that a response is regenerated, which removes every later message. */
@Composable
internal fun RegenerateResponseDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Regenerate this response?") },
        text = { Text("Later messages will be removed. The current response will remain available as an earlier version.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Regenerate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
        title = { Text(if (hasMultipleVersions) "Delete Message Version" else "Delete Message") },
        text = {
            Text(
                if (hasMultipleVersions) {
                    "This message has ${message.versions.size} versions. Delete only the current version or delete the entire message with all versions?"
                } else {
                    "Are you sure you want to delete this message?"
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
                        Text("Delete current version")
                    }
                    TextButton(
                        onClick = onDeleteAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete all versions")
                    }
                }
            } else {
                Button(
                    onClick = onDeleteAll,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
