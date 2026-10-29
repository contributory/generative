package org.starfall.generative.features.speech.ui

import org.starfall.generative.shared.ui.layout.scrollableScaffoldPadding
import org.starfall.generative.shared.ui.layout.edgeToEdgeTopAppBarColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import com.mohamedrejeb.compose.dnd.annotation.ExperimentalDndApi
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.scroll.dragAutoScroll
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.starfall.generative.R
import java.util.UUID
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import org.starfall.generative.shared.model.SpeechService
import org.starfall.generative.shared.model.effectiveTtsProviderId
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.ui.icons.EntityIcon
import org.starfall.generative.shared.ui.lists.FadeGridListContent
import org.starfall.generative.shared.ui.lists.ItemCard
import org.starfall.generative.shared.ui.lists.ListViewToggle
import org.starfall.generative.shared.ui.icons.SharedIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.menus.ItemContextMenu
import org.starfall.generative.shared.ui.lists.moved
import org.starfall.generative.shared.ui.icons.providerInitials
import org.starfall.generative.shared.ui.layout.RetainedScreenContent
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

private data class SpeechEditorPage(val service: SpeechService, val isNew: Boolean)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalDndApi::class)
@Composable
fun SpeechServicesScreen(
    speechServices: List<SpeechService>,
    isGridView: Boolean = false,
    onToggleGridView: ((Boolean) -> Unit)? = null,
    providers: List<ModelProvider>,
    selectedSpeechServiceId: String?,
    onSetTtsProvider: (String?) -> Unit,
    onSaveService: (SpeechService) -> Unit,
    onDeleteService: (String) -> Unit,
    onReorderServices: (List<String>) -> Unit,
    onTestVoice: (SpeechService, String) -> Unit,
    onBack: () -> Unit,
    activeTestServiceId: String? = null,
    onStopPlayback: () -> Unit = {}
) {
    val context = LocalContext.current
    var editor by remember { mutableStateOf<SpeechEditorPage?>(null) }
    var deletingServiceId by remember { mutableStateOf<String?>(null) }
    var orderedServices by remember(speechServices) { mutableStateOf(speechServices) }
    val providersById = remember(providers) { providers.associateBy { it.id } }
    val effectiveSelectedId = speechServices.effectiveTtsProviderId(selectedSpeechServiceId)

    RetainedScreenContent(editor = editor) { page ->
        if (page != null) {
            key(page.service.id) {
                SpeechEditor(
                    initialService = page.service,
                    isNew = page.isNew,
                    providers = providers,
                    isPlaying = page.service.id == activeTestServiceId,
                    onTest = { service, text ->
                        if (service.id == activeTestServiceId) onStopPlayback()
                        else onTestVoice(service, text)
                    },
                    onDismiss = { editor = null },
                    onSave = { saved -> onSaveService(saved); editor = null }
                )
            }
        } else {
            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = edgeToEdgeTopAppBarColors(),
                        title = {
                            Column {
                                Text(
                                    stringResource(R.string.speech_services_title),
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
                                SharedIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                            }
                        },
                        actions = {
                            ListViewToggle(isGridView) { onToggleGridView?.invoke(!isGridView) }
                            IconButton(
                                onClick = {
                                    editor = SpeechEditorPage(SpeechService(
                                        id = UUID.randomUUID().toString(),
                                        name = context.getString(R.string.speech_custom_tts),
                                        icon = null,
                                        provider = "system",
                                        modelId = null,
                                        voice = "Default",
                                        speed = 1.0f,
                                        pitch = 1.0f
                                    ), true)
                                }
                            ) {
                                SharedIcon(Icons.Default.Add, contentDescription = stringResource(R.string.speech_add_service))
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
                    val dndState = rememberDragAndDropState<String>(dragAfterLongPress = true)
                    DragAndDropContainer(state = dndState, modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(state = gridState,
                        columns = GridCells.Fixed(if (gridMode) 2 else 1),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = scrollableScaffoldPadding(padding, horizontal = 16.dp, top = 8.dp, bottom = 8.dp),
                        modifier = Modifier.fillMaxSize().dragAutoScroll(state = dndState, lazyGridState = gridState)
                    ) {
                        itemsIndexed(orderedServices, key = { _, item -> item.id }) { _, service ->
                            ReorderableItem(modifier = Modifier.animateItem(), state = dndState, key = service.id, data = service.id,
                                onDragEnter = { dragged ->
                                    val from = orderedServices.indexOfFirst { it.id == dragged.data }
                                    val to = orderedServices.indexOfFirst { it.id == service.id }
                                    if (from >= 0 && to >= 0 && from != to) orderedServices = orderedServices.moved(from, to)
                                }, onDragEnd = { onReorderServices(orderedServices.map { it.id }) }) {
                                val providerName = when {
                                    service.engine == org.starfall.generative.shared.model.SpeechEngine.SYSTEM -> stringResource(R.string.speech_system_tts)
                                    service.engine.standalone -> speechEngineLabel(service.engine)
                                    else -> providersById[service.provider]?.name ?: service.provider
                                }
                                val provider = providersById[service.provider]
                                SpeechServiceUnifiedCard(
                                    service = service,
                                    provider = provider,
                                    isGrid = gridMode,
                                    modifier = Modifier,
                                    providerName = providerName,
                                    selected = service.id == effectiveSelectedId,
                                    isPlaying = service.id == activeTestServiceId,
                                    onSetTtsProvider = { onSetTtsProvider(service.id) },
                                    onTest = {
                                        if (service.id == activeTestServiceId) onStopPlayback()
                                        else onTestVoice(service, context.getString(R.string.speech_preview_text, service.name))
                                    },
                                    onEdit = { editor = SpeechEditorPage(service, false) },
                                    onDelete = { deletingServiceId = service.id }
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    deletingServiceId?.let { id ->
        DeleteSpeechServiceDialog(
            onConfirm = {
                onDeleteService(id)
                if (selectedSpeechServiceId == id) onSetTtsProvider(null)
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
    onSetTtsProvider: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ItemCard(
        isGrid = isGrid,
        onClick = onEdit,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            prominentContainerColor()
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            prominentContainerBorder(1.5.dp)
        },
        modifier = modifier,
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
                        SharedIcon(
                            if (isPlaying) Icons.Outlined.StopCircle else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.speech_stop else R.string.speech_test_voice),
                            tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    ItemContextMenu(
                        onEdit = onEdit,
                        onDelete = onDelete,
                        deleteColor = MaterialTheme.colorScheme.error,
                        firstActionText = stringResource(R.string.speech_set_tts_provider)
                            .takeUnless { selected },
                        firstActionImageVector = Icons.Outlined.RecordVoiceOver
                            .takeUnless { selected },
                        onFirstAction = if (selected) null else onSetTtsProvider
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
                            SharedIcon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        providerName,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val voiceLine = service.modelId?.let {
                        stringResource(R.string.speech_card_model_voice_line, it, service.voice)
                    } ?: stringResource(R.string.speech_card_voice_line, service.voice)
                    Text(
                        voiceLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPlaying) {
                        Text(
                            stringResource(R.string.speech_playing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        )
}
