package org.starfall.generative

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.starfall.generative.shared.storage.database.SecretCipher

/** A released v9 schema built independently of either migration, using only synthetic source rows. */
internal object CredentialV9Fixture {
    const val PROVIDER_URL = "https://provider.example.test/v1?tenant=fixture#route"
    const val MCP_URL = "https://mcp.example.test/tools?key=fixture"

    val schema = Json.parseToJsonElement("""{
        "type":"object","properties":{"filters":{"type":"array","items":{"anyOf":[
            {"type":"string"},{"type":"object","properties":{"limit":{"type":"integer","default":3}}}
        ]}}},"required":["filters"],"additionalProperties":false,
        "examples":[{"filters":["001",3,true,null,[],{}]}]
    }""").jsonObject

    val extraBody = Json.parseToJsonElement("""{
        "temperature":0.2,"stream":false,"numericString":"001",
        "options":{"languages":["en-US","vi-VN"],"nullable":null,"empty":{},"list":[]}
    }""").jsonObject

    val profileConfig = """{
        "system_prompt":"Exact prompt.\nSecond line.","mcpAccess":{
            "mcp-v9":{"enabled":true,"tools":{"search":true,"delete":false}},
            "mcp-none":{"enabled":false,"tools":{"read":false}}
        },"futureSetting":{"ordered":["001",null,{},[]]}
    }"""

    val messagesJson = """[{"id":"message-v9","role":"model","versions":[{
        "content":"Legacy JSON history","timestamp":"2026-01-01T00:00:00Z","files":["fixture.txt"],
        "provider_id":"provider-v9","model_id":"z-model","model_display_name":"Z model"
    }],"active_version_index":0}]"""

