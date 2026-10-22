package com.entreco.eqms.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.entreco.eqms.feature.ui.WorkspaceItem
import com.entreco.eqms.feature.ui.WorkspaceTextField
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
fun SearchPanel(
    search: DocumentSearch,
    enabled: Boolean,
    onSelect: (SearchHit, historical: Boolean) -> Unit,
    isSelected: (SearchHit, historical: Boolean) -> Boolean,
    onResultsLaidOut: (SearchRenderTiming) -> Unit,
    modifier: Modifier = Modifier,
    documentsContent: LazyListScope.() -> Unit,
) {
    val timingCallback by rememberUpdatedState(onResultsLaidOut)
    val renderRequest = search.renderRequest
    val displayedResults = search.results
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorkspaceTextField(
            value = search.query, onValueChange = search::updateQuery,
            label = "Search saved drafts", singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (search.preparing) Text("Preparing saved-draft search...")
        if (search.loading && !search.preparing) Text("Searching saved drafts...")
        search.error?.let {
            Text(it, color = JewelTheme.globalColors.text.error)
            OutlinedButton(onClick = search::refresh) { Text("Refresh search") }
        }
        key(renderRequest?.generation) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).onGloballyPositioned {
                renderRequest?.let { request -> search.claimRenderTiming(request)?.let(timingCallback) }
            }) {
                if (search.query.isBlank()) {
                    documentsContent()
                } else {
                    displayedResults?.let { results ->
                        if (results.historical) item { Text("Draft/history matches — older saved revisions, read-only.") }
                        if (SearchGroup.entries.all { results.page(it).hits.isEmpty() }) item { Text("No matching saved drafts.") }
                        SearchGroup.entries.forEach { group ->
                            item(key = group) { Text(group.name, Modifier.padding(vertical = 8.dp), fontWeight = FontWeight.SemiBold) }
                            items(results.page(group).hits, key = { "${group.name}:${it.id}:${it.revision}" }) { hit ->
                                val summary = when (group) {
                                    SearchGroup.Documents -> hit.title
                                    SearchGroup.Authors -> "${hit.title} | by ${hit.configuredAuthor}"
                                    SearchGroup.Content -> "${hit.title} | ${hit.excerpt}"
                                }
                                WorkspaceItem(
                                    if (results.historical) "${hit.title} (Draft/history)\n" +
                                        "Revision: ${hit.revision}\nConfigured author: ${hit.configuredAuthor}\n" +
                                        "Local timestamp: ${hit.localTimestamp}\n${hit.excerpt}" else summary,
                                    selected = isSelected(hit, results.historical),
                                    enabled = enabled,
                                    onClick = { onSelect(hit, results.historical) },
                                )
                            }
                            if (results.page(group).nextOffset != null) {
                                item(key = "more:$group") {
                                    OutlinedButton(enabled = !search.loading, onClick = { search.more(group) }) {
                                        Text("Show more ${group.name}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
