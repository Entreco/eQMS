package com.entreco.eqms.search

import com.entreco.eqms.ledger.LedgerModule

object SearchModule {
    val dependencies: List<String> = listOf(LedgerModule.moduleName)
}
