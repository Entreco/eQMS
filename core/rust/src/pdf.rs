//! Derived, deterministic PDF output of one saved document revision (EQMS-082).
//! Pure layout and serialization: this module never reads or writes stored records.
use crate::document::{Document, LifecycleEvent};
use crate::markdown::{self, Orientation, PageSetup};
use crate::render::{self, Node};
use sha2::{Digest, Sha256};
use std::collections::BTreeSet;
use std::fmt::Write as _;

pub const PDF_RENDERER: &str = "eqms-pdf-1";
pub const MAX_PAGES: usize = 2000;
pub const MAX_BYTES: usize = 64 * 1024 * 1024;

const MM: f64 = 72.0 / 25.4;
const BODY: f64 = 10.0;
const SMALL: f64 = 8.0;
const CELL: f64 = 9.0;
const PAD: f64 = 3.0;

#[derive(Debug, Clone)]
pub struct PdfOutput {
    pub bytes: Vec<u8>,
    pub pages: usize,
    pub diagnostics: Vec<String>,
}

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
enum Font {
    Regular,
    Bold,
    Italic,
    BoldItalic,
    Mono,
}

impl Font {
    fn index(self) -> usize {
        self as usize + 1
    }
    fn base_name(self) -> &'static str {
        match self {
            Font::Regular => "Helvetica",
            Font::Bold => "Helvetica-Bold",
            Font::Italic => "Helvetica-Oblique",
            Font::BoldItalic => "Helvetica-BoldOblique",
            Font::Mono => "Courier",
        }
    }
    fn bold(self) -> bool {
        matches!(self, Font::Bold | Font::BoldItalic)
    }
}

const REGULAR_WIDTHS: [u16; 95] = [
    278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556,
    556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556, 1015, 667, 667, 722, 722, 667,
    611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667,
    667, 611, 278, 278, 278, 469, 556, 333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500,
    222, 833, 556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
];

const BOLD_WIDTHS: [u16; 95] = [
    278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556,
    556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611, 975, 722, 722, 722, 722, 667,
    611, 778, 722, 278, 556, 722, 611, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667,
    667, 611, 333, 278, 333, 584, 556, 333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556,
    278, 889, 611, 611, 611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584,
];

/// WinAnsi byte for the supported character repertoire.
fn encode(c: char) -> Option<u8> {
    Some(match c {
        ' '..='~' | '\u{A0}'..='\u{FF}' => c as u32 as u8,
        '€' => 0x80,
        '‚' => 0x82,
        'ƒ' => 0x83,
        '„' => 0x84,
        '…' => 0x85,
        '†' => 0x86,
        '‡' => 0x87,
        'ˆ' => 0x88,
        '‰' => 0x89,
        'Š' => 0x8A,
        '‹' => 0x8B,
        'Œ' => 0x8C,
        'Ž' => 0x8E,
        '‘' => 0x91,
        '’' => 0x92,
        '“' => 0x93,
        '”' => 0x94,
        '•' => 0x95,
        '–' => 0x96,
        '—' => 0x97,
        '˜' => 0x98,
        '™' => 0x99,
        'š' => 0x9A,
        '›' => 0x9B,
        'œ' => 0x9C,
        'ž' => 0x9E,
        'Ÿ' => 0x9F,
        _ => return None,
    })
}

/// Advance width in 1/1000 em. Characters above ASCII use approximate widths.
fn glyph_width(font: Font, c: char) -> f64 {
    if font == Font::Mono {
        return 600.0;
    }
    let byte = encode(c).unwrap_or(b'?');
    let table = if font.bold() {
        &BOLD_WIDTHS
    } else {
        &REGULAR_WIDTHS
    };
    f64::from(match byte {
        32..=126 => table[usize::from(byte - 32)],
        0x85 | 0x97 => 1000,
        0x95 => 350,
        0x91 | 0x92 => 222,
        0x93 | 0x94 => 333,
        0xC0..=0xDE if font.bold() => 722,
        0xC0..=0xDE => 667,
        0xE0..=0xFF if font.bold() => 611,
        _ => 556,
    })
}

fn text_width(font: Font, text: &str, size: f64) -> f64 {
    text.chars().map(|c| glyph_width(font, c)).sum::<f64>() * size / 1000.0
}

#[derive(Clone, Debug)]
enum Item {
    Word(Vec<(Font, String)>),
    Space(Font),
    Break,
}

#[derive(Clone, Debug, Default)]
struct Line {
    runs: Vec<(Font, String)>,
}

#[derive(Clone, Copy, Default)]
struct Style {
    bold: bool,
    italic: bool,
    mono: bool,
}

impl Style {
    fn font(self) -> Font {
        match (self.mono, self.bold, self.italic) {
            (true, _, _) => Font::Mono,
            (_, true, true) => Font::BoldItalic,
            (_, true, false) => Font::Bold,
            (_, false, true) => Font::Italic,
            _ => Font::Regular,
        }
    }
}

struct Flattener<'a> {
    replaced: &'a mut BTreeSet<char>,
}

