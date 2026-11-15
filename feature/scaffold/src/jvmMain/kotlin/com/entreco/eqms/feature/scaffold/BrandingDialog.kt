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
import androidx.compose.ui.unit.DpSize
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

/** Editable branding values shared by project creation and Project Settings. */
internal class BrandingForm(
    initialName: String,
    private val initialSeed: String? = null,
    private val initialAccent: String = "auto",
) {
    var name by mutableStateOf(initialName)
    var shuffleToken by mutableStateOf<String?>(null)
    var accent by mutableStateOf(initialAccent)
    var logoFile by mutableStateOf("")
    var preview by mutableStateOf<BrandingPreview?>(null)
    var problem by mutableStateOf<String?>(null)

    /** Seed the logo is generated from; empty when an existing PNG logo is kept. */
    val seed: String
        get() {
            val base = initialSeed ?: name
            return shuffleToken?.let { (if (base.isEmpty()) name else base).take(MAX_SEED_NAME_CHARS) + "#" + it } ?: base
        }

    val draft: BrandingDraft
        get() = BrandingDraft(name, seed, accent.trim().ifEmpty { "auto" }, logoFile.trim().ifEmpty { null })

    /** True when the logo or accent differs from what the project has, so core must preview it. */
    val lookChanged: Boolean
        get() = initialSeed != null && (
            logoFile.isNotBlank() || seed != initialSeed || accent.trim() != initialAccent
        )

    val dirty: Boolean
        get() = initialSeed != null && (name != initialNameValue || lookChanged)

    private val initialNameValue = initialName
}

/** Project identity step of project creation; every value shown is previewed by core, never recomputed here. */
@Composable
fun BrandingDialog(
    capabilities: ProjectCapabilities,
    folderName: String,
    onCancel: () -> Unit,
    onCreate: (BrandingDraft) -> Unit,
) {
    val form = remember { BrandingForm(folderName.take(60)) }
    WorkspaceDialog(
        onDismissRequest = onCancel,
        title = { Text("Project identity") },
        windowTitle = "Project identity",
        size = DpSize(640.dp, 700.dp),
        text = { BrandingFields(capabilities, form) },
        confirmButton = {
            DefaultButton(enabled = form.preview != null, onClick = { onCreate(form.draft) }) { Text("Create project") }
        },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/**
 * Name, logo and accent editors. With [current] set (Project Settings), core is asked for a preview only
 * once the logo or accent changes; until then the stored branding is shown.
 */
@Composable
internal fun BrandingFields(
    capabilities: ProjectCapabilities,
    form: BrandingForm,
    current: BrandingPreview? = null,
    currentLogoFile: String? = null,
) {
    val scope = rememberCoroutineScope()
    val draft = form.draft

    LaunchedEffect(draft) {
        form.preview = null
        form.problem = null
        if (draft.name.isBlank()) {
            form.problem = "Enter a project name."
            return@LaunchedEffect
        }
        if (current != null && !form.lookChanged) {
            form.preview = current
            return@LaunchedEffect
        }
        delay(PREVIEW_DEBOUNCE_MS)
        capabilities.previewBranding(draft).fold(
            onSuccess = { form.preview = it },
            onFailure = { form.problem = it.message ?: "Branding could not be previewed." },
        )
    }

    val preview = form.preview
    val problem = form.problem
    val keepsStoredPng = current != null && currentLogoFile != null && form.seed.isEmpty() && form.logoFile.isBlank()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorkspaceTextField(
            value = form.name,
            onValueChange = { form.name = it },
            label = "Project name",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                if (preview != null) {
                    if (keepsStoredPng) LogoPreview(BrandingPreview(preview.tokens, null), currentLogoFile)
                    else LogoPreview(preview, draft.logoFile)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = draft.logoFile == null && form.name.isNotBlank(), onClick = {
                    form.shuffleToken = Random.nextLong(0, Long.MAX_VALUE).toString(36).take(6)
                }) { Text("Shuffle logo") }
            }
        }
        Text("Accent colour")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { form.accent = "auto" }) { Text("Match logo") }
            accentSuggestions.forEach { suggestion ->
                Box(
                    Modifier.size(28.dp)
                        .background(Color(0xFF000000L or suggestion.substring(1).toLong(16)))
                        .border(1.dp, JewelTheme.globalColors.borders.normal)
                        .semantics { contentDescription = "Accent $suggestion"; role = Role.Button }
                        .clickable { form.accent = suggestion },
                )
            }
        }
        WorkspaceTextField(
            value = form.accent,
            onValueChange = { form.accent = it },
            label = "Accent colour (#RRGGBB or auto)",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            WorkspaceTextField(
                value = form.logoFile,
                onValueChange = { form.logoFile = it },
                label = "Logo PNG file (optional)",
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedButton(onClick = { scope.launch { capabilities.chooseLogoFile()?.let { form.logoFile = it } } }) {
                Text("Browse")
            }
        }
        preview?.tokens?.warnings.orEmpty().forEach { Text("Warning: $it") }
        problem?.let { Text(it, color = JewelTheme.globalColors.text.error) }
        if (preview == null && problem == null) Text("Previewing...", color = JewelTheme.globalColors.text.info)
    }
}
