package com.entreco.eqms.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.entreco.eqms.desktop.generated.resources.Res
import com.entreco.eqms.desktop.generated.resources.icon
import com.entreco.eqms.feature.scaffold.DesktopChrome
import java.awt.Desktop
import kotlin.test.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.junit.Test

class DesktopNativeMenuTest {
    @Test
    fun realWindowInstallsMenuOrderAndNativeApplicationHandlers() {
        System.setProperty("apple.awt.application.name", "DutchDoxx")
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        val chrome = DesktopChrome(
            desktopVersion(), Result.success(true), { Result.success(Unit) },
            { error("No startup update check expected") }, { error("No browser expected") },
        )
        var failure: Throwable? = null
        application(exitProcessOnExit = false) {
            Window(onCloseRequest = ::exitApplication, title = "DutchDoxx", icon = painterResource(Res.drawable.icon)) {
                DesktopMenus(chrome, ::exitApplication)
                LaunchedEffect(Unit) {
                    try {
                        delay(300)
                        assertEquals("DutchDoxx", window.title)
                        assertTrue(window.iconImages.isNotEmpty())
                        val icon = window.iconImages.first()
                        assertTrue(icon.getWidth(null) > 0)
                        assertEquals(icon.getWidth(null), icon.getHeight(null))
                        installTaskbarIcon()
                        val menus = checkNotNull(window.menuBar)
                        val expected = if (isMacOS) listOf("File", "Edit", "View", "Help")
                            else listOf("Application", "File", "Edit", "View", "Help")
                        assertEquals(expected, (0 until menus.menuCount).map { menus.getMenu(it).label })
                        val file = menus.getMenu(if (isMacOS) 0 else 1)
                        assertEquals(listOf("New project", "Close project"), (0 until file.itemCount).map { file.getItem(it).label })
                        assertTrue(file.getItem(0).isEnabled)
                        assertFalse(file.getItem(1).isEnabled)
                        assertEquals(0, menus.getMenu(if (isMacOS) 1 else 2).itemCount)
                        assertEquals(0, menus.getMenu(if (isMacOS) 2 else 3).itemCount)
                        assertEquals("Check for updates", menus.getMenu(menus.menuCount - 1).getItem(0).label)
                        if (isMacOS) {
                            val desktop = Desktop.getDesktop()
                            assertTrue(desktop.isSupported(Desktop.Action.APP_ABOUT))
                            assertTrue(desktop.isSupported(Desktop.Action.APP_PREFERENCES))
                            assertTrue(desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER))
                        }
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        exitApplication()
                    }
                }
            }
        }
        failure?.let { throw it }
    }
}
