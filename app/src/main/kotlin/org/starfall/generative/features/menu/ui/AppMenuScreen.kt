package org.starfall.generative.features.menu.ui

import org.starfall.generative.shared.ui.menus.DropdownMenu
import org.starfall.generative.shared.ui.menus.DropdownMenuItem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import org.starfall.generative.shared.ui.icons.SharedIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starfall.generative.R
import org.starfall.generative.shared.icons.IconStore
import org.starfall.generative.shared.ui.FeaturesLogo
import org.starfall.generative.shared.ui.layout.appearanceContainerColor
import org.starfall.generative.shared.ui.layout.appearanceContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.icons.FluentUi
import org.starfall.generative.shared.ui.icons.Icons

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

/** App menu screen. Deliberately has no page title; the themed app logo is its header. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppMenuScreen(
    modifier: Modifier = Modifier,
    logoImage: String? = null,
    onLogoImageChange: (String?) -> Unit = {},
    onNavigateToCustomization: () -> Unit = {},
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
    onNavigateToAbout: () -> Unit = {},
    onNavigateToChat: () -> Unit = {}
) {
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val currentNavigateToChat by rememberUpdatedState(onNavigateToChat)

    // Background is owned once by AppNavigation for every destination in the Menu
    // family. Only this screen's content participates in nested navigation slides.
    Box(
        modifier.fillMaxSize().testTag("app-menu-root").pointerInput(Unit) {
            val backSwipeDistance = 72.dp.toPx()
            var horizontalDrag = 0f
            detectHorizontalDragGestures(
                onDragStart = { horizontalDrag = 0f },
                onHorizontalDrag = { change, amount ->
                    horizontalDrag += amount
                    change.consume()
                },
                onDragEnd = {
                    if (horizontalDrag >= backSwipeDistance) currentNavigateToChat()
                    horizontalDrag = 0f
                },
                onDragCancel = { horizontalDrag = 0f }
            )
        }
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Draw the list itself behind transparent system bars, but keep interactive content inside
        // the safe area. This is the same edge-to-edge arrangement used by ChatScreen.
        contentPadding = PaddingValues(
            start = 16.dp + if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) {
                safeDrawing.calculateLeftPadding(layoutDirection)
            } else {
                safeDrawing.calculateRightPadding(layoutDirection)
            },
            top = 16.dp + safeDrawing.calculateTopPadding(),
            end = 16.dp + if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) {
                safeDrawing.calculateRightPadding(layoutDirection)
            } else {
                safeDrawing.calculateLeftPadding(layoutDirection)
            },
            bottom = 24.dp + safeDrawing.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "app-logo") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(84.dp).clip(CircleShape)
                        .combinedClickable(
                            onClick = onNavigateToCustomization,
                            onLongClick = onNavigateToCustomization
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    FeaturesLogo(logoImage, Modifier.size(84.dp))
                }
            }
        }
        item(key = "configuration-heading") {
            SectionHeading(stringResource(R.string.common_configuration))
        }
        item(key = "configuration-group") {
            FeatureGroup {
                FeatureButton(
                    stringResource(R.string.customization_title),
                    stringResource(R.string.customization_description),
                    FluentUi.Regular.Image,
                    onNavigateToCustomization,
                    shape = GroupTopShape
                )
                FeatureButton(
                    stringResource(R.string.settings_chat_options),
                    stringResource(R.string.settings_chat_options_description),
                    FluentUi.Regular.Chat,
                    onNavigateToChatOptions,
                    shape = GroupMiddleShape
                )
                FeatureButton(
                    stringResource(R.string.common_feature_tools),
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
                    stringResource(R.string.providers_title),
                    stringResource(R.string.settings_providers_description),
                    FluentUi.Regular.Cloud,
                    onNavigateToProviders,
                    shape = GroupTopShape
                )
                FeatureButton(
                    stringResource(R.string.common_mcp_connectors),
                    stringResource(R.string.settings_mcp_manage_description),
                    FluentUi.Regular.PuzzlePiece,
                    onNavigateToMcp,
                    shape = GroupMiddleShape
                )
                FeatureButton(
                    stringResource(R.string.speech_services_title),
                    stringResource(R.string.settings_speech_services_description),
                    FluentUi.Regular.PersonVoice,
                    onNavigateToSpeech,
                    shape = GroupMiddleShape
                )
                FeatureButton(
                    stringResource(R.string.remote_machines_title),
                    stringResource(R.string.remote_machines_description),
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
            SectionHeading(stringResource(R.string.common_system))
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
    IconButton(
        onClick = onNavigateToChat,
        modifier = Modifier.align(Alignment.TopStart)
            .padding(
                start = 16.dp + safeDrawing.calculateStartPadding(layoutDirection),
                top = 16.dp + safeDrawing.calculateTopPadding()
            )
            .size(48.dp)
            .testTag("app-menu-back")
    ) {
        SharedIcon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.common_back),
            modifier = Modifier.size(24.dp)
        )
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
        color = prominentContainerColor(),
        border = prominentContainerBorder(1.5.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = appearanceContainerColor(MaterialTheme.colorScheme.primaryContainer),
                border = appearanceContainerBorder(),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SharedIcon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SharedIcon(imageVector = FluentUi.Regular.ArrowForward, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}
