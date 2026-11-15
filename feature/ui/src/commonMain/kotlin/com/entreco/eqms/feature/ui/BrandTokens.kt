package com.entreco.eqms.feature.ui

import androidx.compose.ui.graphics.Color

/** Logo descriptor from core branding; drawing it is left to the project chrome. */
sealed interface BrandLogo {
    data class Generated(val seed: String) : BrandLogo
    data class Png(val sha256: String) : BrandLogo
}

/**
 * Project brand tokens as computed by the Rust core. [onAccent] and [warnings] come from core and are
 * deliberately not recomputed here.
 */
data class BrandTokens(
    val name: String,
    val accent: Color,
    val onAccent: Color,
    val logo: BrandLogo,
    val warnings: List<String> = emptyList(),
) {
    companion object {
        private val hex = Regex("#[0-9A-Fa-f]{6}")
        private val sha256 = Regex("[0-9a-f]{64}")

        fun parse(
            name: String,
            accent: String,
            onAccent: String,
            logo: BrandLogo,
            warnings: List<String> = emptyList(),
        ): Result<BrandTokens> = runCatching {
            require(name.isNotBlank()) { "Brand name is blank." }
            require(onAccent == "#000000" || onAccent == "#FFFFFF") { "Unsupported on-accent colour." }
            when (logo) {
                is BrandLogo.Generated -> require(logo.seed.isNotEmpty()) { "Generated logo has no seed." }
                is BrandLogo.Png -> require(sha256.matches(logo.sha256)) { "Logo hash is invalid." }
            }
            BrandTokens(name, parseColor(accent), parseColor(onAccent), logo, warnings)
        }

        private fun parseColor(value: String): Color {
            require(hex.matches(value)) { "Colour must be #RRGGBB." }
            return Color(0xFF000000L or value.substring(1).toLong(16))
        }
    }
}
