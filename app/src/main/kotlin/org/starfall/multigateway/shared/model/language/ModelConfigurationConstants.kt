package org.starfall.multigateway.shared.model.language

/** Every level a model can be marked as supporting, lowest first. */
val SELECTABLE_REASONING_EFFORTS = listOf("minimal", "low", "medium", "high", "xhigh", "max")
val DEFAULT_REASONING_EFFORTS = listOf("low", "medium", "high")
/** Thinking is on, but the model has no levels: nothing is sent to pick one. */
const val REASONING_EFFORT_ACTIVE = "active"

const val ACTIVE_THINKING_BUDGET = 2048
const val DEFAULT_CONTEXT_WINDOW_TOKENS = 128_000
