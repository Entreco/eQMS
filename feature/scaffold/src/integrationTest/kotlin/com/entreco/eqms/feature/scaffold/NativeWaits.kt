package com.entreco.eqms.feature.scaffold

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Waits until the node is laid out inside the window; a freshly opened native window can exist before it is displayed. */
internal fun ComposeTestRule.waitUntilDisplayed(matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis) {
        runCatching { onNode(matcher).assertIsDisplayed() }.isSuccess
    }
}
