package org.starfall.generative

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.starfall.generative.shared.logging.DebugLog
import org.starfall.generative.shared.model.logging.DebugLogCategory
import org.starfall.generative.shared.ui.logging.DebugLogOverlay

class DebugLogOverlayTest {
    @get:Rule val compose = createComposeRule()

    @After fun clearLogs() = DebugLog.configure(false, emptySet())

    @Test fun requestExpandsAndOutsideTapClosesPanel() {
        DebugLog.configure(true, DebugLogCategory.all)
        val id = DebugLog.beginRequest("HTTP", "POST https://example.test") { "request body" }
        DebugLog.finishRequest(id, "HTTP", "← 200 POST https://example.test", false) { "response body" }
        compose.setContent { MaterialTheme { DebugLogOverlay() } }
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithContentDescription(context.getString(R.string.debug_log_toggle)).performClick()
        compose.onNodeWithText("request body", substring = true).assertDoesNotExist()
        compose.onNodeWithText("← 200 POST https://example.test", substring = true).performClick()
        compose.onNodeWithText("request body", substring = true).assertExists()
        compose.onRoot().performTouchInput { click(Offset(5f, 5f)) }
        compose.onNodeWithText(context.getString(R.string.debug_log_title), substring = true).assertDoesNotExist()
    }
}
