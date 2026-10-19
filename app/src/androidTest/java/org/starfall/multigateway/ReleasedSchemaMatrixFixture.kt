package org.starfall.multigateway

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.starfall.multigateway.shared.model.ConversationSummary
import org.starfall.multigateway.shared.model.SummaryRole

/** Direct v1-v8 sources for the released-schema migration matrix. */
internal object ReleasedSchemaMatrixFixture {
    const val PROVIDER_ID = "matrix-provider"
    const val NO_AUTH_PROVIDER_ID = "matrix-provider-none"
    const val MCP_ID = "matrix-mcp"
    const val NO_AUTH_MCP_ID = "matrix-mcp-none"
    const val SPEECH_ID = "matrix-speech"
    const val NO_AUTH_SPEECH_ID = "matrix-speech-none"
    const val PROFILE_ID = "matrix-profile"
    const val CONVERSATION_ID = "matrix-conversation"
    const val BROKEN_CONVERSATION_ID = "matrix-broken"
    const val MODEL_ID = "matrix-model"

    private val json = Json { encodeDefaults = true }
    private const val messagesJson = """
        [{"id":"matrix-message","role":"model","versions":[{"content":"Matrix content","timestamp":"2026-01-01T00:00:00Z","files":["matrix.txt"],"provider_id":"matrix-provider","model_id":"matrix-model","model_display_name":"Matrix model"}],"active_version_index":0}]
    """
    private val summaryJson = json.encodeToString(
        ConversationSummary("matrix-summary", "Matrix summary", "matrix-message", SummaryRole.SYSTEM, 19)
    )
    private const val profileConfigJson = """
        {"system_prompt":"Matrix prompt","mcpAccess":{"matrix-mcp":{"enabled":true,"tools":{"search":true}},"matrix-mcp-none":{"enabled":false,"tools":{"read":false}}}}
    """
    private const val providerConfigJson = """
        {"supportStream":false,"responsesApi":true,"maxTokens":8192,"headers":{"X-Matrix":"provider-header"},"modelIds":["matrix-model"],"modelConfigs":{"matrix-model":{"displayName":"Matrix model","model_tools":["search"],"enabled_model_tools":["search"]}}}
    """
    private const val providerAuthJson = """
        {"method":"BEARER_TOKEN","key":"Authorization","value":"matrix-provider-token"}
    """
    private const val mcpConfigJson = """
        {"headers":{"X-Matrix":"mcp-header"},"auth":{"method":"BEARER_TOKEN","key":"Authorization","value":"matrix-mcp-token"}}
    """
    private const val mcpToolsJson = """
        [{"name":"Search","description":"Matrix tool","schema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]},"serverId":"matrix-mcp","originalName":"search"}]
    """
    private const val speechOptionsJson = """
        {"icon":"matrix-speech-icon","instructions":"Speak matrix","responseFormat":"wav","languageCode":"en-US","extraBody":{"temperature":0.2}}
    """

    fun create(context: Context, name: String, version: Int) {
        require(version in 1..8)
        val helper = openSource(context, name, version)
        try {
            val source = helper.writableDatabase
            check(source.version == version) { "Fixture opened at ${source.version}, expected $version" }
        } finally {
            helper.close()
        }
    }

