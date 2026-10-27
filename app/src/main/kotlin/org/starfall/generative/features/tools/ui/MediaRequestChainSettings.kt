package org.starfall.generative.features.tools.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.starfall.generative.R
import org.starfall.generative.shared.model.SystemToolConfig
import org.starfall.generative.shared.model.media.*
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.dialogs.AppAlertDialog as AlertDialog
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.layout.LocalScreenTransitionActive
import org.starfall.generative.shared.ui.layout.edgeToEdgeTopAppBarColors
import org.starfall.generative.shared.ui.layout.scrollableScaffoldPadding
import org.starfall.generative.shared.ui.lists.AppListContainer
import org.starfall.generative.shared.ui.menus.AppDropdownMenu
import org.starfall.generative.shared.ui.menus.RoundedDropdownMenuItem

/** A reusable profile library. Selection belongs to the tool's model dropdown, not this list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaRequestChainSettings(
    provider: ModelProvider?,
    config: SystemToolConfig,
    onAutoSave: (Map<String, MediaRequestProfile>, String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onBack: () -> Unit,
    video: Boolean
) {
    val profiles = config.mediaProfiles(video)
    val protectedId = defaultMediaProfile(video).id
    val storedActiveId = if (video) config.activeVideoRequestProfileId else config.activeImageRequestProfileId
    val activeId = storedActiveId.takeIf { it in profiles } ?: protectedId
    var deletingProfileId by remember { mutableStateOf<String?>(null) }

    fun persist(updated: Map<String, MediaRequestProfile>) {
        val normalized = updated.withDefaultMediaProfile(video)
        onAutoSave(normalized, activeId.takeIf { it in normalized } ?: protectedId)
    }

    BackHandler(enabled = LocalScreenTransitionActive.current) { onBack() }
    Scaffold(modifier = Modifier.testTag("media-profiles-screen"), topBar = {
        TopAppBar(
            colors = edgeToEdgeTopAppBarColors(),
            title = { Text(stringResource(if (video) R.string.media_video_gen_profiles else R.string.media_image_gen_profiles)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
            },
            actions = {
                IconButton(onClick = onAddProfile) {
                    FluentIcon(Icons.Default.Add, stringResource(R.string.common_add))
                }
            }
        )
    }) { padding ->
        val ordered = profiles.values.sortedWith(compareBy<MediaRequestProfile> {
            if (it.mode == MediaGenerationMode.DEFAULT) 0 else 1
        }.thenBy { if (it.id == protectedId) 0 else 1 })
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = scrollableScaffoldPadding(padding, horizontal = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ordered, key = { it.id }) { profile ->
                val canDelete = profile.id != protectedId &&
                    (profile.mode != MediaGenerationMode.DEFAULT ||
                        profiles.values.count { it.mode == MediaGenerationMode.DEFAULT } > 1)
                AppListContainer(
                    isGrid = false,
                    onClick = { onOpenProfile(profile.id) },
                    modifier = Modifier.testTag("media-profile-item_${profile.id}")
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FluentIcon(Icons.Outlined.FormatListBulleted, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (profile.mode == MediaGenerationMode.CUSTOM)
                                    pluralStringResource(R.plurals.media_chain_profile_steps, profile.chain.steps.size, profile.chain.steps.size)
                                else stringResource(R.string.common_default),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                FluentIcon(Icons.Default.MoreVert, stringResource(R.string.common_more_options))
                            }
                            AppDropdownMenu(menuExpanded, { menuExpanded = false }) {
                                RoundedDropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_edit)) },
                                    leadingIcon = { FluentIcon(Icons.Outlined.Edit, null) },
                                    onClick = { menuExpanded = false; onOpenProfile(profile.id) }
                                )
                                if (canDelete) RoundedDropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { FluentIcon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuExpanded = false; deletingProfileId = profile.id }
                                )
                            }
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
            confirmButton = {
                TextButton(onClick = {
                    if (id != protectedId && profiles.values.count { it.mode == MediaGenerationMode.DEFAULT } >
                        (if (profiles[id]?.mode == MediaGenerationMode.DEFAULT) 1 else 0)) {
                        persist(profiles - id)
                    }
                    deletingProfileId = null
                }) { Text(stringResource(R.string.common_remove)) }
            },
            dismissButton = { TextButton(onClick = { deletingProfileId = null }) {
                Text(stringResource(R.string.common_cancel))
            } }
        )
    }
}
