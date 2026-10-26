package com.entreco.eqms.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import com.entreco.eqms.feature.scaffold.*
import com.entreco.eqms.desktop.generated.resources.Res
import com.entreco.eqms.desktop.generated.resources.about
import com.entreco.eqms.desktop.generated.resources.icon
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlinx.coroutines.runBlocking
import javax.imageio.ImageIO
import kotlin.test.*
import org.junit.Rule
import org.junit.Test

class DesktopLogoTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun generatedDrawablesMatchCanonicalBytesAndDecodeWithOriginalDimensions() {
        lateinit var about: ImageBitmap
        lateinit var icon: ImageBitmap
        compose.setContent {
            about = imageResource(Res.drawable.about)
            icon = imageResource(Res.drawable.icon)
        }
        compose.runOnIdle {
            for ((name, drawable, bitmap) in listOf(
                Triple("about.jpg", Res.drawable.about, about),
                Triple("icon.png", Res.drawable.icon, icon),
            )) {
                val canonicalBytes = checkNotNull(javaClass.getResourceAsStream("/branding/$name")).use {
                    it.readBytes()
                }
                val generatedBytes = runBlocking {
                    getDrawableResourceBytes(getSystemResourceEnvironment(), drawable)
                }
                assertContentEquals(canonicalBytes, generatedBytes)
                val canonical = checkNotNull(ImageIO.read(canonicalBytes.inputStream()))
                val generated = bitmap.toAwtImage()
                assertEquals(canonical.width, generated.width)
                assertEquals(canonical.height, generated.height)
            }
            assertEquals(0, icon.toAwtImage().getRGB(0, 0) ushr 24)
        }
    }

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
            compose.onNodeWithText("An eQMS quality workspace. Drafts are not approvals.").assertIsDisplayed()
            compose.onNodeWithText("Close").performSemanticsAction(SemanticsActions.OnClick) { it() }
            if (theme == 0) compose.onNodeWithText("Light theme").performClick()
        }
    }

    @Test
    fun fullArtworkRendersWithoutDistortion() {
        compose.setContent {
            org.jetbrains.jewel.intui.standalone.theme.IntUiTheme(isDark = true) { DesktopLogo() }
        }
        val pixels = compose.onNodeWithContentDescription("DutchDoxx logo").captureToImage().toAwtImage()
        assertEquals(564.0 / 165, pixels.width.toDouble() / pixels.height, 0.03)
        assertTrue((0 until pixels.width).any { x ->
            (0 until pixels.height).any { y ->
                val color = pixels.getRGB(x, y)
                (color shr 16 and 255) > 180 && (color shr 8 and 255) in 60..140 && (color and 255) < 80
            }
        })
    }

    @Test
    fun fullAboutArtworkIsPackagedIndependentlyOfLauncherIcon() {
        val artwork = checkNotNull(javaClass.getResourceAsStream("/branding/about.jpg")).use {
            checkNotNull(ImageIO.read(it))
        }
        assertEquals(564, artwork.width)
        assertEquals(165, artwork.height)
    }

    @Test
    fun packagedLauncherRetainsDarkTileAndRoundedCorners() {
        val image = checkNotNull(javaClass.getResourceAsStream("/branding/icon.png")).use {
            checkNotNull(ImageIO.read(it))
        }
        assertEquals(1024, image.width)
        assertEquals(1024, image.height)
        assertEquals(0, image.getRGB(0, 0) ushr 24)
        val source = checkNotNull(javaClass.getResourceAsStream("/branding/logo.png")).use {
            checkNotNull(ImageIO.read(it))
        }
        assertEquals(164, source.width)
        assertEquals(165, source.height)
        for ((x, y) in listOf(512 to 150, 512 to 850, 150 to 512, 850 to 512)) {
            val pixel = image.getRGB(x, y)
            assertEquals(255, pixel ushr 24, "Tile background must stay opaque")
            assertTrue((pixel shr 16 and 255) < 50 && (pixel shr 8 and 255) < 50 && (pixel and 255) < 70)
        }
    }
}