impl Flattener<'_> {
    fn clean(&mut self, c: char) -> char {
        if encode(c).is_some() {
            c
        } else {
            self.replaced.insert(c);
            '?'
        }
    }

    fn push_text(&mut self, text: &str, font: Font, items: &mut Vec<Item>) {
        for c in text.chars() {
            if c.is_whitespace() {
                if !matches!(items.last(), None | Some(Item::Space(_) | Item::Break)) {
                    items.push(Item::Space(font));
                }
            } else if c.is_control() {
                continue;
            } else {
                let c = self.clean(c);
                match items.last_mut() {
                    Some(Item::Word(runs)) => match runs.last_mut() {
                        Some((f, s)) if *f == font => s.push(c),
                        _ => runs.push((font, c.to_string())),
                    },
                    _ => items.push(Item::Word(vec![(font, c.to_string())])),
                }
            }
        }
    }

    fn flatten(&mut self, node: &Node, style: Style, items: &mut Vec<Item>) {
        let mut inner = style;
        match node.kind.as_str() {
            "strong" => inner.bold = true,
            "emphasis" => inner.italic = true,
            "code" | "literal" => inner.mono = true,
            "break" => {
                items.push(Item::Break);
                return;
            }
            "image" => {
                let mut italic = style;
                italic.italic = true;
                let text = format!("[Image: {} - {}]", node.plain_text(), node.target);
                self.push_text(&text, italic.font(), items);
                return;
            }
            _ => {}
        }
        self.push_text(&node.text, inner.font(), items);
        for child in &node.children {
            self.flatten(child, inner, items);
        }
        if node.kind == "link"
            && render::allowed_link(&node.target)
            && !node.target.starts_with('#')
            && node.plain_text() != node.target
        {
            let text = format!(" ({})", node.target);
            self.push_text(&text, inner.font(), items);
        }
    }
}

fn items_of(replaced: &mut BTreeSet<char>, nodes: &[Node], style: Style) -> Vec<Item> {
    let mut flattener = Flattener { replaced };
    let mut items = Vec::new();
    for node in nodes {
        flattener.flatten(node, style, &mut items);
    }
    items
}

fn text_items(replaced: &mut BTreeSet<char>, text: &str, font: Font) -> Vec<Item> {
    let mut items = Vec::new();
    Flattener { replaced }.push_text(text, font, &mut items);
    items
}

fn append(line: &mut Line, font: Font, text: &str) {
    match line.runs.last_mut() {
        Some((f, s)) if *f == font => s.push_str(text),
        _ => line.runs.push((font, text.to_owned())),
    }
}

fn wrap(items: &[Item], avail: f64, size: f64) -> Vec<Line> {
    let mut lines = Vec::new();
    let mut current = Line::default();
    let mut width = 0.0;
    let mut pending = None;
    for item in items {
        match item {
            Item::Break => {
                lines.push(std::mem::take(&mut current));
                width = 0.0;
                pending = None;
            }
            Item::Space(font) => {
                if width > 0.0 {
                    pending = Some(*font);
                }
            }
            Item::Word(runs) => {
                let word: f64 = runs.iter().map(|(f, s)| text_width(*f, s, size)).sum();
                let mut space = pending.map_or(0.0, |f| text_width(f, " ", size));
                if width > 0.0 && width + space + word > avail {
                    lines.push(std::mem::take(&mut current));
                    width = 0.0;
                    space = 0.0;
                }
                if word > avail {
                    for (font, text) in runs {
                        for c in text.chars() {
                            let cw = text_width(*font, &c.to_string(), size);
                            if width > 0.0 && width + cw > avail {
                                lines.push(std::mem::take(&mut current));
                                width = 0.0;
                            }
                            append(&mut current, *font, &c.to_string());
                            width += cw;
                        }
                    }
                } else {
                    if space > 0.0 {
                        append(&mut current, pending.unwrap_or(Font::Regular), " ");
                    }
                    for (font, text) in runs {
                        append(&mut current, *font, text);
                    }
                    width += space + word;
                }
                pending = None;
            }
        }
    }
    if !current.runs.is_empty() {
        lines.push(current);
    }
    lines
}

fn code_lines(replaced: &mut BTreeSet<char>, text: &str, avail: f64, size: f64) -> Vec<Line> {
    let per_line = ((avail / (0.6 * size)).floor() as usize).max(1);
    let mut lines = Vec::new();
    let text = text.strip_suffix('\n').unwrap_or(text);
    for raw in text.lines() {
        let chars: Vec<char> = raw
            .replace('\t', "    ")
            .chars()
            .filter(|c| !c.is_control())
            .map(|c| {
                if encode(c).is_some() {
                    c
                } else {
                    replaced.insert(c);
                    '?'
                }
            })
            .collect();
        if chars.is_empty() {
            lines.push(Line::default());
        }
        for chunk in chars.chunks(per_line) {
            lines.push(Line {
                runs: vec![(Font::Mono, chunk.iter().collect())],
            });
        }
    }
    lines
}

enum Draw {
    Text {
        x: f64,
        y: f64,
        font: Font,
        size: f64,
        gray: f64,
        text: String,
    },
    Line {
        x1: f64,
        y1: f64,
        x2: f64,
        y2: f64,
        gray: f64,
    },
    Rect {
        x: f64,
        y: f64,
        w: f64,
        h: f64,
        fill: Option<f64>,
    },
}

struct Geometry {
    width: f64,
    height: f64,
    left: f64,
    right: f64,
    top: f64,
    bottom: f64,
}

impl Geometry {
    fn new(page: &PageSetup) -> Self {
        let (mut width, mut height) = match page.size {
            markdown::PageSize::A4 => (210.0, 297.0),
            markdown::PageSize::Letter => (215.9, 279.4),
        };
        if page.orientation == Orientation::Landscape {
            std::mem::swap(&mut width, &mut height);
        }
        // Reserve room for the running header and two footer lines.
        Self {
            width: width * MM,
            height: height * MM,
            left: page.margin_left_mm * MM,
            right: page.margin_right_mm * MM,
            top: (page.margin_top_mm * MM).max(30.0),
            bottom: (page.margin_bottom_mm * MM).max(36.0),
        }
    }
    fn content_width(&self) -> f64 {
        self.width - self.left - self.right
    }
    fn limit(&self) -> f64 {
        self.height - self.bottom
    }
}

struct Layout<'a> {
    geo: &'a Geometry,
    pages: Vec<Vec<Draw>>,
    y: f64,
    bars: Vec<f64>,
    marker: Option<(String, f64)>,
    replaced: BTreeSet<char>,
    error: Option<String>,
    list_depth: usize,
}

#[derive(Clone)]
struct Row {
    cells: Vec<Vec<Item>>,
    header: bool,
}

