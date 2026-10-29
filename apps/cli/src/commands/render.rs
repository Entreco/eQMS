use crate::output;
use base64::Engine;
use clap::{Arg, ArgMatches, Command};
use eqms_core::render::{self, Document, Node};
use serde::Deserialize;
use serde_json::json;
use std::io::{Cursor, Read};

pub(super) fn commands() -> Vec<Command> {
    let format = || {
        Arg::new("output")
            .long("output")
            .value_parser(["model", "html"])
            .default_value("model")
    };
    vec![
        Command::new("render-buffer")
            .about("Render bounded unsaved title/body JSON from stdin")
            .arg(format()),
        Command::new("render")
            .about("Render an exact saved document without changing it")
            .arg(Arg::new("path").required(true))
            .arg(Arg::new("id").required(true))
            .arg(Arg::new("revision").long("revision"))
            .arg(format()),
        Command::new("render-asset")
            .about("Read a bounded unversioned assets/ PNG or JPEG")
            .arg(Arg::new("path").required(true))
            .arg(Arg::new("asset").required(true)),
    ]
}

pub(super) fn run(
    operation: &str,
    args: &ArgMatches,
    json_output: bool,
    input: &mut dyn Read,
) -> Result<String, String> {
    let value = |name| {
        args.get_one::<String>(name)
            .expect("required rendering argument")
            .as_str()
    };
    if operation == "render-asset" {
        let workspace =
            eqms_core::workspace::Workspace::open(value("path")).map_err(|e| e.to_string())?;
        let bytes = workspace
            .read_render_asset(value("asset"))
            .map_err(|e| e.to_string())?;
        let reader = image::ImageReader::new(Cursor::new(&bytes))
            .with_guessed_format()
            .map_err(|e| e.to_string())?;
        if !matches!(
            reader.format(),
            Some(image::ImageFormat::Png | image::ImageFormat::Jpeg)
        ) {
            return Err("Only PNG and JPEG assets are supported".into());
        }
        let (width, height) = reader
            .into_dimensions()
            .map_err(|e| format!("Invalid image: {e}"))?;
        if width == 0 || height == 0 || u64::from(width) * u64::from(height) > 16_000_000 {
            return Err("Image exceeds 16 megapixels or has invalid dimensions".into());
        }
        return output::document(
            json!({
                "asset": value("asset"), "width": width, "height": height,
                "unversioned": true, "digest": eqms_core::markdown::hash(&bytes),
                "base64": base64::engine::general_purpose::STANDARD.encode(bytes)
            }),
            json_output,
        );
    }
    let mut document = if operation == "render-buffer" {
        #[derive(Deserialize)]
        #[serde(deny_unknown_fields)]
        struct Buffer {
            title: String,
            body: String,
        }
        let mut bytes = Vec::new();
        input
            .take(8 * 1024 * 1024 + 1)
            .read_to_end(&mut bytes)
            .map_err(|e| e.to_string())?;
        if bytes.len() > 8 * 1024 * 1024 {
            return Err("Render input exceeds 8 MiB JSON limit".into());
        }
        let buffer: Buffer =
            serde_json::from_slice(&bytes).map_err(|e| format!("Invalid render buffer: {e}"))?;
        render::parse(&buffer.title, &buffer.body)?
    } else {
        let docs =
            eqms_core::document::Documents::open(value("path")).map_err(|e| e.to_string())?;
        let snapshot = docs
            .show(
                value("id"),
                args.get_one::<String>("revision").map(String::as_str),
            )
            .map_err(|e| e.to_string())?;
        let mut document = render::parse(&snapshot.title, &snapshot.body)?;
        document.source = render::Provenance {
            kind: "saved".into(),
            id: snapshot.id,
            revision: snapshot.revision,
            content_hash: snapshot.content_hash,
        };
        document
    };
    document.diagnostics.sort();
    if value("output") == "html" {
        let html = html(&document)?;
        if json_output {
            output::document(
                json!({"html": html, "source": document.source, "renderer": document.renderer}),
                true,
            )
        } else {
            Ok(html)
        }
    } else {
        output::document(
            serde_json::to_value(document).map_err(|e| e.to_string())?,
            json_output,
        )
    }
}

fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

fn html(document: &Document) -> Result<String, String> {
    let mut out = String::from(
        "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\"><meta name=\"viewport\" content=\"width=device-width\"><style>body{font:16px/1.6 system-ui;max-width:76ch;margin:3rem auto;padding:1rem;color:#242628}pre,table{overflow:auto}td,th{padding:.5rem;border:1px solid #ccc}blockquote{border-left:3px solid #888;padding-left:1rem}</style><title>",
    );
    out.push_str(&escape(&document.title));
    out.push_str("</title></head><body><header><h1>");
    out.push_str(&escape(&document.title));
    out.push_str("</h1><p>");
    out.push_str(&escape(&format!(
        "{} | {} | {} | {} | {}",
        document.source.kind,
        document.source.id,
        document.source.revision,
        document.source.content_hash,
        document.renderer
    )));
    out.push_str("</p></header><nav aria-label=\"Table of contents\"><ol>");
    for heading in &document.toc {
        out.push_str(&format!(
            "<li><a href=\"#{}\">{}</a></li>",
            escape(&heading.anchor),
            escape(&heading.title)
        ));
    }
    out.push_str("</ol></nav><main>");
    for node in &document.nodes {
        node_html(node, &mut out)?;
    }
    out.push_str("</main><aside>");
    for diagnostic in &document.diagnostics {
        out.push_str(&format!("<p>{}</p>", escape(diagnostic)));
    }
    out.push_str("</aside></body></html>");
    Ok(out)
}

