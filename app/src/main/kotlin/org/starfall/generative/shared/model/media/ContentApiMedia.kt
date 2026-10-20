package org.starfall.generative.shared.model.media

import kotlinx.serialization.json.JsonObject

data class ContentApiMedia(val url: String, val type: String)
data class PreparedContentApiResult(val content: JsonObject, val media: List<ContentApiMedia>)
data class ContentApiMediaResult(val content: JsonObject, val files: List<String>)
