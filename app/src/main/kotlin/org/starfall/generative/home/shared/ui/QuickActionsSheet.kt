package org.starfall.generative.home.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.starfall.generative.R
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.theme.LocalAmoledMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsSheet(
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onTakePhoto: () -> Unit,
    onCreateImage: () -> Unit,
    onCreateVideo: () -> Unit,
    onOpenPromptInstructions: () -> Unit,
    onOpenFeatureTools: () -> Unit,
    onOpenMcpConnectors: () -> Unit,
    onOpenRemoteMachines: () -> Unit,
    onDismiss: () -> Unit
) {
    AppSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        QuickActionsSheetContent(
            onPickImage = onPickImage,
            onPickDocument = onPickDocument,
            onTakePhoto = onTakePhoto,
            onCreateImage = onCreateImage,
            onCreateVideo = onCreateVideo,
            onOpenPromptInstructions = onOpenPromptInstructions,
            onOpenFeatureTools = onOpenFeatureTools,
            onOpenMcpConnectors = onOpenMcpConnectors,
            onOpenRemoteMachines = onOpenRemoteMachines,
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun QuickActionsSheetContent(
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onTakePhoto: () -> Unit,
    onCreateImage: () -> Unit,
    onCreateVideo: () -> Unit,
    onOpenPromptInstructions: () -> Unit,
    onOpenFeatureTools: () -> Unit,
    onOpenMcpConnectors: () -> Unit,
    onOpenRemoteMachines: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AddActionTile(
                icon = Icons.Outlined.Image,
                label = "Photos",
                modifier = Modifier.weight(1f),
                onClick = {
                    onPickImage()
                    onDismiss()
                }
            )
            AddActionTile(
                icon = Icons.Outlined.CameraAlt,
                label = "Camera",
                modifier = Modifier.weight(1f),
                onClick = {
                    onTakePhoto()
                    onDismiss()
                }
            )
            AddActionTile(
                icon = Icons.Outlined.Description,
                label = "Files",
                modifier = Modifier.weight(1f),
                onClick = {
                    onPickDocument()
                    onDismiss()
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AddActionRow(
            icon = Icons.Outlined.Image,
            title = "Image",
            subtitle = "Send directly to an image generation model",
            onClick = { onCreateImage(); onDismiss() }
        )
        AddActionRow(
            icon = Icons.Outlined.Videocam,
            title = "Video",
            subtitle = "Send directly to a video generation model",
            onClick = { onCreateVideo(); onDismiss() }
        )
        AddActionRow(
            icon = Icons.Outlined.DocumentSparkle,
            title = stringResource(R.string.quick_action_prompt_instructions),
            subtitle = stringResource(R.string.quick_action_prompt_instructions_description),
            onClick = onOpenPromptInstructions
        )
        AddActionRow(
            icon = Icons.Outlined.Build,
            title = stringResource(R.string.quick_action_feature_tools),
            subtitle = stringResource(R.string.quick_action_feature_tools_description),
            onClick = onOpenFeatureTools
        )
        AddActionRow(
            icon = Icons.Outlined.Extension,
            title = stringResource(R.string.quick_action_mcp_connectors),
            subtitle = stringResource(R.string.quick_action_mcp_connectors_description),
            onClick = onOpenMcpConnectors
        )
        AddActionRow(
            icon = Icons.Outlined.Computer,
            title = "Remote Machines",
            subtitle = "Choose which machines the model may use",
            onClick = onOpenRemoteMachines
        )
    }
}

@Composable
internal fun QuickActionsSubpageHeader(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                FluentIcon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
internal fun QuickActionsSettingsButton(
    title: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        FluentIcon(
            Icons.Outlined.Settings,
            stringResource(R.string.tool_settings_accessibility, title)
        )
    }
}

@Composable
private fun AddActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val amoledMode = LocalAmoledMode.current
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (amoledMode) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (amoledMode) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FluentIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AddActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            FluentIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
