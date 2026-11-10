package com.entreco.eqms.desktop

import java.util.UUID
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopPreferencesTest {
    private fun withNode(action: (Preferences, DesktopPreferences) -> Unit) {
        val node = Preferences.userRoot().node("com/entreco/dutchdoxx-test/${UUID.randomUUID()}")
        try {
            action(node, DesktopPreferences { node })
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun projectAndRecentsRoundTripAndCloseClearsOnlyCurrentProject() = withNode { _, preferences ->
        assertNull(preferences.readProject().getOrThrow())
        assertEquals(emptyList(), preferences.readRecents().getOrThrow())
        runBlocking { preferences.saveProjects("/a", listOf("/a", "/b")).getOrThrow() }
        assertEquals("/a", preferences.readProject().getOrThrow())
        assertEquals(listOf("/a", "/b"), preferences.readRecents().getOrThrow())
        runBlocking { preferences.saveProjects(null, listOf("/a", "/b")).getOrThrow() }
        assertNull(preferences.readProject().getOrThrow())
        assertEquals(listOf("/a", "/b"), preferences.readRecents().getOrThrow())
    }

    @Test
    fun recentsAreDistinctOrderedAndCappedAtFive() = withNode { _, preferences ->
        runBlocking {
            preferences.saveProjects("/1", listOf("/1", "/2", "/2", "/3", "/4", "/5", "/6", "/7")).getOrThrow()
        }
        assertEquals(listOf("/1", "/2", "/3", "/4", "/5"), preferences.readRecents().getOrThrow())
        runBlocking { preferences.saveProjects("/1", listOf("/1")).getOrThrow() }
        assertEquals(listOf("/1"), preferences.readRecents().getOrThrow())
    }

    @Test
    fun invalidStoredAndOversizedValuesFailExplicitly() = withNode { node, preferences ->
        node.put("project", " ")
        assertTrue(preferences.readProject().isFailure)
        node.put("recent0", "")
        assertTrue(preferences.readRecents().isFailure)
        val tooLong = "x".repeat(Preferences.MAX_VALUE_LENGTH + 1)
        assertTrue(runBlocking { preferences.saveProjects(tooLong, emptyList()) }.isFailure)
        assertTrue(runBlocking { preferences.saveProjects(null, listOf(tooLong)) }.isFailure)
    }

    @Test
    fun backingStoreFailureIsReturnedNotThrown() {
        val preferences = DesktopPreferences { throw BackingStoreException("unavailable") }
        assertTrue(preferences.readProject().isFailure)
        assertTrue(preferences.readRecents().isFailure)
        assertTrue(runBlocking { preferences.saveProjects("/a", emptyList()) }.isFailure)
    }
}
