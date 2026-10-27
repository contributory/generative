package org.starfall.generative.features.userprofile.ui

import org.starfall.generative.shared.ui.layout.scrollableScaffoldPadding
import org.starfall.generative.shared.ui.layout.edgeToEdgeTopAppBarColors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.shared.files.ToolFiles
import org.starfall.generative.shared.icons.AppImages
import org.starfall.generative.shared.icons.IconStore
import org.starfall.generative.shared.storage.preferences.UserProfile
import org.starfall.generative.shared.storage.preferences.UserProfileImageSlot
import org.starfall.generative.shared.ui.FeaturesLogoImage
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.media.mediaMimeType
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.menus.AppDropdownMenu
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem

/** Profile photos and appearance slots are backed by a single persistent UserProfile object. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    profile: UserProfile,
    onChange: ((UserProfile) -> UserProfile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<UserProfileImageSlot?>(null) }
    var menuFor by remember { mutableStateOf<UserProfileImageSlot?>(null) }
    var showStorage by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun storeImage(slot: UserProfileImageSlot, path: String?) {
        val previous = slot.get(profile)
        val updated = slot.put(profile, path)
        onChange { slot.put(it, path) }
        scope.launch(Dispatchers.IO) { UserProfileImages.cleanup(context, previous, updated) }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val slot = target
        if (uri != null && slot != null) scope.launch {
            busy = true
            try {
                val image = UserProfileImages.importGallery(context, uri)
                storeImage(slot, image)
            } catch (e: Exception) { error = e.localizedMessage } finally { busy = false }
        }
    }
    fun openMenu(slot: UserProfileImageSlot) { menuFor = slot }
    fun chooseStorage(slot: UserProfileImageSlot) { target = slot; menuFor = null; showStorage = true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = edgeToEdgeTopAppBarColors(),
                title = { Text(stringResource(R.string.user_profile_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) {
                    FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = scrollableScaffoldPadding(padding, horizontal = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(28.dp), color = prominentContainerColor()) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp))) {
                                ProfileImage(profile.photo, Modifier.fillMaxSize(), fallback = true)
                            }
                            Box(Modifier.align(Alignment.BottomEnd)) {
                                FilledTonalIconButton(
                                    onClick = { openMenu(UserProfileImageSlot.PHOTO) },
                                    modifier = Modifier.size(42.dp)
                                ) { FluentIcon(Icons.Default.MoreVert, stringResource(R.string.user_profile_photo_options)) }
                                SlotMenu(
                                    expanded = menuFor == UserProfileImageSlot.PHOTO,
                                    hasImage = profile.photo != null,
                                    onDismiss = { menuFor = null },
                                    onGallery = {
                                        target = UserProfileImageSlot.PHOTO
                                        menuFor = null
                                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    onStorage = { chooseStorage(UserProfileImageSlot.PHOTO) },
                                    onRemove = { menuFor = null; storeImage(UserProfileImageSlot.PHOTO, null) }
                                )
                            }
                        }
                        Text(stringResource(R.string.user_profile_photo_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.user_profile_photo_description), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Text(stringResource(R.string.settings_tab_appearances), modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(
                        Triple(UserProfileImageSlot.CHAT, stringResource(R.string.user_profile_background_chat), profile.chatBackground),
                        Triple(UserProfileImageSlot.DRAWER, stringResource(R.string.user_profile_background_drawer), profile.drawerBackground),
                        Triple(UserProfileImageSlot.MENU, stringResource(R.string.user_profile_background_menu), profile.menuBackground)
                    ).forEachIndexed { index, (slot, label, image) ->
                        Surface(shape = if (index == 0) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp,
                            bottomStart = 6.dp, bottomEnd = 6.dp) else RoundedCornerShape(6.dp),
                            color = prominentContainerColor()) {
                            Row(Modifier.fillMaxWidth().clickable { openMenu(slot) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ProfileImage(image, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Box {
                                    IconButton(onClick = { openMenu(slot) }) {
                                        FluentIcon(Icons.Default.MoreVert, stringResource(R.string.user_profile_background_options, label))
                                    }
                                    SlotMenu(
                                        expanded = menuFor == slot,
                                        hasImage = image != null,
                                        onDismiss = { menuFor = null },
                                        onGallery = {
                                            target = slot; menuFor = null
                                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        onStorage = { chooseStorage(slot) },
                                        onRemove = { menuFor = null; storeImage(slot, null) }
                                    )
                                }
                            }
                        }
                    }
                    listOf(stringResource(R.string.user_profile_background_bottom_sheet),
                        stringResource(R.string.user_profile_background_dialog)).forEachIndexed { index, label ->
                        Surface(shape = if (index == 1) RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp,
                            bottomStart = 22.dp, bottomEnd = 22.dp) else RoundedCornerShape(6.dp),
                            color = prominentContainerColor()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    FluentIcon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                IconButton(enabled = false, onClick = {}) {
                                    FluentIcon(Icons.Default.MoreVert, stringResource(R.string.user_profile_background_unavailable, label))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showStorage) MediaStoragePhotoSheet(
        onDismiss = { showStorage = false },
        onSelected = { file ->
            val slot = target ?: return@MediaStoragePhotoSheet
            showStorage = false
            scope.launch {
                busy = true
                try { storeImage(slot, UserProfileImages.importStorage(context, file)) }
                catch (e: Exception) { error = e.localizedMessage }
                finally { busy = false }
            }
        }
    )
    if (busy) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    error?.let { message -> AlertDialog(
        onDismissRequest = { error = null }, title = { Text(stringResource(R.string.user_profile_image_error)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.common_ok)) } }
    ) }
}

@Composable
private fun SlotMenu(expanded: Boolean, hasImage: Boolean, onDismiss: () -> Unit,
                     onGallery: () -> Unit, onStorage: () -> Unit, onRemove: () -> Unit) {
    AppDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        RoundedDropdownMenuItem(text = { Text(stringResource(R.string.user_profile_photos_gallery)) }, onClick = onGallery,
            leadingIcon = { FluentIcon(Icons.Outlined.Image, null) })
        RoundedDropdownMenuItem(text = { Text(stringResource(R.string.settings_storage)) }, onClick = onStorage,
            leadingIcon = { FluentIcon(Icons.Outlined.Folder, null) })
        RoundedDropdownMenuItem(text = { Text(stringResource(R.string.common_remove)) }, enabled = hasImage, onClick = onRemove,
            leadingIcon = { FluentIcon(Icons.Outlined.Delete, null) })
    }
}

@Composable
private fun ProfileImage(image: String?, modifier: Modifier, fallback: Boolean = false) {
    if (image == null) {
        if (fallback) FeaturesLogoImage(null, modifier)
        else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            FluentIcon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val context = LocalContext.current
        val model = remember(image) { IconStore(context).imageData(image) ?: AppImages.source(image) }
        AsyncImage(model = model, contentDescription = null, modifier = modifier,
            contentScale = ContentScale.Crop)
    }
}

@Composable
private fun MediaStoragePhotoSheet(onDismiss: () -> Unit, onSelected: (File) -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<File>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        photos = withContext(Dispatchers.IO) {
            ToolFiles(context).list().filter { mediaMimeType(it.name).startsWith("image/") }
        }
    }
    AppSheet(onDismissRequest = onDismiss, fixedHeightFraction = 0.82f) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterStart), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                    FluentIcon(Icons.Default.Close, stringResource(R.string.common_close))
                }
            }
            OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.user_profile_search_photos)) }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp))
            Spacer(Modifier.height(12.dp))
            val filtered = photos.filter { it.name.contains(search, ignoreCase = true) }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.user_profile_no_photos), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.absolutePath }) { file ->
                        AsyncImage(model = file, contentDescription = file.name,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp)).clickable { onSelected(file) },
                            contentScale = ContentScale.Crop)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

