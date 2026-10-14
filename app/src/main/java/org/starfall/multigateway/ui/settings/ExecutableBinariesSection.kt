package org.starfall.multigateway.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.starfall.multigateway.R
import org.starfall.multigateway.data.service.ExecutableBinary

/** What the Preferences page needs to manage uploaded executables; keeps the screen signatures small. */
data class ExecutableBinaryControls(
    val binaries: List<ExecutableBinary> = emptyList(),
    val onImport: suspend (Uri) -> Result<ExecutableBinary> = { Result.failure(UnsupportedOperationException()) },
    val onDelete: (String) -> Unit = {}
)

@Composable
fun ExecutableBinariesSection(controls: ExecutableBinaryControls) {
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            controls.onImport(uri)
                .onSuccess { error = null }
                .onFailure { error = it.message }
        }
    }
    SettingsSection(stringResource(R.string.settings_executable_binaries)) {
        Text(
            stringResource(R.string.settings_executable_binaries_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            controls.binaries.forEach { binary ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Code, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(binary.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            binary.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { controls.onDelete(binary.id) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { launcher.launch(arrayOf("application/octet-stream", "application/x-executable", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_add_executable_binary))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
