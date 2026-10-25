package org.starfall.generative.home.chat.ui

import org.starfall.generative.R
import org.starfall.generative.home.BottomSheetsLink
import org.starfall.generative.home.BottomSheetsLinkState

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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.unit.Velocity
import kotlin.math.exp
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import org.starfall.generative.home.chat.logic.SAVE_CONVERSATION_ERROR_PREFIX
import org.starfall.generative.shared.model.ChatRole
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
import org.starfall.generative.home.chat.logic.input.ChatInputEditDraft
import org.starfall.generative.home.chat.logic.input.DirectMediaRequest
import org.starfall.generative.shared.model.language.ModelType
import org.starfall.generative.home.chat.logic.unansweredUserMessageIds
import org.starfall.generative.home.chat.ui.input.UserInputArea
import org.starfall.generative.shared.ui.code.LocalCodeRenderingPreferences
import org.starfall.generative.home.shared.ui.ConversationSummarySheet
import org.starfall.generative.home.chat.model.ContextWindowStatus
import org.starfall.generative.shared.ui.layout.prominentContainerBorder
import org.starfall.generative.shared.ui.layout.prominentContainerColor

// Jump-to-latest: ease time constant (smaller is faster), frames the bottom must hold, and a hard cap.
private const val JUMP_EASE_MS = 90f
private const val JUMP_SETTLE_FRAMES = 3
private const val JUMP_MAX_DURATION_MS = 2500L
// Far larger than any item; clamped to the bottom by the first LazyColumn measure.
private const val OPEN_AT_BOTTOM_OFFSET = 10_000_000

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
    modelPickerSelectedTab: String = "default",
    onModelPickerSelectedTabChange: (String) -> Unit = {},
    selectedProviderId: String,
    selectedModelName: String,
    onSendMessage: (String, List<String>) -> Boolean,
    onSendMedia: (DirectMediaRequest) -> Boolean,
    onMediaModeChange: (ModelType?, String, String) -> Unit = { _, _, _ -> },
    onStopGenerating: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    featuresLogoImage: String? = null,
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
    contextWindowStatus: ContextWindowStatus? = null,
    speakingMessageId: String? = null,
    onResendUserMessage: (String) -> Boolean = { false },
    /** Remote machines with a live connection; non-empty shows the terminal button in the app bar. */
    connectedMachines: List<RemoteMachine> = emptyList(),
    onOpenTerminal: (RemoteMachine) -> Unit = {},
    onOpenFeatureToolsSettings: () -> Unit = {},
    onOpenMcpConnectorsSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val conversationKey = conversation?.id ?: "__empty_conversation__"
    // Start at the newest item so opening a conversation does not flash its oldest messages.
    // The offset is clamped by LazyColumn after the first measure pass.
    val initialLastIndex = (
        (conversation?.messages?.size ?: 0) + queuedMessages.size - 1 +
            (if (isGenerating && generatingConversationId != conversation?.id) 1 else 0)
        ).coerceAtLeast(0)
    val listState = remember(conversationKey) {
        LazyListState(
            firstVisibleItemIndex = initialLastIndex,
            firstVisibleItemScrollOffset = OPEN_AT_BOTTOM_OFFSET
        )
    }
    var followBottom by remember(conversationKey) { mutableStateOf(true) }
    var jumpingToBottom by remember(conversationKey) { mutableStateOf(false) }
    var openingAtBottom by remember(conversationKey) { mutableStateOf(true) }
    var openingLayoutRevision by remember(conversationKey) { mutableIntStateOf(0) }
    val settleTracker = remember(conversationKey) { ChatSettleTracker() }
    // Scroll position of a just-sent message: middle by default, top only by the user's drag.
    val anchor = remember(conversationKey) { ChatScrollAnchor() }
    var revealed by remember(conversationKey) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val seenMessageIds = remember(conversationKey) { mutableSetOf<String>() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedModelDisplayName = selectedProvider?.config?.modelConfigs?.get(selectedModelName)
        ?.displayName?.takeIf { it.isNotBlank() } ?: selectedModelName

    val conversationMessages = conversation?.messages.orEmpty()
    val messages = remember(conversationMessages, queuedMessages) {
        conversationMessages + queuedMessages
    }
    // A just-sent message is the anchor. A chat that merely ends with a user message (reopened, or
    // still unanswered) gets the same tail padding for it, so it can be dragged flush under the app bar.
    val tailAnchorId = anchor.messageId
        ?: messages.lastOrNull()?.takeIf { it.role == ChatRole.USER && !it.isQueued }?.id
    // A user message entering history is a send, whichever way it got there (typed now, handed over
    // by the tool loop with its queued id, or drained from the queue under a new id). Detecting it
    // here, in the apply phase, runs before any LaunchedEffect of this commit starts, so every scroll
    // effect below already sees anchor.settling and leaves the list alone.
    SideEffect {
        val sentId = anchor.detector.observe(conversationMessages)
        if (sentId != null && (followBottom || !autoScroll)) {
            openingAtBottom = false
            anchor.start(sentId)
        } else if (anchor.messageId?.let { id -> messages.none { it.id == id } } == true) {
            anchor.end()
        }
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
    val streamingHere = isGenerating && generatingConversationId == conversation?.id
    val lastMessage = conversationMessages.lastOrNull()
    val streamingMessage = if (streamingHere) conversationMessages.lastOrNull { it.role == ChatRole.MODEL } else null
    val unansweredMessages = remember(conversationMessages) { unansweredUserMessageIds(conversationMessages) }
    // Streaming text is followed by the per-frame loop below; stepping on text length made the scroll jump per chunk.
    val autoScrollTick = if (!autoScroll || streamingHere) null else lastMessage?.activeVersionIndex
    // A chat that was already settled at its newest message earlier in this process, and whose parsed
    // content is still in the (evictable) RAM cache, opens at its final size on the first frame and needs
    // no veil. Otherwise the first open hides the chat until it is fully loaded and at the latest message.
    val renderEnvironment = chatRenderEnvironmentKey()
    val openSignature = remember(conversationMessages, streamingHere) {
        if (streamingHere) 0 else chatOpenSignature(conversationMessages)
    }
    val hasSavedMessages = conversationMessages.isNotEmpty()
    val openedWarm = remember(conversationKey, hasSavedMessages, openSignature, renderEnvironment) {
        hasSavedMessages && ChatOpenCache.isWarm(conversationKey, openSignature, renderEnvironment)
    }
    val veilActive = openingAtBottom && !revealed && hasSavedMessages && !openedWarm && !streamingHere
    fun whenIdle(action: () -> Unit) {
        if (!isGenerating) action()
    }
    val nearBottomPx = with(LocalDensity.current) { 32.dp.toPx() }
    fun isNearBottom(): Boolean {
        val layout = listState.layoutInfo
        val last = layout.visibleItemsInfo.lastOrNull() ?: return false
        if (last.index != layout.totalItemsCount - 1) return false
        return last.offset + last.size - (layout.viewportEndOffset - layout.afterContentPadding) <= nearBottomPx
    }
    val scrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Stop the frame-based auto-scroll before the list consumes any part of a
                // finger drag. This lets a reverse swipe take control immediately while the
                // response is still streaming.
                if (source == NestedScrollSource.UserInput) {
                    openingAtBottom = false
                    followBottom = false
                    anchor.release()
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // A drag back to the newest message resumes following once it reaches the end.
                if (source == NestedScrollSource.UserInput && isNearBottom()) followBottom = true
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Resume following only when the fling was toward newer messages. A short fling
                // toward history can end within the near-bottom tolerance; treating that as a
                // request to follow would immediately pull the list back to the latest message.
                if (consumed.y > 0f && isNearBottom()) followBottom = true
                return Velocity.Zero
            }
        }
    }
    val messageIndexOffset = if (isGenerating && !streamingHere) 1 else 0
    // The jump-to-latest button is only needed when the newest message is outside the viewport.
    // Keep this independent of followBottom, which controls scrolling behavior.
    val latestMessageVisible by remember(listState, messages.size, messageIndexOffset) {
        derivedStateOf {
            val latestIndex = messages.lastIndex + messageIndexOffset
            latestIndex >= 0 && listState.layoutInfo.visibleItemsInfo.any { it.index == latestIndex }
        }
    }
    suspend fun scrollToBottom(includeQueued: Boolean = true) {
        // Derive the target from the current data rather than layoutInfo: a message added in
        // this frame may not have been measured yet.
        val index = (if (includeQueued) messages.lastIndex else conversationMessages.lastIndex) +
            messageIndexOffset
        if (index < 0) return
        // While a sent message is anchored the end of the content is the position under the app bar
        // (the newest item is padded for it). Reveal the newest message instead of jumping there.
        if (anchor.messageId != null) listState.revealItemBottom(index)
        else listState.scrollToItem(index, OPEN_AT_BOTTOM_OFFSET)
    }
    // Animated version used by the jump-to-latest button. Distant targets are first brought within
    // a few items so the animation covers a short, visible distance instead of the whole history.
    // The rest is one continuous per-frame ease toward the bottom edge of the newest item. Re-reading
    // the layout every frame keeps it smooth when sizes change underneath it (items measured for the
    // first time, the tail spacer, streamed text); chained fixed-length tweens would each decelerate to
    // zero and make the scroll stutter.
    suspend fun animateScrollToBottom() {
        val index = messages.lastIndex + messageIndexOffset
        if (index < 0) return
        if (index - listState.firstVisibleItemIndex > 6) listState.scrollToItem(index - 3)
        var previous = 0L
        var started = 0L
        var settledFrames = 0
        while (settledFrames < JUMP_SETTLE_FRAMES) {
            val now = withFrameNanos { it }
            if (started == 0L) started = now
            // Never loop forever if the content keeps growing under the animation.
            if ((now - started) / 1_000_000L > JUMP_MAX_DURATION_MS) break
            val elapsedMs = if (previous == 0L) 16f else ((now - previous) / 1_000_000f).coerceIn(1f, 64f)
            previous = now
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null) {
                listState.scrollToItem(index)
                continue
            }
            val remaining = (item.offset + item.size - (layout.viewportEndOffset - layout.afterContentPadding)).toFloat()
            if (remaining <= 1f) {
                settledFrames++
                continue
            }
            settledFrames = 0
            val step = (remaining * (1f - exp(-elapsedMs / JUMP_EASE_MS))).coerceAtLeast(min(remaining, elapsedMs * 0.05f))
            listState.scrollBy(step)
        }
        scrollToBottom()
    }
    val messageIds = remember(conversationMessages) { conversationMessages.map { it.id } }
    LaunchedEffect(conversation?.id, messageIds, autoScroll) {
        val hasNewMessage = messageIds.any { it !in seenMessageIds }
        val hasNewUserMessage = conversationMessages.any { it.id !in seenMessageIds && it.role == ChatRole.USER }
        val firstLoad = seenMessageIds.isEmpty()
        seenMessageIds.addAll(messageIds)
        if (hasNewMessage && !firstLoad) openingAtBottom = false
        // The anchor owns the scroll position until the sent message is in place.
        if (anchor.settling) return@LaunchedEffect
        if (firstLoad && messageIds.isNotEmpty()) {
            // The conversation can arrive after ChatScreen was first composed. Wait until its
            // items have been measured before correcting the initial state.
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            if (openingAtBottom) {
                withFrameNanos { }
                followBottom = true
                scrollToBottom(includeQueued = true)
            }
        } else if (hasNewMessage && !firstLoad && (if (autoScroll) followBottom else hasNewUserMessage)) {
            // With auto scroll on, a newly started response is followed once. With it off only a
            // user message is shown; the AI reply (including its first message) never moves the list.
            scrollToBottom()
            followBottom = true
        }
    }
    LaunchedEffect(listState, openingAtBottom, streamingHere, messages.size, messageIndexOffset) {
        if (!openingAtBottom || messages.isEmpty()) return@LaunchedEffect
        // Markdown parsing, media loading and input-area measurement can grow the layout
        // after the first scroll. Keep a reopened chat at the end until the user takes
        // control or a new message arrives, even when streaming auto-scroll is disabled.
        snapshotFlow { listState.canScrollForward }.collect { canScrollForward ->
            if (openingAtBottom && canScrollForward) openingLayoutRevision++
        }
    }
    LaunchedEffect(openingLayoutRevision, openingAtBottom, streamingHere, messages.size, messageIndexOffset) {
        if (!openingAtBottom || messages.isEmpty()) return@LaunchedEffect
        // Run outside the snapshot observer that noticed the layout change.
        withFrameNanos { }
        if (openingAtBottom) scrollToBottom()
    }
    LaunchedEffect(conversationKey, veilActive) {
        if (!veilActive) return@LaunchedEffect
        // Wait until the newest items exist, then until the list has stopped changing: nothing is still
        // being parsed or loaded and the view has stayed at the latest message for a few frames.
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        var stableFrames = 0
        while (true) {
            withFrameNanos { }
            val settled = settleTracker.pending == 0 && !listState.canScrollForward && !listState.isScrollInProgress
            stableFrames = if (settled) stableFrames + 1 else 0
            if (stableFrames >= OPENING_STABLE_FRAMES) break
        }
        revealed = true
    }
    LaunchedEffect(conversationKey, openSignature, revealed, openedWarm, streamingHere) {
        if (!(revealed || openedWarm) || streamingHere || !hasSavedMessages) return@LaunchedEffect
        // Remember the settled state so the next open of this chat is instant. Only record while the
        // view is at the newest message and idle, never a half-scrolled or still-loading layout.
        snapshotFlow {
            settleTracker.pending == 0 && !listState.isScrollInProgress &&
                (!listState.canScrollForward || anchor.messageId != null)
        }.first { it }
        ChatOpenCache.remember(conversationKey, openSignature, renderEnvironment, settleTracker.readyAssets())
    }
    LaunchedEffect(
        conversation?.id,
        conversationMessages.size,
        autoScroll,
        autoScrollTick,
        followBottom,
        streamingHere,
        anchor.settling,
        if (autoScroll) summaryProgress?.progress else null
    ) {
        if (autoScroll && followBottom && messages.isNotEmpty() && !streamingHere && !anchor.settling) {
            scrollToBottom(includeQueued = true)
        }
    }
    // Auto scroll on: ease toward the bottom every frame while text streams. Keeping this
    // independent of text length makes the movement smooth instead of jumping per chunk.
    LaunchedEffect(conversation?.id, conversationMessages.size, autoScroll, streamingHere, followBottom, anchor.settling) {
        if (!autoScroll || !streamingHere || !followBottom || anchor.settling) return@LaunchedEffect
        var previous = 0L
        while (isActive) {
            // The nested-scroll callback changes this as soon as the user touches the list.
            // Check it inside the loop as well so a pending frame cannot take the scroll lock
            // back from a manual reverse swipe.
            if (!followBottom || anchor.settling) return@LaunchedEffect
            val now = withFrameNanos { it }
            val elapsedMs = if (previous == 0L) 16f else ((now - previous) / 1_000_000f).coerceIn(1f, 64f)
            previous = now
            if (!followBottom || listState.isScrollInProgress || conversationMessages.isEmpty()) continue
            val index = conversationMessages.lastIndex + messageIndexOffset
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null) {
                scrollToBottom(includeQueued = false)
                continue
            }
            val remaining = (item.offset + item.size - (layout.viewportEndOffset - layout.afterContentPadding)).toFloat()
            if (remaining > 0.5f) {
                val step = (remaining * (1f - exp(-elapsedMs / 120f))).coerceAtLeast(min(remaining, elapsedMs * 0.05f))
                try {
                    listState.scrollBy(step)
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
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
        // Let the list occupy the whole edge-to-edge surface. Its top padding keeps the newest
        // content readable initially, while later scrolls visibly pass behind the translucent app bar.
        BoxWithConstraints(
            Modifier.fillMaxSize().clipToBounds()
        ) {
        val bottomClearance = with(density) { inputAreaHeightPx.toDp() } + navigationBarHeight + maxHeight * 0.015f
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = appBarHeight + 8.dp, bottom = 88.dp + navigationBarHeight),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(org.starfall.generative.R.string.connect_providers_description),
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
        } else {
            val visibleChatPx = with(density) {
                (maxHeight - (appBarHeight + 8.dp) - bottomClearance).coerceAtLeast(0.dp).roundToPx()
            }
            val anchorTailIds = remember(messages, tailAnchorId) {
                val index = messages.indexOfFirst { it.id == tailAnchorId }
                if (index < 0) emptyList() else messages.drop(index).map { it.id }
            }
            ChatAnchorEffects(
                anchor = anchor,
                listState = listState,
                itemIndexOf = { id ->
                    messages.indexOfFirst { it.id == id }.let { if (it < 0) -1 else it + messageIndexOffset }
                },
                itemCount = { messages.size + messageIndexOffset }
            )
            ChatOpeningVeil(active = veilActive, modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalChatSettleTracker provides settleTracker) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection),
                contentPadding = PaddingValues(top = appBarHeight + 8.dp, bottom = bottomClearance)
            ) {
                if (isGenerating && !streamingHere) {
                    item(key = "generation-warning") {
                        Text(
                            stringResource(R.string.chat_error_other_conversation),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    val isLast = msg.id == lastMessage?.id
                    Column(
                        Modifier.anchorTailItem(
                            anchor, msg.id, msg.id == messages.last().id,
                            anchorTailIds.dropLast(1), visibleChatPx,
                            padNewest = tailAnchorId != null
                        )
                    ) {

                    if (msg.role == ChatRole.USER) {
                        UserMessageCard(
                            message = msg,
                            selectedImageAttachments = inputAttachments,
                            onToggleChatImage = toggleChatImage,
                            onResend = if (msg.id in unansweredMessages) ({
                                if (onResendUserMessage(msg.id)) followBottom = true
                            }) else null,
                            onResendFromMenu = if (!msg.isQueued) ({
                                if (onResendUserMessage(msg.id)) followBottom = true
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
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.common_copy), msg.content))
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
            }
            }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
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
                        // Resume following only after the animation finishes; setting it first
                        // would trigger the instant jump in the auto-scroll effect.
                        openingAtBottom = false
                        coroutineScope.launch {
                            jumpingToBottom = true
                            try {
                                animateScrollToBottom()
                                followBottom = true
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
            UserInputArea(
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
                    onSendMedia(request).also { if (it) followBottom = true }
                },
                onSendMessage = { text, files ->
                    inlineChatError = null
                    revealed = true
                    onSendMessage(text, files).also { if (it && !isGenerating) followBottom = true }
                },
                onEditMessage = { id, text, files ->
                    val isQueued = editDraft?.isQueued == true
                    if (isQueued) {
                        onEditQueuedMessage(id, text, files)
                    } else {
                        inlineChatError = null
                        revealed = true
                        onEditMessage(id, text, files).also { if (it) followBottom = true }
                    }
                },
                onSubmissionFailed = { inlineChatError = it },
                editDraft = editDraft,
                onCancelEdit = { editDraft = null },
                onStopGenerating = onStopGenerating,
                contextWindowStatus = contextWindowStatus,
                onOpenSummary = { showContextSummarySheet = true },
                selectedModelName = selectedModelName,
                providers = providers,
                providerGroups = providerGroups,
                modelPickerCollapsedGroups = modelPickerCollapsedGroups,
                modelPickerCollapsedProviders = modelPickerCollapsedProviders,
                onModelPickerCollapsedGroupsChange = onModelPickerCollapsedGroupsChange,
                onModelPickerCollapsedProvidersChange = onModelPickerCollapsedProvidersChange,
                 modelPickerSelectedTab = modelPickerSelectedTab,
                 onModelPickerSelectedTabChange = onModelPickerSelectedTabChange,
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
                 onOpenMcpConnectorsSettings = onOpenMcpConnectorsSettings
             )
        }

        }
        ChatAppBar(
            modelName = selectedModelDisplayName,
            providerName = selectedProvider?.name.orEmpty(),
            onOpenDrawer = onOpenDrawer,
            onNewChat = onNewChat,
            onOpenSettings = onOpenSettings,
            featuresLogoImage = featuresLogoImage,
            onOpenModelPicker = { modelPickerOpenRequest++ },
            showTerminalButton = connectedMachines.isNotEmpty(),
            onOpenTerminal = {
                // One running machine opens its terminal directly; several need a choice first.
                if (connectedMachines.size == 1) onOpenTerminal(connectedMachines.single())
                else showTerminalMachinePicker = true
            },
            modifier = Modifier.align(Alignment.TopCenter)
                .onSizeChanged { appBarHeightPx = it.height }
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
