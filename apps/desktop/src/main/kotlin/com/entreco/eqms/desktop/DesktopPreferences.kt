package com.entreco.eqms.desktop

import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DesktopPreferences(
    private val node: () -> Preferences = { Preferences.userRoot().node("com/entreco/dutchdox") },
) {
    fun readDark(): Result<Boolean> = access {
        when (node().get("appearance", null)) {
            null, "dark" -> true
            "light" -> false
            else -> throw IllegalArgumentException("Invalid stored appearance.")
        }
    }

    suspend fun saveDark(dark: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        access {
            node().apply {
                put("appearance", if (dark) "dark" else "light")
                flush()
            }
            Unit
        }
    }

    private fun <T> access(action: () -> T): Result<T> = try {
        Result.success(action())
    } catch (error: BackingStoreException) {
        Result.failure(error)
    } catch (error: SecurityException) {
        Result.failure(error)
    } catch (error: IllegalArgumentException) {
        Result.failure(error)
    }
}
