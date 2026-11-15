package com.entreco.eqms.feature.scaffold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** About body. Takes only the build version and logo; there is no input for project branding. */
@Composable
internal fun AboutContent(version: String, logo: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        logo()
        Text(ProductIdentity.NAME, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${ProductIdentity.NAME} $version")
        Text(ProductIdentity.DESCRIPTION)
        Text("Author: ${ProductIdentity.AUTHOR}")
        Text(ProductIdentity.LICENCE_NOTICE, color = JewelTheme.globalColors.text.info)
        Text(ProductIdentity.ATTRIBUTION, color = JewelTheme.globalColors.text.info)
    }
}

/** Non-configurable attribution mark for the status footer. */
@Composable
internal fun ProductAttribution(modifier: Modifier = Modifier) {
    Text(ProductIdentity.ATTRIBUTION, modifier, color = JewelTheme.globalColors.text.info)
}
