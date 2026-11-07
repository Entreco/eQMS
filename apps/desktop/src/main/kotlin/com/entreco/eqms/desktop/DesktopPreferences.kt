package com.entreco.eqms.desktop

import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DesktopPreferences(
    private val node: () -> Preferences = { Preferences.userRoot().node("com/entreco/dutchdoxx") },
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

    fun readProject(): Result<String?> = access { node().get("project", null)?.also(::requireStoredPath) }

    fun readRecents(): Result<List<String>> = access {
        (0 until MAX_RECENT).mapNotNull { node().get("recent$it", null)?.also(::requireStoredPath) }.distinct()
    }

    suspend fun saveProjects(project: String?, recents: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        access {
            val bounded = recents.distinct().take(MAX_RECENT)
            (listOfNotNull(project) + bounded).forEach(::requireStoredPath)
            node().apply {
                if (project == null) remove("project") else put("project", project)
                for (index in 0 until MAX_RECENT) {
                    bounded.getOrNull(index)?.let { put("recent$index", it) } ?: remove("recent$index")
                }
                flush()
            }
            Unit
        }
    }

    private fun requireStoredPath(path: String) {
        require(path.isNotBlank() && path.length <= Preferences.MAX_VALUE_LENGTH) { "Invalid stored project path." }
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

    private companion object {
        const val MAX_RECENT = 5
    }
}
