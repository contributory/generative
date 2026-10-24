package org.starfall.generative.features.tools.ui

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
import org.starfall.generative.shared.model.media.MediaRequestProfile
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.layout.SettingsCard
import java.util.UUID

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
    val newProfileName = stringResource(R.string.profile_new)
    val storedProfiles = if (video) config.videoRequestProfiles else config.imageRequestProfiles
    val initialProfiles = remember(provider.id, video) {
        storedProfiles.ifEmpty {
            val id = UUID.randomUUID().toString()
            mapOf(id to MediaRequestProfile(id, newProfileName))
        }
    }
    var profiles by remember(provider.id, video) { mutableStateOf(initialProfiles) }
    val storedActiveId = if (video) config.activeVideoRequestProfileId else config.activeImageRequestProfileId
    var activeId by remember(provider.id, video) {
        mutableStateOf(storedActiveId.takeIf { it in initialProfiles } ?: initialProfiles.keys.first())
    }
    var baselineProfiles by remember(provider.id, video) { mutableStateOf(initialProfiles) }
    var baselineActiveId by remember(provider.id, video) { mutableStateOf(storedActiveId) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var deletingProfileId by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = profiles != baselineProfiles || activeId != baselineActiveId

    fun leave() {
        if (dirty) confirmDiscard = true else onBack()
    }

    BackHandler(enabled = LocalScreenTransitionActive.current) { leave() }

    editingProfileId?.let { id ->
        val profile = profiles[id]
        if (profile == null) {
            editingProfileId = null
        } else {
            MediaRequestProfileEditor(
                provider = provider,
                profile = profile,
                video = video,
                onSave = { updated ->
                    profiles = profiles + (updated.id to updated)
                    editingProfileId = null
                },
                onAutoSave = { updated ->
                    val nextProfiles = profiles + (updated.id to updated)
                    profiles = nextProfiles
                    baselineProfiles = nextProfiles
                    baselineActiveId = activeId
                    onAutoSave(nextProfiles, activeId)
                },
                onBack = { editingProfileId = null }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (video) R.string.tool_video_settings else R.string.tool_image_settings)) },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = profiles.isNotEmpty(),
                        onClick = { onSave(profiles, activeId) },
                    ) {
                        FluentIcon(Icons.Outlined.Save, stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    provider.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.media_chain_profiles_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.media_chain_profiles),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val id = UUID.randomUUID().toString()
                        profiles = profiles + (id to MediaRequestProfile(id, newProfileName))
                        activeId = id
                        editingProfileId = id
                    }) {
                        FluentIcon(Icons.Default.Add, stringResource(R.string.common_add))
                    }
                }
            }
            items(profiles.values.toList(), key = { it.id }) { profile ->
                val active = profile.id == activeId
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = active,
                            onClick = { activeId = profile.id }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.media_chain_profile_step_count, profile.chain.steps.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    IconButton(onClick = { editingProfileId = profile.id }) {
                        FluentIcon(Icons.Outlined.Edit, stringResource(R.string.common_edit))
                    }
                    IconButton(
                        enabled = profiles.size > 1,
                        onClick = { deletingProfileId = profile.id }
                    ) {
                        FluentIcon(Icons.Outlined.Delete, stringResource(R.string.common_remove))
                    }
                    }
                    if (active) {
                        Text(
                            stringResource(R.string.media_chain_active_profile),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 52.dp)
                        )
                    }
                }
            }
        }
    }

    deletingProfileId?.let { profileId ->
        AlertDialog(
            onDismissRequest = { deletingProfileId = null },
            text = { Text(stringResource(R.string.media_chain_remove_profile_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val nextProfiles = profiles - profileId
                    profiles = nextProfiles
                    if (activeId == profileId) activeId = nextProfiles.keys.first()
                    deletingProfileId = null
                }) {
                    Text(stringResource(R.string.common_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProfileId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.common_discard_changes)) },
        text = { Text(stringResource(R.string.media_chain_discard_message)) },
        confirmButton = {
            TextButton(onClick = { confirmDiscard = false; onBack() }) {
                Text(stringResource(R.string.common_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDiscard = false }) {
                Text(stringResource(R.string.common_keep_editing))
            }
        }
    )
}
