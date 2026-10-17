package org.starfall.multigateway.features.appdata.model

/** Identifies the data collection used by JSON import and export actions. */
enum class JsonExportKind(val fileName: String) {
    PROVIDERS("multigateway-providers.json"),
    MCP_SERVERS("multigateway-mcp.json"),
    SPEECH_SERVICES("multigateway-speech-services.json"),
    CONVERSATIONS("multigateway-chats.json")
}