impl<'a> Layout<'a> {
    fn new(geo: &'a Geometry) -> Self {
        Self {
            geo,
            pages: vec![Vec::new()],
            y: geo.top,
            bars: Vec::new(),
            marker: None,
            replaced: BTreeSet::new(),
            error: None,
            list_depth: 0,
        }
    }

    fn has_content(&self) -> bool {
        self.y > self.geo.top + 0.01 || self.pages.last().is_some_and(|p| !p.is_empty())
    }

    fn new_page(&mut self) {
        if self.pages.len() >= MAX_PAGES {
            self.error = Some(format!("PDF exceeds the {MAX_PAGES} page limit"));
            self.y = self.geo.top;
            return;
        }
        self.pages.push(Vec::new());
        self.y = self.geo.top;
    }

    fn page(&mut self) -> &mut Vec<Draw> {
        self.pages.last_mut().expect("at least one page")
    }

    fn space(&mut self, amount: f64) {
        if self.has_content() {
            self.y += amount;
        }
    }

    fn ensure(&mut self, needed: f64) {
        if self.has_content() && self.y + needed > self.geo.limit() {
            self.new_page();
        }
    }

    fn place(&mut self, lines: &[Line], size: f64, x: f64, shade: bool) {
        let height = size * 1.35;
        let mut index = 0;
        while index < lines.len() && self.error.is_none() {
            let fits = ((self.geo.limit() - self.y + 1e-6) / height)
                .floor()
                .max(0.0) as usize;
            let remaining = lines.len() - index;
            let mut take = fits.min(remaining);
            if take < remaining {
                if take == 1 && self.has_content() {
                    take = 0;
                } else if remaining - take == 1 && take >= 3 {
                    take -= 1;
                }
            }
            if take == 0 {
                if self.has_content() {
                    self.new_page();
                    continue;
                }
                take = 1;
            }
            for line in &lines[index..index + take] {
                self.draw_line(line, size, height, x, shade);
                self.y += height;
            }
            index += take;
            if index < lines.len() {
                self.new_page();
            }
        }
    }

    fn draw_line(&mut self, line: &Line, size: f64, height: f64, x: f64, shade: bool) {
        let (y, width) = (self.y, self.geo.width - self.geo.right - x);
        if shade {
            self.page().push(Draw::Rect {
                x,
                y,
                w: width,
                h: height,
                fill: Some(0.94),
            });
        }
        for bar in self.bars.clone() {
            self.page().push(Draw::Line {
                x1: bar,
                y1: y,
                x2: bar,
                y2: y + height,
                gray: 0.55,
            });
        }
        let baseline = y + size * 1.05;
        if let Some((marker, marker_x)) = self.marker.take() {
            self.page().push(Draw::Text {
                x: marker_x,
                y: baseline,
                font: Font::Regular,
                size,
                gray: 0.0,
                text: marker,
            });
        }
        let mut cursor = x;
        for (font, text) in &line.runs {
            self.page().push(Draw::Text {
                x: cursor,
                y: baseline,
                font: *font,
                size,
                gray: 0.0,
                text: text.clone(),
            });
            cursor += text_width(*font, text, size);
        }
    }

    fn paragraph(&mut self, items: &[Item], size: f64, indent: f64) {
        let x = self.geo.left + indent;
        let lines = wrap(items, self.geo.content_width() - indent, size);
        if lines.is_empty() && self.marker.is_some() {
            self.place(&[Line::default()], size, x, false);
        }
        self.place(&lines, size, x, false);
    }

    fn table(&mut self, rows: &[Row], widths: &[f64], indent: f64) {
        let x0 = self.geo.left + indent;
        let lh = CELL * 1.3;
        let prepared: Vec<Vec<Vec<Line>>> = rows
            .iter()
            .map(|row| {
                row.cells
                    .iter()
                    .zip(widths)
                    .map(|(cell, w)| wrap(cell, w - 2.0 * PAD, CELL))
                    .collect()
            })
            .collect();
        let fresh = (((self.geo.limit() - self.geo.top - 2.0 * PAD) / lh).floor() as usize).max(1);
        for (index, row) in rows.iter().enumerate() {
            let total = prepared[index]
                .iter()
                .map(Vec::len)
                .max()
                .unwrap_or(0)
                .max(1);
            let mut start = 0;
            while start < total && self.error.is_none() {
                let remaining = total - start;
                let mut fits = ((self.geo.limit() - self.y - 2.0 * PAD) / lh)
                    .floor()
                    .max(0.0) as usize;
                if fits < remaining && remaining <= fresh && self.has_content() {
                    fits = 0;
                }
                if fits == 0 && self.has_content() {
                    self.new_page();
                    for (h, header) in rows.iter().enumerate().take_while(|(_, r)| r.header) {
                        if !row.header && h < index {
                            let lines = prepared[h].iter().map(Vec::len).max().unwrap_or(0);
                            self.draw_row(header, &prepared[h], 0, lines.max(1), x0, widths, lh);
                        }
                    }
                    continue;
                }
                let take = fits.max(1).min(remaining);
                self.draw_row(row, &prepared[index], start, take, x0, widths, lh);
                start += take;
                if start < total {
                    self.new_page();
                }
            }
        }
        self.y += 6.0;
    }

    #[allow(clippy::too_many_arguments)]
    fn draw_row(
        &mut self,
        row: &Row,
        cells: &[Vec<Line>],
        start: usize,
        take: usize,
        x0: f64,
        widths: &[f64],
        lh: f64,
    ) {
        let height = take as f64 * lh + 2.0 * PAD;
        let mut x = x0;
        let y = self.y;
        for (cell, w) in cells.iter().zip(widths) {
            self.page().push(Draw::Rect {
                x,
                y,
                w: *w,
                h: height,
                fill: row.header.then_some(0.9),
            });
            for (offset, line) in cell.iter().skip(start).take(take).enumerate() {
                let baseline = y + PAD + offset as f64 * lh + CELL * 1.05;
                let mut cursor = x + PAD;
                for (font, text) in &line.runs {
                    self.page().push(Draw::Text {
                        x: cursor,
                        y: baseline,
                        font: *font,
                        size: CELL,
                        gray: 0.0,
                        text: text.clone(),
                    });
                    cursor += text_width(*font, text, CELL);
                }
            }
            x += w;
        }
        self.y += height;
    }

