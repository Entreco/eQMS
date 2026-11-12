package com.entreco.eqms.feature.document

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Tooltip
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

internal fun InlineText.toAnnotated(): AnnotatedString {
    val source = this
    val size = source.length
    return buildAnnotatedString {
    var i = 0
    while (i < size) {
        var j = i
        while (j < size && bits[j] == bits[i] && links[j] == links[i]) j++
        val bit = bits[i]
        val style = SpanStyle(
            fontWeight = if (bit and InlineMark.Bold != 0) FontWeight.Bold else null,
            fontStyle = if (bit and InlineMark.Italic != 0) FontStyle.Italic else null,
            fontFamily = if (bit and InlineMark.Code != 0) FontFamily.Monospace else null,
            background = if (bit and InlineMark.Code != 0) Paper.codeBackground else androidx.compose.ui.graphics.Color.Unspecified,
            color = if (links[i] != null) Paper.link else androidx.compose.ui.graphics.Color.Unspecified,
            textDecoration = listOfNotNull(
                TextDecoration.LineThrough.takeIf { bit and InlineMark.Strike != 0 },
                TextDecoration.Underline.takeIf { links[i] != null },
            ).takeIf { it.isNotEmpty() }?.let { TextDecoration.combine(it) },
        )
        withStyle(style) { append(source.text.substring(i, j)) }
        i = j
    }
    }
}

private fun paperStyle(level: Int) = TextStyle(
    color = Paper.ink,
    fontSize = if (level == 0) 15.sp else (29 - level * 2).sp,
    lineHeight = if (level == 0) 24.sp else (37 - level * 2).sp,
    fontWeight = if (level == 0) FontWeight.Normal else FontWeight.SemiBold,
)

private val commandKey: (KeyEvent) -> Boolean = {
    if (System.getProperty("os.name").startsWith("Mac")) it.isMetaPressed else it.isCtrlPressed
}

@Composable
internal fun WysiwygView(
    project: String,
    tab: DocumentTab,
    pageSetup: DocumentPageSetup,
    capabilities: DocumentCapabilities,
    cache: DocumentBitmapCache,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val editor = tab.editor
    val render = rememberDocumentRender(project, tab, capabilities)
    LaunchedEffect(render.current) { render.current?.let(editor::sync) }
    val doc = editor.doc
    val editable = interactive && editor.canEdit
    var linkOpen by remember(tab) { mutableStateOf(false) }
    Column(
        modifier.semantics { contentDescription = "Document editor" }.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !commandKey(event) || event.isAltPressed) false
            else when (event.key) {
                Key.B -> { if (editable) editor.toggleMark(InlineMark.Bold); true }
                Key.I -> { if (editable) editor.toggleMark(InlineMark.Italic); true }
                Key.K -> { if (editable) linkOpen = true; true }
                Key.Z, Key.Y -> {
                    if (editable) tab.undoBodyEdit(redo = event.isShiftPressed || event.key == Key.Y)
                    true
                }
                else -> false
            }
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FormatToolbar(tab, editor, editable, linkOpen, { linkOpen = it })
        if (doc == null && editor.unavailable == null) Text("Loading editor...", color = JewelTheme.globalColors.text.info)
        (render.error ?: editor.unavailable)?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(it, color = JewelTheme.globalColors.text.error)
                OutlinedButton(onClick = { tab.viewMode = DocumentViewMode.Source }) { Text("Open Markdown") }
            }
        }
        if (!tab.editable) Text(
            if (tab.historical) "Historical revision - read-only." else "Only Draft documents can be edited.",
            color = JewelTheme.globalColors.text.info,
        )
        if (doc != null) Desk(tab, doc, pageSetup, editor, editable, project, capabilities, cache)
    }
}

