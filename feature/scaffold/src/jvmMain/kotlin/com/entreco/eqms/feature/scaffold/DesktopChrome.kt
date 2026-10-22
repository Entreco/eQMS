package com.entreco.eqms.feature.scaffold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.entreco.eqms.feature.ui.WorkspaceDialog
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

enum class DesktopCommand { About, Settings, CheckUpdates, NewProject, CloseProject }

data class ReleaseCheck(val message: String, val releaseUrl: String? = null)

class DesktopChrome(
    val version: String,
    val initialAppearance: Result<Boolean>,
    val saveAppearance: suspend (Boolean) -> Result<Unit>,
    val checkUpdates: suspend () -> Result<ReleaseCheck>,
    val openRelease: suspend (String) -> Result<Unit>,
) {
    var enabled by mutableStateOf(true)
        internal set
    var hasProject by mutableStateOf(false)
        internal set
    internal var command by mutableStateOf<DesktopCommand?>(null)
        private set

    fun request(action: DesktopCommand) {
        if (enabled && command == null && (action != DesktopCommand.CloseProject || hasProject)) command = action
    }

    internal fun consume(): DesktopCommand? = command.also { command = null }
}

@Composable
internal fun ChromeDialog(
    command: DesktopCommand,
    chrome: DesktopChrome,
    dark: Boolean,
    saving: Boolean,
    appearanceError: String?,
    onAppearance: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(command == DesktopCommand.CheckUpdates) }
    var result by remember { mutableStateOf<ReleaseCheck?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(command) {
        if (command == DesktopCommand.CheckUpdates) {
            chrome.checkUpdates().fold(
                onSuccess = { result = it },
                onFailure = { error = it.message ?: "Could not check for updates." },
            )
            checking = false
        }
    }

    WorkspaceDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(when (command) {
                DesktopCommand.About -> "About DutchDoxx"
                DesktopCommand.Settings -> "Settings"
                else -> "Check for updates"
            })
        },
        text = {
            Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (command) {
                    DesktopCommand.About -> {
                        Text("DutchDoxx ${chrome.version}")
                        Text("An eQMS quality workspace. Drafts are not approvals.")
                    }
                    DesktopCommand.Settings -> {
                        Text("Appearance: ${if (dark) "Dark" else "Light"}")
                        OutlinedButton(enabled = !saving, onClick = { onAppearance(!dark) }) {
                            Text(if (dark) "Use light appearance" else "Use dark appearance")
                        }
                        if (saving) Text("Saving appearance...")
                        appearanceError?.let { Text(it, color = JewelTheme.globalColors.text.error) }
                    }
                    else -> {
                        Text(if (checking) "Checking for updates..." else result?.message ?: "Update check failed.")
                        error?.let { Text(it, color = JewelTheme.globalColors.text.error) }
                        result?.releaseUrl?.let { url ->
                            OutlinedButton(enabled = !checking, onClick = {
                                checking = true
                                scope.launch {
                                    chrome.openRelease(url).fold(
                                        onSuccess = { error = null },
                                        onFailure = { error = it.message ?: "Could not open the release page." },
                                    )
                                    checking = false
                                }
                            }) { Text("Open release page") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DefaultButton(enabled = !saving, onClick = onDismiss) { Text(if (checking) "Cancel" else "Close") }
        },
    )
}