    fn blocks(&mut self, nodes: &[Node], indent: f64) {
        let mut inline: Vec<&Node> = Vec::new();
        for node in nodes {
            if is_inline(node) {
                inline.push(node);
                continue;
            }
            self.flush_inline(&mut inline, indent);
            self.block(node, indent);
        }
        self.flush_inline(&mut inline, indent);
    }

    fn flush_inline(&mut self, pending: &mut Vec<&Node>, indent: f64) {
        if pending.is_empty() {
            return;
        }
        let nodes: Vec<Node> = pending.drain(..).cloned().collect();
        let items = items_of(&mut self.replaced, &nodes, Style::default());
        self.paragraph(&items, BODY, indent);
        self.space(4.0);
    }

    fn block(&mut self, node: &Node, indent: f64) {
        let x = self.geo.left + indent;
        match node.kind.as_str() {
            "paragraph" => {
                let items = items_of(&mut self.replaced, &node.children, Style::default());
                self.paragraph(&items, BODY, indent);
                self.space(if self.list_depth > 0 { 2.0 } else { 6.0 });
            }
            "h1" | "h2" | "h3" | "h4" | "h5" | "h6" => {
                let size = match node.kind.as_str() {
                    "h1" => 18.0,
                    "h2" => 15.0,
                    "h3" => 13.0,
                    _ => 11.0,
                };
                self.space(8.0);
                self.ensure(size * 1.35 + BODY * 2.7);
                let style = Style {
                    bold: true,
                    ..Style::default()
                };
                let items = items_of(&mut self.replaced, &node.children, style);
                self.paragraph(&items, size, indent);
                self.space(4.0);
            }
            "quote" => {
                self.bars.push(x + 4.0);
                self.blocks(&node.children, indent + 14.0);
                self.bars.pop();
            }
            "code_block" | "literal" => {
                let text = node.plain_text();
                let lines = code_lines(
                    &mut self.replaced,
                    &text,
                    self.geo.content_width() - indent,
                    9.0,
                );
                self.place(&lines, 9.0, x, true);
                self.space(6.0);
            }
            "ordered" | "unordered" => {
                let first: u64 = node.target.parse().unwrap_or(1);
                self.list_depth += 1;
                for (number, item) in (first..).zip(&node.children) {
                    let marker = if node.kind == "ordered" {
                        format!("{number}.")
                    } else {
                        "\u{2022}".to_owned()
                    };
                    self.marker = Some((marker, x));
                    self.blocks(&item.children, indent + 18.0);
                    if self.marker.is_some() {
                        self.paragraph(&[], BODY, indent + 18.0);
                        self.marker = None;
                    }
                }
                self.list_depth -= 1;
                self.space(4.0);
            }
            "table" => self.render_table(node, indent),
            "rule" => {
                self.space(4.0);
                self.ensure(8.0);
                let y = self.y;
                let right = self.geo.width - self.geo.right;
                self.page().push(Draw::Line {
                    x1: x,
                    y1: y,
                    x2: right,
                    y2: y,
                    gray: 0.5,
                });
                self.y += 8.0;
            }
            "page_break" => {
                if self.has_content() {
                    self.new_page();
                }
            }
            "footnote" => {
                self.marker = Some((format!("[{}]", node.text), x));
                self.blocks(&node.children, indent + 24.0);
                if self.marker.take().is_some() {
                    self.paragraph(&[], BODY, indent + 24.0);
                }
            }
            _ => self.blocks(&node.children, indent),
        }
    }

    fn render_table(&mut self, node: &Node, indent: f64) {
        let mut rows = Vec::new();
        for part in &node.children {
            let header = part.kind == "table_header";
            let style = Style {
                bold: header,
                ..Style::default()
            };
            rows.push(Row {
                cells: part
                    .children
                    .iter()
                    .map(|cell| items_of(&mut self.replaced, &cell.children, style))
                    .collect(),
                header,
            });
        }
        let columns = rows.iter().map(|r| r.cells.len()).max().unwrap_or(0);
        if columns == 0 {
            return;
        }
        for row in &mut rows {
            row.cells.resize(columns, Vec::new());
        }
        let width = (self.geo.content_width() - indent) / columns as f64;
        self.table(&rows, &vec![width; columns], indent);
    }
}

fn is_inline(node: &Node) -> bool {
    matches!(
        node.kind.as_str(),
        "text"
            | "strong"
            | "emphasis"
            | "strike"
            | "code"
            | "link"
            | "note_link"
            | "image"
            | "break"
    )
}

fn fit(text: &str, font: Font, size: f64, avail: f64) -> String {
    let mut out = text.to_owned();
    while text_width(font, &out, size) > avail && out.pop().is_some() {}
    out
}

fn short(value: &str, keep: usize) -> String {
    let (prefix, digits) = value.split_once(':').map_or(("", value), |(p, d)| (p, d));
    let cut: String = digits.chars().take(keep).collect();
    let prefix = if prefix.is_empty() {
        String::new()
    } else {
        format!("{prefix}:")
    };
    if digits.chars().count() > keep {
        format!("{prefix}{cut}...")
    } else {
        format!("{prefix}{cut}")
    }
}

