package com.entreco.eqms.feature.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.entreco.eqms.feature.ui.WorkspaceTextField
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text

private fun margin(value: Double) = value.toString()

/** Builds the page setup described by the text fields, or explains why it is invalid. */
internal fun metadataCandidate(
    base: DocumentPageSetup, margins: List<String>, header: String, footer: String,
): Result<DocumentPageSetup> {
    val parsed = margins.map { it.trim().toDoubleOrNull() }
    if (parsed.any { it == null || !it.isFinite() }) {
        return Result.failure(IllegalArgumentException("Margins must be numeric millimetre values."))
    }
    return runCatching {
        base.copy(
            marginTopMm = parsed[0]!!, marginRightMm = parsed[1]!!,
            marginBottomMm = parsed[2]!!, marginLeftMm = parsed[3]!!,
            headerTemplate = header, footerTemplate = footer,
        ).validate()
    }
}

@Composable
internal fun DocumentMetadataPanel(tab: DocumentTab, interactive: Boolean, modifier: Modifier = Modifier) {
    val snapshot = tab.snapshot
    val editable = tab.editable && interactive
    // Text is kept locally so invalid input can be shown and corrected; only valid setups reach the tab buffer.
    var top by remember(tab.key, snapshot?.revision) { mutableStateOf(margin(tab.page.marginTopMm)) }
    var right by remember(tab.key, snapshot?.revision) { mutableStateOf(margin(tab.page.marginRightMm)) }
    var bottom by remember(tab.key, snapshot?.revision) { mutableStateOf(margin(tab.page.marginBottomMm)) }
    var left by remember(tab.key, snapshot?.revision) { mutableStateOf(margin(tab.page.marginLeftMm)) }
    var header by remember(tab.key, snapshot?.revision) { mutableStateOf(tab.page.headerTemplate) }
    var footer by remember(tab.key, snapshot?.revision) { mutableStateOf(tab.page.footerTemplate) }
    val page = tab.page
    val candidate = metadataCandidate(page, listOf(top, right, bottom, left), header, footer)
    val problem = candidate.exceptionOrNull()?.message
    SideEffect { tab.metadataError = problem }
    DisposableEffect(tab) { onDispose { tab.metadataError = null } }

    fun commit(next: DocumentPageSetup) {
        tab.page = next
        tab.renderRequestedAt = System.nanoTime()
        tab.message = null
    }

    fun edit(change: () -> Unit) {
        change()
        metadataCandidate(page, listOf(top, right, bottom, left), header, footer).getOrNull()?.let(::commit)
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).semantics { contentDescription = "Document metadata fields" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!tab.editable) Text("Read-only: metadata of this record cannot be changed.", color = JewelTheme.globalColors.text.info)
        Text("Identity", color = JewelTheme.globalColors.text.info)
        Text("ID: ${snapshot?.id ?: "assigned on first save"}")
        Text("Revision: ${snapshot?.revision ?: "not saved"}")
        Text("Content hash: ${snapshot?.contentHash ?: "not saved"}")
        Text("Status: ${snapshot?.status?.label ?: "New draft"}")
        Text("Editable metadata", color = JewelTheme.globalColors.text.info)
        WorkspaceTextField(
            tab.title,
            { tab.renderRequestedAt = System.nanoTime(); tab.titleField.value = tab.titleField.value.copy(text = it); tab.message = null },
            "Title", singleLine = true, readOnly = !tab.editable, enabled = interactive,
            textState = tab.titleField, modifier = Modifier.fillMaxWidth(),
        )
        Text("Page size")
        ListComboBox(
            items = PageSize.entries.map { it.label },
            selectedIndex = PageSize.entries.indexOf(page.size),
            onSelectedItemChange = { commit(tab.page.copy(size = PageSize.entries[it]).let { next -> runCatching { next.validate() }.getOrDefault(tab.page) }) },
            enabled = editable,
            modifier = Modifier.semantics { contentDescription = "Page size" },
        )
        Text("Orientation")
        ListComboBox(
            items = PageOrientation.entries.map { it.name },
            selectedIndex = PageOrientation.entries.indexOf(page.orientation),
            onSelectedItemChange = { commit(tab.page.copy(orientation = PageOrientation.entries[it]).let { next -> runCatching { next.validate() }.getOrDefault(tab.page) }) },
            enabled = editable,
            modifier = Modifier.semantics { contentDescription = "Orientation" },
        )
        WorkspaceTextField(top, { edit { top = it } }, "Top margin (mm)",
            singleLine = true, readOnly = !tab.editable, enabled = interactive, modifier = Modifier.fillMaxWidth())
        WorkspaceTextField(right, { edit { right = it } }, "Right margin (mm)",
            singleLine = true, readOnly = !tab.editable, enabled = interactive, modifier = Modifier.fillMaxWidth())
        WorkspaceTextField(bottom, { edit { bottom = it } }, "Bottom margin (mm)",
            singleLine = true, readOnly = !tab.editable, enabled = interactive, modifier = Modifier.fillMaxWidth())
        WorkspaceTextField(left, { edit { left = it } }, "Left margin (mm)",
            singleLine = true, readOnly = !tab.editable, enabled = interactive, modifier = Modifier.fillMaxWidth())
        WorkspaceTextField(
            header, { edit { header = it } },
            "Header template", singleLine = true, readOnly = !tab.editable, enabled = interactive,
            modifier = Modifier.fillMaxWidth(),
        )
        WorkspaceTextField(
            footer, { edit { footer = it } },
            "Footer template", singleLine = true, readOnly = !tab.editable, enabled = interactive,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Use {title}, {page} and {total} in header and footer.", color = JewelTheme.globalColors.text.info)
        problem?.let {
            Text("Cannot save: $it", color = JewelTheme.globalColors.text.error)
        }
        Text(
            "Metadata changes are part of the document revision and are persisted only by Save.",
            color = JewelTheme.globalColors.text.info,
        )
    }
}
