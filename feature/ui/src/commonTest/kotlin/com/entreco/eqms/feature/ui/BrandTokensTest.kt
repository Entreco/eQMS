package com.entreco.eqms.feature.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandTokensTest {
    private val logo = BrandLogo.Generated("seed")

    @Test
    fun parsesValidTokensInEitherCaseAndKeepsWarningsVerbatim() {
        val tokens = BrandTokens.parse("Acme", "#c2410c", "#FFFFFF", logo, listOf("w1", "w2")).getOrThrow()
        assertEquals(Color(0xFFC2410C), tokens.accent)
        assertEquals(Color.White, tokens.onAccent)
        assertEquals(listOf("w1", "w2"), tokens.warnings)
        assertEquals(Color.Black, BrandTokens.parse("A", "#FFFF00", "#000000", logo).getOrThrow().onAccent)
    }

    @Test
    fun rejectsInvalidInput() {
        listOf("red", "#FFF", "#GG0000", "#12345678", "", " #112233").forEach {
            assertTrue(BrandTokens.parse("Acme", it, "#000000", logo).isFailure, it)
        }
        assertTrue(BrandTokens.parse("Acme", "#112233", "#123456", logo).isFailure)
        assertTrue(BrandTokens.parse(" ", "#112233", "#000000", logo).isFailure)
        assertTrue(BrandTokens.parse("Acme", "#112233", "#000000", BrandLogo.Generated("")).isFailure)
        assertTrue(BrandTokens.parse("Acme", "#112233", "#000000", BrandLogo.Png("abc")).isFailure)
        assertTrue(BrandTokens.parse("Acme", "#112233", "#000000", BrandLogo.Png("a".repeat(64))).isSuccess)
    }
}
