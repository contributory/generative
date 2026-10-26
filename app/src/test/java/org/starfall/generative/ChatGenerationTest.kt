package org.starfall.generative

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.starfall.generative.shared.model.*
import org.starfall.generative.features.providers.shared.ui.*
import org.starfall.generative.shared.model.generation.GenerationEvent
import org.starfall.generative.features.home.chat.logic.ChatGeneration
import org.starfall.generative.features.home.chat.model.ChatStreamStatus
import org.starfall.generative.features.home.chat.logic.prepareRegeneration
import org.starfall.generative.features.home.chat.logic.updateResponse

class ChatGenerationTest {
    @Test fun regenerationAndStreamingPreserveOriginalAndNewModelNames() {
        val original = conversation().copy(messages = conversation().messages.map { message ->
            if (message.role != ChatRole.MODEL) message else message.copy(versions = listOf(
                message.activeVersion.copy(modelId = "old", modelDisplayName = "Old model")
            ))
        })
        val regenerated = prepareRegeneration(original, "a1", MessageVersion(
            providerId = "new-provider", modelId = "new", modelDisplayName = "New model"))!!
        val streamed = updateResponse(regenerated, "a1", "A new answer")
        assertEquals("Old model", streamed.messages.last().versions.first().modelDisplayName)
        assertEquals("New model", streamed.messages.last().activeVersion.modelDisplayName)
        assertEquals("new-provider", streamed.messages.last().activeVersion.providerId)
    }
    private fun message(id: String, role: ChatRole, content: String) =
        StoredMessage(id, role, listOf(MessageVersion(content = content)))
    private fun conversation() = Conversation("a", "Chat A", 1, 1, messages = listOf(
        message("u1", ChatRole.USER, "First question"),
        message("a1", ChatRole.MODEL, "")
    ))

    @Test fun navigationDoesNotRedirectOrLoseStream() = runBlocking {
        var selected = conversation()
        val saved = mutableMapOf<String, Conversation>()
        val next = CompletableDeferred<Unit>()
        val runner = ChatGeneration(CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            { saved[it.id] = it }, { if (selected.id == it.id) selected = it })
        runner.start(selected, "a1", flow { emit("first"); next.await(); emit(" second") })
        assertEquals("a", selected.id)
        assertEquals("", selected.messages.last().content)
        selected = conversation().copy(id = "b", title = "Chat B")
        next.complete(Unit)
        yield()
        assertEquals("b", selected.id)
        assertEquals("", selected.messages.last().content)
        assertEquals("first second", saved["a"]!!.messages.last().content)
        assertFalse(runner.busy.value)
    }

