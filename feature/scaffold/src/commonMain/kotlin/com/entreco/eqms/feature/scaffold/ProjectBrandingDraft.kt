package com.entreco.eqms.feature.scaffold

import com.entreco.eqms.feature.ui.BrandTokens

/** Branding chosen while creating a project; [accent] is `#RRGGBB` or `auto` (derive from the seed). */
data class BrandingDraft(
    val name: String,
    val seed: String = name,
    val accent: String = "auto",
    val logoFile: String? = null,
)

enum class GeneratedLogoShape { Circle, Square, Hexagon }

/** Rendering instructions computed by core for a generated logo. */
data class GeneratedLogo(val initials: String, val shape: GeneratedLogoShape)

/** What core would record for a [BrandingDraft]; nothing is written to disk. */
data class BrandingPreview(val tokens: BrandTokens, val generated: GeneratedLogo?)
