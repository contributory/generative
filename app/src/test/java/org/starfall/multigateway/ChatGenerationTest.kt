package org.starfall.multigateway

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.starfall.multigateway.shared.model.*
import org.starfall.multigateway.features.providers.shared.*
import org.starfall.multigateway.home.chat.screen.*
import org.starfall.multigateway.home.chat.screen.ChatAppbar.*
import org.starfall.multigateway.home.modelpicker.sheet.*
import org.starfall.multigateway.home.quickactions.sheet.*
import org.starfall.multigateway.shared.model.generation.GenerationEvent

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
        assertEquals("first", selected.messages.last().content)
        selected = conversation().copy(id = "b", title = "Chat B")
        next.complete(Unit)
        yield()
        assertEquals("b", selected.id)
        assertEquals("", selected.messages.last().content)
        assertEquals("first second", saved["a"]!!.messages.last().content)
        assertFalse(runner.busy.value)
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