    @Test fun titleChangesDuringGenerationSurviveTheFinalResponseCommit() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val continueResponse = CompletableDeferred<Unit>()
        var saved: Conversation? = null
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = { saved = it },
            publish = {}
        )

        runner.start(conversation(), "a1", flow {
            emit("partial")
            requestStarted.complete(Unit)
            continueResponse.await()
            emit("\nanswer")
        })
        requestStarted.await()
        runner.updateConversation { it.copy(title = "Renamed while streaming") }
        continueResponse.complete(Unit)
        runner.stopAndJoin()

        assertEquals("Renamed while streaming", saved!!.title)
        assertEquals("partial\nanswer", saved!!.messages.last().content)
    }

    @Test fun streamCommitsAllAvailableCharactersInOneRenderWindow() = runBlocking {
        val publishedConversations = mutableListOf<Conversation>()
        val streamStates = mutableListOf<org.starfall.generative.features.home.chat.model.ActiveChatStreamState>()
        val waits = mutableListOf<Long>()
        var now = 0L
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = {},
            publish = { publishedConversations += it },
            publishStream = { _, state -> state?.let(streamStates::add) },
            streamCommitIntervalMs = 50L,
            monotonicTimeMillis = { now },
            waitForStreamInterval = { duration ->
                waits += duration
                now += duration
            }
        )
        val output = "a".repeat(30) + "b".repeat(30) + "c".repeat(60)

        runner.start(conversation(), "a1", flow { emit(output) })

        assertEquals("ordinary stream batches do not publish Conversation per chunk", 2, publishedConversations.size)
        assertEquals("initial state, one render-window commit, and terminal lifecycle state", 3, streamStates.size)
        assertEquals(120, streamStates[1].lastCommitMetrics?.characterCount)
        assertEquals(120, streamStates[1].content.length.toInt())
        assertEquals(0, streamStates[1].lastCommitMetrics?.queuedCharacters)
        assertEquals(1L, streamStates[1].cumulativeMetrics.batchCount)
        assertEquals(120L, streamStates[1].cumulativeMetrics.committedCharacters)
        assertTrue(waits.isEmpty())
        assertTrue(streamStates[2].revision > streamStates[1].revision)
        assertEquals(ChatStreamStatus.COMPLETED, streamStates.last().status)
        assertEquals(output, streamStates.last().content.materialize())
    }

    @Test fun shortChunkIsRenderedImmediatelyWithoutACharacterLimit() = runBlocking {
        val streamStates = mutableListOf<org.starfall.generative.features.home.chat.model.ActiveChatStreamState>()
        var now = 0L
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = {},
            publish = {},
            publishStream = { _, state -> state?.let(streamStates::add) },
            streamCommitIntervalMs = 50L,
            monotonicTimeMillis = { now },
            waitForStreamInterval = { duration -> now += duration }
        )

        runner.start(conversation(), "a1", flow {
            emit("short text")
            awaitCancellation()
        })

        assertEquals(2, streamStates.size)
        assertEquals("short text", streamStates.last().content.materialize())
        assertEquals(10, streamStates.last().lastCommitMetrics?.characterCount)
        assertFalse(streamStates.last().lastCommitMetrics?.terminal == true)
        assertEquals(ChatStreamStatus.STREAMING, streamStates.last().status)
        runner.stopAndJoin()
    }

    @Test fun stopFreezesTheLastPublishedChunkWithoutATerminalRerender() = runBlocking {
        val published = mutableListOf<org.starfall.generative.features.home.chat.model.ActiveChatStreamState>()
        val visibleChunkPublished = CompletableDeferred<Unit>()
        val queuedChunkAccepted = CompletableDeferred<Unit>()
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = {},
            publish = {},
            publishStream = { _, state ->
                state?.let {
                    published += it
                    if (it.content.materialize() == "visible") visibleChunkPublished.complete(Unit)
                }
            },
            streamCommitIntervalMs = 50L,
            monotonicTimeMillis = { 0L },
            waitForStreamInterval = { awaitCancellation() }
        )

        runner.start(conversation(), "a1", flow {
            emit("visible")
            visibleChunkPublished.await()
            emit(" queued")
            queuedChunkAccepted.complete(Unit)
            awaitCancellation()
        })
        visibleChunkPublished.await()
        queuedChunkAccepted.await()

        assertEquals("visible", published.last().content.materialize())
        val publishedCountAtStop = published.size
        runner.stop()
        runner.stopAndJoin()

        assertEquals("Stop must not publish a terminal stream revision", publishedCountAtStop, published.size)
        assertEquals("visible", published.last().content.materialize())
        assertEquals(ChatStreamStatus.STREAMING, published.last().status)
    }

    @Test fun stoppingPersistsExactlyTheFrozenVisibleSnapshotAndDropsQueuedText() = runBlocking {
        var saved: Conversation? = null
        val visibleChunkPublished = CompletableDeferred<Unit>()
        val queuedChunkAccepted = CompletableDeferred<Unit>()
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = { saved = it },
            publish = {},
            publishStream = { _, state ->
                if (state?.content?.materialize() == "visible") visibleChunkPublished.complete(Unit)
            },
            streamCommitIntervalMs = 50L,
            monotonicTimeMillis = { 0L },
            waitForStreamInterval = { awaitCancellation() }
        )

        runner.start(conversation(), "a1", flow {
            emit("visible")
            visibleChunkPublished.await()
            emit(" queued but not yet published")
            queuedChunkAccepted.complete(Unit)
            awaitCancellation()
        })
        visibleChunkPublished.await()
        queuedChunkAccepted.await()

        runner.stop()
        runner.stopAndJoin()

        assertEquals("visible", saved!!.messages.last().activeVersion.content)
        assertTrue(saved!!.messages.last().activeVersion.generationInterrupted)
    }

    @Test fun terminalFlushPublishesFinalShortCharacterChunkOnce() = runBlocking {
        val streamStates = mutableListOf<org.starfall.generative.features.home.chat.model.ActiveChatStreamState>()
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = {},
            publish = {},
            publishStream = { _, state -> state?.let(streamStates::add) },
            streamCommitIntervalMs = 50L,
            waitForStreamInterval = { awaitCancellation() }
        )

        runner.start(conversation(), "a1", flow {
            emit("short stream text")
            awaitCancellation()
        })
        assertEquals("the short chunk is visible before terminal completion", 2, streamStates.size)

        runner.stopAndJoin()

        assertEquals(3, streamStates.size)
        assertEquals("short stream text", streamStates[1].content.materialize())
        assertEquals(17, streamStates[1].lastCommitMetrics?.characterCount)
        assertFalse(streamStates[1].lastCommitMetrics?.terminal == true)
        assertEquals(ChatStreamStatus.CANCELLED, streamStates.last().status)
    }

    @Test fun busyRemainsSetWhileWaitingForTheFirstResponseEvent() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val responseReady = CompletableDeferred<Unit>()
        val runner = ChatGeneration(CoroutineScope(coroutineContext + Dispatchers.Unconfined), {}, {})

        runner.start(conversation(), "a1", flow {
            requestStarted.complete(Unit)
            responseReady.await()
            emit("first response")
        })

        requestStarted.await()
        assertTrue(runner.busy.value)

        responseReady.complete(Unit)
        runner.stopAndJoin()
        assertFalse(runner.busy.value)
    }

    @Test fun rapidSendIsRejectedAndCancellationSavesPartialText() = runBlocking {
        var saved: Conversation? = null
        val runner = ChatGeneration(CoroutineScope(coroutineContext + Dispatchers.Unconfined), { saved = it }, {})
        assertTrue(runner.start(conversation(), "a1", flow { emit("partial"); awaitCancellation() }))
        assertFalse(runner.start(conversation().copy(id = "b"), "a1", flowOf("wrong")))
        runner.stopAndJoin()
        assertEquals("a", saved!!.id)
        assertEquals("partial", saved!!.messages.last().content)
        assertNull(runner.error.value)
        assertFalse(runner.busy.value)
        assertTrue(runner.start(conversation().copy(id = "b"), "a1", flowOf("next")))
        assertEquals("next", saved!!.messages.last().content)
    }

    @Test fun busyRemainsSetUntilCancelledResponseIsSaved() = runBlocking {
        val saving = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var saves = 0
        val runner = ChatGeneration(CoroutineScope(coroutineContext + Dispatchers.Unconfined), {
            if (++saves == 2) { saving.complete(Unit); release.await() }
        }, {})
        runner.start(conversation(), "a1", flow { emit("partial"); awaitCancellation() })
        runner.stop()
        saving.await()
        assertTrue(runner.busy.value)
        assertFalse(runner.start(conversation(), "a1", emptyFlow()))
        release.complete(Unit)
        runner.stopAndJoin()
        assertFalse(runner.busy.value)
    }

    @Test fun deletingAfterStopCannotResurrectConversation() = runBlocking {
        val saved = mutableMapOf<String, Conversation>()
        val runner = ChatGeneration(CoroutineScope(coroutineContext + Dispatchers.Unconfined), { saved[it.id] = it }, {})
        runner.start(conversation(), "a1", flow { emit("partial"); awaitCancellation() })
        runner.stopAndJoin()
        saved.remove("a")
        yield()
        assertTrue(saved.isEmpty())
    }

    @Test fun regenerationTargetsSelectedAnswerAndPreservesVersions() {
        val original = conversation().copy(messages = listOf(
            message("u1", ChatRole.USER, "First question"), message("a1", ChatRole.MODEL, "Old answer"),
            message("u2", ChatRole.USER, "Second question"), message("a2", ChatRole.MODEL, "Last answer")
        ))
        val regenerated = prepareRegeneration(original, "a1")!!
        assertEquals(listOf("u1", "a1"), regenerated.messages.map { it.id })
        assertEquals("tail truncation advances the structure epoch", 1L, regenerated.structuralRevision)
        assertEquals("First question", regenerated.messages.dropLast(1).single().content)
        val updated = updateResponse(regenerated, "a1", "New answer")
        assertEquals("Old answer", updated.messages.last().versions.first().content)
        assertEquals("New answer", updated.messages.last().content)
        assertEquals(1, updated.messages.last().activeVersionIndex)
        assertNull(prepareRegeneration(original, "missing"))
        assertNull(prepareRegeneration(original, "u1"))
    }

    @Test fun regenerationReplacesAnErrorOnlyAnswerInsteadOfAddingAVersion() {
        val original = conversation().copy(messages = listOf(
            message("u1", ChatRole.USER, "First question"),
            StoredMessage("a1", ChatRole.MODEL, listOf(MessageVersion(generationError = "offline")))
        ))
        val replacement = MessageVersion(content = "new", providerId = "provider", modelId = "model")

        val regenerated = prepareRegeneration(original, "a1", replacement)!!

        assertEquals(1, regenerated.messages.last().versions.size)
        assertEquals(replacement, regenerated.messages.last().activeVersion)
        assertEquals(0, regenerated.messages.last().activeVersionIndex)
    }

    @Test fun emptyAssistantResponsesAreNotPersistedButErrorOnlyResponsesAre() = runBlocking {
        var saved: Conversation? = null
        val runner = ChatGeneration(
            CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            { saved = it },
            {}
        )

        runner.start(conversation(), "a1", emptyFlow())
        assertEquals(listOf("u1"), saved!!.messages.map { it.id })

        runner.start(conversation(), "a1", flow { error("offline") })
        assertEquals(listOf("u1", "a1"), saved!!.messages.map { it.id })
        assertEquals("offline", saved!!.messages.last().activeVersion.generationError)
    }

    @Test fun reasoningSurvivesStopAndCompletion() {
        val partial = updateResponse(conversation(), "a1", "<think>Working")
        assertEquals("Working", partial.messages.last().reasoningContent)
        assertEquals("", partial.messages.last().content)
        val complete = updateResponse(partial, "a1", "<think>Working</think>\nAnswer")
        assertEquals("Working", complete.messages.last().reasoningContent)
        assertEquals("Answer", complete.messages.last().content)
    }

    @Test fun consecutiveGenerationsUseFreshIdsAndDoNotReuseThePreviousDisplaySnapshot() = runBlocking {
        val states = mutableListOf<org.starfall.generative.features.home.chat.model.ActiveChatStreamState>()
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = {},
            publish = {},
            publishStream = { _, state -> state?.let(states::add) }
        )

        runner.start(conversation(), "a1", flowOf("first response"))
        val firstGenerationId = states.last().generationId
        runner.start(conversation(), "a1", flowOf("second response"))

        val secondGenerationStates = states.filter { it.generationId != firstGenerationId }
        assertTrue(secondGenerationStates.isNotEmpty())
        assertTrue(secondGenerationStates.first().generationId > firstGenerationId)
        assertEquals("new generation begins empty", "", secondGenerationStates.first().content.materialize())
        assertEquals("second response", secondGenerationStates.last().content.materialize())
        assertFalse(secondGenerationStates.any { it.content.materialize().contains("first response") })
    }

    @Test fun toolTurnResetsTheActiveStreamIdentityAndPublishesTerminalLifecycle() = runBlocking {
        val states = mutableListOf<org.starfall.generative.features.home.chat.model.ActiveChatStreamState>()
        val clearedConversationIds = mutableListOf<String>()
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = {},
            publish = {},
            publishStream = { id, state ->
                if (state == null) clearedConversationIds += id else states += state
            }
        )
        val nextUser = message("u2", ChatRole.USER, "Continue")

        runner.startEvents(conversation(), "a1", flow {
            emit(GenerationEvent.Text("First answer\n"))
            emit(GenerationEvent.UserMessage(nextUser))
            emit(GenerationEvent.Text("Second answer"))
        })

        val generationIds = states.map { it.generationId }.distinct()
        val secondMessageId = states.first { it.messageId != "a1" }.messageId
        val newMessageInitialState = states.first { it.messageId == secondMessageId }
        assertEquals(1, generationIds.size)
        assertTrue(generationIds.single() > 0L)
        assertEquals("First answer\n", states.last { it.messageId == "a1" }.content.materialize())
        assertEquals("a new assistant message starts with an empty display", "", newMessageInitialState.content.materialize())
        assertEquals("Second answer", states.last { it.messageId == secondMessageId }.content.materialize())
        assertEquals(ChatStreamStatus.COMPLETED, states.last().status)
        assertEquals(listOf("a"), clearedConversationIds)
    }

    @Test fun pendingUserMessageSplitsTheToolTurnBeforeTheNextAnswer() = runBlocking {
        var saved: Conversation? = null
        val runner = ChatGeneration(
            CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            { saved = it },
            {}
        )
        val pending = message("u2", ChatRole.USER, "Continue with this")
        runner.startEvents(conversation(), "a1", flow {
            emit(GenerationEvent.Tool(ToolActivity("tool-1", "search", status = "success")))
            emit(GenerationEvent.UserMessage(pending))
            emit(GenerationEvent.Text("New answer"))
        })

        assertEquals(listOf("u1", "a1", "u2"), saved!!.messages.dropLast(1).map { it.id })
        assertEquals(ChatRole.USER, saved!!.messages[2].role)
        assertEquals("New answer", saved!!.messages.last().content)
        assertEquals(1L, saved!!.structuralRevision)
        assertTrue(saved!!.messages[1].activeVersion.processingFinishedAt != null)
    }

    @Test fun failureRetainsPartialResponseAndReleasesBusyState() = runBlocking {
        var saved: Conversation? = null
        val runner = ChatGeneration(CoroutineScope(coroutineContext + Dispatchers.Unconfined), { saved = it }, {})
        runner.start(conversation(), "a1", flow { emit("partial"); error("offline") })
        assertTrue(saved!!.messages.last().content.startsWith("partial"))
        assertEquals("offline", saved!!.messages.last().activeVersion.generationError)
        assertEquals("offline", runner.error.value)
        assertFalse(runner.busy.value)
    }

    @Test fun failurePublishesTerminalStreamStatusEvenWhenNoTextWasCommitted() = runBlocking {
        val states = mutableListOf<org.starfall.generative.features.home.chat.model.ActiveChatStreamState>()
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = {},
            publish = {},
            publishStream = { _, state -> state?.let(states::add) }
        )

        runner.start(conversation(), "a1", flow { error("offline") })

        assertEquals(2, states.size)
        assertEquals(ChatStreamStatus.FAILED, states.last().status)
        assertEquals(0L, states.last().cumulativeMetrics.batchCount)
    }

    @Test fun failureFlushesTheFinalPartialLineIntoStreamState() = runBlocking {
        val states = mutableListOf<org.starfall.generative.features.home.chat.model.ActiveChatStreamState>()
        val runner = ChatGeneration(
            scope = CoroutineScope(coroutineContext + Dispatchers.Unconfined),
            save = {},
            publish = {},
            publishStream = { _, state -> state?.let(states::add) }
        )

        runner.start(conversation(), "a1", flow {
            emit("partial line before failure")
            error("offline")
        })

        assertEquals("partial line before failure", states.last().content.materialize())
        assertEquals(ChatStreamStatus.FAILED, states.last().status)
        assertTrue(states.last().lastCommitMetrics?.terminal == true)
    }

    @Test fun clearingOwnerScopePersistsPartialResponseAndReleasesGeneration() = runBlocking {
        val owner = Job(coroutineContext[Job])
        var saved: Conversation? = null
        val runner = ChatGeneration(
            CoroutineScope(owner + Dispatchers.Unconfined),
            { saved = it },
            {}
        )
        runner.start(conversation(), "a1", flow {
            emit("partial before Activity finishes")
            awaitCancellation()
        })

        owner.cancelAndJoin()

        assertEquals("partial before Activity finishes", saved!!.messages.last().content)
        assertFalse(runner.busy.value)
        assertNull(runner.conversationId.value)
        assertNull(runner.error.value)
    }
}
