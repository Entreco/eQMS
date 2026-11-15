package com.entreco.eqms.feature.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.entreco.eqms.feature.ui.WorkspaceDialog
import com.entreco.eqms.feature.ui.WorkspaceTextField
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/** Suggestions only: the hex field stays authoritative and core validates every value. */
private val accentSuggestions = listOf(
    "#C2410C", "#B91C1C", "#0F766E", "#1D4ED8", "#6D28D9", "#BE185D", "#15803D", "#A16207",
)

private const val PREVIEW_DEBOUNCE_MS = 200L
private const val MAX_SEED_NAME_CHARS = 50

/** Project identity step of project creation; every value shown is previewed by core, never recomputed here. */
@Composable
fun BrandingDialog(
    capabilities: ProjectCapabilities,
    folderName: String,
    onCancel: () -> Unit,
    onCreate: (BrandingDraft) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(folderName.take(60)) }
    var shuffleToken by remember { mutableStateOf<String?>(null) }
    var accent by remember { mutableStateOf("auto") }
    var logoFile by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<BrandingPreview?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    val seed = shuffleToken?.let { name.take(MAX_SEED_NAME_CHARS) + "#" + it } ?: name
    val draft = BrandingDraft(name, seed, accent.trim().ifEmpty { "auto" }, logoFile.trim().ifEmpty { null })

    LaunchedEffect(draft) {
        preview = null
        problem = null
        if (draft.name.isBlank()) {
            problem = "Enter a project name."
            return@LaunchedEffect
        }
        delay(PREVIEW_DEBOUNCE_MS)
        capabilities.previewBranding(draft).fold(
            onSuccess = { preview = it },
            onFailure = { problem = it.message ?: "Branding could not be previewed." },
        )
    }

    WorkspaceDialog(
        onDismissRequest = onCancel,
        title = { Text("Project identity") },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkspaceTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Project name",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        preview?.let { LogoPreview(it, draft.logoFile) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = draft.logoFile == null && name.isNotBlank(), onClick = {
                            shuffleToken = Random.nextLong(0, Long.MAX_VALUE).toString(36).take(6)
                        }) { Text("Shuffle logo") }
                    }
                }
                Text("Accent colour")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { accent = "auto" }) { Text("Match logo") }
                    accentSuggestions.forEach { suggestion ->
                        Box(
                            Modifier.size(28.dp)
                                .background(Color(0xFF000000L or suggestion.substring(1).toLong(16)))
                                .border(1.dp, JewelTheme.globalColors.borders.normal)
                                .semantics { contentDescription = "Accent $suggestion"; role = Role.Button }
                                .clickable { accent = suggestion },
                        )
                    }
                }
                WorkspaceTextField(
                    value = accent,
                    onValueChange = { accent = it },
                    label = "Accent colour (#RRGGBB or auto)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    WorkspaceTextField(
                        value = logoFile,
                        onValueChange = { logoFile = it },
                        label = "Logo PNG file (optional)",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedButton(onClick = { scope.launch { capabilities.chooseLogoFile()?.let { logoFile = it } } }) {
                        Text("Browse")
                    }
                }
                preview?.tokens?.warnings.orEmpty().forEach { Text("Warning: $it") }
                problem?.let { Text(it, color = JewelTheme.globalColors.text.error) }
                if (preview == null && problem == null) Text("Previewing...", color = JewelTheme.globalColors.text.info)
            }
        },
        confirmButton = {
            DefaultButton(enabled = preview != null, onClick = { onCreate(draft) }) { Text("Create project") }
        },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancel") } },
    )
}
