package com.entreco.eqms.cmd

import com.entreco.eqms.ledger.SqliteDatabaseBootstrap
import java.nio.file.Path

fun main() {
    val databaseInfo = SqliteDatabaseBootstrap(Path.of("build", "eqms.db")).initialize()
    println("eQMS cmd bootstrap module")
    println("SQLite ready at ${databaseInfo.path} (schema v${databaseInfo.schemaVersion})")
}
