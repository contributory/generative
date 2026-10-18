package org.starfall.multigateway.shared.model.media

data class ImageOptionField(
    val path: String, val label: String, val kind: String = "string",
    val choices: List<String> = emptyList(), val min: Double? = null, val max: Double? = null
)
