package org.starfall.multigateway.features.providers.shared.logic

import androidx.compose.ui.unit.IntSize

internal fun IntSize.isEmpty() = width <= 0 || height <= 0
