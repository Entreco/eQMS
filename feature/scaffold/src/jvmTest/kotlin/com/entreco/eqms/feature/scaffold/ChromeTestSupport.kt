package com.entreco.eqms.feature.scaffold

import androidx.compose.ui.test.junit4.ComposeContentTestRule

internal fun testChrome(
    project: Result<String?> = Result.success(null),
    recents: Result<List<String>> = Result.success(emptyList()),
    saveProjects: suspend (String?, List<String>) -> Result<Unit> = { _, _ -> Result.success(Unit) },
) = DesktopChrome(
    "1.0.0-dev", Result.success(true), { Result.success(Unit) },
    { error("No update check expected") }, { error("No browser expected") },
    initialProject = project, initialRecents = recents, saveProjects = saveProjects,
)

internal fun ComposeContentTestRule.menu(chrome: DesktopChrome, command: DesktopCommand) {
    runOnIdle { chrome.request(command) }
    waitForIdle()
}

internal fun ComposeContentTestRule.openRecent(chrome: DesktopChrome, path: String) {
    runOnIdle { chrome.requestRecent(path) }
    waitForIdle()
}
