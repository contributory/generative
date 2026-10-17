package org.starfall.multigateway.shared.model.language

import kotlinx.serialization.Serializable

@Serializable
enum class ModelType(val displayName: String) {
    TEXT_GENERATION("Text Generation"),
    IMAGE_GENERATION("Image Generation"),
    VIDEO_GENERATION("Video Generation"),
    SPEECH_TO_TEXT("Speech to Text"),
    TEXT_TO_SPEECH("Text to Speech"),
    EMBEDDING("Embedding"),
    RERANKING("Reranking"),
    REALTIME("Realtime")
}