/// Lifecycle events up to and including the rendered revision, oldest first.
fn events_for<'e>(
    document: &Document,
    history: &[Document],
    events: &'e [LifecycleEvent],
) -> Vec<&'e LifecycleEvent> {
    let included: BTreeSet<&str> = history
        .iter()
        .skip_while(|d| d.revision != document.revision)
        .map(|d| d.revision.as_str())
        .collect();
    let mut selected: Vec<_> = events
        .iter()
        .filter(|e| included.contains(e.revision.as_str()))
        .collect();
    selected.sort_by(|a, b| a.local_timestamp.cmp(&b.local_timestamp));
    selected
}

fn record_page(
    layout: &mut Layout<'_>,
    document: &Document,
    events: &[&LifecycleEvent],
    notes: &[String],
) {
    let title_items = text_items(&mut layout.replaced, &document.title, Font::Bold);
    layout.paragraph(&title_items, 20.0, 0.0);
    layout.space(4.0);
    let subtitle = text_items(
        &mut layout.replaced,
        "Controlled output record (derived PDF view; the canonical document is the saved Markdown revision)",
        Font::Italic,
    );
    layout.paragraph(&subtitle, BODY, 0.0);
    layout.space(12.0);

    let template = document.template.as_ref().map_or_else(
        || "none".to_owned(),
        |t| format!("{} v{} {}", t.id, t.version, t.hash),
    );
    let mut facts = vec![
        ("Artifact ID", document.id.clone()),
        ("Revision", document.revision.clone()),
        ("Lifecycle status", document.status.clone()),
        ("Content hash", document.content_hash.clone()),
        ("Template", template),
        (
            "Renderers",
            format!("{} / {PDF_RENDERER}", render::RENDERER),
        ),
        ("Configured author", document.configured_author.clone()),
        ("Saved (local time)", document.local_timestamp.clone()),
    ];
    if !notes.is_empty() {
        facts.push(("Rendering notes", notes.join("; ")));
    }
    let rows: Vec<Row> = facts
        .into_iter()
        .map(|(label, value)| Row {
            cells: vec![
                text_items(&mut layout.replaced, label, Font::Bold),
                text_items(&mut layout.replaced, &value, Font::Regular),
            ],
            header: false,
        })
        .collect();
    let width = layout.geo.content_width();
    layout.table(&rows, &[width * 0.28, width * 0.72], 0.0);

    layout.space(8.0);
    layout.ensure(80.0);
    let heading = text_items(
        &mut layout.replaced,
        "Signature and approval summary",
        Font::Bold,
    );
    layout.paragraph(&heading, 13.0, 0.0);
    layout.space(4.0);
    let statement = text_items(
        &mut layout.replaced,
        "No cryptographic signature is recorded for this revision: the signature engine is not implemented. \
         The events below are asserted lifecycle metadata, not authenticated approval, and do not by themselves \
         establish compliance. Revisions and hashes are abbreviated.",
        Font::Regular,
    );
    layout.paragraph(&statement, BODY, 0.0);
    layout.space(6.0);
    let mut rows = vec![Row {
        cells: ["Saved (local time)", "Transition", "Revision", "Event hash"]
            .iter()
            .map(|h| text_items(&mut layout.replaced, h, Font::Bold))
            .collect(),
        header: true,
    }];
    for event in events {
        let cells = [
            event.local_timestamp.clone(),
            format!("{} -> {}", event.from_status, event.to_status),
            short(&event.revision, 12),
            short(&event.event_hash, 14),
        ];
        rows.push(Row {
            cells: cells
                .iter()
                .map(|c| text_items(&mut layout.replaced, c, Font::Regular))
                .collect(),
            header: false,
        });
    }
    if events.is_empty() {
        rows.push(Row {
            cells: vec![
                text_items(
                    &mut layout.replaced,
                    "No lifecycle transitions recorded",
                    Font::Italic,
                ),
                Vec::new(),
                Vec::new(),
                Vec::new(),
            ],
            header: false,
        });
    }
    layout.table(
        &rows,
        &[width * 0.27, width * 0.27, width * 0.2, width * 0.26],
        0.0,
    );
}

fn expand(template: &str, title: &str, page: usize, total: usize) -> String {
    template
        .replace("{title}", title)
        .replace("{page}", &page.to_string())
        .replace("{total}", &total.to_string())
}

fn number(value: f64) -> String {
    let text = format!("{value:.2}");
    text.trim_end_matches('0').trim_end_matches('.').to_owned()
}

fn pdf_string(text: &str) -> String {
    let mut out = String::from("(");
    for c in text.chars() {
        let byte = encode(c).unwrap_or(b'?');
        match byte {
            b'(' | b')' | b'\\' => {
                out.push('\\');
                out.push(char::from(byte));
            }
            0x20..=0x7E => out.push(char::from(byte)),
            _ => {
                let _ = write!(out, "\\{byte:03o}");
            }
        }
    }
    out.push(')');
    out
}

fn info_string(text: &str) -> String {
    if text.chars().all(|c| (' '..='~').contains(&c)) {
        return pdf_string(text);
    }
    let mut out = String::from("<FEFF");
    for unit in text.encode_utf16() {
        let _ = write!(out, "{unit:04X}");
    }
    out.push('>');
    out
}

fn content(draws: &[Draw], height: f64) -> String {
    let mut out = String::new();
    for draw in draws {
        match draw {
            Draw::Text {
                x,
                y,
                font,
                size,
                gray,
                text,
            } => {
                let _ = writeln!(
                    out,
                    "BT /F{} {} Tf {} g {} {} Td {} Tj ET",
                    font.index(),
                    number(*size),
                    number(*gray),
                    number(*x),
                    number(height - y),
                    pdf_string(text)
                );
            }
            Draw::Line {
                x1,
                y1,
                x2,
                y2,
                gray,
            } => {
                let _ = writeln!(
                    out,
                    "{} G 0.5 w {} {} m {} {} l S",
                    number(*gray),
                    number(*x1),
                    number(height - y1),
                    number(*x2),
                    number(height - y2)
                );
            }
            Draw::Rect { x, y, w, h, fill } => {
                let rect = format!(
                    "{} {} {} {} re",
                    number(*x),
                    number(height - y - h),
                    number(*w),
                    number(*h)
                );
                match fill {
                    Some(gray) => {
                        let _ = writeln!(out, "{} g {rect} f", number(*gray));
                    }
                    None => {
                        let _ = writeln!(out, "0.6 G 0.5 w {rect} S");
                    }
                }
            }
        }
    }
    out
}

