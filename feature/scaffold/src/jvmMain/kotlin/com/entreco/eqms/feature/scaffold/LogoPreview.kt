package com.entreco.eqms.feature.scaffold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.jewel.ui.component.Text

/** Draws what core would record as the logo: the chosen PNG, or the generated shape with initials. */
@Composable
fun LogoPreview(preview: BrandingPreview, logoFile: String?, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val tokens = preview.tokens
    val png = remember(logoFile) {
        logoFile?.let {
            runCatching { org.jetbrains.skia.Image.makeFromEncoded(File(it).readBytes()).toComposeImageBitmap() }.getOrNull()
        }
    }
    val description = "Logo preview for ${tokens.name}"
    if (png != null) {
        Image(png, description, modifier.size(size))
        return
    }
    val generated = preview.generated ?: return
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            when (generated.shape) {
                GeneratedLogoShape.Circle -> drawCircle(tokens.accent)
                GeneratedLogoShape.Square -> drawRoundRect(tokens.accent, cornerRadius = CornerRadius(size.toPx() * 0.18f))
                GeneratedLogoShape.Hexagon -> drawPath(hexagon(this.size.width / 2f, Offset(this.size.width / 2f, this.size.height / 2f)), tokens.accent)
            }
        }
        Text(generated.initials, color = tokens.onAccent, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

private fun hexagon(radius: Float, center: Offset): Path = Path().apply {
    for (corner in 0 until 6) {
        val angle = PI / 3 * corner + PI / 6
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (corner == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
