package com.entreco.eqms.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.entreco.eqms.feature.scaffold.DesktopChrome
import java.awt.Taskbar
import javax.imageio.ImageIO

@Composable
internal fun DesktopLogo() {
    val artwork = painterResource("branding/about.jpg")
    Image(
        painter = artwork,
        contentDescription = "DutchDoxx logo",
        modifier = Modifier.fillMaxWidth().aspectRatio(artwork.intrinsicSize.width / artwork.intrinsicSize.height),
    )
}

internal fun installTaskbarIcon() {
    if (Taskbar.isTaskbarSupported()) {
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            val image = checkNotNull(DesktopChrome::class.java.getResourceAsStream("/branding/icon.png")) {
                "DutchDoxx launcher icon is missing."
            }.use { checkNotNull(ImageIO.read(it)) { "DutchDoxx launcher icon is invalid." } }
            taskbar.iconImage = image
        }
    }
}