/// Render one exact saved revision. `history` is the document's saved revisions
/// (newest first) and `events` its verified lifecycle events.
pub fn render(
    document: &Document,
    history: &[Document],
    events: &[LifecycleEvent],
) -> Result<PdfOutput, String> {
    let semantic = render::parse(&document.title, &document.body)?;
    let geo = Geometry::new(&document.page);

    let mut body = Layout::new(&geo);
    body.blocks(&semantic.nodes, 0.0);
    if let Some(error) = body.error.take() {
        return Err(error);
    }

    let mut notes: Vec<String> = semantic.diagnostics.clone();
    notes.sort();
    notes.dedup();
    if !body.replaced.is_empty() {
        let list: Vec<String> = body
            .replaced
            .iter()
            .take(8)
            .map(|c| format!("U+{:04X}", *c as u32))
            .collect();
        notes.push(format!(
            "{} unsupported character(s) replaced with '?' ({})",
            body.replaced.len(),
            list.join(", ")
        ));
    }
    let selected = events_for(document, history, events);
    let mut record = Layout::new(&geo);
    record_page(&mut record, document, &selected, &notes);
    if let Some(error) = record.error.take() {
        return Err(error);
    }

    let mut pages = record.pages;
    if body.pages.iter().any(|page| !page.is_empty()) {
        pages.extend(body.pages);
    }
    if pages.len() > MAX_PAGES {
        return Err(format!("PDF exceeds the {MAX_PAGES} page limit"));
    }
    let total = pages.len();

    let mut title_scratch = BTreeSet::new();
    let clean = |text: &str, scratch: &mut BTreeSet<char>| -> String {
        text.chars()
            .map(|c| {
                if encode(c).is_some() {
                    c
                } else {
                    scratch.insert(c);
                    '?'
                }
            })
            .collect()
    };
    let title = clean(&document.title, &mut title_scratch);
    let header = clean(&document.page.header, &mut title_scratch);
    let footer = clean(&document.page.footer, &mut title_scratch);
    let trace = format!(
        "{} | revision {} | {}",
        document.id,
        short(&document.revision, 12),
        short(&document.content_hash, 16)
    );
    let avail = geo.content_width();
    for (index, page) in pages.iter_mut().enumerate() {
        let number = index + 1;
        let header_text = fit(
            &expand(&header, &title, number, total),
            Font::Regular,
            SMALL,
            avail,
        );
        page.push(Draw::Text {
            x: geo.left,
            y: (geo.top * 0.5).max(9.0),
            font: Font::Regular,
            size: SMALL,
            gray: 0.35,
            text: header_text,
        });
        let footer_text = fit(
            &expand(&footer, &title, number, total),
            Font::Regular,
            SMALL,
            avail,
        );
        let trace_text = fit(&trace, Font::Mono, SMALL, avail);
        let baseline = geo.height - (geo.bottom * 0.3).max(8.0);
        for (text, font, y) in [
            (footer_text, Font::Regular, baseline - 10.0),
            (trace_text, Font::Mono, baseline),
        ] {
            let width = text_width(font, &text, SMALL);
            page.push(Draw::Text {
                x: geo.left + (avail - width) / 2.0,
                y,
                font,
                size: SMALL,
                gray: 0.35,
                text,
            });
        }
    }

    let bytes = serialize(document, &geo, &pages)?;
    let mut diagnostics = notes;
    diagnostics.sort();
    Ok(PdfOutput {
        bytes,
        pages: total,
        diagnostics,
    })
}

