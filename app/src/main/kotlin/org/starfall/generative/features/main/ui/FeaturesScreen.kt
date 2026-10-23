package org.starfall.generative.features.main.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.shared.icons.IconStore
import org.starfall.generative.shared.theme.LocalAmoledMode
import org.starfall.generative.shared.ui.FeaturesLogoImage
import org.starfall.generative.shared.ui.icons.FluentUi

internal val GroupOuterCorner = 24.dp
internal val GroupInnerCorner = 6.dp

internal val GroupTopShape = RoundedCornerShape(
    topStart = GroupOuterCorner,
    topEnd = GroupOuterCorner,
    bottomStart = GroupInnerCorner,
    bottomEnd = GroupInnerCorner
)

internal val GroupMiddleShape = RoundedCornerShape(GroupInnerCorner)

internal val GroupBottomShape = RoundedCornerShape(
    topStart = GroupInnerCorner,
    topEnd = GroupInnerCorner,
    bottomStart = GroupOuterCorner,
    bottomEnd = GroupOuterCorner
)

/** Main feature directory. Deliberately has no page title; the themed app logo is its header. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturesScreen(
    modifier: Modifier = Modifier,
    logoImage: String? = null,
    onLogoImageChange: (String?) -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToChatOptions: () -> Unit = {},
    onNavigateToToolsEditor: () -> Unit = {},
    onNavigateToProviders: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToSpeech: () -> Unit = {},
    onNavigateToRemoteMachines: () -> Unit = {},
    onNavigateToStorage: () -> Unit = {},
    onNavigateToAppSettings: () -> Unit = {},
    onNavigateToIcons: () -> Unit = {},
    onNavigateToAppData: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val iconStore = remember(context) { IconStore(context) }
    var logoMenuExpanded by remember { mutableStateOf(false) }
    var logoBusy by remember { mutableStateOf(false) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            logoBusy = true
            try {
                val previous = logoImage
                val replacement = withContext(Dispatchers.IO) {
                    iconStore.importImage(uri, shared = false)
                }
                previous?.let { old ->
                    withContext(Dispatchers.IO) { runCatching { iconStore.delete(old) } }
                }
                onLogoImageChange(replacement)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the existing logo if the selected image cannot be imported.
            } finally {
                logoBusy = false
            }
        }
    }

    val edgeSwipeStarted = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val edgeSwipeDistance = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val gestureStartWidthPx = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp.toPx() / 2f)
    }
    val backThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }

    LazyColumn(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(onBack, gestureStartWidthPx, backThresholdPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        edgeSwipeStarted.value = offset.x <= gestureStartWidthPx
                        edgeSwipeDistance.floatValue = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (edgeSwipeStarted.value && dragAmount > 0f) {
                            edgeSwipeDistance.floatValue += dragAmount
                            if (edgeSwipeDistance.floatValue >= backThresholdPx) {
                                change.consume()
                                edgeSwipeStarted.value = false
                                onBack()
                            }
                        }
                    },
                    onDragEnd = {
                        edgeSwipeStarted.value = false
                        edgeSwipeDistance.floatValue = 0f
                    },
                    onDragCancel = {
                        edgeSwipeStarted.value = false
                        edgeSwipeDistance.floatValue = 0f
                    }
                )
            },
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "app-logo") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { if (!logoBusy) logoMenuExpanded = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        FeaturesLogoImage(logoImage, Modifier.size(84.dp))
                    }
                    DropdownMenu(
                        expanded = logoMenuExpanded,
                        onDismissRequest = { logoMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.features_logo_choose_image)) },
                            enabled = !logoBusy,
                            onClick = {
                                logoMenuExpanded = false
                                logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.features_logo_remove)) },
                            enabled = !logoBusy && logoImage != null,
                            onClick = {
                                logoMenuExpanded = false
                                logoImage?.let { old ->
                                    scope.launch {
                                        logoBusy = true
                                        try {
                                            withContext(Dispatchers.IO) { iconStore.delete(old) }
                                            onLogoImageChange(null)
                                        } finally {
                                            logoBusy = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        item(key = "configuration-heading") {
            SectionHeading(stringResource(R.string.settings_group_configuration))
        }
        item(key = "configuration-group") {
            FeatureGroup {
                FeatureButton(
                    stringResource(R.string.settings_chat_options),
                    stringResource(R.string.settings_chat_options_description),
                    FluentUi.Regular.Chat,
                    onNavigateToChatOptions,
                    shape = GroupTopShape
                )
                FeatureButton(
                    stringResource(R.string.settings_tools_editor),
                    stringResource(R.string.settings_tools_editor_description),
                    FluentUi.Regular.WrenchScrewdriver,
                    onNavigateToToolsEditor,
                    shape = GroupBottomShape
                )
            }
        }
        item(key = "external-services-heading") {
            Spacer(Modifier.height(8.dp))
            SectionHeading(stringResource(R.string.settings_group_external_services))
        }
        item(key = "external-services-group") {
            FeatureGroup {
                FeatureButton(
                    stringResource(R.string.settings_providers),
                    stringResource(R.string.settings_providers_description),
                    FluentUi.Regular.Cloud,
                    onNavigateToProviders,
                    shape = GroupTopShape
                )
                FeatureButton(
                    stringResource(R.string.settings_mcp_manage),
                    stringResource(R.string.settings_mcp_manage_description),
                    FluentUi.Regular.PuzzlePiece,
                    onNavigateToMcp,
                    shape = GroupMiddleShape
                )
                FeatureButton(
                    stringResource(R.string.settings_speech_services),
                    stringResource(R.string.settings_speech_services_description),
                    FluentUi.Regular.PersonVoice,
                    onNavigateToSpeech,
                    shape = GroupMiddleShape
                )
                FeatureButton(
                    stringResource(R.string.settings_remote_machines),
                    stringResource(R.string.settings_remote_machines_description),
                    FluentUi.Regular.Desktop,
                    onNavigateToRemoteMachines,
                    shape = GroupBottomShape
                )
            }
        }
        item(key = "data-caching-heading") {
            Spacer(Modifier.height(8.dp))
            SectionHeading(stringResource(R.string.settings_group_data_caching))
        }
        item(key = "data-caching-group") {
            FeatureGroup {
                FeatureButton(
                    stringResource(R.string.settings_storage),
                    stringResource(R.string.settings_storage_description),
                    FluentUi.Regular.FolderOpen,
                    onNavigateToStorage,
                    shape = GroupTopShape
                )
                FeatureButton(
                    stringResource(R.string.icon_settings_title),
                    stringResource(R.string.icon_settings_description),
                    FluentUi.Regular.Image,
                    onNavigateToIcons,
                    shape = GroupBottomShape
                )
            }
        }
        item(key = "system-heading") {
            Spacer(Modifier.height(8.dp))
            SectionHeading(stringResource(R.string.settings_group_system))
        }
        item(key = "system-group") {
            FeatureGroup {
                FeatureButton(
                    stringResource(R.string.settings_category_app_settings),
                    stringResource(R.string.settings_category_app_settings_description),
                    FluentUi.Regular.Settings,
                    onNavigateToAppSettings,
                    shape = GroupTopShape
                )
                FeatureButton(
                    stringResource(R.string.settings_category_app_data),
                    stringResource(R.string.settings_category_app_data_description),
                    FluentUi.Regular.History,
                    onNavigateToAppData,
                    shape = GroupMiddleShape
                )
                FeatureButton(
                    stringResource(R.string.settings_category_about),
                    stringResource(R.string.settings_category_about_description),
                    FluentUi.Regular.Info,
                    onNavigateToAbout,
                    shape = GroupBottomShape
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun FeatureGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
internal fun featureButtonContainerColor(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    amoledMode: Boolean = LocalAmoledMode.current
): Color = if (amoledMode) {
    colorScheme.surfaceContainerLow
} else {
    colorScheme.surfaceContainerHigh
}

@Composable
internal fun featureButtonBorder(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    amoledMode: Boolean = LocalAmoledMode.current
): BorderStroke? = if (amoledMode) {
    BorderStroke(1.5.dp, colorScheme.outlineVariant)
} else {
    null
}

@Composable
private fun FeatureButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    shape: Shape
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = featureButtonContainerColor(),
        border = featureButtonBorder()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = FluentUi.Regular.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}
