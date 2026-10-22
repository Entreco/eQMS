package com.entreco.eqms.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.entreco.eqms.feature.scaffold.DesktopChrome
import java.awt.Taskbar
import javax.imageio.ImageIO

@Composable
internal fun DesktopLogo() {
    Image(
        painter = painterResource("branding/icon.png"),
        contentDescription = "DutchDoxx logo",
        modifier = Modifier.size(112.dp),
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
