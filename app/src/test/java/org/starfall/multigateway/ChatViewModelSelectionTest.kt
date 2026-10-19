package org.starfall.multigateway

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.starfall.multigateway.home.chat.logic.ChatViewModel
import org.starfall.multigateway.home.chat.logic.tool.ToolChatViewModel
import org.starfall.multigateway.integrations.mcp.McpService
import org.starfall.multigateway.integrations.modelapis.AiService
import org.starfall.multigateway.shared.files.ToolFiles
import org.starfall.multigateway.shared.media.SpeechAudioPlayer
import org.starfall.multigateway.shared.media.SpeechSynthesisService
import org.starfall.multigateway.shared.media.TtsHelper
import org.starfall.multigateway.shared.model.ChatRole
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.MessageVersion
import org.starfall.multigateway.shared.model.StoredMessage
import org.starfall.multigateway.shared.networking.ToolHttp
import org.starfall.multigateway.shared.storage.ConversationRepository
import org.starfall.multigateway.shared.storage.McpRepository
import org.starfall.multigateway.shared.storage.ModelRepository
import org.starfall.multigateway.shared.storage.SpeechRepository
import org.starfall.multigateway.shared.storage.database.AppDatabase
import org.starfall.multigateway.shared.storage.preferences.AppPreferencesRepository
import org.starfall.multigateway.shared.storage.preferences.ToolSettingsStore
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = LibraryTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatViewModelSelectionTest {
    @Test fun startNewChatWinsOverAnInFlightConversationSelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val root = Files.createTempDirectory("selection-race").toFile()
        val files = ToolFiles(File(root, "tool_media"))
        val conversation = Conversation(
            id = "persisted",
            title = "Persisted",
            createdAt = 1,
            updatedAt = 1,
            messages = listOf(StoredMessage("message", ChatRole.USER, listOf(MessageVersion(content = "hello"))))
        )
        val conversationRepo = ConversationRepository(db)
        conversationRepo.saveConversation(conversation)
        val preferences = AppPreferencesRepository(context)
        preferences.setContinueLastConversation(false)
        val http = ToolHttp(files)
        val ai = AiService(context)
        val viewModel = ChatViewModel(
            conversationRepo = conversationRepo,
            modelRepo = ModelRepository(db, ai),
            mcpRepo = McpRepository(db, McpService(http)),
            prefsRepo = preferences,
            toolChat = ToolChatViewModel(http, McpService(http), ai),
            toolStore = ToolSettingsStore(context),
            speechRepo = SpeechRepository(db),
            ttsHelper = TtsHelper(context),
            speechSynthesis = SpeechSynthesisService(),
            speechAudioPlayer = SpeechAudioPlayer(context),
            toolFiles = files
        )
        try {
            val database = db.openHelper.writableDatabase
            database.beginTransaction()
            viewModel.selectConversation(conversation.id)
            // Start the history read while SQLite is blocked, then create the blank draft before
            // releasing it. The old implementation applied the queued result after this point.
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.startNewChat()
            database.setTransactionSuccessful()
            database.endTransaction()

            repeat(100) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }

            assertEquals("New Chat", viewModel.currentConversation.value?.title)
            assertNotEquals(conversation.id, viewModel.currentConversation.value?.id)
        } finally {
            db.close()
            root.deleteRecursively()
        }
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("ViewModel operation did not complete", condition())
    }
}
