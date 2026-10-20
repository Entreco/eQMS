package com.entreco.eqms.ledger

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class SqliteDatabaseBootstrap(
    private val databasePath: Path = Path.of("build", "eqms.db")
) {
    data class DatabaseInfo(
        val path: Path,
        val schemaVersion: Int
    )

    fun initialize(): DatabaseInfo {
        val parentDir = databasePath.parent ?: Path.of(".")
        Files.createDirectories(parentDir)

        val jdbcUrl = "jdbc:sqlite:${databasePath.toAbsolutePath()}"
        DriverManager.getConnection(jdbcUrl).use { connection ->
            ensureSchemaVersionTable(connection)
            migrate(connection)
            val schemaVersion = readSchemaVersion(connection)
            return DatabaseInfo(databasePath.toAbsolutePath(), schemaVersion)
        }
    }

    private fun ensureSchemaVersionTable(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER NOT NULL,
                    applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent()
            )
        }
    }

    private fun migrate(connection: Connection) {
        val currentVersion = readSchemaVersion(connection)
        if (currentVersion == 0) {
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS ledger_events (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """.trimIndent()
                )
            }
            setSchemaVersion(connection, 1)
        }
    }

    private fun setSchemaVersion(connection: Connection, newVersion: Int) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                INSERT INTO schema_version (version) VALUES ($newVersion)
                """.trimIndent()
            )
        }
    }

    private fun readSchemaVersion(connection: Connection): Int {
        val query = "SELECT MAX(version) FROM schema_version"
        connection.createStatement().use { statement ->
            val resultSet = statement.executeQuery(query)
            return if (resultSet.next()) {
                resultSet.getInt(1)
            } else {
                0
            }
        }
    }
}