@Composable
private fun Desk(
    tab: DocumentTab,
    doc: WysiwygDocument,
    setup: DocumentPageSetup,
    editor: WysiwygEditorState,
    editable: Boolean,
    project: String,
    capabilities: DocumentCapabilities,
    cache: DocumentBitmapCache,
) {
    val sections = mutableListOf(mutableListOf<Int>())
    doc.blocks.forEachIndexed { index, block ->
        if (block.content == BlockContent.PageBreak) sections += mutableListOf<Int>() else sections.last() += index
    }
    val sheets = remember { mutableStateOf<Map<Int, Rect>>(emptyMap()) }
    LaunchedEffect(sections.size) { sheets.value = sheets.value.filterKeys { it < sections.size } }
    var deskOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Paper.desk()).onGloballyPositioned { deskOrigin = it.positionInRoot() }) {
        val sheetWidth = maxWidth - RulerThickness * 2 - 48.dp
        Column(
            Modifier.fillMaxSize().padding(RulerThickness).verticalScroll(tab.editorScroll).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            val breaks = doc.blocks.indices.filter { doc.blocks[it].content == BlockContent.PageBreak }
            sections.forEachIndexed { page, indices ->
                PaperSheet(setup, sheetWidth, Modifier.semantics { contentDescription = "Page ${page + 1} sheet" }
                    .onGloballyPositioned { sheets.value = sheets.value + (page to Rect(it.positionInRoot() - deskOrigin, it.size.toSize())) }) { margins ->
                    BasicText(
                        renderTemplate(setup.headerTemplate, tab.title, page + 1, sections.size),
                        Modifier.fillMaxWidth().padding(start = margins.left, end = margins.right, top = margins.top / 2),
                        style = TextStyle(color = Paper.muted, fontSize = 11.sp),
                    )
                    Column(
                        Modifier.fillMaxWidth().weight(1f, fill = false)
                            .padding(start = margins.left, end = margins.right, top = margins.top / 2, bottom = margins.bottom / 2),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (page == 0) TitleField(tab, editable)
                        indices.forEach { index -> BlockView(index, doc.blocks[index], editor, editable, project, tab, capabilities, cache) }
                        if (doc.blocks.isEmpty()) {
                            OutlinedButton(onClick = editor::startTyping, enabled = editable) {
                                Text("Start typing")
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f, fill = true).heightIn(min = 0.dp))
                    BasicText(
                        renderTemplate(setup.footerTemplate, tab.title, page + 1, sections.size),
                        Modifier.fillMaxWidth().padding(start = margins.left, end = margins.right, bottom = margins.bottom / 2),
                        style = TextStyle(color = Paper.muted, fontSize = 11.sp),
                    )
                }
                if (page < breaks.size) Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { contentDescription = "Page break" },
                ) {
                    Text("Page break", color = JewelTheme.globalColors.text.info)
                    OutlinedButton(onClick = { editor.deleteBlock(breaks[page]) }, enabled = editable,
                        modifier = Modifier.semantics { contentDescription = "Remove page break" }) { Text("Remove") }
                }
            }
            Text("Pages follow explicit page breaks. Automatic page fit is shown in Markdown + Preview and Preview.",
                color = JewelTheme.globalColors.text.info, fontSize = 11.sp)
        }
        DeskRulers(setup, sheets, Modifier.fillMaxSize())
    }
}

