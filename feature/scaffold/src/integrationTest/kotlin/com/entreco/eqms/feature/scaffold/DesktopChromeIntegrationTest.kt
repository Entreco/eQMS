package com.entreco.eqms.feature.scaffold

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import kotlin.test.*
import org.junit.Test

class DesktopChromeIntegrationTest : ScaffoldDocumentTestSupport(nativeIntegration = true) {
    private val projects = chromeTestProjects()

    @Test
    fun aboutIsOneNativeDecoratedNonModalWindowWithIndependentLifecycle() {
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.success(true), { Result.success(Unit) },
            { error("No check expected") }, { error("No browser expected") },
        )
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.menu(chrome, DesktopCommand.OpenProject)
        compose.onNodeWithText("Select directory").performClick()
        compose.onNodeWithText("Project verified and opened.").assertExists()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertIsDisplayed()
        fun aboutFrames() = java.awt.Window.getWindows().filterIsInstance<java.awt.Frame>()
            .filter { it.isShowing && it.title == "About DutchDoxx" }
        var about: java.awt.Frame? = null
        compose.runOnIdle {
            about = aboutFrames().single()
            assertFalse(about!!.isUndecorated)
            assertTrue(chrome.enabled)
            assertTrue(chrome.hasProject)
            assertTrue(java.awt.Window.getWindows().none { it.isShowing && it is java.awt.Dialog && it.isModal })
            chrome.request(DesktopCommand.About)
        }
        compose.runOnIdle { assertSame(about, aboutFrames().single()) }
        compose.onNodeWithText("Light theme").performClick()
        compose.onNodeWithText("Dark theme").assertExists()
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertIsDisplayed()
        compose.runOnIdle {
            about!!.extendedState = about!!.extendedState or java.awt.Frame.ICONIFIED
        }
        compose.waitUntil(3000) { about!!.extendedState and java.awt.Frame.ICONIFIED != 0 }
        compose.waitForIdle()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.waitUntil(3000) { about!!.extendedState and java.awt.Frame.ICONIFIED == 0 }
        compose.runOnIdle {
            assertSame(about, aboutFrames().single())
            about!!.dispatchEvent(java.awt.event.WindowEvent(about, java.awt.event.WindowEvent.WINDOW_CLOSING))
        }
        compose.waitUntil(3000) { !about!!.isDisplayable }
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertDoesNotExist()
        compose.onNodeWithText("Project verified and opened.").assertExists()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertIsDisplayed()
        compose.runOnIdle { assertNotSame(about, aboutFrames().single()) }
        compose.onNodeWithText("Close").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertTrue(aboutFrames().isEmpty()); assertTrue(chrome.hasProject) }
    }

    @Test
    fun invalidInitialPreferenceIsVisibleAndAboutShowsBuildVersion() {
        val chrome = DesktopChrome(
            "1.0.0-dev", Result.failure(IllegalArgumentException("invalid value")),
            { Result.success(Unit) }, { error("No check expected") }, { error("No browser expected") },
        )
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        compose.onNodeWithText("Could not load appearance: invalid value").assertExists()
        compose.runOnIdle { chrome.request(DesktopCommand.About) }
        compose.onNodeWithText("DutchDoxx 1.0.0-dev").assertExists()
    }
}
