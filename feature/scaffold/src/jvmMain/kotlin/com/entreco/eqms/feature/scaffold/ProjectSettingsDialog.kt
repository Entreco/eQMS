package com.entreco.eqms.feature.scaffold

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.entreco.eqms.feature.ui.BrandLogo
import com.entreco.eqms.feature.ui.WorkspaceDialog
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.Orientation

internal fun storedLogoPath(project: String, branding: BrandingPreview): String? =
    (branding.tokens.logo as? BrandLogo.Png)?.let { "$project/branding/logos/${it.sha256}.png" }

private fun hex(color: androidx.compose.ui.graphics.Color): String =
    "#%02X%02X%02X".format((color.red * 255 + 0.5f).toInt(), (color.green * 255 + 0.5f).toInt(), (color.blue * 255 + 0.5f).toInt())

/**
 * Project Settings: appearance (applies immediately) and project branding (explicit Save, journaled by core).
 * Without a project only the appearance is offered.
 */
@Composable
internal fun ProjectSettingsDialog(
    project: String?,
    capabilities: ProjectCapabilities,
    current: BrandingPreview?,
    dark: Boolean,
    savingAppearance: Boolean,
    appearanceError: String?,
    onAppearance: (Boolean) -> Unit,
    onBrandingSaved: (BrandingPreview) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val form = remember(current) {
        BrandingForm(
            current?.tokens?.name.orEmpty(),
            initialSeed = current?.let { (it.tokens.logo as? BrandLogo.Generated)?.seed.orEmpty() },
            initialAccent = current?.let { hex(it.tokens.accent) } ?: "auto",
        )
    }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val editable = project != null && current != null
    val busy = saving || savingAppearance

    fun save() {
        val path = project ?: return
        saving = true
        saveError = null
        saved = false
        scope.launch {
            val latest = capabilities.projectBranding(path).getOrNull()
            val result = if (latest != null && latest != current) {
                Result.failure(IllegalStateException("Branding changed elsewhere. Close and reopen Settings to review it."))
            } else {
                capabilities.saveBranding(path, form.draft)
            }
            result.fold(
                onSuccess = { onBrandingSaved(it); saved = true },
                onFailure = { saveError = it.message ?: "Branding could not be saved." },
            )
            saving = false
        }
    }

    WorkspaceDialog(
        onDismissRequest = {
            if (busy) return@WorkspaceDialog
            if (form.dirty && !confirmDiscard) confirmDiscard = true else onDismiss()
        },
        title = { Text("Project Settings") },
        windowTitle = "Project Settings",
        size = DpSize(640.dp, 720.dp),
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Appearance: ${if (dark) "Dark" else "Light"}")
                OutlinedButton(enabled = !busy, onClick = { onAppearance(!dark) }) {
                    Text(if (dark) "Use light appearance" else "Use dark appearance")
                }
                if (savingAppearance) Text("Saving appearance...")
                appearanceError?.let { Text(it, color = JewelTheme.globalColors.text.error) }
                Divider(Orientation.Horizontal)
                if (editable) {
                    Text("Project branding")
                    BrandingFields(capabilities, form, current, project?.let { storedLogoPath(it, current!!) })
                    if (form.dirty) Text("Unsaved branding changes.", color = JewelTheme.globalColors.text.info)
                    if (saving) Text("Saving branding...")
                    if (saved && !form.dirty) Text("Branding saved.")
                    saveError?.let { Text(it, color = JewelTheme.globalColors.text.error) }
                    if (confirmDiscard) Text("Discard unsaved branding changes? Press Discard to close without saving.")
                } else {
                    Text("Open a project to edit its branding.", color = JewelTheme.globalColors.text.info)
                }
            }
        },
        confirmButton = {
            if (editable) {
                DefaultButton(enabled = !busy && form.dirty && form.preview != null, onClick = ::save) { Text("Save") }
            }
        },
        dismissButton = {
            if (confirmDiscard) OutlinedButton(enabled = !busy, onClick = onDismiss) { Text("Discard") }
            OutlinedButton(enabled = !busy, onClick = {
                if (form.dirty && !confirmDiscard) confirmDiscard = true else onDismiss()
            }) { Text("Close") }
        },
    )
}
