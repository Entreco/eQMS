package com.entreco.eqms.desktop

import java.awt.GraphicsEnvironment

internal fun requireNativeIntegrationEnvironment() {
    check(System.getenv("GITHUB_ACTIONS") == "true" && System.getenv("RUNNER_OS") == "macOS") {
        "Native integration tests are CI-only; local IDE/JUnit execution must not open windows or manipulate input."
    }
    check(isMacOS && !GraphicsEnvironment.isHeadless()) {
        "Native integration CI requires a graphical macOS session."
    }
    check(Runtime.version().feature() == 25 && System.getProperty("java.vendor").contains("JetBrains")) {
        "Native integration CI requires JetBrains Runtime 25."
    }
}
