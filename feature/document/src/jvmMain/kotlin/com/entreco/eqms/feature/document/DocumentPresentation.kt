package com.entreco.eqms.feature.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import com.entreco.eqms.feature.ui.WorkspaceTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Cursor
import java.security.MessageDigest

internal class DocumentBitmapCache {
    private val entries = LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)
    private var bytes = 0L

    @Synchronized
    fun decode(image: DocumentImage): ImageBitmap {
        val key = MessageDigest.getInstance("SHA-256").digest(image.bytes).joinToString("") { "%02x".format(it) }
        entries[key]?.let { return it }
        val bitmap = SkiaImage.makeFromEncoded(image.bytes).use { encoded ->
            require(encoded.width == image.width && encoded.height == image.height) { "Decoded image dimensions differ from the native response." }
            org.jetbrains.skia.Bitmap().use { pixels ->
                require(pixels.allocN32Pixels(image.width, image.height)) { "Could not allocate bounded image pixels." }
                require(encoded.readPixels(pixels)) { "Could not decode image pixels." }
                pixels.setImmutable()
                SkiaImage.makeFromBitmap(pixels).toComposeImageBitmap()
            }
        }
        val size = bitmap.width.toLong() * bitmap.height * 4
        while (bytes + size > 64L * 1024 * 1024 && entries.isNotEmpty()) {
            val first = entries.entries.first()
            bytes -= first.value.width.toLong() * first.value.height * 4
            entries.remove(first.key)
        }
        entries[key] = bitmap
        bytes += size
        return bitmap
    }
}

