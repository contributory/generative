package org.starfall.multigateway.home.sheets.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.starfall.multigateway.shared.ui.sheets.AppBottomSheet
import org.starfall.multigateway.shared.ui.icons.FluentIcon
import org.starfall.multigateway.shared.ui.icons.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsBottomSheet(
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onTakePhoto: () -> Unit,
    onCreateImage: () -> Unit,
    onCreateVideo: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenRemoteMachines: () -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
                icon = Icons.Outlined.Extension,
                title = "Tools Manage",
                subtitle = "Choose tools available to the model",
                onClick = {
                    onOpenTools()
                    onDismiss()
                }
            )
            AddActionRow(
                icon = Icons.Outlined.Computer,
                title = "Remote Machines",
                subtitle = "Choose which machines the model may use",
                onClick = {
                    onOpenRemoteMachines()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun AddActionTile(
    icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
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
    icon: Int,
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