    fun createSchema(db: SupportSQLiteDatabase) {
        InstrumentationRegistry.getInstrumentation().context.assets.open("phase4-v9-schema.sql")
            .bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("--") }.forEach { db.execSQL(it) }
            }
    }

    fun openSource(context: Context, name: String): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createSchema(db)
                        insertRows(db)
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        error("The fixture must be created directly at v9")
                }).build()
        )

    fun snapshot(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> {
        fun rows(sql: String) = db.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
                    if (cursor.isNull(it)) null else cursor.getString(it)
                })
            }
        }
        val tables = listOf("conversations", "chat_profiles", "provider_groups", "model_providers",
            "provider_models", "mcp_servers", "speech_services", "room_master_table")
        return tables.associateWith { rows("SELECT * FROM $it ORDER BY rowid") } +
            ("schema" to rows("SELECT type, name, tbl_name, sql FROM sqlite_master ORDER BY type, name"))
    }

    fun create(context: Context, name: String) {
        val helper = openSource(context, name)
        try {
            val source = helper.writableDatabase
            assertEquals(9, source.version)
            // Both history and configuration must be legacy JSON, with no intermediate tables.
            source.query("SELECT authJson, configJson FROM model_providers WHERE id = 'provider-v9'").use {
                check(it.moveToFirst())
                check(SecretCipher.isEncrypted(it.getString(0)))
                check(SecretCipher.isEncrypted(it.getString(1)))
            }
            source.query("SELECT messagesJson, summaryJson FROM conversations").use {
                check(it.moveToFirst())
                assertEquals(messagesJson, it.getString(0))
                check(it.isNull(1))
            }
            source.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('messages', 'message_versions', 'provider_auth')").use {
                check(!it.moveToFirst())
            }
        } finally { helper.close() }
    }

    fun addMalformedConversation(context: Context, name: String) {
        openSource(context, name).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO conversations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("matrix-broken", "Broken matrix conversation", 0, 0,
                    "not valid conversation json", null, "", "", null, "{not a summary}", null)
            )
        }
    }

    private fun insertRows(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO provider_groups VALUES (?, ?, ?, ?)",
            arrayOf<Any?>("group-v9", "Fixture group", 1, "group-icon"))
        db.execSQL("INSERT INTO model_providers VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
            "provider-v9", "Fixture provider", "OPENAI", SecretCipher.encrypt(PROVIDER_URL),
            SecretCipher.encrypt("""{"method":"BEARER_TOKEN","key":"Authorization","value":"v9-provider-token"}"""),
            SecretCipher.encrypt("""{
                "supportStream":false,"responsesApi":true,"maxTokens":8192,"multipleApiKeys":true,
                "headers":{"X-Fixture":"provider-header"},"httpProxy":{"url":"http://proxy.example.test"},
                "socksProxy":{"url":"socks5://proxy.example.test"},"customListModelsUrl":"https://provider.example.test/models",
                "modelIds":["z-model","custom","a-model"],"modelConfigs":{
                    "z-model":{"temperature":0.25,"top_p":0.75,"top_k":8,"supportStream":false,
                        "displayName":"Z model","contextWindowTokens":64000,"supportsVision":false,
                        "supportsVideoInput":true,"supportsAudioInput":true,"supportsThinking":false,
                        "reasoning_effort":"high","reasoning_efforts":["high","low"],"thinking_level":"medium",
                        "model_tools":["search","code"],"enabled_model_tools":["code"],"thinking_passback":"off","model_json":$schema},
                    "a-model":{"displayName":"A model","modelType":"EMBEDDING","reasoning_efforts":[]}
                },"apiKeys":[{"id":"key-1","label":"Primary","value":"v9-provider-token"},
                    {"id":"key-2","label":"Backup","value":"v9-backup-token"}],
                "oauthAccounts":[{"id":"account-1","label":"Fixture","identity":"fixture@example.test","marker":"fixture-marker"}]
            }"""), "provider-icon", 2, "group-v9"
        ))
        db.execSQL("INSERT INTO model_providers VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
            "provider-none", "No auth", "OPENAI", "https://public.example.test", """{"method":"NONE"}""",
            """{"modelIds":[]}""", null, 3, null
        ))
        db.execSQL("INSERT INTO provider_models VALUES (?, ?)", arrayOf<Any?>("provider-v9", """[
            {"id":"z-model","display_name":"Catalog Z","icon":"catalog-icon","provider_id":"remote-provider",
                "input_capabilities":{"text":false,"image":true,"video":true,"audio":true,"others":"input-extra"},
                "output_capabilities":{"text":true,"image":false,"embed":true,"others":"output-extra"},"model_tools":["code","search"]},
            {"id":"a-model","display_name":"Catalog A","provider_id":"provider-v9"}
        ]"""))
        db.execSQL("INSERT INTO chat_profiles VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
            "profile-v9", "Fixture profile", "profile-icon", SecretCipher.encrypt(profileConfig),
            """["mcp-v9","mcp-none"]""", """["code","search"]""", 4
        ))
        db.execSQL("INSERT INTO mcp_servers VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
            "mcp-v9", "Fixture MCP", "SSE", SecretCipher.encrypt(MCP_URL),
            SecretCipher.encrypt("""{"headers":{"X-Fixture":"mcp-header"},"auth":{
                "method":"BEARER_TOKEN","key":"Authorization","value":"v9-mcp-token"},"icon":"mcp-icon"}"""),
            """[{"name":"Search","description":"Nested schema","schema":$schema,"serverId":"mcp-v9","originalName":"search"}]""", 5
        ))
        db.execSQL("INSERT INTO mcp_servers VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
            "mcp-none", "No auth MCP", "STREAMABLE_HTTP", "https://public.example.test/mcp", null, null, 6
        ))
        db.execSQL("INSERT INTO speech_services VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
            "speech-v9", "Fixture speech", "provider-v9", "tts", "voice-1", 1.25f, 0.75f,
            SecretCipher.encrypt("v9-speech-token"), 7, SecretCipher.encrypt("""{
                "icon":"speech-icon","instructions":"Speak clearly","responseFormat":"wav","languageCode":"vi-VN","extraBody":$extraBody
            }""")
        ))
        // v9 explicitly allowed NULL optionsJson. These must receive the original SpeechService defaults.
        db.execSQL("INSERT INTO speech_services VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
            "speech-defaults", "System voice", "system", null, "Default", 1f, 1f, "", 8, null
        ))
        db.execSQL("INSERT INTO conversations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(
            "conversation-v9", "Fixture conversation", 1, 2, messagesJson, 10, "provider-v9", "z-model", "profile-v9", null, "high"
        ))
    }
}
