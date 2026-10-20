package org.starfall.multigateway.integrations.modelapis.antigravity

/** Canonical IDs and wire routes from vscode-unify-chat-provider's model resolver. */
internal object AntigravityModels {
    fun resolve(model: String, effort: String? = null): String {
        val id = model.trim().removePrefix("models/")
        if (id.contains("claude")) {
            val base = id.removeSuffix("-thinking")
            return if (base.contains("opus")) "$base-thinking" else base
        }
        if (id in listOf("gemini-3-flash-agent", "gemini-3.5-flash-extra-low", "gemini-3.5-flash-low")) return id
        val suffix = Regex("-(minimal|low|medium|high|xhigh|max)$").find(id)
        val base = suffix?.let { id.removeSuffix(it.value) } ?: id
        // The chosen level is never swapped for another one; levels without a known route are
        // requested as "<model>-<level>" so the endpoint answers for them.
        // These routes cannot express Off, so it behaves like the selector's Default.
        val level = effort?.takeUnless { it in listOf("none", "off") } ?: suffix?.groupValues?.get(1) ?: "high"
        return when (base) {
            "gemini-3.1-pro" -> when (level) {
                "low" -> "gemini-3.1-pro-low"
                "high" -> "gemini-pro-agent"
                else -> "$base-$level"
            }

            "gemini-3.5-flash" -> when (level) {
                "low" -> "gemini-3.5-flash-extra-low"
                "medium" -> "gemini-3.5-flash-low"
                "high" -> "gemini-3-flash-agent"
                else -> "$base-$level"
            }

            "gemini-3.6-flash", "gemini-3.7-flash" -> "$base-$level"
            else -> id
        }
    }
}
