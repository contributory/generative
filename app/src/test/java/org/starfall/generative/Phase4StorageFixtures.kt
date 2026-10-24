package org.starfall.generative

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.starfall.generative.shared.model.*
import org.starfall.generative.shared.model.api.ModelApiType
import org.starfall.generative.shared.model.auth.AuthMethod
import org.starfall.generative.shared.model.auth.Authorization
import org.starfall.generative.shared.model.language.*
import org.starfall.generative.shared.model.provider.*

/** Synthetic values only; these fixtures never open the application's database or token stores. */
internal object Phase4StorageFixtures {
    val nestedSchema = Json.parseToJsonElement("""{
        "type":"object",
        "properties":{"filters":{"type":"array","items":{"anyOf":[
            {"type":"string","enum":["001","true",""]},
            {"type":"object","properties":{"limit":{"type":"integer","default":3}}}
        ]}}},
        "required":["filters"],"additionalProperties":false,
        "examples":[{"filters":["001",3,true,null,[],{}]}],"empty":{}
    }""").jsonObject

    val extraBody = Json.parseToJsonElement("""{
        "temperature":0.25,"stream":false,"seed":42,"numericString":"001",
        "options":{"languages":["en-US","vi-VN"],"nullable":null,"empty":{},"list":[]},
        "segments":[{"text":"hello\nworld","weight":1.5},false,null]
    }""").jsonObject

    fun provider(id: String = "provider", groupId: String? = null) = ModelProvider(
        id = id, name = "Synthetic provider", type = ModelApiType.OPENAI,
        auth = Authorization(AuthMethod.BEARER_TOKEN, "Authorization", "fixture-provider-token"),
        baseUrl = "https://provider.example.test/v1?tenant=fixture#route", icon = "provider-icon",
        sortOrder = 2, groupId = groupId,
        config = ProviderConfiguration(
            headers = linkedMapOf("X-Fixture" to "header-value", "X-Other" to "second"),
            httpProxy = mapOf("url" to "http://proxy.example.test:8080"),
            socksProxy = mapOf("url" to "socks5://proxy.example.test:1080"),
            supportStream = false, responsesApi = true, maxTokens = 8192,
            customListModelsUrl = "https://provider.example.test/models?key=fixture",
            modelIds = listOf("second", "custom", "first"),
            modelConfigs = linkedMapOf(
                "second" to ModelConfiguration(
                    temperature = 0.25, topP = 0.75, topK = 8, supportStream = false,
                    displayName = "Second", contextWindowTokens = 64000,
                    supportsVision = false, supportsVideoInput = true, supportsAudioInput = true,
                    supportsThinking = false, reasoningEffort = "high", thinkingLevel = "medium",
                    reasoningEfforts = listOf("high", "low"), supportsToolCalls = false,
                    modelTools = listOf("search", "code"), enabledModelTools = listOf("code"),
                    thinkingPassback = ThinkingPassback.OFF,
                    modelJson = nestedSchema, icon = "model-icon"
                ),
                "first" to ModelConfiguration(displayName = "First", supportStream = null,
                    modelType = ModelType.EMBEDDING, reasoningEfforts = emptyList(), modelTools = listOf("browse"))
            ),
            multipleApiKeys = true,
            apiKeys = listOf(ProviderApiKey("key-1", "Primary", "fixture-provider-token"),
                ProviderApiKey("key-2", "Backup", "fixture-backup-token")),
            oauthAccounts = listOf(ProviderOAuthAccount("account-1", "Fixture", "fixture@example.test", "fixture-marker")),
            codexClientVersion = "0.158.0",
            antigravityVersion = "2.25.0",
            antigravityChangelist = "123456789",
            antigravityProjectIds = linkedMapOf("account-1" to "project-fixture", "account-2" to "project-backup")
        )
    )

    fun server(id: String = "mcp") = McpInfo(
        id, "Synthetic MCP", McpProtocol.SSE,
        url = "https://mcp.example.test/tools?key=fixture#transport",
        headers = mapOf("X-Fixture" to "mcp-header"),
        auth = McpAuthorization(McpAuthMethod.BEARER_TOKEN, "Authorization", "fixture-mcp-token"),
        cachedTools = listOf(
            ToolDefinition("Search", "Nested fixture schema", nestedSchema, id, "search"),
            ToolDefinition("Empty", "Empty schema", Json.parseToJsonElement("{}").jsonObject, id, "empty")
        ),
        sortOrder = 3, icon = "mcp-icon"
    )

    fun speech() = SpeechService(
        "speech", "Synthetic speech", icon = "speech-icon", provider = "provider", modelId = "tts-model",
        voice = "voice-1", speed = 1.25f, pitch = 0.75f, apiKey = "fixture-speech-token", sortOrder = 4,
        instructions = "Speak clearly", responseFormat = "wav", languageCode = "vi-VN", extraBody = extraBody
    )

    fun profile() = ChatProfile(
        "profile", "Synthetic profile", "profile-icon", sortOrder = 5,
        config = AiChatConfig("Keep the exact prompt.\nSecond line.", linkedMapOf(
            "mcp-b" to McpAccess(true, linkedMapOf("search" to true, "delete" to false)),
            "mcp-a" to McpAccess(false, linkedMapOf("read" to false, "write" to true))
        )),
        activeMcp = listOf("mcp-b", "mcp-a"), activeModelTools = listOf("code", "search")
    )
}
