package org.starfall.generative.features.customization.ui

import org.starfall.generative.shared.ui.layout.scrollableScaffoldPadding
import org.starfall.generative.shared.ui.layout.edgeToEdgeTopAppBarColors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.starfall.generative.R
import org.starfall.generative.shared.icons.AppImages
import org.starfall.generative.shared.icons.IconStore
import org.starfall.generative.shared.storage.preferences.UserProfile
import org.starfall.generative.shared.storage.preferences.AppPreferences
import org.starfall.generative.shared.theme.CustomPalette
import org.starfall.generative.shared.storage.preferences.UserProfileImageSlot
import org.starfall.generative.shared.ui.FeaturesLogo
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.background.ProfileBackground
import org.starfall.generative.shared.ui.dialogs.AlertDialog
import org.starfall.generative.shared.ui.menus.DropdownMenu
import org.starfall.generative.shared.ui.menus.DropdownMenuItem

/** App-wide customization: colors, component opacity, profile image and backgrounds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    profile: UserProfile,
    onChange: ((UserProfile) -> UserProfile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    appearance: AppPreferences = AppPreferences(),
    onDynamicColorChange: (Boolean) -> Unit = {},
    onColorSchemeChange: (String) -> Unit = {},
    onCustomPaletteChange: (CustomPalette) -> Unit = {},
    onItemTransparencyChange: (Float) -> Unit = {},
    onInputAreaTransparencyChange: (Float) -> Unit = {},
    onInputAreaBlurChange: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<UserProfileImageSlot?>(null) }
    var menuFor by remember { mutableStateOf<UserProfileImageSlot?>(null) }
    val transparencyDrafts = remember { mutableStateMapOf<UserProfileImageSlot, Float>() }
    LaunchedEffect(profile) {
        transparencyDrafts.toMap().forEach { (slot, draft) ->
            if (slot.imageTransparency(profile) == draft) transparencyDrafts.remove(slot)
        }
    }
    var showStorage by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewSlot by rememberSaveable { mutableStateOf(UserProfileImageSlot.CHAT) }

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
    fun openMenu(slot: UserProfileImageSlot) {
        if (slot != UserProfileImageSlot.PHOTO) previewSlot = slot
        menuFor = slot
    }
    fun chooseStorage(slot: UserProfileImageSlot) {
        if (slot != UserProfileImageSlot.PHOTO) previewSlot = slot
        target = slot; menuFor = null; showStorage = true
    }

    Scaffold(

        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = edgeToEdgeTopAppBarColors(),
                title = { Text(stringResource(R.string.customization_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) {
                    SharedIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("customization-list"),
            contentPadding = scrollableScaffoldPadding(padding, horizontal = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "photo") {
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
                                ) { SharedIcon(Icons.Default.MoreVert, stringResource(R.string.user_profile_photo_options)) }
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
            item(key = "backgrounds-heading") { Text(stringResource(R.string.customization_backgrounds_title), modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            item(key = "background-preview") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_palette_preview),
                        style = MaterialTheme.typography.titleSmall)
                    val slots = listOf(
                        UserProfileImageSlot.CHAT to R.string.user_profile_background_chat,
                        UserProfileImageSlot.DRAWER to R.string.user_profile_background_drawer,
                        UserProfileImageSlot.MENU to R.string.user_profile_background_menu,
                        UserProfileImageSlot.SHEET to R.string.user_profile_background_bottom_sheet,
                        UserProfileImageSlot.DIALOG to R.string.user_profile_background_dialog
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        slots.forEach { (slot, label) ->
                            FilterChip(
                                selected = previewSlot == slot, onClick = { previewSlot = slot },
                                label = { Text(stringResource(label), maxLines = 1) },
                                modifier = Modifier.testTag("profile-preview-select-${slot.name.lowercase()}")
                            )
                        }
                    }
                    ProfileBackdropPreview(
                        profile, previewSlot,
                        Modifier.fillMaxWidth().testTag("profile-preview-active"),
                        transparencyDrafts[previewSlot] ?: previewSlot.imageTransparency(profile)
                    )
                }
            }
            item(key = "backgrounds") {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(
                        Triple(UserProfileImageSlot.CHAT, stringResource(R.string.user_profile_background_chat), profile.chatBackground),
                        Triple(UserProfileImageSlot.DRAWER, stringResource(R.string.user_profile_background_drawer), profile.drawerBackground),
                        Triple(UserProfileImageSlot.MENU, stringResource(R.string.user_profile_background_menu), profile.menuBackground),
                        Triple(UserProfileImageSlot.SHEET, stringResource(R.string.user_profile_background_bottom_sheet), profile.sheetBackground),
                        Triple(UserProfileImageSlot.DIALOG, stringResource(R.string.user_profile_background_dialog), profile.dialogBackground)
                    ).forEachIndexed { index, (slot, label, image) ->
                        val transparency = transparencyDrafts[slot] ?: slot.imageTransparency(profile)
                        val transparencyLabel = stringResource(R.string.user_profile_background_transparency)
                        Surface(shape = if (index == 0) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp,
                            bottomStart = 6.dp, bottomEnd = 6.dp) else RoundedCornerShape(6.dp),
                            color = prominentContainerColor()) {
                            Column {
                                Row(Modifier.fillMaxWidth().clickable { openMenu(slot) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    ProfileImage(image, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    Box {
                                        IconButton(onClick = { openMenu(slot) }) {
                                            SharedIcon(Icons.Default.MoreVert, stringResource(R.string.user_profile_background_options, label))
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
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = 14.dp, end = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Slider(
                                        value = transparency,
                                        onValueChange = { value ->
                                            previewSlot = slot
                                            transparencyDrafts[slot] = value
                                        },
                                        onValueChangeFinished = {
                                            val chosen = transparencyDrafts[slot] ?: slot.imageTransparency(profile)
                                            onChange { slot.withImageTransparency(it, chosen) }
                                        },
                                        modifier = Modifier.weight(1f)
                                            .semantics { contentDescription = "$label: $transparencyLabel" }
                                            .testTag("profile-transparency-${slot.name.lowercase()}")
                                    )
                                    Text(
                                        "${(transparency * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.width(44.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                    IconButton(onClick = {
                                        previewSlot = slot
                                        transparencyDrafts[slot] = 0.5f
                                        onChange { slot.withImageTransparency(it, 0.5f) }
                                    }) {
                                        SharedIcon(Icons.Default.Refresh,
                                            stringResource(R.string.user_profile_background_transparency_reset))
                                    }
                                }
                            }
                        }
                    }

                }
            }
            item(key = "colors") {
                CustomizationColors(
                    appearance = appearance,
                    onDynamicColorChange = onDynamicColorChange,
                    onColorSchemeChange = onColorSchemeChange,
                    onCustomPaletteChange = onCustomPaletteChange,
                    onItemTransparencyChange = onItemTransparencyChange
                )
            }
            item(key = "input-area") {
                InputAreaSettings(appearance, onInputAreaTransparencyChange, onInputAreaBlurChange)
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

/** Live approximation of each destination using the same image and alpha as its real surface. */
@Composable
private fun ProfileBackdropPreview(
    profile: UserProfile,
    slot: UserProfileImageSlot,
    modifier: Modifier = Modifier,
    transparency: Float = slot.imageTransparency(profile)
) {
    val title = stringResource(when (slot) {
        UserProfileImageSlot.CHAT -> R.string.user_profile_background_chat
        UserProfileImageSlot.DRAWER -> R.string.user_profile_background_drawer
        UserProfileImageSlot.MENU -> R.string.user_profile_background_menu
        UserProfileImageSlot.SHEET -> R.string.user_profile_background_bottom_sheet
        UserProfileImageSlot.DIALOG -> R.string.user_profile_background_dialog
        UserProfileImageSlot.PHOTO -> R.string.user_profile_photo_title
    })
    Surface(modifier.height(132.dp)
        .semantics { stateDescription = "${(transparency * 100).toInt()}%" },
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.fillMaxSize()) {
            ProfileBackground(slot.get(profile), Modifier.matchParentSize(), transparency)
            Column(Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Surface(shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)) {
                    Text(stringResource(R.string.user_profile_background_preview_sample),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                Box(Modifier.fillMaxWidth(0.65f).height(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
            }
        }
    }
}

@Composable
private fun SlotMenu(expanded: Boolean, hasImage: Boolean, onDismiss: () -> Unit,
                     onGallery: () -> Unit, onStorage: () -> Unit, onRemove: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.user_profile_photos_gallery)) }, onClick = onGallery,
            leadingIcon = { SharedIcon(Icons.Outlined.Image, null) })
        DropdownMenuItem(text = { Text(stringResource(R.string.settings_storage)) }, onClick = onStorage,
            leadingIcon = { SharedIcon(Icons.Outlined.Folder, null) })
        DropdownMenuItem(text = { Text(stringResource(R.string.common_remove)) }, enabled = hasImage, onClick = onRemove,
            leadingIcon = { SharedIcon(Icons.Outlined.Delete, null) })
    }
}

@Composable
private fun ProfileImage(image: String?, modifier: Modifier, fallback: Boolean = false) {
    if (image == null) {
        if (fallback) FeaturesLogo(null, modifier)
        else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            SharedIcon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val context = LocalContext.current
        val model = remember(image) { IconStore(context).imageData(image) ?: AppImages.source(image) }
        AsyncImage(model = model, contentDescription = null, modifier = modifier,
            contentScale = ContentScale.Crop)
    }
}
