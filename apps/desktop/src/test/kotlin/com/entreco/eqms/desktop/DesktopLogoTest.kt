package com.entreco.eqms.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.toAwtImage
import com.entreco.eqms.feature.scaffold.*
import javax.imageio.ImageIO
import kotlin.test.*
import org.junit.Rule
import org.junit.Test

class DesktopLogoTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun actualAboutDialogShowsAccessibleLogoAndVersionInBothThemes() {
        val chrome = DesktopChrome(
            desktopVersion(), Result.success(true), { Result.success(Unit) },
            { error("No update request expected") }, { error("No browser expected") },
            aboutLogo = { DesktopLogo() },
        )
        val projects = object : ProjectCapabilities {
            override suspend fun listDirectory(path: String?): Result<DirectoryListing> = error("No directory request expected")
            override suspend fun createFolder(parent: String, name: String): Result<String> = error("No creation expected")
            override suspend fun openProject(path: String, create: Boolean): Result<String> = error("No project request expected")
        }
        compose.setContent { ProjectScreen(projects, chrome = chrome) }
        repeat(2) { theme ->
            compose.runOnIdle { chrome.request(DesktopCommand.About) }
            compose.onNodeWithContentDescription("DutchDoxx logo").assertIsDisplayed()
            compose.onNodeWithText("DutchDoxx ${desktopVersion()}").assertIsDisplayed()
            val pixels = compose.onNodeWithContentDescription("DutchDoxx logo").captureToImage().toAwtImage()
            assertTrue(pixels.width > 0 && pixels.height > 0)
            val colors = (0 until pixels.width).flatMap { x ->
                (0 until pixels.height).map { y -> pixels.getRGB(x, y) and 0xffffff }
            }
            assertTrue(colors.any { color -> (color shr 16 and 255) > 180 && (color shr 8 and 255) in 60..140 && (color and 255) < 80 })
            compose.onNodeWithText("Close").performClick()
            if (theme == 0) compose.onNodeWithText("Light theme").performClick()
        }
    }

    @Test
    fun packagedPngIsSquareTransparentAndContainsAllLogoLayers() {
        val image = checkNotNull(javaClass.getResourceAsStream("/branding/icon.png")).use {
            checkNotNull(ImageIO.read(it))
        }
        assertEquals(1024, image.width)
        assertEquals(1024, image.height)
        assertEquals(0, image.getRGB(0, 0) ushr 24)
        for (rgb in listOf(0xEE611C, 0x12457B, 0xB9B8B9)) {
            assertTrue((0 until image.width).any { x ->
                (0 until image.height).any { y -> image.getRGB(x, y) == (0xff000000.toInt() or rgb) }
            }, "Missing SVG layer color: $rgb")
        }
    }
}