@Composable
private fun TitleField(tab: DocumentTab, editable: Boolean) {
    val value = tab.titleField.value
    BasicTextField(
        value, { tab.renderRequestedAt = System.nanoTime(); tab.titleField.value = it; tab.message = null },
        Modifier.fillMaxWidth().semantics { contentDescription = "Document title" },
        readOnly = !editable, singleLine = true,
        textStyle = TextStyle(color = Paper.ink, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(Paper.ink),
        decorationBox = { inner ->
            Box { if (value.text.isEmpty()) BasicText("Untitled document", style = TextStyle(color = Paper.muted, fontSize = 28.sp, fontWeight = FontWeight.Bold)); inner() }
        },
    )
}

@Composable
private fun BlockView(
    index: Int,
    block: EditorBlock,
    editor: WysiwygEditorState,
    editable: Boolean,
    project: String,
    tab: DocumentTab,
    capabilities: DocumentCapabilities,
    cache: DocumentBitmapCache,
) {
    when (val c = block.content) {
        is BlockContent.Rich -> RichField(FieldId(index), c.text, editor, editable, paperStyle(c.level),
            if (c.level == 0) "Paragraph" else "Heading ${c.level}", if (c.level == 0) "Type here" else "Heading",
            Modifier.fillMaxWidth().then(if (c.level > 0) Modifier.semantics { heading() } else Modifier))
        is BlockContent.Items -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            c.items.forEachIndexed { part, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(if (c.ordered) "${c.startAt + part}${c.marker}" else "\u2022", style = paperStyle(0))
                    RichField(FieldId(index, part), item, editor, editable, paperStyle(0),
                        "List item ${part + 1}", "List item", Modifier.weight(1f))
                }
            }
        }
        is BlockContent.Quote -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(Paper.rule))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.paragraphs.forEachIndexed { part, text ->
                    RichField(FieldId(index, part), text, editor, editable, paperStyle(0).copy(color = Paper.muted),
                        "Quote paragraph ${part + 1}", "Quote", Modifier.fillMaxWidth())
                }
            }
        }
        is BlockContent.Table -> Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, Paper.rule)
            .semantics { contentDescription = "Table with ${c.header.size} columns and ${c.rows.size + 1} rows" }) {
            (listOf(c.header) + c.rows).forEachIndexed { row, cells ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    cells.forEachIndexed { column, cell ->
                        RichField(FieldId(index, row, column), cell, editor, editable,
                            paperStyle(0).copy(fontWeight = if (row == 0) FontWeight.SemiBold else FontWeight.Normal),
                            "Table row ${row + 1} column ${column + 1}", "",
                            Modifier.width(160.dp).fillMaxHeight().background(if (row == 0) Paper.codeBackground else Paper.sheet)
                                .border(0.5.dp, Paper.rule).padding(8.dp),
                            multiline = false)
                    }
                }
            }
        }
        BlockContent.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(Paper.muted).semantics { contentDescription = "Horizontal rule" })
        BlockContent.PageBreak -> Unit
        is BlockContent.Raw -> RawBlock(index, c, block, editor, editable, project, tab, capabilities, cache)
    }
}

@Composable
private fun RawBlock(
    index: Int,
    content: BlockContent.Raw,
    block: EditorBlock,
    editor: WysiwygEditorState,
    editable: Boolean,
    project: String,
    tab: DocumentTab,
    capabilities: DocumentCapabilities,
    cache: DocumentBitmapCache,
) {
    var field by remember(index) { mutableStateOf(TextFieldValue(content.text)) }
    if (field.text != content.text) field = TextFieldValue(content.text, TextRange(field.selection.start.coerceAtMost(content.text.length)))
    fun images(n: RenderNode): List<RenderNode> = if (n.kind == "image") listOf(n) else n.children.flatMap(::images)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText("Markdown block - edited as source", style = TextStyle(color = Paper.muted, fontSize = 11.sp))
        block.node?.let { node ->
            images(node).forEach {
                DocumentImageView(it, project, tab.historical, capabilities, cache, Modifier.fillMaxWidth())
            }
        }
        BasicTextField(
            field, { value ->
                if (editable && value.text != field.text) editor.changeRaw(index, value.text)
                field = value
            },
            Modifier.fillMaxWidth().background(Paper.codeBackground).padding(10.dp)
                .semantics { contentDescription = "Markdown block ${index + 1}" },
            readOnly = !editable,
            textStyle = TextStyle(color = Paper.ink, fontSize = 13.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Paper.ink),
        )
    }
}

