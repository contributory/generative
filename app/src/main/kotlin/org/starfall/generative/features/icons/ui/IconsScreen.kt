package org.starfall.generative.features.icons.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.shared.icons.IconStore
import org.starfall.generative.shared.icons.model.StoredIcon
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.FluentUi
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.SettingsCard
import org.starfall.generative.shared.ui.layout.nestedContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.menus.ItemContextMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { IconStore(context) }
    val scope = rememberCoroutineScope()
    val revision by IconStore.revision.collectAsState()
    val entries by produceState(emptyList<StoredIcon>(), revision) {
        value = withContext(Dispatchers.IO) { store.entries() }
    }
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchHasFocus by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoredIcon?>(null) }
    var patterns by remember { mutableStateOf(emptyList<String>()) }
    var pickingDark by remember { mutableStateOf(false) }

    fun edit(entry: StoredIcon) {
        editing = entry
        patterns = entry.patterns
    }
    fun mutate(action: () -> Unit) {
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { action() }
                editing = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, R.string.icon_cache_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val entry = withContext(Dispatchers.IO) {
                    val image = store.importImage(uri)
                    store.entries().first { it.image == image }
                }
                edit(entry)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, R.string.icon_import_failed, Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
    val variantPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val entry = editing
        val dark = pickingDark
        if (uri != null && entry != null) scope.launch {
            busy = true
            try {
                editing = withContext(Dispatchers.IO) {
                    val replacement = store.importImage(uri, shared = false)
                    store.setVariant(entry.image, replacement, dark)
                    store.entries().first { it.image == entry.image }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, R.string.icon_import_failed, Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocus.requestFocus()
    }

    val trimmedQuery = query.trim()
    val visible = entries.filter { entry ->
        entry.filename.contains(trimmedQuery, ignoreCase = true) ||
            entry.image.contains(trimmedQuery, ignoreCase = true) ||
            entry.patterns.any { it.contains(trimmedQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(targetState = searchExpanded, label = "icons_app_bar_search") { searching ->
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocus)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            searchHasFocus = true
                                        } else if (searchHasFocus) {
                                            searchHasFocus = false
                                            searchExpanded = false
                                            query = ""
                                        }
                                    },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.icon_cache_search)) },
                                leadingIcon = { Icon(FluentUi.Regular.Search, contentDescription = null) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.icon_settings_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (!searchExpanded) {
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(FluentUi.Regular.Search, contentDescription = stringResource(R.string.icon_cache_search))
                        }
                    }
                    IconButton(enabled = !busy, onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(FluentUi.Regular.ImageAdd, contentDescription = stringResource(R.string.icon_cache_add))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (visible.isEmpty()) item {
                    SettingsCard {
                        Text(
                            stringResource(if (query.isBlank()) R.string.icon_cache_empty else R.string.icon_cache_no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(visible, key = { it.image }) { entry ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = prominentContainerColor(),
                        border = prominentContainerBorder()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StoredIconPreview(entry.image, Modifier.size(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.filename, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall)
                                Text(entry.patterns.joinToString(" · ").ifEmpty {
                                    stringResource(R.string.icon_cache_no_matches)
                                }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            ItemContextMenu(
                                onEdit = { edit(entry) },
                                onDelete = { mutate { store.delete(entry.image) } },
                                deleteColor = MaterialTheme.colorScheme.error,
                                enabled = !busy
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        val valid = patterns.all { it.isNotBlank() && runCatching { Regex(it) }.isSuccess }
        AlertDialog(
            onDismissRequest = { if (!busy) editing = null },
            title = { Text(stringResource(R.string.icon_cache_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StoredIconPreview(entry.image, Modifier.size(48.dp))
                        Text(entry.filename, modifier = Modifier.weight(1f), maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(false, true).forEach { dark ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(if (dark) R.string.icon_variant_dark else R.string.icon_variant_normal),
                                    style = MaterialTheme.typography.labelMedium)
                                StoredIconPreview(
                                    if (dark) entry.darkImage else entry.lightImage,
                                    Modifier.size(48.dp),
                                    useDarkVariant = false,
                                    backgroundColor = if (dark) Color(0xFF202024) else Color(0xFFF4F4F4)
                                )
                                TextButton(enabled = !busy, onClick = {
                                    pickingDark = dark
                                    variantPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) { Text(stringResource(R.string.choose_icon)) }
                                if (dark && entry.darkImage != null) IconButton(enabled = !busy, onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            editing = withContext(Dispatchers.IO) {
                                                store.setVariant(entry.image, null, true)
                                                store.entries().first { it.image == entry.image }
                                            }
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (_: Exception) {
                                            Toast.makeText(context, R.string.icon_cache_save_failed, Toast.LENGTH_LONG).show()
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }) { Icon(FluentUi.Regular.Dismiss, stringResource(R.string.remove_icon)) }
                            }
                        }
                    }
                    LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(patterns.size) { index ->
                            val pattern = patterns[index]
                            val invalid = pattern.isBlank() || runCatching { Regex(pattern) }.isFailure
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = pattern,
                                    onValueChange = { value -> patterns = patterns.toMutableList().apply { this[index] = value } },
                                    modifier = Modifier.weight(1f),
                                    enabled = !busy,
                                    label = { Text(stringResource(R.string.icon_cache_regex)) },
                                    isError = invalid,
                                    supportingText = if (invalid) ({ Text(stringResource(R.string.icon_cache_invalid_regex)) }) else null
                                )
                                IconButton(enabled = !busy, onClick = {
                                    patterns = patterns.filterIndexed { i, _ -> i != index }
                                }) { Icon(FluentUi.Regular.Dismiss, stringResource(R.string.icon_cache_delete)) }
                            }
                        }
                    }
                    TextButton(enabled = !busy, onClick = { patterns = patterns + "" }) {
                        Icon(FluentUi.Regular.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.icon_cache_add_match))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = valid && !busy, onClick = {
                    val updated = patterns.toList()
                    mutate { store.editMatches(entry.image, updated) }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { editing = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun StoredIconPreview(
    image: String?,
    modifier: Modifier = Modifier,
    useDarkVariant: Boolean? = null,
    backgroundColor: Color? = null
) {
    val context = LocalContext.current
    val dark = useDarkVariant ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val data = remember(image, dark, context) { IconStore(context).imageData(image, dark) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor ?: MaterialTheme.colorScheme.surfaceContainerHighest,
        border = if (backgroundColor == null) nestedContainerBorder() else null
    ) {
        if (data != null && (data !is File || data.exists())) {
            AsyncImage(model = data, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit)
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(FluentUi.Regular.ImageOff, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