@Composable
internal fun DocumentPresentation(
    project: String,
    tab: DocumentTab,
    capabilities: DocumentCapabilities,
    interactive: Boolean,
    cache: DocumentBitmapCache,
    onExternalLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRendered: (DocumentRenderTiming) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DocumentViewMode.entries.forEach { mode ->
                val control = Modifier.semantics { contentDescription = "View ${mode.name}"; selected = tab.viewMode == mode }
                if (tab.viewMode == mode) {
                    DefaultButton(onClick = {}, enabled = interactive, modifier = control) { Text(mode.name) }
                } else {
                    OutlinedButton(onClick = { tab.viewMode = mode }, enabled = interactive, modifier = control) { Text(mode.name) }
                }
            }
        }
        if (tab.viewMode == DocumentViewMode.Source) {
            Source(tab, interactive, Modifier.weight(1f).fillMaxWidth())
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val availableWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                if (tab.viewMode == DocumentViewMode.Preview) {
                    Preview(project, tab, capabilities, cache, onExternalLink, onRendered, Modifier.fillMaxSize())
                } else {
                    Row(Modifier.fillMaxSize()) {
                        Source(tab, interactive, Modifier.weight(tab.splitFraction).fillMaxHeight())
                        val adjust: (Float) -> Unit = { tab.splitFraction = (tab.splitFraction + it).coerceIn(0.25f, 0.75f) }
                        OutlinedButton(
                            onClick = { adjust(0.05f) }, enabled = interactive,
                            modifier = Modifier.width(12.dp).fillMaxHeight()
                                .semantics { contentDescription = "Resize source and preview; arrow keys adjust" }
                                .pointerHoverIcon(if (interactive) PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)) else PointerIcon.Default)
                                .onPreviewKeyEvent {
                                    if (interactive && it.type == KeyEventType.KeyDown && it.key in listOf(Key.DirectionLeft, Key.DirectionRight)) {
                                        adjust(if (it.key == Key.DirectionLeft) -0.05f else 0.05f); true
                                    } else false
                                }
                                .draggable(rememberDraggableState { adjust(it / availableWidth) }, Orientation.Horizontal, enabled = interactive),
                        ) {}
                        Preview(project, tab, capabilities, cache, onExternalLink, onRendered,
                            Modifier.weight(1f - tab.splitFraction).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun Source(tab: DocumentTab, interactive: Boolean, modifier: Modifier) {
    Column(modifier.verticalScroll(tab.scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WorkspaceTextField(tab.title, { tab.renderRequestedAt = System.nanoTime(); tab.titleField.value = tab.titleField.value.copy(text = it); tab.message = null },
            "Document title", singleLine = true, readOnly = !tab.editable, enabled = interactive,
            textState = tab.titleField, modifier = Modifier.fillMaxWidth())
        WorkspaceTextField(tab.body, { tab.renderRequestedAt = System.nanoTime(); tab.bodyField.value = tab.bodyField.value.copy(text = it); tab.message = null },
            "Markdown body", readOnly = !tab.editable, enabled = interactive,
            textState = tab.bodyField, modifier = Modifier.fillMaxWidth().height(440.dp))
    }
}

@Composable
private fun Preview(
    project: String,
    tab: DocumentTab,
    capabilities: DocumentCapabilities,
    cache: DocumentBitmapCache,
    onExternalLink: (String) -> Unit,
    onRendered: (DocumentRenderTiming) -> Unit,
    modifier: Modifier,
) {
    val title = tab.title
    val body = tab.body
    val revision = tab.snapshot?.revision
    val dirty = tab.dirty || tab.snapshot == null
    var error by remember(tab) { mutableStateOf<String?>(null) }
    var loading by remember(tab) { mutableStateOf(false) }
    var showContents by remember(tab) { mutableStateOf(false) }
    var showSourceDetails by remember(tab) { mutableStateOf(false) }
    val current = tab.rendered?.takeIf {
        tab.renderedTitle == title && tab.renderedBody == body && tab.renderedRevision == revision &&
            (it.source.kind == "unsaved") == dirty
    }
    LaunchedEffect(tab, title, body, revision, dirty) {
        if (current != null) return@LaunchedEffect
        loading = true
        error = null
        withContext(Dispatchers.Default) { delay(100) }
        val result = if (dirty) capabilities.renderBuffer(project, title, body)
            else capabilities.renderSaved(project, requireNotNull(tab.snapshot))
        coroutineContext.ensureActive()
        if (tab.title == title && tab.body == body && tab.snapshot?.revision == revision) {
            result.fold(
                onSuccess = {
                    tab.renderedTitle = title; tab.renderedBody = body; tab.renderedRevision = revision
                    tab.rendered = it
                },
                onFailure = { error = it.message ?: "Document rendering failed. Source remains available." },
            )
        }
        loading = false
    }
    val scope = rememberCoroutineScope()
    val navigate: (String) -> Unit = { target ->
        if (target.startsWith("#")) {
            val anchor = target.drop(1)
            val index = current?.nodes?.indexOfFirst { node ->
                fun contains(n: RenderNode): Boolean = n.anchor == anchor || n.children.any(::contains)
                contains(node)
            } ?: -1
            if (index >= 0) scope.launch { tab.previewScroll.animateScrollToItem(index + 1) }
            else error = "Reference target was not found: $target"
        } else if (target.startsWith("https://") || target.startsWith("http://") || target.startsWith("mailto:")) {
            onExternalLink(target)
        } else error = "Unsupported link: $target"
    }
    Column(modifier.semantics { contentDescription = "Rendered document" }) {
        if (loading) Text("Rendering document...", color = JewelTheme.globalColors.text.info)
        error?.let { Text(it, color = JewelTheme.globalColors.text.error) }
        if (current != null) {
            key(current) {
                LazyColumn(state = tab.previewScroll, modifier = Modifier.fillMaxSize().onGloballyPositioned {
                    if (tab.reportedRendering !== current) {
                        tab.reportedRendering = current
                        onRendered(DocumentRenderTiming(current.inputDigest,
                            (System.nanoTime() - tab.renderRequestedAt) / 1_000_000.0))
                    }
                }.semantics { contentDescription = "Rendered document content" },
                    contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item(key = "document-header") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(current.title.ifBlank { "Untitled document" }, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (dirty) "Unsaved preview - not a saved or approved record"
                                    else "${if (tab.historical) "Historical" else "Saved"} revision ${current.source.revision.take(12)}",
                                    color = JewelTheme.globalColors.text.info)
                                if (!dirty) {
                                    OutlinedButton(onClick = { showSourceDetails = !showSourceDetails }) {
                                        Text(if (showSourceDetails) "Hide source details" else "Source details")
                                    }
                                    if (showSourceDetails) Text("${current.source.id}\n${current.source.revision}\n${current.source.contentHash}", fontSize = 11.sp,
                                        color = JewelTheme.globalColors.text.info)
                                }
                                if (current.toc.isNotEmpty()) {
                                    OutlinedButton(onClick = { showContents = !showContents }) {
                                        Text(if (showContents) "Hide contents" else "Table of contents")
                                    }
                                    if (showContents) LazyColumn(Modifier.height(180.dp)) {
                                        itemsIndexed(current.toc) { _, heading ->
                                            OutlinedButton(onClick = { navigate("#${heading.anchor}") },
                                                modifier = Modifier.padding(start = ((heading.level - 1) * 8).dp)
                                                    .semantics { contentDescription = "Go to ${heading.title}" }) {
                                                Text(heading.title)
                                            }
                                        }
                                    }
                                }
                                current.diagnostics.forEach { Text(it, color = JewelTheme.globalColors.text.info) }
                            }
                        }
                    }
                    itemsIndexed(current.nodes) { _, node ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Block(node, project, tab.historical, capabilities, cache, navigate,
                                Modifier.widthIn(max = 760.dp).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Block(node: RenderNode, project: String, historical: Boolean, capabilities: DocumentCapabilities,
    cache: DocumentBitmapCache, navigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = JewelTheme.globalColors
    val base = JewelTheme.defaultTextStyle.copy(color = colors.text.normal, fontSize = 15.sp, lineHeight = 24.sp)
    when (node.kind) {
        "rule" -> Box(modifier.height(1.dp).background(colors.borders.normal))
        "image" -> DocumentImageView(node, project, historical, capabilities, cache, modifier)
        "unordered", "ordered" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            node.children.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (node.kind == "ordered") "${(node.target.toLongOrNull() ?: 1) + index}." else "\u2022")
                    Column(Modifier.weight(1f)) {
                        item.children.ifEmpty { listOf(item.copy(kind = "paragraph")) }.forEach {
                            Block(it, project, historical, capabilities, cache, navigate)
                        }
                    }
                }
            }
        }
        "table" -> BoxWithConstraints(modifier) {
            val columns = node.children.maxOfOrNull { it.children.size } ?: 0
            val cellWidth = maxOf(150.dp, maxWidth / columns.coerceAtLeast(1))
            Column(Modifier.horizontalScroll(rememberScrollState())
                .semantics { contentDescription = "Rendered table with $columns columns" }
                .border(1.dp, colors.borders.normal)) {
                node.children.forEach { row ->
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        row.children.forEach { cell ->
                            BasicText(inlineText(cell.children, navigate),
                                Modifier.width(cellWidth).fillMaxHeight().border(0.5.dp, colors.borders.normal).padding(10.dp),
                                style = base.copy(fontWeight = if (row.kind == "table_header") FontWeight.SemiBold else FontWeight.Normal))
                        }
                    }
                }
            }
        }
        "code_block", "literal" -> SelectionContainer {
            BasicText(node.plainText(), modifier.background(colors.borders.normal.copy(alpha = 0.15f))
                .horizontalScroll(rememberScrollState()).padding(12.dp),
                style = base.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
        }
        "quote", "section", "footnote", "item" -> Column(modifier
            .border(1.dp, colors.borders.normal.copy(alpha = 0.4f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (node.kind == "footnote") Text("Note ${node.text}", fontWeight = FontWeight.SemiBold)
            node.children.forEach { Block(it, project, historical, capabilities, cache, navigate) }
            if (node.kind == "footnote") OutlinedButton(onClick = { navigate("#reference-${node.anchor}") }) { Text("Back to reference") }
        }
        else -> {
            val level = node.kind.takeIf { it.matches(Regex("h[1-6]")) }?.last()?.digitToInt()
            val style = if (level != null) base.copy(fontSize = (29 - level * 2).sp, lineHeight = (37 - level * 2).sp,
                fontWeight = FontWeight.SemiBold) else base
            val text = inlineText(if (node.children.isEmpty()) listOf(node) else node.children, navigate)
            Column(modifier) {
                SelectionContainer {
                    BasicText(text, Modifier.fillMaxWidth().then(if (level != null) Modifier.semantics { heading() } else Modifier),
                        style = style)
                }
                fun images(n: RenderNode): List<RenderNode> =
                    if (n.kind == "image") listOf(n) else n.children.flatMap(::images)
                node.children.flatMap(::images).forEach {
                    DocumentImageView(it, project, historical, capabilities, cache, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun inlineText(nodes: List<RenderNode>, navigate: (String) -> Unit): AnnotatedString {
    val linkColor = JewelTheme.globalColors.text.normal
    return buildAnnotatedString {
        fun appendNode(node: RenderNode) {
            when (node.kind) {
                "link", "note_link" -> withLink(LinkAnnotation.Clickable(node.target,
                    TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { navigate(node.target) })) {
                    append(node.text); node.children.forEach(::appendNode)
                }
                "strong" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { node.children.forEach(::appendNode) }
                "emphasis" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { node.children.forEach(::appendNode) }
                "strike" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { node.children.forEach(::appendNode) }
                "code", "literal" -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(node.text) }
                "image" -> append("[Image: ${node.plainText()}]")
                else -> { append(node.text); node.children.forEach(::appendNode) }
            }
        }
        nodes.forEach(::appendNode)
    }
}

@Composable
private fun DocumentImageView(node: RenderNode, project: String, historical: Boolean, capabilities: DocumentCapabilities,
    cache: DocumentBitmapCache, modifier: Modifier) {
    var bitmap by remember(project, node.target, historical) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(project, node.target, historical) { mutableStateOf<String?>(null) }
    LaunchedEffect(project, node.target, historical) {
        if (historical) {
            error = "Historical image unavailable: assets are not versioned."
        } else if (!node.target.startsWith("assets/")) {
            error = "Image blocked: only project-local assets/ PNG/JPEG files are supported."
        } else {
            capabilities.readDocumentImage(project, node.target).fold(
                onSuccess = { image ->
                    try {
                        bitmap = withContext(Dispatchers.Default) { cache.decode(image) }
                    } catch (failure: IllegalArgumentException) {
                        error = "Image decode failed: ${failure.message}"
                    } catch (failure: IllegalStateException) {
                        error = "Image decode failed: ${failure.message}"
                    }
                },
                onFailure = { error = it.message ?: "Image could not be loaded." },
            )
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (bitmap == null && error == null) Text("Loading image: ${node.plainText()}", color = JewelTheme.globalColors.text.info)
        bitmap?.let {
            Image(it, node.plainText().ifBlank { "Document image" }, Modifier.fillMaxWidth().heightIn(max = 400.dp)
                .aspectRatio(it.width.toFloat() / it.height), contentScale = ContentScale.Fit)
            Text("Local unversioned image - not covered by the document hash", fontSize = 11.sp,
                color = JewelTheme.globalColors.text.info)
        }
        error?.let { Text("${node.plainText()}: $it", color = JewelTheme.globalColors.text.info) }
    }
}
