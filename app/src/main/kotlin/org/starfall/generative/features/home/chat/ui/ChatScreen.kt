package org.starfall.generative.features.home.chat.ui

import org.starfall.generative.R
import org.starfall.generative.features.home.BottomSheetsLink
import org.starfall.generative.features.home.BottomSheetsLinkState

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.unit.Velocity
import kotlin.math.exp
import kotlin.math.min
import kotlinx.coroutines.isActive
import org.starfall.generative.features.home.chat.logic.SAVE_CONVERSATION_ERROR_PREFIX
import org.starfall.generative.shared.model.ChatRole
import org.starfall.generative.shared.model.AgentSkill
import org.starfall.generative.shared.model.Conversation
import org.starfall.generative.shared.model.ConversationSummaryProgress
import org.starfall.generative.shared.model.ConversationSummaryRequest
import org.starfall.generative.shared.model.RemoteMachine
import org.starfall.generative.shared.model.StoredMessage
import org.starfall.generative.shared.model.SummaryRole
import org.starfall.generative.shared.model.language.ThinkingPassback
import org.starfall.generative.shared.model.provider.ModelProvider
import org.starfall.generative.shared.model.provider.ProviderGroup
import org.starfall.generative.shared.ui.media.ChatAttachmentSelection
import org.starfall.generative.shared.ui.icons.FluentIcon
import org.starfall.generative.shared.ui.icons.Icons
import org.starfall.generative.shared.ui.media.LocalChatAttachmentSelection
import org.starfall.generative.shared.ui.sheets.AppSheet
import org.starfall.generative.shared.ui.text.PlatformTextSelection
import org.starfall.generative.shared.ui.text.PlatformTextSelectionRegion
import org.starfall.generative.features.home.chat.logic.input.ChatInputEditDraft
import org.starfall.generative.features.home.chat.logic.input.DirectMediaRequest
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.shared.model.defaultAgentSkills
import org.starfall.generative.features.home.chat.logic.unansweredUserMessageIds
import org.starfall.generative.features.home.chat.ui.input.UserInputArea
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.features.home.shared.ui.ConversationSummarySheet
import org.starfall.generative.features.home.chat.model.ContextWindowStatus
import org.starfall.generative.features.home.chat.model.ActiveChatStreamState
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

// An intentionally large offset lands the initial view at the latest message without flashing history.
private const val OPEN_AT_BOTTOM_OFFSET = 10_000_000

// Smoothly track the newest reply with velocity, not a fresh per-line scroll animation.
private const val CHAT_FOLLOW_DISTANCE_TIME_SECONDS = 0.18f
private const val CHAT_FOLLOW_VELOCITY_EASE_SECONDS = 0.085f
private const val CHAT_FOLLOW_MAX_SPEED_DP_PER_SECOND = 1_700f

// The opening veil lifts after the list has stayed at its newest message with no background work
// pending for this many frames.
private const val OPENING_STABLE_FRAMES = 4

