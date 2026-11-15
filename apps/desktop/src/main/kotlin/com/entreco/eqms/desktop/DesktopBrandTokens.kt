package com.entreco.eqms.desktop

import com.entreco.eqms.feature.ui.BrandLogo
import com.entreco.eqms.feature.ui.BrandTokens
import java.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Maps the `data` of `qms project branding show --json` to tokens; rejects anything malformed. */
internal fun brandTokensFrom(data: JsonObject): BrandTokens {
    fun invalid(): Nothing = throw IOException("qms returned invalid branding data.")
    val logo = data["logo"] as? JsonObject ?: invalid()
    val brandLogo = when (logo.stringValue("kind")) {
        "generated" -> BrandLogo.Generated(logo.stringValue("seed") ?: invalid())
        "png" -> BrandLogo.Png(logo.stringValue("sha256") ?: invalid())
        else -> invalid()
    }
    val warnings = (data["warnings"] as? JsonArray ?: invalid()).map {
        (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: invalid()
    }
    return BrandTokens.parse(
        name = data.stringValue("name") ?: invalid(),
        accent = data.stringValue("accent") ?: invalid(),
        onAccent = data.stringValue("on_accent") ?: invalid(),
        logo = brandLogo,
        warnings = warnings,
    ).getOrElse { throw IOException("qms returned invalid branding data: ${it.message}") }
}
