package org.starfall.multigateway.features.appdata.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.starfall.multigateway.shared.model.JsonExportKind
import org.starfall.multigateway.features.appdata.model.ProvidersExport
import org.starfall.multigateway.shared.model.Conversation
import org.starfall.multigateway.shared.model.McpInfo
import org.starfall.multigateway.shared.model.SpeechService
import org.starfall.multigateway.shared.model.provider.ModelProvider
import org.starfall.multigateway.shared.model.provider.ProviderGroup

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun buildJsonExport(
    kind: JsonExportKind,
    providers: List<ModelProvider>,
    mcpServers: List<McpInfo>,
    speechServices: List<SpeechService>,
    conversations: List<Conversation>,
    providerGroups: List<ProviderGroup> = emptyList()
): String {
    val items: JsonElement = when (kind) {
        JsonExportKind.PROVIDERS -> exportJson.parseToJsonElement(exportJson.encodeToString(providers))
        JsonExportKind.MCP_SERVERS -> exportJson.parseToJsonElement(exportJson.encodeToString(mcpServers))
        JsonExportKind.SPEECH_SERVICES -> exportJson.parseToJsonElement(exportJson.encodeToString(speechServices))
        JsonExportKind.CONVERSATIONS -> exportJson.parseToJsonElement(exportJson.encodeToString(conversations))
    }
    return exportJson.encodeToString(buildJsonObject {
        put("formatVersion", 1)
        put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
        put("type", kind.name.lowercase(Locale.US))
        put("items", items)
        if (kind == JsonExportKind.PROVIDERS) {
            put("groups", exportJson.parseToJsonElement(exportJson.encodeToString(providerGroups)))
            put("speechServices", exportJson.parseToJsonElement(exportJson.encodeToString(speechServices)))
        }
    })
}

private fun importedRoot(raw: String, kind: JsonExportKind) =
    exportJson.parseToJsonElement(raw).jsonObject.also { root ->
        require(root["formatVersion"]?.jsonPrimitive?.int == 1) {
            "Unsupported JSON export version."
        }
        require(root["type"]?.jsonPrimitive?.content == kind.name.lowercase(Locale.US)) {
            "This file is not a ${kind.name.lowercase(Locale.US)} export."
        }
        require("items" in root) { "The JSON export has no items." }
    }

private fun importedItems(raw: String, kind: JsonExportKind): JsonElement =
    importedRoot(raw, kind).getValue("items")

fun parseProvidersExport(raw: String): ProvidersExport {
    val root = importedRoot(raw, JsonExportKind.PROVIDERS)
    return ProvidersExport(
        providers = exportJson.decodeFromString(root.getValue("items").toString()),
        groups = root["groups"]?.let {
            exportJson.decodeFromString<List<ProviderGroup>>(it.toString())
        },
        speechServices = root["speechServices"]?.let {
            exportJson.decodeFromString<List<SpeechService>>(it.toString())
        }.orEmpty()
    )
}

fun parseMcpExport(raw: String): List<McpInfo> =
    exportJson.decodeFromString(importedItems(raw, JsonExportKind.MCP_SERVERS).toString())

fun parseSpeechServicesExport(raw: String): List<SpeechService> =
    exportJson.decodeFromString(importedItems(raw, JsonExportKind.SPEECH_SERVICES).toString())

fun parseConversationsExport(raw: String): List<Conversation> =
    exportJson.decodeFromString(importedItems(raw, JsonExportKind.CONVERSATIONS).toString())
