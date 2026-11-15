package com.entreco.eqms.feature.scaffold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductIdentityTest {
    @Test
    fun fixedIdentity() {
        assertEquals("DutchDoxx", ProductIdentity.NAME)
        assertEquals("Powered by DutchDoxx", ProductIdentity.ATTRIBUTION)
        assertTrue(ProductIdentity.LICENCE_NOTICE.contains("not yet specified"))
    }

    @Test
    fun windowTitleAlwaysContainsProductName() {
        assertEquals("DutchDoxx", ProductIdentity.windowTitle(null))
        assertEquals("DutchDoxx", ProductIdentity.windowTitle("  \n\t "))
        assertEquals("Acme \u2014 DutchDoxx", ProductIdentity.windowTitle(" Acme "))
        assertEquals("Ab \u2014 DutchDoxx", ProductIdentity.windowTitle("A\u0000b"))
        val long = ProductIdentity.windowTitle("x".repeat(500))
        assertTrue(long.endsWith(" \u2014 DutchDoxx"))
        assertEquals(60 + " \u2014 DutchDoxx".length, long.length)
    }
}
