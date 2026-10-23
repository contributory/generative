package org.starfall.generative.features.speech.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.starfall.generative.R
import java.util.UUID
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.ScrollMoveMode
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.layout.AdaptiveCardLayout
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.lists.FadeGridListContent
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.ItemContextMenu
import org.starfall.generative.shared.ui.lists.moved
import org.starfall.generative.shared.ui.icons.providerInitials

private data class SpeechEditor(val service: SpeechService, val isNew: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechServicesScreen(
    speechServices: List<SpeechService>,
    isGridView: Boolean = false,
    onToggleGridView: ((Boolean) -> Unit)? = null,
    providers: List<ModelProvider>,
    selectedSpeechServiceId: String?,
    onSelectService: (String?) -> Unit,
    onSaveService: (SpeechService) -> Unit,
    onDeleteService: (String) -> Unit,
    onReorderServices: (List<String>) -> Unit,
    onTestVoice: (SpeechService, String) -> Unit,
    onBack: () -> Unit,
    activeTestServiceId: String? = null,
    onStopPlayback: () -> Unit = {}
) {
    var editor by remember { mutableStateOf<SpeechEditor?>(null) }
    var deletingServiceId by remember { mutableStateOf<String?>(null) }
    var orderedServices by remember(speechServices) { mutableStateOf(speechServices) }
    val providersById = remember(providers) { providers.associateBy { it.id } }
    val effectiveSelectedId = speechServices.find { it.id == selectedSpeechServiceId }?.id
        ?: speechServices.firstOrNull { it.provider.equals("system", true) }?.id
        ?: speechServices.firstOrNull()?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Speech Services",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            stringResource(R.string.speech_services_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        FluentIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleGridView?.invoke(!isGridView) }) {
                        FluentIcon(
                            imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View"
                        )
                    }
                    IconButton(
                        onClick = {
                            editor = SpeechEditor(SpeechService(
                                id = UUID.randomUUID().toString(),
                                name = "Custom TTS",
                                icon = null,
                                provider = "system",
                                modelId = null,
                                voice = "Default",
                                speed = 1.0f,
                                pitch = 1.0f
                            ), true)
                        }
                    ) {
                        FluentIcon(Icons.Default.Add, contentDescription = "Add speech service")
                    }
                }
            )
        }
    ) { padding ->
        FadeGridListContent(
            isGrid = isGridView,
            modifier = Modifier.fillMaxSize()
        ) { gridMode ->
            val gridState = rememberLazyGridState()
            val reorderState = rememberReorderableLazyGridState(gridState, scrollMoveMode = ScrollMoveMode.INSERT) { from, to ->
                orderedServices = orderedServices.moved(from.index, to.index)
            }
            LazyVerticalGrid(state = gridState,
                columns = GridCells.Fixed(if (gridMode) 2 else 1),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                itemsIndexed(orderedServices, key = { _, item -> item.id }) { _, service ->
                    ReorderableItem(reorderState, key = service.id) { _ ->
                        val providerName = when {
                            service.provider.equals("system", ignoreCase = true) -> "Android System TTS"
                            else -> providersById[service.provider]?.name ?: service.provider
                        }
                        val provider = providersById[service.provider]
                        SpeechServiceUnifiedCard(
                            service = service,
                            provider = provider,
                            isGrid = gridMode,
                            modifier = Modifier.longPressDraggableHandle(
                                onDragStopped = { onReorderServices(orderedServices.map { it.id }) }
                            ),
                            providerName = providerName,
                            selected = service.id == effectiveSelectedId,
                            isPlaying = service.id == activeTestServiceId,
                            onSelect = { onSelectService(service.id) },
                            onTest = {
                                if (service.id == activeTestServiceId) onStopPlayback()
                                else onTestVoice(service, "Hello! This is a preview of the ${service.name} voice.")
                            },
                            onEdit = { editor = SpeechEditor(service, false) },
                            onDelete = { deletingServiceId = service.id }
                        )
                    }
                }
            }
        }
    }

    editor?.let { page ->
        AddOrEditSpeechDialog(
            initialService = page.service,
            isNew = page.isNew,
            providers = providers,
            isPlaying = page.service.id == activeTestServiceId,
            onTest = { service, text ->
                if (service.id == activeTestServiceId) onStopPlayback()
                else onTestVoice(service, text)
            },
            onDismiss = {
                editor = null
            },
            onSave = { saved ->
                onSaveService(saved)
                if (selectedSpeechServiceId == null && !saved.provider.equals("system", true)) {
                    onSelectService(saved.id)
                }
                editor = null
            }
        )
    }

    deletingServiceId?.let { id ->
        DeleteSpeechServiceDialog(
            onConfirm = {
                onDeleteService(id)
                if (selectedSpeechServiceId == id) onSelectService(null)
                deletingServiceId = null
            },
            onDismiss = { deletingServiceId = null }
        )
    }
}

@Composable
private fun SpeechServiceUnifiedCard(
    service: SpeechService,
    provider: ModelProvider? = null,
    isGrid: Boolean,
    modifier: Modifier = Modifier,
    providerName: String,
    selected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val shapeCorner by animateDpAsState(
        targetValue = if (isGrid) 20.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "speechShapeCorner"
    )

    Surface(
        shape = RoundedCornerShape(shapeCorner),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.5.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        AdaptiveCardLayout(
            isGrid = isGrid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            icon = {
                EntityIcon(
                    image = service.icon ?: provider?.icon,
                    modifier = Modifier.size(42.dp),
                    text = providerInitials(provider?.name ?: service.name),
                    fallback = Icons.Outlined.RecordVoiceOver,
                    matchName = provider?.name ?: service.name
                )
            },
            actions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTest,
                        modifier = Modifier.size(36.dp),
                        colors = if (isPlaying) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()
                    ) {
                        FluentIcon(
                            if (isPlaying) Icons.Outlined.StopCircle else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.speech_stop else R.string.speech_test_voice),
                            tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    ItemContextMenu(
                        onEdit = onEdit,
                        onDelete = onDelete,
                        deleteColor = MaterialTheme.colorScheme.error
                    )
                }
            },
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            service.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (selected) {
                            FluentIcon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        providerName,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val modelLine = service.modelId?.let { "Model: $it · " }.orEmpty()
                    Text(
                        "${modelLine}Voice: ${service.voice}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPlaying) {
                        Text(
                            stringResource(R.string.speech_playing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        )
    }
}
