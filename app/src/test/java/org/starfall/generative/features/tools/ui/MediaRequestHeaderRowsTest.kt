package org.starfall.generative.features.tools.ui

import org.junit.Assert.*
import org.junit.Test

class MediaRequestHeaderRowsTest {
    @Test fun validHeadersAreMappedWithoutJsonEditor() {
        assertEquals(linkedMapOf("X-Trace" to "{{model}}", "Authorization" to "Bearer {{api_token}}"),
            headersFromRows(listOf(
                EditableHeader(1, "X-Trace", "{{model}}"),
                EditableHeader(2, "Authorization", "Bearer {{api_token}}"),
                EditableHeader(3, "", "")
            )))
    }

    @Test fun incompleteInvalidOrDuplicateHeadersNeverOverwriteSavedMap() {
        assertNull(headersFromRows(listOf(EditableHeader(1, "", "secret"))))
        assertNull(headersFromRows(listOf(EditableHeader(1, "Bad Name", "value"))))
        assertNull(headersFromRows(listOf(EditableHeader(1, "A", "line\nbreak"))))
        assertNull(headersFromRows(listOf(EditableHeader(1, "x-token", "one"), EditableHeader(2, "X-Token", "two"))))
        assertEquals(emptyMap<String, String>(), headersFromRows(emptyList()))
    }
}
