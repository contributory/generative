package org.starfall.multigateway.shared.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.icons.FaviconResult
import org.starfall.multigateway.shared.icons.FaviconService
import org.starfall.multigateway.shared.icons.IconStore
import org.starfall.multigateway.shared.ui.icons.EntityIcon
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons
import org.starfall.multigateway.shared.ui.text.SelectableOutlinedTextField

@Composable
internal fun FaviconDialog(
    image: String?,
    defaultImage: String?,
    text: String?,
    fallback: Int?,
    matchName: String?,
    model: Boolean,
    baseUrl: String,
    onSave: (String?) -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { FaviconService(context) }
    val store = remember(context) { IconStore(context) }
    var customUrl by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FaviconResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val currentBusy by rememberUpdatedState(onBusyChange)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            currentBusy(true)
            error = null
            try {
                val id = withContext(Dispatchers.IO) { store.importImage(uri, shared = false) }
                onSave(id)
                result = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: context.getString(R.string.icon_import_failed)
            } finally {
                busy = false
                currentBusy(false)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { currentBusy(false) } }

    AppAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(context.getString(R.string.entity_icon)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val preview = result
                if (preview != null) {
                    Image(
                        bitmap = preview.bitmap.asImageBitmap(),
                        contentDescription = context.getString(R.string.favicon_preview),
                        modifier = Modifier.size(112.dp).clickable(enabled = !busy) {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                } else {
                    EntityIcon(
                        image = image ?: defaultImage,
                        modifier = Modifier.size(112.dp).clickable(enabled = !busy) {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        text = text,
                        fallback = fallback,
                        matchName = matchName,
                        model = model
                    )
                }
                SelectableOutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it; error = null },
                    enabled = !busy,
                    label = { Text(context.getString(R.string.favicon_homepage_url)) },
                    supportingText = { Text(context.getString(R.string.favicon_homepage_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (customUrl.isNotEmpty()) {
                            IconButton(enabled = !busy, onClick = { customUrl = ""; error = null }) {
                                FluentIcon(Icons.Outlined.Close, contentDescription = context.getString(R.string.common_clear))
                            }
                        }
                    }
                )
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                preview?.let { Text(it.url.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (image != null) {
                    TextButton(enabled = !busy, onClick = {
                        onSave(null)
                        result = null
                        customUrl = ""
                        error = null
                    }) { Text(context.getString(R.string.common_remove)) }
                }
                TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    currentBusy(true)
                    error = null
                    try {
                        val fetched = withContext(Dispatchers.IO) { service.get(customUrl, baseUrl) }
                        val id = withContext(Dispatchers.IO) { store.importBitmap(fetched.bitmap) }
                        result = fetched
                        onSave(id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "Could not retrieve the image."
                    } finally {
                        busy = false
                        currentBusy(false)
                    }
                }
                }) { Text(context.getString(R.string.favicon_fetch)) }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(context.getString(R.string.common_close)) }
        }
    )
}