    private fun openSource(context: Context, name: String, version: Int): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createSchema(db, version)
                        insertRows(db, version)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("Released v$version fixture was created through an upgrade")
                    }
                }).build()
        )

    private fun createSchema(db: SupportSQLiteDatabase, version: Int) {
        InstrumentationRegistry.getInstrumentation().context.assets.open("released-v$version-schema.sql")
            .bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("--") }.forEach { db.execSQL(it) }
            }
    }

    private fun insertRows(db: SupportSQLiteDatabase, version: Int) {
        val sortOrder = if (version >= 4) "sortOrder" else null
        val groupId = if (version >= 6) "groupId" else null
        if (version >= 6) {
            db.execSQL("INSERT INTO provider_groups VALUES (?, ?, ?, ?)", args("matrix-group", "Matrix group", 1, "group-icon"))
        }

        val conversationColumns = if (version >= 5) {
            "id, title, createdAt, updatedAt, messagesJson, tokenCount, providerId, modelId, profileId, summaryJson, reasoningEffort"
        } else {
            "id, title, createdAt, updatedAt, messagesJson, tokenCount, providerId, modelId, profileId"
        }
        val conversationValues = if (version >= 5) {
            args(CONVERSATION_ID, "Matrix conversation", 10, 20, messagesJson, 123, PROVIDER_ID, MODEL_ID,
                PROFILE_ID, summaryJson, "high")
        } else {
            args(CONVERSATION_ID, "Matrix conversation", 10, 20, messagesJson, 123, PROVIDER_ID, MODEL_ID, PROFILE_ID)
        }
        db.execSQL("INSERT INTO conversations ($conversationColumns) VALUES (${placeholders(conversationValues.size)})", conversationValues)
        val brokenValues = if (version >= 5) {
            args(BROKEN_CONVERSATION_ID, "Broken matrix conversation", 5, 10, "not valid conversation json", null,
                "", "", null, "{not a summary}", null)
        } else {
            args(BROKEN_CONVERSATION_ID, "Broken matrix conversation", 5, 10, "not valid conversation json", null, "", "", null)
        }
        db.execSQL("INSERT INTO conversations ($conversationColumns) VALUES (${placeholders(brokenValues.size)})", brokenValues)

        val profileColumns = listOf("id", "name", "icon", "configJson", "activeMcpJson", "activeModelToolsJson") +
            listOfNotNull(sortOrder)
        val profileValues = listOf<Any?>(PROFILE_ID, "Matrix profile", "profile-icon", profileConfigJson,
            "[\"matrix-mcp\",\"matrix-mcp-none\"]", "[\"search\"]") +
            listOfNotNull(if (version >= 4) 8 else null)
        db.execSQL("INSERT INTO chat_profiles (${profileColumns.joinToString()}) VALUES (${placeholders(profileValues.size)})", profileValues.toTypedArray())

        val providerColumns = listOf("id", "name", "type", "baseUrl", "authJson", "configJson", "icon") +
            listOfNotNull(sortOrder, groupId)
        insertProvider(db, providerColumns, listOf<Any?>(PROVIDER_ID, "Matrix provider", "OPENAI",
            "https://matrix-provider.example/v1?private=provider-$version#fragment", providerAuthJson, providerConfigJson,
            "provider-icon", 2.takeIf { version >= 4 }, "matrix-group".takeIf { version >= 6 }))
        insertProvider(db, providerColumns, listOf<Any?>(NO_AUTH_PROVIDER_ID, "Matrix public provider", "OPENAI",
            "https://matrix-public.example/v1", "{\"method\":\"NONE\"}", "{\"modelIds\":[]}", null,
            3.takeIf { version >= 4 }, null))

        db.execSQL("INSERT INTO llm_models (id, modelsJson) VALUES (?, ?)", args(PROVIDER_ID, """
            [{"id":"matrix-model","display_name":"Matrix catalog model","provider_id":"matrix-provider","input_capabilities":{"text":true},"output_capabilities":{"text":true},"model_tools":["search"]}]
        """.trimIndent()))

        val mcpColumns = listOf("id", "name", "protocol", "url", "headersJson") +
            (if (version >= 3) listOf("cachedToolsJson") else emptyList()) +
            (if (version >= 4) listOf("sortOrder") else emptyList())
        val mcpValues = listOf<Any?>(MCP_ID, "Matrix MCP", "SSE",
            "https://matrix-mcp.example/tools?private=mcp-$version#fragment", mcpConfigJson) +
            (if (version >= 3) listOf<Any?>(mcpToolsJson) else emptyList()) +
            (if (version >= 4) listOf<Any?>(4) else emptyList())
        db.execSQL("INSERT INTO mcp_servers (${mcpColumns.joinToString()}) VALUES (${placeholders(mcpValues.size)})", mcpValues.toTypedArray())
        val noAuthMcpValues = listOf<Any?>(NO_AUTH_MCP_ID, "Matrix public MCP", "STREAMABLE_HTTP",
            "https://matrix-public.example/mcp", null) +
            (if (version >= 3) listOf<Any?>(null) else emptyList()) +
            (if (version >= 4) listOf<Any?>(5) else emptyList())
        db.execSQL("INSERT INTO mcp_servers (${mcpColumns.joinToString()}) VALUES (${placeholders(noAuthMcpValues.size)})", noAuthMcpValues.toTypedArray())

        if (version >= 2) {
            val speechColumns = listOf("id", "name", "provider") +
                (if (version >= 3) listOf("modelId") else emptyList()) +
                listOf("voice", "speed", "pitch", "apiKey") +
                (if (version >= 4) listOf("sortOrder") else emptyList()) +
                (if (version >= 8) listOf("optionsJson") else emptyList())
            val speechValues = listOf<Any?>(SPEECH_ID, "Matrix speech", PROVIDER_ID) +
                (if (version >= 3) listOf<Any?>("matrix-model") else emptyList()) +
                listOf<Any?>("voice-1", 1.25f, 0.75f, "matrix-speech-token") +
                (if (version >= 4) listOf<Any?>(6) else emptyList()) +
                (if (version >= 8) listOf<Any?>(speechOptionsJson) else emptyList())
            db.execSQL("INSERT INTO speech_services (${speechColumns.joinToString()}) VALUES (${placeholders(speechValues.size)})", speechValues.toTypedArray())
            val noAuthSpeechValues = listOf<Any?>(NO_AUTH_SPEECH_ID, "Matrix system speech", "system") +
                (if (version >= 3) listOf<Any?>(null) else emptyList()) +
                listOf<Any?>("Default", 1f, 1f, "") +
                (if (version >= 4) listOf<Any?>(7) else emptyList()) +
                (if (version >= 8) listOf<Any?>(null) else emptyList())
            db.execSQL("INSERT INTO speech_services (${speechColumns.joinToString()}) VALUES (${placeholders(noAuthSpeechValues.size)})", noAuthSpeechValues.toTypedArray())
        }
    }

    private fun insertProvider(db: SupportSQLiteDatabase, columns: List<String>, values: List<Any?>) {
        val actual = values.take(columns.size)
        db.execSQL("INSERT INTO llm_providers (${columns.joinToString()}) VALUES (${placeholders(actual.size)})", actual.toTypedArray())
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString()

    private fun args(vararg values: Any?): Array<Any?> = values.toList().toTypedArray()
}