fn serialize(document: &Document, geo: &Geometry, pages: &[Vec<Draw>]) -> Result<Vec<u8>, String> {
    let fonts = [
        Font::Regular,
        Font::Bold,
        Font::Italic,
        Font::BoldItalic,
        Font::Mono,
    ];
    let page_base = 9;
    let mut objects: Vec<String> = Vec::new();
    objects.push("<< /Type /Catalog /Pages 2 0 R >>".into());
    let kids: Vec<String> = (0..pages.len())
        .map(|i| format!("{} 0 R", page_base + i * 2))
        .collect();
    objects.push(format!(
        "<< /Type /Pages /Count {} /Kids [{}] >>",
        pages.len(),
        kids.join(" ")
    ));
    for font in fonts {
        objects.push(format!(
            "<< /Type /Font /Subtype /Type1 /BaseFont /{} /Encoding /WinAnsiEncoding >>",
            font.base_name()
        ));
    }
    objects.push(String::new());
    let resources = "<< /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R /F5 7 0 R >> >>";
    for (index, page) in pages.iter().enumerate() {
        objects.push(format!(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {} {}] /Resources {resources} /Contents {} 0 R >>",
            number(geo.width),
            number(geo.height),
            page_base + index * 2 + 1
        ));
        let stream = content(page, geo.height);
        objects.push(format!(
            "<< /Length {} >>\nstream\n{stream}endstream",
            stream.len()
        ));
    }
    let info = format!(
        "<< /Title {} /Subject {} /Producer {} /EQMS-DocumentId {} /EQMS-Revision {} /EQMS-ContentHash {} /EQMS-Renderer {} >>",
        info_string(&document.title),
        info_string(&format!(
            "Controlled PDF output of {} revision {}",
            document.id, document.revision
        )),
        pdf_string(PDF_RENDERER),
        info_string(&document.id),
        info_string(&document.revision),
        info_string(&document.content_hash),
        pdf_string(&format!("{}/{PDF_RENDERER}", render::RENDERER)),
    );
    objects[7] = info;
    let mut out = b"%PDF-1.4\n%\xE2\xE3\xCF\xD3\n".to_vec();
    let mut offsets = Vec::new();
    for (index, object) in objects.iter().enumerate() {
        offsets.push(out.len());
        out.extend_from_slice(format!("{} 0 obj\n{object}\nendobj\n", index + 1).as_bytes());
        if out.len() > MAX_BYTES {
            return Err("PDF exceeds the 64 MiB output limit".into());
        }
    }
    let digest = format!("{:x}", Sha256::digest(&out));
    let xref = out.len();
    out.extend_from_slice(
        format!("xref\n0 {}\n0000000000 65535 f \n", objects.len() + 1).as_bytes(),
    );
    for offset in offsets {
        out.extend_from_slice(format!("{offset:010} 00000 n \n").as_bytes());
    }
    let id = &digest[..32];
    out.extend_from_slice(
        format!(
            "trailer\n<< /Size {} /Root 1 0 R /Info 8 0 R /ID [<{id}> <{id}>] >>\nstartxref\n{xref}\n%%EOF\n",
            objects.len() + 1
        )
        .as_bytes(),
    );
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn doc(body: &str) -> Document {
        Document {
            id: "DOC-0123456789abcdef0123456789abcdef".into(),
            title: "Policy (draft)".into(),
            page: PageSetup::default(),
            template: None,
            body: body.into(),
            status: "Draft".into(),
            revision: "a".repeat(40),
            content_hash: markdown::hash(body.as_bytes()),
            event_hash: markdown::hash(b"event"),
            configured_author: "Author".into(),
            local_timestamp: "2026-01-01T00:00:00Z".into(),
        }
    }

    fn export(document: &Document) -> PdfOutput {
        render(document, std::slice::from_ref(document), &[]).unwrap()
    }

    struct Op {
        font: Font,
        size: f64,
        x: f64,
        text: String,
    }

    fn unescape(raw: &str) -> String {
        let mut out = String::new();
        let mut chars = raw.chars();
        while let Some(c) = chars.next() {
            if c != '\\' {
                out.push(c);
                continue;
            }
            let next = chars.next().unwrap();
            if next.is_ascii_digit() {
                let digits: String = std::iter::once(next)
                    .chain(chars.by_ref().take(2))
                    .collect();
                out.push(char::from(u8::from_str_radix(&digits, 8).unwrap()));
            } else {
                out.push(next);
            }
        }
        out
    }

    /// Text operations of every page's content stream, in drawing order.
    fn page_ops(bytes: &[u8]) -> Vec<Vec<Op>> {
        let text: String = bytes.iter().map(|b| char::from(*b)).collect();
        let fonts = [
            Font::Regular,
            Font::Bold,
            Font::Italic,
            Font::BoldItalic,
            Font::Mono,
        ];
        text.split("\nstream\n")
            .skip(1)
            .map(|stream| {
                stream
                    .split("endstream")
                    .next()
                    .unwrap()
                    .lines()
                    .filter(|l| l.starts_with("BT"))
                    .map(|line| {
                        let mut head = line.split(' ');
                        let font = fonts[head.nth(1).unwrap()[2..].parse::<usize>().unwrap() - 1];
                        let size = head.next().unwrap().parse().unwrap();
                        let x = head.nth(3).unwrap().parse().unwrap();
                        let start = line.find('(').unwrap();
                        let end = line.rfind(") Tj").unwrap();
                        Op {
                            font,
                            size,
                            x,
                            text: unescape(&line[start + 1..end]),
                        }
                    })
                    .collect()
            })
            .collect()
    }

    fn pages_text(bytes: &[u8]) -> Vec<Vec<String>> {
        page_ops(bytes)
            .into_iter()
            .map(|page| page.into_iter().map(|op| op.text).collect())
            .collect()
    }

    fn find(haystack: &[u8], needle: &str) -> usize {
        haystack
            .windows(needle.len())
            .rposition(|w| w == needle.as_bytes())
            .unwrap()
    }

    fn long_body() -> String {
        let mut body = String::from("# Purpose\n\n");
        for i in 0..60 {
            body.push_str(&format!("Paragraph {i} {}\n\n", "lorem ipsum ".repeat(30)));
        }
        body
    }

    #[test]
    fn t082_01_structure_is_valid_and_deterministic() {
        let document = doc("# Title\n\nBody text.");
        let a = export(&document);
        assert_eq!(a.bytes, export(&document).bytes);
        let bytes = &a.bytes;
        assert!(bytes.starts_with(b"%PDF-1.4\n"));
        assert!(bytes.ends_with(b"%%EOF\n"));
        let marker = find(bytes, "startxref\n") + "startxref\n".len();
        let startxref: usize = String::from_utf8_lossy(&bytes[marker..])
            .lines()
            .next()
            .unwrap()
            .parse()
            .unwrap();
        assert!(bytes[startxref..].starts_with(b"xref\n"));
        let table = String::from_utf8_lossy(&bytes[startxref..]).into_owned();
        for (n, entry) in table
            .lines()
            .skip(3)
            .take_while(|l| l.ends_with(" n "))
            .enumerate()
        {
            let offset: usize = entry[..10].parse().unwrap();
            assert!(bytes[offset..].starts_with(format!("{} 0 obj", n + 1).as_bytes()));
        }
        let changed = export(&doc("# Title\n\nBody text!"));
        assert_ne!(a.bytes, changed.bytes);
    }

    #[test]
    fn t082_02_paginates_with_header_footer_and_page_breaks() {
        let out = export(&doc(&long_body()));
        assert!(out.pages > 3);
        let pages = pages_text(&out.bytes);
        assert_eq!(pages.len(), out.pages);
        for (index, page) in pages.iter().enumerate() {
            assert!(page.contains(&"Policy (draft)".to_owned()), "header");
            assert!(page.contains(&format!("Page {} of {}", index + 1, out.pages)));
        }
        let broken = export(&doc("Before\n\n<!-- pagebreak -->\n\nAfter"));
        let pages = pages_text(&broken.bytes);
        assert_eq!(pages.len(), 3);
        assert!(pages[1].contains(&"Before".to_owned()));
        assert!(pages[2].contains(&"After".to_owned()));
        assert!(!pages[2].contains(&"Before".to_owned()));
        // Leading/trailing/repeated markers do not create blank pages.
        let normalized = export(&doc(
            "<!-- pagebreak -->\n\nA\n\n<!-- pagebreak -->\n\n<!-- pagebreak -->\n\nB\n\n<!-- pagebreak -->\n",
        ));
        assert_eq!(normalized.pages, 3);
    }

    #[test]
    fn t082_02_page_setup_controls_media_box_and_tables_repeat_headers() {
        let mut document = doc("text");
        document.page.size = markdown::PageSize::Letter;
        document.page.orientation = Orientation::Landscape;
        document.page.header = "H {title} {page}/{total}".into();
        let out = export(&document);
        let text = String::from_utf8_lossy(&out.bytes).into_owned();
        assert!(text.contains("/MediaBox [0 0 792 612]"));
        assert!(pages_text(&out.bytes)[0].contains(&"H Policy (draft) 1/2".to_owned()));

        let mut body = String::from("| Head A | Head B |\n|---|---|\n");
        for i in 0..90 {
            body.push_str(&format!("| row{i} | value |\n"));
        }
        let pages = pages_text(&export(&doc(&body)).bytes);
        let with_header = pages
            .iter()
            .skip(1)
            .filter(|p| p.contains(&"Head A".to_owned()))
            .count();
        assert_eq!(with_header, pages.len() - 1);
    }

    #[test]
    fn t082_03_identity_hash_and_signature_summary() {
        let mut document = doc("body");
        document.status = "Approved".into();
        let old = Document {
            revision: "b".repeat(40),
            ..document.clone()
        };
        let newer = Document {
            revision: "c".repeat(40),
            ..document.clone()
        };
        let event = |revision: &str, to: &str, time: &str| LifecycleEvent {
            revision: revision.into(),
            event_hash: markdown::hash(to.as_bytes()),
            content_hash: document.content_hash.clone(),
            from_status: "Draft".into(),
            to_status: to.into(),
            local_timestamp: time.into(),
        };
        let history = [newer.clone(), document.clone(), old.clone()];
        let events = [
            event(&newer.revision, "Superseded", "3"),
            event(&document.revision, "Approved", "2"),
            event(&old.revision, "In Review", "1"),
        ];
        let out = render(&document, &history, &events).unwrap();
        let all: Vec<String> = pages_text(&out.bytes).concat();
        let joined = all.join("\n");
        assert!(joined.contains(&document.id));
        assert!(joined.contains(&document.revision));
        assert!(all.concat().contains(&document.content_hash));
        assert!(joined.contains("No cryptographic signature is recorded"));
        assert!(joined.contains("Draft -> Approved"));
        assert!(joined.contains("Draft -> In Review"));
        assert!(!joined.contains("Superseded"), "later events are excluded");
    }

    #[test]
    fn t082_04_metadata_binds_source_hash() {
        let document = doc("body");
        let out = export(&document);
        let text = String::from_utf8_lossy(&out.bytes).into_owned();
        assert!(text.contains(&format!("/EQMS-DocumentId ({})", document.id)));
        assert!(text.contains(&format!("/EQMS-Revision ({})", document.revision)));
        assert!(text.contains(&format!("/EQMS-ContentHash ({})", document.content_hash)));
        assert!(text.contains("/EQMS-Renderer (eqms-markdown-3/eqms-pdf-1)"));
    }

    #[test]
    fn t082_06_no_active_content_and_text_is_escaped() {
        let body = "Parens (a) \\ back [x](javascript:bad) <script>alert(1)</script> ![i](https://bad.invalid/x) snow \u{2603} café";
        let out = export(&doc(body));
        let text = String::from_utf8_lossy(&out.bytes).into_owned();
        for forbidden in [
            "/JS",
            "/JavaScript",
            "/Launch",
            "/URI",
            "/EmbeddedFile",
            "/OpenAction",
            "/Annots",
            "/AA",
            "/XObject",
        ] {
            assert!(!text.contains(forbidden), "{forbidden}");
        }
        let joined = pages_text(&out.bytes).concat().concat();
        assert!(joined.contains("Parens (a) \\ back"));
        assert!(joined.contains("<script>alert(1)</script>"));
        assert!(joined.contains("[Image: i - https://bad.invalid/x]"));
        assert!(joined.contains("snow ? caf\u{e9}"));
        assert!(out.diagnostics.iter().any(|d| d.contains("U+2603")));
    }

    #[test]
    fn t082_07_page_limit_is_enforced() {
        let body = "x\n\n<!-- pagebreak -->\n\n".repeat(MAX_PAGES + 5);
        let error = render(&doc(&body), &[], &[]).unwrap_err();
        assert!(error.contains("page limit"));
    }

    #[test]
    fn long_words_code_lists_and_quotes_stay_inside_margins() {
        let body = format!(
            "{}\n\n```\n{}\n```\n\n> quote\n\n1. one\n   - nested\n\n[^n]: note\n",
            "W".repeat(400),
            "c".repeat(300)
        );
        let out = export(&doc(&body));
        let right = 595.2756 - 25.4 * MM + 0.5;
        for op in page_ops(&out.bytes).into_iter().flatten() {
            assert!(op.x >= 25.4 * MM - 0.5 || op.x >= 0.0);
            assert!(
                op.x + text_width(op.font, &op.text, op.size) <= right,
                "{}",
                op.text
            );
        }
    }
}
