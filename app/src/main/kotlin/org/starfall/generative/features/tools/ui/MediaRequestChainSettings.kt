package org.starfall.generative.features.tools.ui

import org.starfall.generative.shared.ui.layout.scrollableScaffoldPadding
import org.starfall.generative.shared.ui.layout.edgeToEdgeTopAppBarColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.media.*
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.layout.SettingsCard
import java.util.UUID

/** Library of ImageGenProfiles or VideoGenProfiles, with a protected built-in Default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaRequestChainSettings(
    provider: ModelProvider,
    config: SystemToolConfig,
    onSave: (Map<String, MediaRequestProfile>, String) -> Unit,
    onAutoSave: (Map<String, MediaRequestProfile>, String) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    video: Boolean
) {
    val initialProfiles = remember(provider.id, video) { config.mediaProfiles(video) }
    var profiles by remember(provider.id, video) { mutableStateOf(initialProfiles) }
    val storedActiveId = provider.config.modelConfigs[config.modelId]?.mediaRequestProfileId
        ?.takeIf { it in initialProfiles }
        ?: if (video) config.activeVideoRequestProfileId else config.activeImageRequestProfileId
    var activeId by remember(provider.id, video) {
        mutableStateOf(storedActiveId.takeIf { it in initialProfiles } ?: initialProfiles.keys.first())
    }
    var baselineProfiles by remember(provider.id, video) { mutableStateOf(initialProfiles) }
    var baselineActiveId by remember(provider.id, video) { mutableStateOf(activeId) }
    var editing by remember { mutableStateOf<MediaRequestProfile?>(null) }
    var deletingProfileId by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val protectedId = defaultMediaProfile(video).id
    val dirty = profiles != baselineProfiles || activeId != baselineActiveId

    fun saveProfiles() {
        val normalized = profiles.withDefaultMediaProfile(video)
        onSave(normalized, activeId.takeIf { it in normalized } ?: protectedId)
        baselineProfiles = normalized
        baselineActiveId = activeId
    }
    fun leave() { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(enabled = LocalScreenTransitionActive.current && editing == null) { leave() }

    editing?.let { profile ->
        MediaRequestProfileEditor(
            provider = provider,
            config = config,
            profile = profile,
            video = video,
            onSave = { updated ->
                profiles = (profiles + (updated.id to updated)).withDefaultMediaProfile(video)
                editing = null
            },
            // Nested step edits stay in the editor draft until its Save action.
            onAutoSave = {},
            onBack = { editing = null }
        )
        return
    }

    Scaffold(topBar = {
        TopAppBar(
            colors = edgeToEdgeTopAppBarColors(),
            title = { Text(stringResource(if (video) R.string.media_video_gen_profiles else R.string.media_image_gen_profiles)) },
            navigationIcon = { IconButton(onClick = ::leave) {
                FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
            } },
            actions = {
                IconButton(onClick = ::saveProfiles, enabled = dirty) {
                    FluentIcon(Icons.Outlined.Save, stringResource(R.string.common_save))
                }
            }
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = scrollableScaffoldPadding(padding, horizontal = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.media_chain_profiles), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        editing = MediaRequestProfile(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            mode = MediaGenerationMode.DEFAULT
                        )
                    }) { FluentIcon(Icons.Default.Add, stringResource(R.string.common_add)) }
                }
            }
            // Default profiles are grouped first; the protected Default always appears at the top.
            val ordered = profiles.values.sortedWith(compareBy<MediaRequestProfile> {
                if (it.mode == MediaGenerationMode.DEFAULT) 0 else 1
            }.thenBy { if (it.id == protectedId) 0 else 1 })
            items(ordered, key = { it.id }) { profile ->
                val active = profile.id == activeId
                val canDelete = profile.id != protectedId &&
                    (profile.mode != MediaGenerationMode.DEFAULT || profiles.values.count { it.mode == MediaGenerationMode.DEFAULT } > 1)
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = active, onClick = { activeId = profile.id })
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (profile.mode == MediaGenerationMode.DEFAULT) R.string.common_default else R.string.media_chain_custom),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = profile }) {
                            FluentIcon(Icons.Outlined.Edit, stringResource(R.string.common_edit))
                        }
                        IconButton(enabled = canDelete, onClick = { deletingProfileId = profile.id }) {
                            FluentIcon(Icons.Outlined.Delete, stringResource(R.string.common_remove))
                        }
                    }
                }
            }
        }
    }
    deletingProfileId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingProfileId = null },
            title = { Text(stringResource(R.string.dialog_remove_profile_title)) },
            text = { Text(stringResource(R.string.media_chain_remove_profile_message)) },
            confirmButton = { TextButton(onClick = {
                if (id != protectedId && profiles.values.count { it.mode == MediaGenerationMode.DEFAULT } >
                    (if (profiles[id]?.mode == MediaGenerationMode.DEFAULT) 1 else 0)) {
                    profiles = (profiles - id).withDefaultMediaProfile(video)
                    if (activeId == id) activeId = protectedId
                }
                deletingProfileId = null
            }) { Text(stringResource(R.string.common_remove)) } },
            dismissButton = { TextButton(onClick = { deletingProfileId = null }) {
                Text(stringResource(R.string.common_cancel))
            } }
        )
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.common_discard_changes)) },
        text = { Text(stringResource(R.string.media_chain_discard_message)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) {
            Text(stringResource(R.string.common_discard))
        } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) {
            Text(stringResource(R.string.common_keep_editing))
        } }
    )
}
