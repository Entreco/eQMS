package com.entreco.eqms.feature.scaffold

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.entreco.eqms.feature.ui.BrandLogo
import com.entreco.eqms.feature.ui.BrandTokens

internal fun testBrandingPreview(draft: BrandingDraft): Result<BrandingPreview> =
    if (draft.accent != "auto" && !Regex("#[0-9A-Fa-f]{6}").matches(draft.accent)) {
        Result.failure(IllegalArgumentException("accent must be #RRGGBB"))
    } else {
        BrandTokens.parse(
            draft.name, draft.accent.takeIf { it != "auto" } ?: "#C2410C", "#FFFFFF", BrandLogo.Generated(draft.seed),
            if (draft.accent == "#FFFF00") listOf("accent #FFFF00 has 1.07:1 contrast on the light theme (3:1 recommended)") else emptyList(),
        ).map { BrandingPreview(it, GeneratedLogo("AC", GeneratedLogoShape.Hexagon)) }
    }

/** Accepts the default identity once core's preview has arrived. */
internal fun ComposeContentTestRule.confirmIdentity() {
    waitUntil(5_000) { onAllNodesWithText("Project identity").fetchSemanticsNodes().isNotEmpty() }
    waitUntil(5_000) { runCatching { onNodeWithText("Create project").assertIsEnabled() }.isSuccess }
    onNodeWithText("Create project").performClick()
}

internal fun chromeTestProjects() = object : ProjectCapabilities {
    override suspend fun listDirectory(path: String?) = Result.success(DirectoryListing("/project", null, emptyList()))
    override suspend fun createFolder(parent: String, name: String) = Result.success("$parent/$name")
    override suspend fun openProject(path: String, create: Boolean) = Result.success(path)
    override suspend fun previewBranding(draft: BrandingDraft) = testBrandingPreview(draft)
}

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

/** Flips the theme the way a user does: gear button, appearance button, Close. */
internal fun ComposeContentTestRule.toggleAppearanceFromSettings() {
    onNodeWithContentDescription("Settings").performClick()
    val toDark = onAllNodesWithText("Use dark appearance").fetchSemanticsNodes().isNotEmpty()
    onNodeWithText(if (toDark) "Use dark appearance" else "Use light appearance").performClick()
    onNodeWithText("Close").performClick()
}