@Composable
private fun RichField(
    id: FieldId,
    text: InlineText,
    editor: WysiwygEditorState,
    editable: Boolean,
    style: TextStyle,
    description: String,
    placeholder: String,
    modifier: Modifier,
    multiline: Boolean = true,
) {
    val requester = remember(id) { FocusRequester() }
    var field by remember(id) { mutableStateOf(TextFieldValue(text.toAnnotated())) }
    var emitted by remember(id) { mutableStateOf(text) }
    if (text != emitted) {
        emitted = text
        field = field.copy(annotatedString = text.toAnnotated(), selection = TextRange(field.selection.start.coerceAtMost(text.length)), composition = null)
    }
    fun publish() {
        editor.focus = FieldFocus(id, emitted, field.selection) { updated ->
            emitted = updated
            field = field.copy(annotatedString = updated.toAnnotated())
            editor.changeText(id, updated)
        }
    }
    var focused by remember(id) { mutableStateOf(false) }
    var layout by remember(id) { mutableStateOf<TextLayoutResult?>(null) }
    DisposableEffect(id) { onDispose { editor.layouts.remove(id) } }
    val request = editor.focusRequest
    LaunchedEffect(request) {
        if (request != null && request.id == id) {
            requester.requestFocus()
            field = field.copy(selection = TextRange(request.selection.start.coerceAtMost(field.text.length), request.selection.end.coerceAtMost(field.text.length)))
            publish()
            editor.consumeRequest(request)
        }
    }
    BasicTextField(
        field,
        { new ->
            if (editable) {
                val old = field
                if (new.text != old.text) {
                    val pending = if (old.selection.collapsed) editor.pendingFor(old.selection.start) else null
                    val updated = emitted.applyTextChange(new.text, pending)
                    emitted = updated
                    field = new.copy(annotatedString = updated.toAnnotated())
                    editor.clearPending()
                    editor.changeText(id, updated)
                } else {
                    field = new
                    if (new.selection != old.selection && editor.pendingFor(new.selection.start) == null) editor.clearPending()
                }
                publish()
            }
        },
        modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused; if (it.isFocused) publish() }
            .semantics { contentDescription = description }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !editable) false
                else when {
                    event.key == Key.Enter && !event.isShiftPressed && !commandKey(event) ->
                        editor.enter(id, field.selection.start) || !multiline
                    event.key == Key.Enter && !multiline -> true
                    plainNavigation(event) && navigationKey(event.key, field, layout)?.let { (forward, vertical) ->
                        val l = layout
                        val x = l?.getCursorRect(field.selection.start.coerceAtMost(field.text.length))?.left ?: 0f
                        editor.navigate(id, forward, vertical, x)
                    } == true -> true
                    event.key == Key.Backspace && field.selection.collapsed && field.selection.start == 0 &&
                        !event.isMetaPressed && !event.isCtrlPressed && !event.isAltPressed -> editor.backspaceAtStart(id)
                    else -> false
                }
            },
        readOnly = !editable,
        textStyle = style,
        onTextLayout = { layout = it; editor.layouts[id] = it },
        cursorBrush = SolidColor(Paper.ink),
        decorationBox = { inner ->
            Box {
                if (text.length == 0 && placeholder.isNotEmpty() && editable && focused) BasicText(placeholder, style = style.copy(color = Paper.muted.copy(alpha = 0.7f)))
                inner()
            }
        },
    )
}

private fun plainNavigation(event: KeyEvent) =
    !event.isShiftPressed && !event.isMetaPressed && !event.isCtrlPressed && !event.isAltPressed

/** Returns (forward, vertical) when the arrow key must leave the current field, otherwise null. */
private fun navigationKey(key: Key, field: TextFieldValue, layout: TextLayoutResult?): Pair<Boolean, Boolean>? {
    if (!field.selection.collapsed) return null
    val caret = field.selection.start
    return when (key) {
        Key.DirectionLeft -> if (caret == 0) false to false else null
        Key.DirectionRight -> if (caret >= field.text.length) true to false else null
        Key.DirectionUp -> if (layout == null || layout.getLineForOffset(caret.coerceAtMost(field.text.length)) == 0) false to true else null
        Key.DirectionDown -> if (layout == null || layout.getLineForOffset(caret.coerceAtMost(field.text.length)) >= layout.lineCount - 1) true to true else null
        else -> null
    }
}

@Composable
private fun ToolButton(label: String, description: String, enabled: Boolean, active: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.semantics { contentDescription = description }
    if (active) DefaultButton(onClick, modifier, enabled = enabled) { Text(label) }
    else OutlinedButton(onClick, modifier, enabled = enabled) { Text(label) }
}

