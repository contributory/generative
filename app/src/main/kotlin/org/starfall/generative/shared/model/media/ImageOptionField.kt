package org.starfall.generative.shared.model.media

data class ImageOptionField(
    val path: String, val label: String, val kind: String = "string",
    val choices: List<String> = emptyList(), val min: Double? = null, val max: Double? = null,
    /** String resource for [label]; 0 keeps the provider-supplied label. */
    val labelRes: Int = 0
)
