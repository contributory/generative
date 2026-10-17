package org.starfall.multigateway.shared.ui.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Masks text with bullets for display only. Unlike PasswordVisualTransformation it does not mark
 * the field as a password, so the keyboard does not offer password-manager suggestions for it.
 */
object ApiKeyMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString("\u2022".repeat(text.length)), OffsetMapping.Identity)
}
