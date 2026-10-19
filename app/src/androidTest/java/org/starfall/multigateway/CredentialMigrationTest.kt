package org.starfall.multigateway

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.starfall.multigateway.shared.storage.database.AppDatabase
import org.starfall.multigateway.shared.storage.database.SecretCipher
import org.starfall.multigateway.shared.storage.ConversationMigrationException
import org.starfall.multigateway.shared.storage.ConversationRepository
import org.starfall.multigateway.shared.model.*
import java.util.UUID

/** Run on Android: real Room schema validation and real Keystore, never a fake cipher. */
class CredentialMigrationTest {
    private val fixtureJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test fun v1UpgradePreservesRowsAndEncryptsCredentialsIdempotently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, messagesJson TEXT NOT NULL, tokenCount INTEGER, providerId TEXT NOT NULL, modelId TEXT NOT NULL, profileId TEXT)")
                    db.execSQL("CREATE TABLE chat_profiles (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, icon TEXT, configJson TEXT NOT NULL, activeMcpJson TEXT NOT NULL, activeModelToolsJson TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE ${"ll" + "m_providers"} (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, baseUrl TEXT NOT NULL, authJson TEXT NOT NULL, configJson TEXT NOT NULL, icon TEXT)")
                    db.execSQL("CREATE TABLE ${"ll" + "m_models"} (id TEXT NOT NULL PRIMARY KEY, modelsJson TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE mcp_servers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, protocol TEXT NOT NULL, url TEXT, headersJson TEXT)")
                    db.execSQL("INSERT INTO conversations VALUES ('c','keep',1,2,'[]',NULL,'p','m','profile')")
                    db.execSQL("INSERT INTO chat_profiles VALUES ('profile','keep',NULL,'{}','[]','[]')")
                    db.execSQL("INSERT INTO ${"ll" + "m_providers"} VALUES ('p','keep','OPENAI','https://example.com','legacy-secret','{}',NULL)")
                    db.execSQL("INSERT INTO mcp_servers VALUES ('s','keep','SSE',NULL,'legacy-header')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10
        ).build()
        try {
            val db = room.openHelper.writableDatabase // Room validates every migrated table.
            db.execSQL("INSERT INTO speech_services (id, name, provider, voice, speed, pitch, apiKey) VALUES ('speech','keep','p','voice',1.0,1.0,'speech-secret')")
            AppDatabase.encryptLegacySecrets(db)
            fun field(sql: String): String = db.query(sql).use { assertTrue(it.moveToFirst()); it.getString(0) }
            val encrypted = field("SELECT authJson FROM model_providers")
            assertNotEquals("legacy-secret", encrypted)
            assertEquals("legacy-secret", SecretCipher.decrypt(encrypted))
            assertEquals("legacy-header", SecretCipher.decrypt(field("SELECT headersJson FROM mcp_servers")))
            assertEquals("speech-secret", SecretCipher.decrypt(field("SELECT apiKey FROM speech_services")))
            assertEquals("keep", field("SELECT title FROM conversations"))
            assertEquals("keep", field("SELECT name FROM chat_profiles"))
            AppDatabase.encryptLegacySecrets(db)
            assertEquals(encrypted, field("SELECT authJson FROM model_providers"))
        } finally { room.close(); context.deleteDatabase(name) }
    }
    @Test fun corruptedEnvelopeFailsWithoutPlaintextFallback() {
        val encrypted = SecretCipher.encrypt("secret")
        val error = runCatching { SecretCipher.decrypt(encrypted.dropLast(8) + "AAAAAAAA") }.exceptionOrNull()
        assertNotNull(error)
    }

    @Test fun v9FixturePreservesRepresentativePayloadsForMigrationBaseline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "v9-fixture-${UUID.randomUUID()}"
        createV9Fixture(context, name)
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_9_10)
            .build()
        try {
            val repository = ConversationRepository(room)
            val list = repository.conversationList.first()
            assertEquals(listOf("rich", "malformed"), list.map { it.id })
            assertTrue(list.first().hasMessages)
            assertTrue(list.first().hasSummary)
            assertTrue(list.last().hasMessages)
            assertTrue(list.last().hasSummary)

            val rich = repository.loadById("rich") ?: error("v9 fixture conversation was not readable")
            assertEquals("Rich history", rich.title)
            assertEquals(20L, rich.updatedAt)
            assertEquals(123, rich.tokenCount)
            assertEquals("provider-rich", rich.providerId)
            assertEquals("catalog-model", rich.modelId)
            assertEquals("profile-rich", rich.profileId)
            assertEquals("high", rich.reasoningEffort)
            assertEquals(
                ConversationSummary("summary-1", "Important decisions", "assistant-1", SummaryRole.SYSTEM, 19),
                rich.summary
            )

            val user = rich.messages[0]
            assertEquals("user-1", user.id)
            assertEquals(ChatRole.USER, user.role)
            assertEquals(2, user.versions.size)
            assertEquals(1, user.activeVersionIndex)
            assertEquals(listOf("shared.txt", "image.png"), user.activeVersion.files)
            assertEquals("revised request", user.activeVersion.content)

            val assistant = rich.messages[1]
            assertEquals("assistant-1", assistant.id)
            assertEquals(2, assistant.versions.size)
            assertEquals("", assistant.versions[0].content)
            assertEquals("failed", assistant.versions[0].generationError)
            assertEquals("provider-rich", assistant.activeVersion.providerId)
            assertEquals("catalog-model", assistant.activeVersion.modelId)
            assertEquals("signature", assistant.activeVersion.reasoningSignature)
            assertEquals(18L, assistant.activeVersion.processingFinishedAt)
            assertEquals(listOf("shared.txt", "result.json"), assistant.activeVersion.files)
            val activity = assistant.activeVersion.toolActivity.single()
            assertEquals("activity-1", activity.id)
            assertEquals("Search", activity.name)
            assertEquals("search", activity.wireName)
            assertEquals("interrupted", activity.status)
            assertEquals("The previous run did not finish.", activity.summary)
            assertEquals("query", activity.arguments)
            assertEquals("full tool response", activity.response)
            assertEquals(42, activity.contentOffset)
            assertEquals(7, activity.reasoningOffset)
            assertTrue(activity.inlineMedia)
            assertEquals(80, activity.responsePreviewLimit)

            val provider = room.modelProviderDao().getProviderById("provider-rich")!!
            assertEquals("https://api.example.test/v1", SecretCipher.decrypt(provider.baseUrl))
            val providerAuth = fixtureJson.parseToJsonElement(SecretCipher.decrypt(provider.authJson)).jsonObject
            assertEquals("provider-secret", providerAuth["value"]!!.toString().trim('"'))
            val providerConfig = fixtureJson.parseToJsonElement(SecretCipher.decrypt(provider.configJson)).jsonObject
            assertEquals("catalog-model", providerConfig["modelIds"]!!.toString().trim('[', '"', ']'))

            val catalog = room.providerModelsDao().getModelsForProvider("provider-rich")!!
            val catalogJson = fixtureJson.parseToJsonElement(catalog.modelsJson).jsonArray.single().jsonObject
            assertEquals("catalog-model", catalogJson["id"]!!.toString().trim('"'))
            assertEquals("Catalog Model", catalogJson["display_name"]!!.toString().trim('"'))

            val profile = room.chatProfileDao().getProfileById("profile-rich")!!
            assertEquals("rich-profile", profile.name)
            assertEquals("default prompt", fixtureJson.parseToJsonElement(profile.configJson).jsonObject["system_prompt"]!!.toString().trim('"'))
            assertEquals("mcp-rich", fixtureJson.decodeFromString<List<String>>(profile.activeMcpJson).single())
            assertEquals("search", fixtureJson.decodeFromString<List<String>>(profile.activeModelToolsJson).single())

            val mcp = room.mcpServerDao().getServerById("mcp-rich")!!
            assertEquals("https://mcp.example.test", SecretCipher.decrypt(mcp.url!!))
            assertEquals("mcp-token", Json.parseToJsonElement(SecretCipher.decrypt(mcp.headersJson!!)).jsonObject["auth"]!!.jsonObject["value"]!!.toString().trim('"'))
            val tools = fixtureJson.decodeFromString<List<ToolDefinition>>(mcp.cachedToolsJson!!)
            assertEquals("search", tools.single().originalName)
            assertEquals(listOf("query"), tools.single().schema["required"]?.let { fixtureJson.decodeFromJsonElement<List<String>>(it) })
            assertEquals("string", tools.single().schema["properties"]!!.jsonObject["query"]!!.jsonObject["type"]!!.toString().trim('"'))

            val speech = room.speechServiceDao().getServiceById("speech-rich")!!
            assertEquals("speech-secret", SecretCipher.decrypt(speech.apiKey))
            assertEquals("en-US", Json.parseToJsonElement(SecretCipher.decrypt(speech.optionsJson!!)).jsonObject["languageCode"]!!.toString().trim('"'))

            // Keep the malformed source value visible to the future migration test. The current
            // repository intentionally has legacy empty-list fallback, but migration must not
            // mistake this value for a valid empty conversation or discard it silently.
            val malformedIssues = room.conversationDao().getMigrationIssues("malformed")
            assertEquals(setOf("invalid_json"), malformedIssues.map { it.errorCode }.toSet())
            assertEquals("not valid conversation json", malformedIssues.first { it.payloadKind == "messages_json" }.rawJson)
            assertEquals("{not a summary}", malformedIssues.first { it.payloadKind == "summary_json" }.rawJson)
            assertTrue(runCatching { repository.loadById("malformed") }.exceptionOrNull() is ConversationMigrationException)

            fun count(table: String): Long = room.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use {
                assertTrue(it.moveToFirst())
                it.getLong(0)
            }
            assertEquals(2L, count("messages"))
            assertEquals(4L, count("message_versions"))
            assertEquals(1L, count("tool_activities"))
            assertEquals(4L, count("message_version_files"))
            assertEquals(1L, count("tool_activity_files"))
            assertEquals(1L, count("conversation_summaries"))
            assertEquals(2L, count("conversation_migration_quarantine"))
            assertTrue(room.openHelper.writableDatabase.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf("index_message_version_files_reference")
            ).use { it.moveToFirst() })
            assertFalse(room.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }

    private fun createV9Fixture(context: Context, name: String) {
        val richMessages = listOf(
            StoredMessage(
                id = "user-1",
                role = ChatRole.USER,
                versions = listOf(
                    MessageVersion(content = "original request", timestamp = "2026-01-01T00:00:00Z"),
                    MessageVersion(
                        content = "revised request",
                        timestamp = "2026-01-01T00:01:00Z",
                        files = listOf("shared.txt", "image.png"),
                        reasoningContent = "user reasoning"
                    )
                ),
                activeVersionIndex = 1
            ),
            StoredMessage(
                id = "assistant-1",
                role = ChatRole.MODEL,
                versions = listOf(
                    MessageVersion(content = "[Error: failed]", timestamp = "2026-01-01T00:02:00Z"),
                    MessageVersion(
                        content = "current answer",
                        timestamp = "2026-01-01T00:03:00Z",
                        files = listOf("shared.txt", "result.json"),
                        toolActivity = listOf(
                            ToolActivity(
                                id = "activity-1",
                                name = "Search",
                                wireName = "search",
                                status = "running",
                                summary = "Found a result",
                                files = listOf("result.json"),
                                arguments = "query",
                                response = "full tool response",
                                contentOffset = 42,
                                reasoningOffset = 7,
                                inlineMedia = true,
                                responsePreviewLimit = 80
                            )
                        ),
                        reasoningContent = "private reasoning",
                        reasoningSignature = "signature",
                        processingFinishedAt = 18,
                        providerId = "provider-rich",
                        modelId = "catalog-model",
                        modelDisplayName = "Catalog Model"
                    )
                ),
                activeVersionIndex = 1
            )
        )
        val summary = ConversationSummary("summary-1", "Important decisions", "assistant-1", SummaryRole.SYSTEM, 19)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createV9Schema(db)
                        db.execSQL(
                            "INSERT INTO conversations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            sqlArgs("rich", "Rich history", 10, 20, fixtureJson.encodeToString(richMessages), 123, "provider-rich", "catalog-model", "profile-rich", fixtureJson.encodeToString(summary), "high")
                        )
                        db.execSQL(
                            "INSERT INTO conversations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            sqlArgs("malformed", "Needs recovery", 5, 10, "not valid conversation json", null, "", "", null, "{not a summary}", null)
                        )
                        db.execSQL("INSERT INTO chat_profiles VALUES (?, ?, ?, ?, ?, ?, ?)", sqlArgs("profile-rich", "rich-profile", "profile-icon", "{\"system_prompt\":\"default prompt\",\"mcpAccess\":{}}", "[\"mcp-rich\"]", "[\"search\"]", 2))
                        db.execSQL("INSERT INTO provider_groups VALUES (?, ?, ?, ?)", sqlArgs("group-rich", "Primary", 1, "group-icon"))
                        db.execSQL("INSERT INTO model_providers VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", sqlArgs("provider-rich", "Rich Provider", "OPENAI", SecretCipher.encrypt("https://api.example.test/v1"), SecretCipher.encrypt("{\"method\":\"BEARER_TOKEN\",\"key\":\"Authorization\",\"value\":\"provider-secret\"}"), SecretCipher.encrypt("{\"modelIds\":[\"catalog-model\"],\"headers\":{\"X-Test\":\"yes\"}}"), "provider-icon", 3, "group-rich"))
                        db.execSQL("INSERT INTO provider_models VALUES (?, ?)", sqlArgs("provider-rich", "[{\"id\":\"catalog-model\",\"display_name\":\"Catalog Model\",\"provider_id\":\"provider-rich\",\"input_capabilities\":{\"text\":true,\"image\":true},\"output_capabilities\":{\"text\":true},\"model_tools\":[\"search\"]}]"))
                        db.execSQL("INSERT INTO mcp_servers VALUES (?, ?, ?, ?, ?, ?, ?)", sqlArgs("mcp-rich", "Rich MCP", "STREAMABLE_HTTP", SecretCipher.encrypt("https://mcp.example.test"), SecretCipher.encrypt("{\"headers\":{\"X-Test\":\"yes\"},\"auth\":{\"method\":\"BEARER_TOKEN\",\"key\":\"Authorization\",\"value\":\"mcp-token\"},\"icon\":\"mcp-icon\"}"), "[{\"name\":\"search\",\"description\":\"Search tool\",\"schema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]},\"serverId\":\"mcp-rich\",\"originalName\":\"search\"}]", 4))
                        db.execSQL("INSERT INTO speech_services (id, name, provider, modelId, voice, speed, pitch, apiKey, sortOrder, optionsJson) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", sqlArgs("speech-rich", "Rich Speech", "provider-rich", null, "voice-1", 1.1f, 0.9f, SecretCipher.encrypt("speech-secret"), 5, SecretCipher.encrypt("{\"icon\":\"speech-icon\",\"instructions\":\"speak clearly\",\"responseFormat\":\"wav\",\"languageCode\":\"en-US\",\"extraBody\":{\"temperature\":0.2}}")))
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase
        helper.close()
    }

    private fun createV9Schema(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, messagesJson TEXT NOT NULL, tokenCount INTEGER, providerId TEXT NOT NULL, modelId TEXT NOT NULL, profileId TEXT, summaryJson TEXT, reasoningEffort TEXT)")
        db.execSQL("CREATE TABLE chat_profiles (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, icon TEXT, configJson TEXT NOT NULL, activeMcpJson TEXT NOT NULL, activeModelToolsJson TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE provider_groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, icon TEXT)")
        db.execSQL("CREATE TABLE model_providers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, baseUrl TEXT NOT NULL, authJson TEXT NOT NULL, configJson TEXT NOT NULL, icon TEXT, sortOrder INTEGER NOT NULL, groupId TEXT)")
        db.execSQL("CREATE TABLE provider_models (id TEXT NOT NULL PRIMARY KEY, modelsJson TEXT NOT NULL)")
        db.execSQL("CREATE TABLE mcp_servers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, protocol TEXT NOT NULL, url TEXT, headersJson TEXT, cachedToolsJson TEXT, sortOrder INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE speech_services (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, provider TEXT NOT NULL, modelId TEXT, voice TEXT NOT NULL, speed REAL NOT NULL, pitch REAL NOT NULL, apiKey TEXT NOT NULL, sortOrder INTEGER NOT NULL, optionsJson TEXT)")
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, '3b81f5d79afc80562e68b7b8c41fc3cc')")
    }

    private fun sqlArgs(vararg values: Any?): Array<Any?> {
        val result = arrayOfNulls<Any>(values.size)
        values.forEachIndexed { index, value -> result[index] = value }
        return result
    }
}