@Composable
fun ChatScreen(
    conversation: Conversation?,
    isGenerating: Boolean,
    generatingConversationId: String?,
    chatError: String?,
    providers: List<ModelProvider>,
    providerGroups: List<ProviderGroup> = emptyList(),
    modelPickerCollapsedGroups: Set<String> = emptySet(),
    modelPickerCollapsedProviders: Set<String> = emptySet(),
    onModelPickerCollapsedGroupsChange: (Set<String>) -> Unit = {},
    onModelPickerCollapsedProvidersChange: (Set<String>) -> Unit = {},
    selectedProviderId: String,
    selectedModelName: String,
    onSendMessage: (String, List<String>) -> Boolean,
    onSendMedia: (DirectMediaRequest) -> Boolean,
    onMediaModeChange: (ModelType?, String, String) -> Unit = { _, _, _ -> },
    onStopGenerating: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenTemporaryChat: () -> Unit = onNewChat,
    onOpenSettings: () -> Unit,
    featuresLogoImage: String? = null,
    backgroundImage: String? = null,
    backgroundTransparency: Float = 0.5f,
    onRegenerate: (String) -> Unit,
    onRetryFromError: (String) -> Unit = {},
    onEditMessage: (messageId: String, newContent: String, files: List<String>) -> Boolean,
    onDeleteMessage: (messageId: String) -> Unit,
    onDeleteMessageVersion: (messageId: String) -> Unit,
    onSwitchVersion: (messageId: String, versionIndex: Int) -> Unit,
    onSelectModel: (providerId: String, modelId: String) -> Unit,
    summaryProgress: ConversationSummaryProgress?,
    onSetReasoningEffort: (String?) -> Unit,
    onSetThinkingPassback: (providerId: String, modelId: String, mode: ThinkingPassback) -> Unit = { _, _, _ -> },
    onSetModelReasoningEffort: (providerId: String, modelId: String, effort: String?) -> Unit = { _, _, _ -> },
    onSetModelTools: (providerId: String, modelId: String, tools: List<String>) -> Unit = { _, _, _ -> },
    onEditModel: (providerId: String, modelId: String) -> Unit = { _, _ -> },
    onEditProvider: (providerId: String) -> Unit = {},
    onStartConversationSummary: (ConversationSummaryRequest) -> Boolean,
    onCancelConversationSummary: () -> Unit = {},
    onSummaryRoleChange: (SummaryRole) -> Unit,
    onDeleteSummary: () -> Unit,
    onReadMessage: (String, String) -> Unit,
    onFetchOllamaModels: (suspend (String) -> List<String>)? = null,
    queuedMessages: List<StoredMessage> = emptyList(),
    onEditQueuedMessage: (String, String, List<String>) -> Boolean = { _, _, _ -> true },
    onDeleteQueuedMessage: (String) -> Unit = {},
    autoScroll: Boolean = false,
    loadingConversationId: String? = null,
    contextWindowStatus: ContextWindowStatus? = null,
    speakingMessageId: String? = null,
    onResendUserMessage: (String) -> Boolean = { false },
    /** Remote machines with a live connection; non-empty shows the terminal button in the app bar. */
    connectedMachines: List<RemoteMachine> = emptyList(),
    onOpenTerminal: (RemoteMachine) -> Unit = {},
    onOpenFeatureToolsSettings: () -> Unit = {},
    onOpenMcpConnectorsSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    activeStream: StateFlow<ActiveChatStreamState?>? = null,
    agentSkills: List<AgentSkill> = defaultAgentSkills(),
    onSendMessageWithSkill: (String, List<String>, Boolean, String?) -> Boolean = { text, files, _, _ -> onSendMessage(text, files) },
    onOpenSkillsSettings: (() -> Unit)? = null,
    isDrawerOpening: Boolean = false
) {
    val conversationKey = conversation?.id ?: "__empty_conversation__"
    // Chronological LazyColumn: start at the last item rather than composing the oldest on open.
    val initialLastIndex = ((conversation?.messages?.size ?: 0) + queuedMessages.size - 1).coerceAtLeast(0)
    val listState = remember(conversationKey) {
        LazyListState(initialLastIndex, OPEN_AT_BOTTOM_OFFSET)
    }
    val minimapState = remember(conversationKey) { ConversationMinimapSideBarState() }
    LaunchedEffect(isDrawerOpening) {
        if (isDrawerOpening) minimapState.isOpen = false
    }
    var followBottom by remember(conversationKey) { mutableStateOf(true) }
    // An interrupted follow stays paused until a manual swipe actually reaches the newest end.
    var userPausedFollow by remember(conversationKey) { mutableStateOf(false) }
    fun resumeFollowing() {
        userPausedFollow = false
        followBottom = true
    }
    var openingAtBottom by remember(conversationKey) { mutableStateOf(true) }
    val anchor = remember(conversationKey) { ChatScrollAnchor() }
    var jumpingToBottom by remember(conversationKey) { mutableStateOf(false) }
    val settleTracker = remember(conversationKey) { ChatSettleTracker() }
    var revealed by remember(conversationKey) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val seenMessageIds = remember(conversationKey) { mutableSetOf<String>() }
    val seenQueuedIds = remember(conversationKey) { mutableSetOf<String>() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedModelDisplayName = selectedProvider?.config?.modelConfigs?.get(selectedModelName)
        ?.displayName?.takeIf { it.isNotBlank() } ?: selectedModelName

    val conversationMessages = conversation?.messages.orEmpty()
    val structuralRevision = conversation?.structuralRevision ?: 0L
    val streamingHere = isGenerating && generatingConversationId == conversation?.id
    val messageIds = remember(conversationKey, structuralRevision) { conversationMessages.map { it.id } }
    val queuedIds = remember(queuedMessages) { queuedMessages.map { it.id } }
    val messages = remember(conversationKey, structuralRevision, conversation?.updatedAt, queuedMessages) {
        conversationMessages + queuedMessages
    }
    val tailAnchorId = anchor.messageId
        ?: messages.lastOrNull()?.takeIf { it.role == ChatRole.USER && !it.isQueued }?.id
    SideEffect {
        ChatRenderMetrics.recordChatScreenRecomposition()
        anchor.detector.observe(conversationMessages, structuralRevision)?.let { id ->
            openingAtBottom = false
            anchor.start(id)
            resumeFollowing() // Sending a new message explicitly resumes following.
        }
        if (anchor.messageId?.let { id -> messages.none { it.id == id } } == true) anchor.end()
        if (streamingHere) openingAtBottom = false
    }

    var editDraft by remember(conversation?.id) { mutableStateOf<ChatInputEditDraft?>(null) }
    val selection = LocalChatAttachmentSelection.current ?: remember(conversation?.id) { ChatAttachmentSelection() }
    var inputAttachments by selection.attachments
    val chatModeRequest = selection.requestId
    val toggleChatImage: (String) -> Unit = selection::toggle

    // Dialog state for deleting a message
    var deletingMessageId by remember(conversation?.id) { mutableStateOf<String?>(null) }

    var regeneratingMessageId by remember(conversation?.id) { mutableStateOf<String?>(null) }
    var showContextSummarySheet by remember(conversation?.id) { mutableStateOf(false) }
    var showTerminalMachinePicker by remember { mutableStateOf(false) }
    var showSummaryDialog by remember(conversation?.id) { mutableStateOf(false) }
    var inlineChatError by remember(conversation?.id) { mutableStateOf<String?>(null) }
    var modelPickerOpenRequest by remember(conversation?.id) { mutableIntStateOf(0) }
    val lastMessage = conversationMessages.lastOrNull()
    val streamingMessage = if (streamingHere) conversationMessages.lastOrNull { it.role == ChatRole.MODEL } else null
    val lastResponseState = conversationMessages.lastOrNull()?.activeVersion
    val unansweredMessages = remember(
        conversationKey,
        structuralRevision,
        lastResponseState?.generationError,
        lastResponseState?.generationInterrupted,
        lastResponseState?.processingFinishedAt
    ) { unansweredUserMessageIds(conversationMessages) }
    // Streaming text is followed by the per-frame loop below; stepping on text length made the scroll jump per chunk.
    val autoScrollTick = if (!autoScroll || streamingHere) null else lastMessage?.activeVersionIndex
    // A chat that was already settled at its newest message earlier in this process, and whose parsed
    // content is still in the (evictable) RAM cache, opens at its final size on the first frame and needs
    // no veil. Otherwise the first open hides the chat until it is fully loaded and at the latest message.
    val renderEnvironment = chatRenderEnvironmentKey()
    val openSignature = remember(
        conversationKey,
        structuralRevision,
        streamingHere,
        if (streamingHere) 0L else conversation?.updatedAt
    ) {
        if (streamingHere) 0 else chatOpenSignature(conversationMessages)
    }
    val hasSavedMessages = conversationMessages.isNotEmpty()
    val openedWarm = remember(conversationKey, hasSavedMessages, openSignature, renderEnvironment) {
        hasSavedMessages && ChatOpenCache.isWarm(conversationKey, openSignature, renderEnvironment)
    }
    // A pending history selection must be hidden even before its first message arrives.
    val historyLoading = loadingConversationId != null
    val veilActive = historyLoading || (openingAtBottom && !revealed && hasSavedMessages && !openedWarm && !streamingHere)
    fun whenIdle(action: () -> Unit) {
        if (!isGenerating) action()
    }
    // A small edge tolerance prevents a gesture from needing an extra swipe for its last pixels.
    val resumeEdgePx = with(LocalDensity.current) { 20.dp.roundToPx() }
    fun reachedLatest(): Boolean {
        val layout = listState.layoutInfo
        if (layout.totalItemsCount == 0) return false
        if (!listState.canScrollForward) return true
        val last = layout.visibleItemsInfo.lastOrNull { it.index == layout.totalItemsCount - 1 }
            ?: return false
        // Only within the actual end of the item; a blank tail must not count as a hit.
        return last.offset + last.size - layout.viewportEndOffset in 0..resumeEdgePx
    }
    val scrollConnection = remember(listState, resumeEdgePx) {
        object : NestedScrollConnection {
            private var swipingTowardsLatest = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    val towardsLatest = available.y < 0f
                    // Once the end is reached, continuing the same forward swipe must not
                    // pause and resume on every pixel (which would restart scroll effects).
                    if (!towardsLatest || !swipingTowardsLatest || userPausedFollow) {
                        openingAtBottom = false
                        followBottom = false
                        userPausedFollow = true
                        anchor.release()
                    }
                    swipingTowardsLatest = towardsLatest
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val movingTowardsLatest = consumed.y + available.y < 0f
                // Fling frames arrive as SideEffect. Resume on the FIRST frame that hits the
                // bottom, not only when the fling ends (which may report zero velocity).
                if (userPausedFollow && swipingTowardsLatest && movingTowardsLatest &&
                    (source == NestedScrollSource.UserInput || source == NestedScrollSource.SideEffect) &&
                    reachedLatest()
                ) resumeFollowing()
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (userPausedFollow && swipingTowardsLatest && reachedLatest()) resumeFollowing()
                swipingTowardsLatest = false
                return Velocity.Zero
            }
        }
    }
    val latestMessageVisible by remember(listState, messages.size) {
        derivedStateOf {
            messages.isNotEmpty() && listState.layoutInfo.visibleItemsInfo.any { it.index == messages.lastIndex }
        }
    }
    suspend fun scrollToBottom(includeQueued: Boolean = true) {
        val index = if (includeQueued) messages.lastIndex else conversationMessages.lastIndex
        if (index < 0) return
        // The tail padding fills free room *after* the newest bubble, not between the user
        // message and its reply. Show that padded end rather than scrolling past it.
        if (tailAnchorId != null) listState.revealItemBottom(index)
        else listState.scrollToItem(index, OPEN_AT_BOTTOM_OFFSET)
    }
    suspend fun animateScrollToBottom() {
        val index = messages.lastIndex
        if (index < 0) return
        if (index - listState.firstVisibleItemIndex > 6) listState.scrollToItem(index - 3)
        listState.animateScrollToItem(index)
        scrollToBottom()
    }
    LaunchedEffect(conversation?.id, messageIds, autoScroll) {
        val hasNewMessage = messageIds.any { it !in seenMessageIds }
        val hasNewUserMessage = conversationMessages.any { it.id !in seenMessageIds && it.role == ChatRole.USER }
        val firstLoad = seenMessageIds.isEmpty()
        seenMessageIds.addAll(messageIds)
        if (firstLoad) {
            if (messages.isNotEmpty() && openingAtBottom) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
                if (openingAtBottom) scrollToBottom()
            }
            return@LaunchedEffect
        }
        if (hasNewMessage && !anchor.settling &&
            (hasNewUserMessage || (autoScroll && followBottom && !userPausedFollow))
        ) {
            openingAtBottom = false
            scrollToBottom()
            if (hasNewUserMessage) resumeFollowing()
        }
    }
    LaunchedEffect(conversationKey, queuedIds) {
        val newQueued = queuedIds.any { it !in seenQueuedIds }
        seenQueuedIds.addAll(queuedIds)
        // A newly queued user message is still an explicit send, even before it enters history.
        if (newQueued && !openingAtBottom) {
            resumeFollowing()
            scrollToBottom()
        }
    }
    LaunchedEffect(listState, openingAtBottom, messages.size) {
        if (!openingAtBottom || messages.isEmpty()) return@LaunchedEffect
        // A saved conversation may continue measuring after the initial scroll. This runs only
        // while opening it, never while the user scrolls or a new answer is streaming.
        snapshotFlow { listState.canScrollForward }.collect { canScrollForward ->
            if (openingAtBottom && canScrollForward && !anchor.settling) {
                withFrameNanos { }
                if (openingAtBottom && !anchor.settling) scrollToBottom()
            }
        }
    }
    LaunchedEffect(conversationKey, veilActive, historyLoading) {
        if (!veilActive || historyLoading) return@LaunchedEffect
        // Wait until the newest items exist, then until the list has stopped changing: nothing is still
        // being parsed or loaded and the view has stayed at the latest message for a few frames.
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        var stableFrames = 0
        while (true) {
            withFrameNanos { }
            val settled = settleTracker.pending == 0 && !listState.canScrollForward
            stableFrames = if (settled) stableFrames + 1 else 0
            if (stableFrames >= OPENING_STABLE_FRAMES) break
        }
        revealed = true
    }
    LaunchedEffect(conversationKey, openSignature, revealed, openedWarm, streamingHere) {
        if (!(revealed || openedWarm) || streamingHere || !hasSavedMessages) return@LaunchedEffect
        // Remember the settled state so the next open of this chat is instant. Only record while the
        // view is at the newest message with its assets ready, never a half-loaded layout.
        snapshotFlow {
            settleTracker.pending == 0 && !listState.canScrollForward
        }.first { it }
        ChatOpenCache.remember(conversationKey, openSignature, renderEnvironment, settleTracker.readyAssets())
    }
    LaunchedEffect(
        conversation?.id,
        conversationMessages.size,
        autoScroll,
        autoScrollTick,
        followBottom,
        userPausedFollow,
        anchor.settling,
        streamingHere,
        if (autoScroll) summaryProgress?.progress else null
    ) {
        if (autoScroll && followBottom && !userPausedFollow && !anchor.settling && messages.isNotEmpty() && !streamingHere) {
            // Let a finger drag or fling finish before a programmatic correction takes over.
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            if (followBottom && !userPausedFollow) scrollToBottom(includeQueued = true)
        }
    }
    // One continuous follower: incoming lines change the distance, never restart a scroll animation.
    val maxFollowSpeedPx = with(LocalDensity.current) { CHAT_FOLLOW_MAX_SPEED_DP_PER_SECOND.dp.toPx() }
    LaunchedEffect(conversation?.id, conversationMessages.size, autoScroll, streamingHere, followBottom, userPausedFollow, anchor.settling, maxFollowSpeedPx) {
        if (!autoScroll || !streamingHere || !followBottom || userPausedFollow || anchor.settling) return@LaunchedEffect
        var previousFrame = 0L
        var velocity = 0f
        while (isActive) {
            if (!followBottom || userPausedFollow) return@LaunchedEffect
            val frame = withFrameNanos { it }
            val seconds = if (previousFrame == 0L) 1f / 60f else
                ((frame - previousFrame) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            previousFrame = frame
            if (anchor.settling || listState.isScrollInProgress || conversationMessages.isEmpty()) {
                velocity = 0f
                continue
            }
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == conversationMessages.lastIndex }
            if (item == null) {
                // A structural change can move the reply offscreen; recover it once, not per line.
                scrollToBottom(includeQueued = false)
                velocity = 0f
                continue
            }
            val remaining = (item.offset + item.size - layout.viewportEndOffset).coerceAtLeast(0).toFloat()
            val targetVelocity = min(remaining / CHAT_FOLLOW_DISTANCE_TIME_SECONDS, maxFollowSpeedPx)
            velocity += (targetVelocity - velocity) * (1f - exp(-seconds / CHAT_FOLLOW_VELOCITY_EASE_SECONDS))
            if (remaining > 0.5f && velocity > 0f) {
                // Raw delta avoids creating a competing scroll mutation on every rendered frame.
                // User drags/flings still take priority through isScrollInProgress and the pause flag.
                listState.dispatchRawDelta(min(remaining, velocity * seconds))
            }
        }
    }
    val saveConversationFailedPrefix = stringResource(R.string.chat_save_conversation_failed_prefix)
    LaunchedEffect(chatError, saveConversationFailedPrefix) {
        chatError?.takeIf { it.startsWith(SAVE_CONVERSATION_ERROR_PREFIX) }?.let { error ->
            inlineChatError = saveConversationFailedPrefix + error.removePrefix(SAVE_CONVERSATION_ERROR_PREFIX)
        }
    }
    LaunchedEffect(conversation?.id) {
        focusManager.clearFocus(force = true)
    }
    LaunchedEffect(listState.isScrollInProgress) {
        // Scrolling only tucks the keyboard away; it must not clear the text selection (a tap does that).
        if (listState.isScrollInProgress) keyboardController?.hide()
    }

    val density = LocalDensity.current
    var appBarHeightPx by remember { mutableIntStateOf(0) }
    var inputAreaHeightPx by remember { mutableIntStateOf(0) }
    var bottomPanelHeightPx by remember { mutableIntStateOf(0) }
    var chatRootCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var inputAreaBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val appBarHeight = if (appBarHeightPx > 0) with(density) { appBarHeightPx.toDp() }
        else 72.dp
    val navigationBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    PlatformTextSelection {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { chatRootCoordinates = it }
            .dismissMinimapOnOutsideTap(minimapState)
            // A plain tap anywhere on the chat screen except the input area (messages, blank space, app bar)
            // drops focus: the input field loses focus and any text selection is cleared. Scrolling and dragging
            // do not. Nothing is consumed, and a press held past the long-press timeout is selection, not a tap.
            .pointerInput(focusManager) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size > 1) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        if (change.changedToUpIgnoreConsumed()) {
                            val inRoot = chatRootCoordinates?.takeIf { it.isAttached }?.localToRoot(down.position)
                            val onInput = inRoot != null && inputAreaBounds?.contains(inRoot) == true
                            if (!onInput) focusManager.clearFocus(force = true)
                            break
                        }
                    }
                }
            }
    ) {
        org.starfall.generative.shared.ui.background.ProfileBackground(backgroundImage, transparency = backgroundTransparency)
        // Let the list occupy the whole edge-to-edge surface. Its top padding keeps the newest
        // content readable initially, while later scrolls visibly pass behind the translucent app bar.
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val bottomClearance = with(density) { inputAreaHeightPx.toDp() } + navigationBarHeight + maxHeight * 0.015f
        if (messages.isEmpty()) {
            ChatOpeningVeil(
                active = historyLoading,
                modifier = Modifier.fillMaxSize().padding(top = appBarHeight + 8.dp, bottom = bottomClearance)
            ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = if (conversation?.isTemporary == true)
                            stringResource(R.string.chat_temporary_title) else stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (conversation?.isTemporary == true)
                            stringResource(R.string.chat_temporary_description) else
                            stringResource(R.string.connect_providers_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            if (isGenerating && !streamingHere) {
                Text(
                    stringResource(R.string.chat_error_other_conversation),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 12.dp, end = 12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            }
        } else {
            val visibleChatPx = with(density) {
                (maxHeight - (appBarHeight + 8.dp) - bottomClearance).coerceAtLeast(0.dp).roundToPx()
            }
            val newestMessageId = messages.lastOrNull()?.id
            val precedingUserId = messages.getOrNull(messages.size - 2)
                ?.takeIf { it.role == ChatRole.USER && messages.lastOrNull()?.role == ChatRole.MODEL }?.id
            val tailTurn = remember(conversationKey) { TailTurnAnchor(with(density) { 112.dp.roundToPx() }) }
            ChatAnchorEffects(anchor, listState,
                itemIndexOf = { id -> messages.indexOfFirst { it.id == id } },
                itemCount = { messages.size }
            )
            ChatOpeningVeil(active = veilActive, modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalChatSettleTracker provides settleTracker
            ) {
            PlatformTextSelectionRegion {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // Fade scrolled messages out BEFORE they cross the app bar/status bar.
                    // Mask only the chat layer so the profile background remains visible.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fadeEnd = (appBarHeight + 8.dp).toPx().coerceAtLeast(1f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.72f to Color.Transparent,
                                1f to Color.Black,
                                startY = 0f,
                                endY = fadeEnd
                            ),
                            size = androidx.compose.ui.geometry.Size(size.width, fadeEnd.coerceAtMost(size.height)),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    .nestedScroll(scrollConnection),
                contentPadding = PaddingValues(top = appBarHeight + 8.dp, bottom = bottomClearance)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isLast = msg.id == lastMessage?.id
                    // Pad only after the final message: a sent user's bubble remains just below
                    // the app bar while the reply starts immediately underneath it.
                    val isNewest = msg.id == newestMessageId
                    val tailModifier = when {
                        msg.role == ChatRole.USER && isNewest -> Modifier.tailScrollPadding(
                            visibleChatPx,
                            onNaturalHeight = { tailTurn.recordUser(msg.id, it) }
                        )
                        msg.role == ChatRole.USER && msg.id == precedingUserId ->
                            Modifier.recordTurnUserHeight(tailTurn, msg.id)
                        msg.role == ChatRole.MODEL && isNewest && precedingUserId != null ->
                            Modifier.replyTailPadding(tailTurn, precedingUserId, visibleChatPx)
                        else -> Modifier
                    }
                    Column(
                        modifier = tailModifier
                    ) {

                    if (msg.role == ChatRole.USER) {
                        UserMessageCard(
                            message = msg,
                            conversationId = conversation?.id,
                            cacheEnabled = conversation?.isTemporary != true,
                            selectedImageAttachments = inputAttachments,
                            onToggleChatImage = toggleChatImage,
                            onResend = if (msg.id in unansweredMessages) ({
                                if (onResendUserMessage(msg.id)) resumeFollowing()
                            }) else null,
                            onResendFromMenu = if (!msg.isQueued) ({
                                if (onResendUserMessage(msg.id)) resumeFollowing()
                            }) else null,
                            resendEnabled = !isGenerating && summaryProgress == null &&
                                selectedModelName.isNotBlank() && providers.any { it.id == selectedProviderId },
                            onEdit = {
                                if (msg.isQueued) {
                                    editDraft = ChatInputEditDraft(
                                        messageId = msg.id,
                                        text = msg.content,
                                        attachments = msg.files,
                                        isQueued = true,
                                        revision = System.nanoTime()
                                    )
                                } else {
                                    editDraft = ChatInputEditDraft(
                                        messageId = msg.id,
                                        text = msg.content,
                                        attachments = msg.files,
                                        revision = System.nanoTime()
                                    )
                                }
                            },
                            onDelete = {
                                if (msg.isQueued) {
                                    onDeleteQueuedMessage(msg.id)
                                } else {
                                    deletingMessageId = msg.id
                                }
                            },
                            onSwitchVersion = { newIdx ->
                                if (!msg.isQueued) {
                                    whenIdle { onSwitchVersion(msg.id, newIdx) }
                                }
                            },
                            modifier = if (msg.isQueued) Modifier.alpha(0.5f) else Modifier
                        )
                    } else {
                        AssistantMessageCard(
                            message = msg,
                            isReading = speakingMessageId == msg.id,
                            modelIcon = msg.activeVersion.let { version ->
                                val provider = providers.find { it.id == version.providerId.ifBlank { conversation?.providerId.orEmpty() } }
                                provider?.config?.modelConfigs?.get(version.modelId.ifBlank { conversation?.modelId.orEmpty() })?.icon
                            },
                            fallbackModelName = providers.find { it.id == conversation?.providerId }
                                ?.config?.modelConfigs?.get(conversation?.modelId)?.displayName
                                ?.ifBlank { conversation?.modelId.orEmpty() } ?: conversation?.modelId.orEmpty(),
                            selectedImageAttachments = inputAttachments,
                            onToggleChatImage = toggleChatImage,
                            isStreaming = streamingHere && msg.id == streamingMessage?.id,
                            conversationId = conversation?.id,
                            cacheEnabled = conversation?.isTemporary != true,
                            streamStateFlow = activeStream,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.common_copy), assistantCopyText(msg)))
                                Toast.makeText(context, context.getString(R.string.common_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                            },
                            onRegenerate = {
                                whenIdle {
                                    if (!isLast) regeneratingMessageId = msg.id
                                    else onRegenerate(msg.id)
                                }
                            },
                            onRetryError = if (isLast) {
                                { whenIdle { onRetryFromError(msg.id) } }
                            } else null,
                            onEdit = {
                                editDraft = ChatInputEditDraft(
                                    messageId = msg.id,
                                    text = msg.content,
                                    attachments = msg.files,
                                    revision = System.nanoTime()
                                )
                            },
                            onDelete = {
                                deletingMessageId = msg.id
                            },
                            onRead = {
                                onReadMessage(msg.id, msg.content)
                            },
                            onSwitchVersion = { newIdx ->
                                whenIdle { onSwitchVersion(msg.id, newIdx) }
                            }
                        )
                    }

                    val progressHere = summaryProgress?.takeIf {
                        it.conversationId == conversation?.id && it.throughMessageId == msg.id
                    }
                    val summaryHere = conversation?.summary?.takeIf {
                        it.throughMessageId == msg.id && progressHere == null
                    }
                    if (progressHere != null || summaryHere != null) {
                        ConversationSummaryDivider(
                            summary = summaryHere,
                            progress = progressHere,
                            onOpenSummary = { showSummaryDialog = true },
                            onCancelSummary = onCancelConversationSummary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    }
                }
                if (isGenerating && !streamingHere) {
                    item(key = "generation-warning") {
                        Text(
                            stringResource(R.string.chat_error_other_conversation),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            }
            }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomPanelHeightPx = it.height }
                .navigationBarsPadding()
        ) {
            inlineChatError?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            error,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = { inlineChatError = null }) {
                            FluentIcon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_dismiss_error), tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            if (messages.isNotEmpty() && !latestMessageVisible && !jumpingToBottom) {
                FilledTonalIconButton(
                    onClick = {
                        openingAtBottom = false
                        // Resume following only after the animation finishes; setting it first
                        // would trigger the instant jump in the auto-scroll effect.
                        coroutineScope.launch {
                            jumpingToBottom = true
                            try {
                                animateScrollToBottom()
                                resumeFollowing()
                            } finally {
                                jumpingToBottom = false
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                ) {
                    FluentIcon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.chat_jump_to_latest),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            var summarySuggestionDismissed by remember(conversation?.id, conversation?.summary?.id) { mutableStateOf(false) }
            if (contextWindowStatus?.shouldSuggestSummary == true && !streamingHere && summaryProgress == null &&
                !summarySuggestionDismissed &&
                org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences.current.summarySuggestionEnabled) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(androidx.compose.ui.res.stringResource(org.starfall.generative.R.string.context_summary_suggestion,
                            contextWindowStatus.percentage), style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { showContextSummarySheet = true }) {
                            Text(androidx.compose.ui.res.stringResource(org.starfall.generative.R.string.summarize_now))
                        }
                        IconButton(onClick = { summarySuggestionDismissed = true }, modifier = Modifier.size(32.dp)) {
                            FluentIcon(Icons.Default.Close,
                                androidx.compose.ui.res.stringResource(org.starfall.generative.R.string.common_close),
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            // Composer state must not escape a disposable chat when switching sessions.
            key(if (conversation?.isTemporary == true) conversationKey else "regular_composer") {
            UserInputArea(
                temporaryChatId = conversation?.takeIf { it.isTemporary }?.id,
                attachments = inputAttachments,
                onAttachmentsChange = { inputAttachments = it },
                chatModeRequest = chatModeRequest,
                modifier = Modifier
                    .onSizeChanged { inputAreaHeightPx = it.height }
                    .onGloballyPositioned { inputAreaBounds = it.boundsInRoot() },
                isGenerating = isGenerating,
                onMediaModeChange = onMediaModeChange,
                onSendMedia = { request ->
                    inlineChatError = null
                    revealed = true
                    onSendMedia(request).also { if (it) resumeFollowing() }
                },
                onSendMessage = { text, files ->
                    inlineChatError = null
                    revealed = true
                    onSendMessage(text, files).also { if (it && !isGenerating) resumeFollowing() }
                },
                onSendMessageWithSkill = { text, files, requested, skillName ->
                    inlineChatError = null
                    revealed = true
                    onSendMessageWithSkill(text, files, requested, skillName).also { if (it && !isGenerating) resumeFollowing() }
                },
                onEditMessage = { id, text, files ->
                    val isQueued = editDraft?.isQueued == true
                    if (isQueued) {
                        onEditQueuedMessage(id, text, files)
                    } else {
                        inlineChatError = null
                        revealed = true
                        onEditMessage(id, text, files).also { if (it) resumeFollowing() }
                    }
                },
                onSubmissionFailed = { inlineChatError = it },
                editDraft = editDraft,
                onCancelEdit = { editDraft = null },
                onStopGenerating = onStopGenerating,
                contextWindowStatus = contextWindowStatus,
                onStartSummary = onStartConversationSummary,
                selectedModelName = selectedModelName,
                providers = providers,
                providerGroups = providerGroups,
                modelPickerCollapsedGroups = modelPickerCollapsedGroups,
                modelPickerCollapsedProviders = modelPickerCollapsedProviders,
                onModelPickerCollapsedGroupsChange = onModelPickerCollapsedGroupsChange,
                onModelPickerCollapsedProvidersChange = onModelPickerCollapsedProvidersChange,
                 modelPickerOpenRequest = modelPickerOpenRequest,
                selectedProviderId = selectedProviderId,
                onSelectModel = onSelectModel,
                conversationReasoningEffort = conversation?.reasoningEffort,
                onSetReasoningEffort = onSetReasoningEffort,
                onSetThinkingPassback = onSetThinkingPassback,
                 onSetModelReasoningEffort = onSetModelReasoningEffort,
                 onSetModelTools = onSetModelTools,
                 onEditModel = onEditModel,
                 onEditProvider = onEditProvider,
                 onFetchOllamaModels = onFetchOllamaModels,
                 onOpenFeatureToolsSettings = onOpenFeatureToolsSettings,
                  onOpenMcpConnectorsSettings = onOpenMcpConnectorsSettings,
                  agentSkills = agentSkills,
                  onOpenSkillsSettings = onOpenSkillsSettings,
                 onModelSheetOpen = { minimapState.isOpen = false }
              )
            }
        }

        }
        ChatAppBar(
            modelName = selectedModelDisplayName,
            providerName = selectedProvider?.name.orEmpty(),
            onOpenDrawer = {
                minimapState.isOpen = false
                onOpenDrawer()
            },
            onNewChat = onNewChat,
            onOpenTemporaryChat = onOpenTemporaryChat,
            onOpenSettings = onOpenSettings,
            featuresLogoImage = featuresLogoImage,
            backgroundTransparency = backgroundTransparency,
            isTemporaryChat = conversationMessages.isEmpty() && conversation?.isTemporary != true,
            onOpenModelPicker = {
                minimapState.isOpen = false
                modelPickerOpenRequest++
            },
            showTerminalButton = connectedMachines.isNotEmpty(),
            onOpenTerminal = {
                // One running machine opens its terminal directly; several need a choice first.
                if (connectedMachines.size == 1) onOpenTerminal(connectedMachines.single())
                else showTerminalMachinePicker = true
            },
            modifier = Modifier.align(Alignment.TopCenter)
                .onSizeChanged { appBarHeightPx = it.height }
        )
        ConversationMinimapSideBar(
            conversationKey = conversationKey,
            messages = messages,
            listState = listState,
            topObscuredPx = with(density) { appBarHeight.roundToPx() },
            // Measure the entire bottom panel, including suggestions, errors and navigation insets.
            bottomObscuredPx = bottomPanelHeightPx + with(density) { 8.dp.roundToPx() },
            onOpen = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
            },
            onNavigate = {
                followBottom = false
                revealed = true
            },
            state = minimapState
        )
    }

    }

    if (showContextSummarySheet) {
        ConversationSummarySheet(onStart = onStartConversationSummary, onDismiss = { showContextSummarySheet = false })
    }

    if (showTerminalMachinePicker) {
        AppSheet(
            onDismissRequest = { showTerminalMachinePicker = false }
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(org.starfall.generative.R.string.remote_machine_terminal_choose_machine),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                connectedMachines.forEach { machine ->
                    Surface(
                        onClick = {
                            showTerminalMachinePicker = false
                            onOpenTerminal(machine)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = prominentContainerColor(),
                        border = prominentContainerBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FluentIcon(
                                Icons.Outlined.Terminal, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    machine.name.ifBlank { stringResource(org.starfall.generative.R.string.remote_machine_unnamed) },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${machine.username.ifBlank { "?" }}@${machine.displayHost}:${machine.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FluentIcon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showSummaryDialog) {
        conversation?.summary?.let { summary ->
            ConversationSummaryDialog(
                summary = summary,
                onRoleChange = onSummaryRoleChange,
                onDelete = onDeleteSummary,
                onDismiss = { showSummaryDialog = false }
            )
        } ?: run { showSummaryDialog = false }
    }

    if (regeneratingMessageId != null) {
        RegenerateResponseDialog(
            onConfirm = {
                regeneratingMessageId?.let(onRegenerate)
                regeneratingMessageId = null
            },
            onDismiss = { regeneratingMessageId = null }
        )
    }

    // Delete message/version confirmation dialog
    val deletingMessage = deletingMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    if (deletingMessage != null) {
        DeleteMessageDialog(
            message = deletingMessage,
            onDeleteVersion = {
                onDeleteMessageVersion(deletingMessage.id)
                deletingMessageId = null
            },
            onDeleteAll = {
                onDeleteMessage(deletingMessage.id)
                deletingMessageId = null
            },
            onDismiss = { deletingMessageId = null }
        )
    }
}

/** Changes whenever the saved messages change, so a cached open is never reused for different content. */
private fun chatOpenSignature(messages: List<StoredMessage>): Int =
    messages.fold(messages.size) { hash, message ->
        var h = 31 * hash + message.id.hashCode()
        h = 31 * h + message.activeVersionIndex
        h = 31 * h + message.content.hashCode()
        31 * h + message.files.hashCode()
    }

/** Everything besides the messages that changes how they are laid out. */
@Composable
private fun chatRenderEnvironmentKey(): Int {
    val preferences = LocalCodeRenderingPreferences.current
    val density = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return java.util.Objects.hash(
        preferences.effectiveMessageFontSize,
        preferences.messageFontFamily,
        preferences.latexMode,
        preferences.wordWrapMode,
        density.density,
        density.fontScale,
        widthDp,
        dark
    )
}
