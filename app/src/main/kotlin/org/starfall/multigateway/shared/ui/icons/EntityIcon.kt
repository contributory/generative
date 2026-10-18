package org.starfall.multigateway.shared.ui.icons

import coil3.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.starfall.multigateway.R
import org.starfall.multigateway.shared.icons.IconStore
import org.starfall.multigateway.shared.ui.dialogs.FaviconDialog

internal fun providerInitials(name: String): String = name.trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
    .joinToString("")
    .ifBlank { "?" }

@Composable
fun EntityIcon(
    image: String?,
    modifier: Modifier = Modifier.size(42.dp),
    text: String? = null,
    fallback: Int? = null,
    matchName: String? = null,
    model: Boolean = false,
    useDarkVariant: Boolean? = null,
    backgroundColor: Color? = null,
    /** Drawn instead of [fallback]/[text] once the lookup finished without finding an image. */
    fallbackContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val revision by IconStore.lookupRevision.collectAsState()
    val dark = useDarkVariant ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    var data by remember(context, image, matchName, model, dark) { mutableStateOf<Any?>(null) }
    var resolved by remember(context, image, matchName, model, dark) { mutableStateOf(false) }
    var explicitFailed by remember(image, matchName, dark, revision) { mutableStateOf(false) }
    LaunchedEffect(image, matchName, model, revision, context, dark, explicitFailed) {
        data = withContext(Dispatchers.IO) {
            val store = IconStore(context)
            (if (explicitFailed) null else store.imageData(image, dark))
                ?: store.imageData(matchName?.let { store.resolve(it, model) }, dark)
        }
        resolved = true
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = backgroundColor ?: MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(contentAlignment = Alignment.Center) {
            if (data == null) {
                if (fallbackContent != null) {
                    // Wait for the first lookup so a folder logo never flashes the substitute first.
                    if (resolved) fallbackContent()
                } else if (fallback != null) FluentIcon(fallback, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize().padding(9.dp))
                else Text(text?.takeIf { it.isNotBlank() } ?: "?",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            } else AsyncImage(data, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onError = { if (image != null && !explicitFailed) explicitFailed = true })
        }
    }
}

@Composable
fun IconPickerRow(
    image: String?,
    onChange: (String?) -> Unit,
    text: String? = null,
    fallback: Int? = null,
    onBusyChange: (Boolean) -> Unit = {},
    matchName: String? = null,
    model: Boolean = false,
    faviconBaseUrl: String = "",
    defaultImage: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    if (showDialog) FaviconDialog(
        image = image,
        defaultImage = defaultImage,
        text = text,
        fallback = fallback,
        matchName = matchName,
        model = model,
        baseUrl = faviconBaseUrl,
        onSave = onChange,
        onBusyChange = { importing = it; onBusyChange(it) },
        onDismiss = { showDialog = false }
    )
    EntityIcon(
        image = image ?: defaultImage,
        modifier = Modifier.size(56.dp).clickable(enabled = !importing) { showDialog = true },
        text = text,
        fallback = fallback,
        matchName = matchName,
        model = model
    )
}
