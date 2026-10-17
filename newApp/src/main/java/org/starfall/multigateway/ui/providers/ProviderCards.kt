package org.starfall.multigateway.ui.providers

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.starfall.multigateway.shared.model.AuthMethod
import org.starfall.multigateway.shared.model.ModelProvider
import org.starfall.multigateway.shared.model.ProviderGroup
import org.starfall.multigateway.R
import org.starfall.multigateway.ui.components.AdaptiveCardLayout
import org.starfall.multigateway.ui.components.EntityIcon
import org.starfall.multigateway.ui.components.providerInitials
import org.starfall.multigateway.ui.components.RoundedDropdownMenuItem as DropdownMenuItem
import org.starfall.multigateway.ui.icons.FluentIcon
import org.starfall.multigateway.ui.icons.Icons

private val ProviderListCardHeight = 88.dp
private val ProviderGridCardHeight = 152.dp

/** One root tile: a provider card or a folder card. Shared by the grid cell and the drag ghost. */
@Composable
internal fun ProviderRootCard(
    item: ProviderRootItem,
    members: List<ModelProvider>,
    isGrid: Boolean,
    isDropTarget: Boolean,
    testTag: Boolean = true,
    onEditProvider: (ModelProvider) -> Unit,
    onMoveProvider: (ModelProvider) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onOpenGroup: (String) -> Unit
) {
    when (item) {
        is ProviderRootItem.ProviderItem -> ProviderUnifiedCard(
            provider = item.provider,
            isGrid = isGrid,
            modifier = Modifier.then(if (testTag) Modifier.testTag("provider_${item.provider.id}") else Modifier),
            onEdit = { onEditProvider(item.provider) },
            onMoveToGroup = { onMoveProvider(item.provider) },
            onDelete = { onDeleteProvider(item.provider.id) }
        )
        is ProviderRootItem.GroupItem -> ProviderGroupCollapsedCard(
            group = item.group,
            isGrid = isGrid,
            providers = members,
            isDropTarget = isDropTarget,
            modifier = Modifier.then(if (testTag) Modifier.testTag("provider_group_${item.group.id}") else Modifier),
            onOpen = { onOpenGroup(item.group.id) }
        )
    }
}

@Composable
private fun ProviderGroupCollapsedCard(
    group: ProviderGroup,
    isGrid: Boolean,
    providers: List<ModelProvider>,
    isDropTarget: Boolean = false,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (isGrid) 20.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "providerGroupCorner"
    )
    val height = if (isGrid) ProviderGridCardHeight else ProviderListCardHeight

    Surface(
        shape = RoundedCornerShape(corner),
        color = if (isDropTarget) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (isDropTarget) 2.dp else 1.5.dp,
            color = if (isDropTarget) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onOpen)
    ) {
        val titleColor = if (isDropTarget) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current
        if (isGrid) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                ProviderFolderPreview(
                    group,
                    providers,
                    Modifier.size(52.dp).testTag("provider_group_icon_${group.id}")
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.provider_count, providers.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProviderFolderPreview(
                    group,
                    providers,
                    Modifier.size(46.dp).testTag("provider_group_icon_${group.id}")
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.provider_count, providers.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** A provider inside an open folder: the very same card as outside, only with its own test tag. */
@Composable
internal fun ProviderFolderProviderTile(
    provider: ModelProvider,
    isGrid: Boolean,
    onEdit: () -> Unit,
    onMoveToGroup: () -> Unit,
    onDelete: () -> Unit,
    testTag: Boolean = true,
    modifier: Modifier = Modifier
) {
    ProviderUnifiedCard(
        provider = provider,
        isGrid = isGrid,
        modifier = modifier.then(
            if (testTag) Modifier.testTag("provider_folder_provider_${provider.id}") else Modifier
        ),
        onEdit = onEdit,
        onMoveToGroup = onMoveToGroup,
        onDelete = onDelete
    )
}

@Composable
fun ProviderUnifiedCard(
    provider: ModelProvider,
    isGrid: Boolean,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onMoveToGroup: () -> Unit,
    onDelete: () -> Unit
) {
    val shapeCorner by animateDpAsState(
        targetValue = if (isGrid) 20.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "providerCorner"
    )

    Surface(
        shape = RoundedCornerShape(shapeCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isGrid) ProviderGridCardHeight else ProviderListCardHeight)
            .clickable { onEdit() }
    ) {
        AdaptiveCardLayout(
            isGrid = isGrid,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            icon = {
                EntityIcon(provider.icon, Modifier.size(42.dp), text = providerInitials(provider.name), matchName = provider.name)
            },
            actions = {
                ProviderOverflowMenu(onEdit, onMoveToGroup, onDelete)
            },
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                            Text(
                                text = provider.type.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                    if (provider.type.isAccountProvider || provider.auth.method == AuthMethod.OAUTH) ProviderOAuthStatus(provider)
                    if (!provider.type.isAccountProvider) Text(
                        text = provider.baseUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}

@Composable
private fun ProviderOAuthStatus(provider: ModelProvider) {
    val signedIn = provider.auth.method == AuthMethod.OAUTH && !provider.auth.value.isNullOrBlank()
    val color = if (signedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FluentIcon(if (signedIn) Icons.Outlined.CheckCircle else Icons.Outlined.AccountCircle,
            contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        Text(stringResource(if (signedIn) R.string.oauth_signed_in else R.string.oauth_signed_out),
            style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ProviderOverflowMenu(
    onEdit: () -> Unit,
    onMoveToGroup: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            FluentIcon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                leadingIcon = { FluentIcon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { expanded = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.move_to_group)) },
                leadingIcon = { FluentIcon(Icons.Outlined.Folder, contentDescription = null) },
                onClick = { expanded = false; onMoveToGroup() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { FluentIcon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}
