package org.starfall.multigateway.shared.model.api

import kotlinx.serialization.SerialName

enum class ModelApiType(val displayName: String, val defaultName: String, val defaultBaseUrl: String) {
    @SerialName("openai")
    OPENAI("Chat Completions", "", ""),
    @SerialName("openai_responses")
    OPENAI_RESPONSES("OpenAI Responses", "OpenAI Responses", "https://api.openai.com/v1"),
    @SerialName("anthropic")
    ANTHROPIC("Anthropic Messages", "Anthropic", "https://api.anthropic.com/v1"),
    @SerialName("google")
    GOOGLE("Google Gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    @SerialName("ollama")
    OLLAMA("Ollama Cloud", "Ollama Cloud", "https://ollama.com/api"),
    @SerialName("antigravity")
    ANTIGRAVITY("Antigravity", "Antigravity", "https://daily-cloudcode-pa.sandbox.googleapis.com"),
    @SerialName("github_copilot")
    GITHUB_COPILOT("GitHub Copilot", "GitHub Copilot", "https://api.githubcopilot.com"),
    @SerialName("openai_codex")
    OPENAI_CODEX("OpenAI Codex", "OpenAI Codex", "https://chatgpt.com/backend-api/codex/responses"),
    @SerialName("claude_code")
    CLAUDE_CODE("Claude Code", "Claude Code", "https://api.anthropic.com/v1");

    val isAccountProvider: Boolean get() = this in setOf(OPENAI_CODEX, CLAUDE_CODE, ANTIGRAVITY, GITHUB_COPILOT)

    val isOpenAi: Boolean get() = this == OPENAI || this == OPENAI_RESPONSES
}