@Composable
private fun FormatToolbar(
    tab: DocumentTab,
    editor: WysiwygEditorState,
    editable: Boolean,
    linkOpen: Boolean,
    setLinkOpen: (Boolean) -> Unit,
) {
    val block = editor.focusedBlock()
    val convertible = editable && (block is BlockContent.Rich || block is BlockContent.Items || block is BlockContent.Quote)
    val inline = editable && editor.focus != null && block != null && block !is BlockContent.Raw
    val inTable = editable && block is BlockContent.Table
    val level = (block as? BlockContent.Rich)?.level
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(6.dp))
                .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIcon(EditorIcons.Undo, "Undo", editable) { tab.undoBodyEdit() }
            ToolIcon(EditorIcons.Redo, "Redo", editable) { tab.undoBodyEdit(redo = true) }
            ToolDivider()
            ToolIcon(null, "Paragraph style", convertible, level == 0, "Aa") { editor.convert(BlockTarget.Paragraph) }
            listOf(1 to BlockTarget.Heading1, 2 to BlockTarget.Heading2, 3 to BlockTarget.Heading3).forEach { (n, target) ->
                ToolIcon(null, "Heading $n", convertible, level == n, "H$n") { editor.convert(target) }
            }
            ToolDivider()
            ToolIcon(EditorIcons.Bold, "Bold", inline, editor.markActive(InlineMark.Bold)) { editor.toggleMark(InlineMark.Bold) }
            ToolIcon(EditorIcons.Italic, "Italic", inline, editor.markActive(InlineMark.Italic)) { editor.toggleMark(InlineMark.Italic) }
            ToolIcon(EditorIcons.Strike, "Strikethrough", inline, editor.markActive(InlineMark.Strike)) { editor.toggleMark(InlineMark.Strike) }
            ToolIcon(EditorIcons.Code, "Inline code", inline, editor.markActive(InlineMark.Code)) { editor.toggleMark(InlineMark.Code) }
            ToolIcon(EditorIcons.Link, "Link", inline, linkOpen) { setLinkOpen(!linkOpen) }
            ToolDivider()
            ToolIcon(EditorIcons.Bullets, "Bulleted list", convertible, (block as? BlockContent.Items)?.ordered == false) { editor.convert(BlockTarget.Bullets) }
            ToolIcon(EditorIcons.Numbered, "Numbered list", convertible, (block as? BlockContent.Items)?.ordered == true) { editor.convert(BlockTarget.Numbered) }
            ToolIcon(EditorIcons.Quote, "Quote", convertible, block is BlockContent.Quote) { editor.convert(BlockTarget.Quote) }
            ToolDivider()
            ToolIcon(EditorIcons.Table, "Insert table", editable) { editor.insertTable() }
            ToolIcon(EditorIcons.Rule, "Insert horizontal rule", editable) { editor.insertRule() }
            ToolIcon(EditorIcons.PageBreak, "Insert page break", editable) { editor.insertPageBreakBlock() }
            if (inTable) {
                ToolDivider()
                ToolIcon(null, "Add table row", true, label = "+ Row") { editor.addRow() }
                ToolIcon(null, "Remove table row", true, label = "- Row") { editor.removeRow() }
                ToolIcon(null, "Add table column", true, label = "+ Col") { editor.addColumn() }
                ToolIcon(null, "Remove table column", true, label = "- Col") { editor.removeColumn() }
            }
        }
        if (linkOpen) LinkBar(editor, editable) { setLinkOpen(false) }
    }
}

@Composable
private fun ToolDivider() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(18.dp).background(JewelTheme.globalColors.borders.normal))
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ToolIcon(
    icon: ImageVector?,
    description: String,
    enabled: Boolean,
    active: Boolean = false,
    label: String = "",
    onClick: () -> Unit,
) {
    val colors = JewelTheme.globalColors
    Tooltip(tooltip = { Text(description) }) {
        SelectableIconButton(
            selected = active, enabled = enabled, onClick = onClick,
            modifier = Modifier.defaultMinSize(minWidth = 30.dp).height(30.dp).semantics { contentDescription = description },
        ) {
            val tint = if (enabled) colors.text.normal else colors.text.disabled
            if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = tint)
            else Text(label, color = tint, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun LinkBar(editor: WysiwygEditorState, editable: Boolean, close: () -> Unit) {
    var url by remember { mutableStateOf(TextFieldValue(editor.linkAtSelection().orEmpty())) }
    var error by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(url, { url = it; error = null }, Modifier.width(320.dp).semantics { contentDescription = "Link address" }, enabled = editable)
        ToolButton("Apply", "Apply link", editable) {
            error = editor.applyLink(url.text)
            if (error == null) close()
        }
        ToolButton("Remove", "Remove link", editable) { error = editor.applyLink(""); if (error == null) close() }
        ToolButton("Cancel", "Cancel link", true) { close() }
        error?.let { Text(it, color = JewelTheme.globalColors.text.error) }
    }
}