fn node_html(node: &Node, out: &mut String) -> Result<(), String> {
    let (open, close) = match node.kind.as_str() {
        "text" => ("".into(), ""),
        "paragraph" => ("<p>".into(), "</p>"),
        "section" => ("<section>".into(), "</section>"),
        "h1" | "h2" | "h3" | "h4" | "h5" | "h6" => (
            format!("<{} id=\"{}\">", node.kind, escape(&node.anchor)),
            match node.kind.as_str() {
                "h1" => "</h1>",
                "h2" => "</h2>",
                "h3" => "</h3>",
                "h4" => "</h4>",
                "h5" => "</h5>",
                _ => "</h6>",
            },
        ),
        "strong" => ("<strong>".into(), "</strong>"),
        "emphasis" => ("<em>".into(), "</em>"),
        "strike" => ("<s>".into(), "</s>"),
        "code" | "literal" => ("<code>".into(), "</code>"),
        "code_block" => ("<pre><code>".into(), "</code></pre>"),
        "quote" => ("<blockquote>".into(), "</blockquote>"),
        "ordered" => (format!("<ol start=\"{}\">", escape(&node.target)), "</ol>"),
        "unordered" => ("<ul>".into(), "</ul>"),
        "item" => ("<li>".into(), "</li>"),
        "table" => ("<table>".into(), "</table>"),
        "table_header" => ("<thead><tr>".into(), "</tr></thead>"),
        "table_row" => ("<tr>".into(), "</tr>"),
        "table_cell" => ("<td>".into(), "</td>"),
        "footnote" => (
            format!("<section id=\"{}\">", escape(&node.anchor)),
            "</section>",
        ),
        "link" | "note_link" if render::allowed_link(&node.target) => (
            format!(
                "<a id=\"{}\" href=\"{}\" rel=\"noreferrer noopener\">",
                escape(&node.anchor),
                escape(&node.target)
            ),
            "</a>",
        ),
        "link" => ("<span title=\"Blocked link\">".into(), "</span>"),
        "image" => {
            out.push_str(&format!(
                "<p>[Image: {} - unversioned or unavailable asset; {}]</p>",
                escape(&node.plain_text()),
                escape(&node.target)
            ));
            return Ok(());
        }
        "rule" => ("<hr>".into(), ""),
        "break" => ("<br>".into(), ""),
        _ => return Err(format!("Unsupported semantic node: {}", node.kind)),
    };
    out.push_str(&open);
    out.push_str(&escape(&node.text));
    for child in &node.children {
        node_html(child, out)?;
    }
    if node.kind == "footnote" {
        out.push_str(&format!(
            " <a href=\"#reference-{}\">Back to reference</a>",
            escape(&node.anchor)
        ));
    }
    out.push_str(close);
    if out.len() > 16 * 1024 * 1024 {
        return Err("HTML exceeds 16 MiB output limit".into());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn buffer_json_contract_is_explicit_and_never_claims_saved_provenance() {
        let matches = super::super::document::command()
            .try_get_matches_from(["document", "render-buffer"])
            .unwrap();
        let args = matches.subcommand_matches("render-buffer").unwrap();
        let result = run(
            "render-buffer",
            args,
            true,
            &mut Cursor::new(r##"{"title":"A","body":"# Purpose"}"##),
        )
        .unwrap();
        let value: serde_json::Value = serde_json::from_str(&result).unwrap();
        assert_eq!(value["source"]["kind"], "unsaved");
        assert_eq!(value["source"]["content_hash"], "");
        assert_eq!(
            value["input_digest"],
            eqms_core::markdown::hash(b"# Purpose")
        );
        for input in [
            r#"{"title":"A"}"#,
            r#"{"title":"A","body":"","approved":true}"#,
            "not JSON",
        ] {
            assert!(run("render-buffer", args, true, &mut Cursor::new(input)).is_err());
        }
    }
    #[test]
    fn html_is_deterministic_escaped_and_network_free() {
        let doc = render::parse("A <title>", "# Heading\n\n<script>bad()</script>\n\n[x](javascript:bad)\n\n![secret](https://bad.invalid/x)\n").unwrap();
        let output = html(&doc).unwrap();
        assert_eq!(output, html(&doc).unwrap());
        assert!(!output.contains("<script>"));
        assert!(!output.contains("href=\"javascript:"));
        assert!(!output.contains("<img"));
        assert!(output.contains("&lt;script&gt;"));
        assert!(output.contains("id=\"heading\""));
    }
}
