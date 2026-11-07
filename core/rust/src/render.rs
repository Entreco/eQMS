//! Derived Markdown semantics. This module never reads or writes stored records.
use pulldown_cmark::{Event, Options, Parser, Tag};
use serde::{Deserialize, Serialize};

pub const MAX_BODY: usize = 1024 * 1024;
pub const MAX_NODES: usize = 100_000;
pub const MAX_DEPTH: usize = 64;
pub const SEMANTIC_SCHEMA: u32 = 2;
pub const RENDERER: &str = "eqms-markdown-2";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Node {
    pub kind: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub text: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub target: String,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub anchor: String,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub children: Vec<Node>,
}

impl Node {
    fn new(kind: &str) -> Self {
        Self {
            kind: kind.into(),
            text: String::new(),
            target: String::new(),
            anchor: String::new(),
            children: Vec::new(),
        }
    }

    pub fn plain_text(&self) -> String {
        let mut text = self.text.clone();
        for child in &self.children {
            text.push_str(&child.plain_text());
        }
        text
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Heading {
    pub level: u8,
    pub title: String,
    pub anchor: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Provenance {
    pub kind: String,
    pub id: String,
    pub revision: String,
    pub content_hash: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Document {
    pub semantic_schema: u32,
    pub renderer: String,
    pub title: String,
    pub input_digest: String,
    pub source: Provenance,
    pub nodes: Vec<Node>,
    pub toc: Vec<Heading>,
    pub diagnostics: Vec<String>,
}

pub fn allowed_link(target: &str) -> bool {
    !target.chars().any(|c| c.is_control() || c.is_whitespace())
        && (target.starts_with('#')
            || target.starts_with("https://")
            || target.starts_with("http://")
            || target.starts_with("mailto:"))
}

pub fn asset_path(target: &str) -> bool {
    target.starts_with("assets/")
        && !target.contains(['\\', ':', '%', '?', '#'])
        && target
            .split('/')
            .all(|part| !part.is_empty() && part != "." && part != "..")
        && !target.chars().any(char::is_control)
}

fn note_anchor(label: &str) -> String {
    format!("note-{}", &crate::markdown::hash(label.as_bytes())[7..])
}

fn is_exact_page_break(body: &str, range: std::ops::Range<usize>, text: &str) -> bool {
    let text = text
        .strip_suffix("\r\n")
        .or_else(|| text.strip_suffix('\n'))
        .unwrap_or(text);
    if text != "<!-- pagebreak -->" {
        return false;
    }

    let Some(prefix) = body.get(..range.start) else {
        return false;
    };
    if prefix
        .rfind('\n')
        .map_or(range.start != 0, |newline| newline + 1 != range.start)
    {
        return false;
    }
    let Some(line_end) = body[range.start..].find('\n').map(|end| range.start + end) else {
        return body.get(range.start..) == Some("<!-- pagebreak -->");
    };
    body.get(range.start..line_end)
        .and_then(|line| line.strip_suffix('\r').or(Some(line)))
        == Some("<!-- pagebreak -->")
}

pub fn parse(title: &str, body: &str) -> Result<Document, String> {
    if body.len() > MAX_BODY || title.len() > 16 * 1024 {
        return Err(
            "Preview limit exceeded (1 MiB body / 16 KiB title). Source remains available.".into(),
        );
    }
    let mut stack = vec![Node::new("document")];
    let mut toc = Vec::new();
    let mut diagnostics = Vec::new();
    let mut count = 0;
    let mut anchors = std::collections::BTreeSet::new();
    let mut heading_suffixes = std::collections::BTreeMap::new();
    let mut references = std::collections::BTreeMap::<String, usize>::new();
    let options =
        Options::ENABLE_TABLES | Options::ENABLE_FOOTNOTES | Options::ENABLE_STRIKETHROUGH;
    let mut pending_page_break = false;
    let mut skip_html_block_end = false;
    for (event, range) in Parser::new_ext(body, options).into_offset_iter() {
        count += 1;
        if count > MAX_NODES || stack.len() > MAX_DEPTH {
            return Err("Preview node/depth limit exceeded. Source remains available.".into());
        }
        if skip_html_block_end {
            if matches!(event, Event::End(_)) {
                skip_html_block_end = false;
            }
            continue;
        }
        let mut leaf = None;
        match event {
            Event::Start(tag) => {
                pending_page_break = matches!(tag, Tag::HtmlBlock) && stack.len() == 1;
                let mut node = Node::new("section");
                match tag {
                    Tag::Paragraph => node.kind = "paragraph".into(),
                    Tag::Heading { level, .. } => {
                        node.kind = format!("h{}", level as u8);
                    }
                    Tag::BlockQuote(_) => node.kind = "quote".into(),
                    Tag::CodeBlock(_) => node.kind = "code_block".into(),
                    Tag::List(start) => {
                        node.kind = if start.is_some() {
                            "ordered"
                        } else {
                            "unordered"
                        }
                        .into();
                        node.target = start.unwrap_or(1).to_string();
                    }
                    Tag::Item => node.kind = "item".into(),
                    Tag::Emphasis => node.kind = "emphasis".into(),
                    Tag::Strong => node.kind = "strong".into(),
                    Tag::Strikethrough => node.kind = "strike".into(),
                    Tag::Link { dest_url, .. } => {
                        node.kind = "link".into();
                        node.target = dest_url.into_string();
                        if !allowed_link(&node.target) {
                            diagnostics.push(format!("Blocked link: {}", node.target));
                        }
                    }
                    Tag::Image { dest_url, .. } => {
                        node.kind = "image".into();
                        node.target = dest_url.into_string();
                        if !asset_path(&node.target) {
                            diagnostics.push(format!("Blocked image: {}", node.target));
                        }
                    }
                    Tag::Table(_) => node.kind = "table".into(),
                    Tag::TableHead => node.kind = "table_header".into(),
                    Tag::TableRow => node.kind = "table_row".into(),
                    Tag::TableCell => node.kind = "table_cell".into(),
                    Tag::FootnoteDefinition(label) => {
                        node.kind = "footnote".into();
                        node.text = label.to_string();
                        node.anchor = note_anchor(&label);
                    }
                    Tag::HtmlBlock => {
                        node.kind = "literal".into();
                    }
                    _ => diagnostics
                        .push("Unsupported Markdown container shown as plain content.".into()),
                }
                stack.push(node);
            }
            Event::End(_) => {
                let mut node = stack.pop().ok_or("Unbalanced Markdown tree")?;
                if node.kind == "item" {
                    let mut children = Vec::new();
                    let mut paragraph = Node::new("paragraph");
                    for child in node.children {
                        if matches!(
                            child.kind.as_str(),
                            "text"
                                | "strong"
                                | "emphasis"
                                | "strike"
                                | "link"
                                | "note_link"
                                | "code"
                                | "literal"
                                | "image"
                                | "break"
                        ) {
                            paragraph.children.push(child);
                        } else {
                            if !paragraph.children.is_empty() {
                                children.push(paragraph);
                                paragraph = Node::new("paragraph");
                            }
                            children.push(child);
                        }
                    }
                    if !paragraph.children.is_empty() {
                        children.push(paragraph);
                    }
                    node.children = children;
                }
                if node.kind.starts_with('h') && node.kind.len() == 2 {
                    let slug = node
                        .plain_text()
                        .to_lowercase()
                        .chars()
                        .map(|c| if c.is_alphanumeric() { c } else { '-' })
                        .collect::<String>()
                        .split('-')
                        .filter(|part| !part.is_empty())
                        .collect::<Vec<_>>()
                        .join("-");
                    let base = if slug.is_empty() {
                        "section".into()
                    } else {
                        slug
                    };
                    let suffix = heading_suffixes.entry(base.clone()).or_insert(1);
                    node.anchor = if *suffix == 1 {
                        base.clone()
                    } else {
                        format!("{base}-{suffix}")
                    };
                    while !anchors.insert(node.anchor.clone()) {
                        *suffix += 1;
                        node.anchor = format!("{base}-{suffix}");
                    }
                    *suffix += 1;
                    toc.push(Heading {
                        level: node.kind.as_bytes()[1] - b'0',
                        title: node.plain_text(),
                        anchor: node.anchor.clone(),
                    });
                }
                stack
                    .last_mut()
                    .ok_or("Unbalanced Markdown tree")?
                    .children
                    .push(node);
            }
            Event::Text(ref text)
            | Event::Code(ref text)
            | Event::Html(ref text)
            | Event::InlineHtml(ref text) => {
                if matches!(event, Event::Html(_))
                    && pending_page_break
                    && is_exact_page_break(body, range, text)
                {
                    stack.pop().ok_or("Unbalanced Markdown tree")?;
                    stack
                        .last_mut()
                        .ok_or("Unbalanced Markdown tree")?
                        .children
                        .push(Node::new("page_break"));
                    pending_page_break = false;
                    skip_html_block_end = true;
                    continue;
                }
                pending_page_break = false;
                let kind = match &event {
                    Event::Code(_) => "code",
                    Event::Html(_) | Event::InlineHtml(_) => "literal",
                    _ => "text",
                };
                let mut node = Node::new(kind);
                node.text = text.to_string();
                if kind == "literal" {
                    diagnostics.push("Raw HTML is shown literally, not executed.".into());
                }
                leaf = Some(node);
            }
            Event::SoftBreak | Event::HardBreak => {
                pending_page_break = false;
                let mut node = Node::new("break");
                node.text = "\n".into();
                leaf = Some(node);
            }
            Event::Rule => leaf = Some(Node::new("rule")),
            Event::FootnoteReference(label) => {
                pending_page_break = false;
                let mut node = Node::new("note_link");
                node.text = format!("[{label}]");
                node.target = format!("#{}", note_anchor(&label));
                let count = references.entry(label.to_string()).or_default();
                *count += 1;
                node.anchor = format!(
                    "reference-{}{}",
                    &node.target[1..],
                    if *count == 1 {
                        String::new()
                    } else {
                        format!("-{count}")
                    }
                );
                leaf = Some(node);
            }
            _ => {
                return Err("Unsupported Markdown event. Source remains available.".into());
            }
        }
        if let Some(node) = leaf {
            stack
                .last_mut()
                .ok_or("Unbalanced Markdown tree")?
                .children
                .push(node);
        }
    }
    let nodes = stack.pop().ok_or("Missing Markdown tree")?.children;
    let start = nodes
        .iter()
        .position(|node| node.kind != "page_break")
        .unwrap_or(nodes.len());
    let end = nodes
        .iter()
        .rposition(|node| node.kind != "page_break")
        .map_or(start, |index| index + 1);
    let mut normalized = Vec::with_capacity(end.saturating_sub(start));
    let mut previous_was_page_break = false;
    for node in nodes
        .into_iter()
        .skip(start)
        .take(end.saturating_sub(start))
    {
        if node.kind == "page_break" {
            if !previous_was_page_break {
                normalized.push(node);
            }
            previous_was_page_break = true;
        } else {
            previous_was_page_break = false;
            normalized.push(node);
        }
    }
    diagnostics.sort();
    diagnostics.dedup();
    Ok(Document {
        semantic_schema: SEMANTIC_SCHEMA,
        renderer: RENDERER.into(),
        title: title.into(),
        input_digest: crate::markdown::hash(body.as_bytes()),
        source: Provenance {
            kind: "unsaved".into(),
            id: String::new(),
            revision: String::new(),
            content_hash: String::new(),
        },
        nodes: normalized,
        toc,
        diagnostics,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn syntax_anchors_provenance_and_determinism() {
        let body = "# Same\n# Same\n## 日本語\n\n**bold** *em* ~~old~~ `code`\n\n[ref][r] [^a]\n\n[r]: https://example.invalid\n\n[^a]: A note\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n- item\n\n> quote\n\n![alt](assets/test.png)\n";
        let doc = parse("Document", body).unwrap();
        assert_eq!(doc, parse("Document", body).unwrap());
        assert_eq!(doc.toc.len(), 3);
        assert_ne!(doc.toc[0].anchor, doc.toc[1].anchor);
        assert_eq!(doc.toc[0].anchor, "same");
        assert_eq!(doc.toc[1].anchor, "same-2");
        assert_eq!(doc.source.kind, "unsaved");
        assert!(doc.source.content_hash.is_empty());
        let json = serde_json::to_string(&doc).unwrap();
        for kind in [
            "table",
            "footnote",
            "note_link",
            "image",
            "strong",
            "emphasis",
            "strike",
            "code",
            "unordered",
            "quote",
        ] {
            assert!(json.contains(&format!("\"kind\":\"{kind}\"")));
        }
    }

    #[test]
    fn untrusted_content_and_limits_are_explicit() {
        let doc = parse(
            "",
            "<script>bad()</script>\n\n[x](javascript:bad)\n\n![x](../secret)",
        )
        .unwrap();
        assert!(!doc.diagnostics.is_empty());
        for path in [
            "../secret",
            "assets/../secret",
            "assets//x",
            "assets/%2e%2e/x",
            "https://x",
            "assets/x\\y",
        ] {
            assert!(!asset_path(path));
        }
        assert!(!allowed_link("javascript:alert(1)"));
        assert!(!allowed_link("https://example.invalid/\nsecret"));
        assert!(parse("", &"x".repeat(MAX_BODY + 1)).is_err());
        assert!(parse("", &format!("{}x", "> ".repeat(70))).is_err());
        assert!(parse("", &"*x* ".repeat(30_000)).is_err());
        assert!(parse("", "").is_ok());
    }

    #[test]
    fn page_break_is_an_exact_top_level_html_block_and_preserves_input_digest() {
        let body = "Before\n\n<!-- pagebreak -->\n\nAfter\r\n";
        let doc = parse("Document", body).unwrap();
        assert_eq!(doc.semantic_schema, 2);
        assert_eq!(doc.renderer, "eqms-markdown-2");
        assert_eq!(doc.input_digest, crate::markdown::hash(body.as_bytes()));
        assert_eq!(
            doc.nodes
                .iter()
                .map(|node| node.kind.as_str())
                .collect::<Vec<_>>(),
            ["paragraph", "page_break", "paragraph"]
        );

        let crlf = "Before\r\n\r\n<!-- pagebreak -->\r\n\r\nAfter\r\n";
        let crlf_doc = parse("Document", crlf).unwrap();
        assert!(crlf_doc.nodes.iter().any(|node| node.kind == "page_break"));
        assert_eq!(
            crlf_doc.input_digest,
            crate::markdown::hash(crlf.as_bytes())
        );
    }

    #[test]
    fn page_break_requires_the_exact_standalone_top_level_line() {
        let body = concat!(
            "prose <!-- pagebreak -->\n\n",
            "<!--pagebreak-->\n\n",
            "<!-- pagebreak --> extra\n\n",
            "`<!-- pagebreak -->`\n\n",
            "```\n<!-- pagebreak -->\n```\n\n",
            "- <!-- pagebreak -->\n\n",
            "> <!-- pagebreak -->\n\n",
            "ordinary\n\n---\n"
        );
        let doc = parse("Document", body).unwrap();
        fn contains_page_break(nodes: &[Node]) -> bool {
            nodes
                .iter()
                .any(|node| node.kind == "page_break" || contains_page_break(&node.children))
        }
        assert!(!contains_page_break(&doc.nodes));
        assert!(
            doc.nodes
                .iter()
                .any(|node| { node.plain_text().contains("<!-- pagebreak -->") })
        );
        assert!(doc.nodes.iter().any(|node| node.kind == "rule"));
        assert_eq!(doc.input_digest, crate::markdown::hash(body.as_bytes()));
    }

    #[test]
    fn leading_trailing_and_consecutive_page_breaks_are_normalized() {
        let doc = parse(
            "Document",
            "<!-- pagebreak -->\n\nA\n\n<!-- pagebreak -->\n\n<!-- pagebreak -->\n\nB\n\n<!-- pagebreak -->",
        )
        .unwrap();
        assert_eq!(
            doc.nodes
                .iter()
                .map(|node| node.kind.as_str())
                .collect::<Vec<_>>(),
            ["paragraph", "page_break", "paragraph"]
        );

        let adjacent = parse("Document", "A\n<!-- pagebreak -->\n<!-- pagebreak -->\nB").unwrap();
        assert_eq!(
            adjacent
                .nodes
                .iter()
                .map(|node| node.kind.as_str())
                .collect::<Vec<_>>(),
            ["paragraph", "page_break", "paragraph"]
        );
    }
}
