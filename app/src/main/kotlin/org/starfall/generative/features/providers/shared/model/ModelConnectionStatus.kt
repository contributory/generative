package org.starfall.generative.features.providers.shared.model

/** How one model connection probe ended, used for the row state and the editor status bar. */
internal enum class ModelConnectionStatus {
    /** The model answered the probe request with text. */
    PASSED,

    /** The endpoint answered but produced no content, so the connection works while the reply does not. */
    WARNING,

    /** The probe request itself failed. */
    FAILED
}

/** Progress and outcome counts for a run of connection probes over a set of models. */
internal data class ModelConnectionProgress(
    val tested: Int = 0,
    val total: Int = 0,
    val passed: Int = 0,
    val warnings: Int = 0,
    val failures: Int = 0
)
