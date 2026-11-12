use crate::output;
use clap::{Arg, ArgMatches, Command};
use eqms_core::{document::Documents, markdown, pdf};
use serde::Deserialize;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

const MAX_MANIFEST_BYTES: u64 = 1024 * 1024;
const MAX_ENTRIES: usize = 500;
const INDEX_FILE: &str = "pdf-export-index.json";

pub(super) fn commands() -> Vec<Command> {
    vec![
        Command::new("export-pdf")
            .about("Export one exact saved revision as a controlled PDF (never overwrites)")
            .arg(Arg::new("path").required(true))
            .arg(Arg::new("id").required(true))
            .arg(Arg::new("revision").long("revision"))
            .arg(
                Arg::new("file")
                    .long("file")
                    .required(true)
                    .help("New .pdf file outside the project"),
            ),
        Command::new("export-pdf-batch")
            .about("Export every document of a release manifest v1 as PDFs plus an index")
            .arg(Arg::new("path").required(true))
            .arg(Arg::new("manifest").required(true))
            .arg(
                Arg::new("output-dir")
                    .long("output-dir")
                    .required(true)
                    .help("Directory outside the project; created if missing"),
            ),
    ]
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Manifest {
    schema_version: u32,
    release: String,
    documents: Vec<Entry>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Entry {
    id: String,
    revision: String,
}

struct Exported {
    id: String,
    revision: String,
    content_hash: String,
    status: String,
    pdf: pdf::PdfOutput,
}

fn export(docs: &Documents, id: &str, revision: Option<&str>) -> Result<Exported, String> {
    let snapshot = docs.history_snapshot(id).map_err(|e| e.to_string())?;
    let document = match revision {
        Some(revision) => snapshot.revisions.iter().find(|d| d.revision == revision),
        None => snapshot.revisions.first(),
    }
    .ok_or_else(|| format!("{id}: revision is not a saved revision of this document"))?;
    let pdf = pdf::render(document, &snapshot.revisions, &snapshot.lifecycle_events)
        .map_err(|e| format!("{id}: {e}"))?;
    Ok(Exported {
        id: document.id.clone(),
        revision: document.revision.clone(),
        content_hash: document.content_hash.clone(),
        status: document.status.clone(),
        pdf,
    })
}

fn digest(bytes: &[u8]) -> String {
    format!("sha256:{:x}", Sha256::digest(bytes))
}

/// Canonical form of a path that may not exist yet: its deepest existing ancestor
/// resolved, followed by the remaining components.
fn resolve(path: &Path) -> Result<PathBuf, String> {
    let absolute = std::path::absolute(path).map_err(|e| e.to_string())?;
    let mut missing = Vec::new();
    let mut base = absolute.as_path();
    loop {
        match base.canonicalize() {
            Ok(mut resolved) => {
                resolved.extend(missing.iter().rev());
                return Ok(resolved);
            }
            Err(_) => {
                let name = base.file_name().ok_or("invalid output path")?;
                if name == ".." {
                    return Err("output path must not contain '..' components".into());
                }
                missing.push(name.to_owned());
                base = base.parent().ok_or("invalid output path")?;
            }
        }
    }
}

fn outside_project(project: &str, target: &Path) -> Result<PathBuf, String> {
    let root = Path::new(project)
        .canonicalize()
        .map_err(|e| format!("cannot resolve project path: {e}"))?;
    let resolved = resolve(target)?;
    if resolved.starts_with(&root) {
        return Err("PDF output must be outside the project's canonical storage".into());
    }
    Ok(resolved)
}

fn write_new(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)
        .map_err(|e| format!("cannot create {}: {e}", path.display()))?;
    file.write_all(bytes)
        .and_then(|()| file.sync_all())
        .map_err(|e| format!("cannot write {}: {e}", path.display()))
}

fn entry_json(item: &Exported, file: &str, bytes: &[u8]) -> Value {
    json!({
        "id": item.id, "revision": item.revision, "content_hash": item.content_hash,
        "status": item.status, "file": file, "pages": item.pdf.pages,
        "pdf_sha256": digest(bytes), "bytes": bytes.len(),
        "renderer": format!("{}/{}", eqms_core::render::RENDERER, pdf::PDF_RENDERER),
        "diagnostics": item.pdf.diagnostics,
    })
}

pub(super) fn run(operation: &str, args: &ArgMatches, json_output: bool) -> Result<String, String> {
    let value = |name| {
        args.get_one::<String>(name)
            .expect("required PDF argument")
            .as_str()
    };
    let docs = Documents::open(value("path")).map_err(|e| e.to_string())?;
    let data = if operation == "export-pdf" {
        let file = outside_project(value("path"), Path::new(value("file")))?;
        if file.extension().and_then(|e| e.to_str()) != Some("pdf") {
            return Err("PDF output file must end in .pdf".into());
        }
        let item = export(
            &docs,
            value("id"),
            args.get_one::<String>("revision").map(String::as_str),
        )?;
        write_new(&file, &item.pdf.bytes)?;
        let name = file
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or_default();
        entry_json(&item, name, &item.pdf.bytes)
    } else {
        batch(&docs, value("path"), value("manifest"), value("output-dir"))?
    };
    if json_output {
        output::document(data, true)
    } else if operation == "export-pdf" {
        Ok(format!(
            "Exported {} revision {} ({} pages, {}). Content hash {}. PDF is a derived view; it is not a signature or approval.",
            data["id"].as_str().unwrap_or_default(),
            data["revision"].as_str().unwrap_or_default(),
            data["pages"],
            data["pdf_sha256"].as_str().unwrap_or_default(),
            data["content_hash"].as_str().unwrap_or_default(),
        ))
    } else {
        Ok(format!(
            "Exported {} PDFs for release {} with {INDEX_FILE}. The index is not a signed baseline.",
            data["documents"].as_array().map_or(0, Vec::len),
            data["release"].as_str().unwrap_or_default(),
        ))
    }
}

fn batch(docs: &Documents, project: &str, manifest: &str, dir: &str) -> Result<Value, String> {
    let mut bytes = Vec::new();
    std::fs::File::open(manifest)
        .map_err(|e| format!("cannot read release manifest: {e}"))?
        .take(MAX_MANIFEST_BYTES + 1)
        .read_to_end(&mut bytes)
        .map_err(|e| format!("cannot read release manifest: {e}"))?;
    if bytes.len() as u64 > MAX_MANIFEST_BYTES {
        return Err("Release manifest exceeds 1 MiB".into());
    }
    let parsed: Manifest =
        serde_json::from_slice(&bytes).map_err(|e| format!("Invalid release manifest: {e}"))?;
    if parsed.schema_version != 1 {
        return Err("Unsupported release manifest schema_version (expected 1)".into());
    }
    if parsed.release.is_empty()
        || parsed.release.len() > 120
        || parsed.release.chars().any(char::is_control)
    {
        return Err("Release label must be 1-120 characters without control characters".into());
    }
    if parsed.documents.is_empty() || parsed.documents.len() > MAX_ENTRIES {
        return Err(format!("Release manifest needs 1-{MAX_ENTRIES} documents"));
    }
    let mut seen = std::collections::BTreeSet::new();
    for entry in &parsed.documents {
        markdown::validate_id(&entry.id).map_err(|e| e.to_string())?;
        if entry.revision.is_empty() {
            return Err(format!("{}: revision is required", entry.id));
        }
        if !seen.insert(entry.id.as_str()) {
            return Err(format!("{}: duplicate document in manifest", entry.id));
        }
    }
    let dir = outside_project(project, Path::new(dir))?;

    let mut exported = Vec::new();
    for entry in &parsed.documents {
        exported.push(export(docs, &entry.id, Some(&entry.revision))?);
    }
    let mut entries = Vec::new();
    let mut files = Vec::new();
    for item in &exported {
        let name = format!(
            "{}-{}.pdf",
            item.id,
            &item.revision[..item.revision.len().min(12)]
        );
        entries.push(entry_json(item, &name, &item.pdf.bytes));
        files.push((dir.join(&name), &item.pdf.bytes));
    }
    let index = json!({
        "schema_version": 1,
        "release": parsed.release,
        "manifest_sha256": digest(&bytes),
        "documents": entries,
        "note": "Derived PDF export index. Not a signed baseline or approval record.",
    });
    let mut index_bytes = serde_json::to_vec_pretty(&index).map_err(|e| e.to_string())?;
    index_bytes.push(b'\n');
    files.push((dir.join(INDEX_FILE), &index_bytes));

    // Preflight every target so a conflict writes nothing.
    std::fs::create_dir_all(&dir).map_err(|e| format!("cannot create output directory: {e}"))?;
    for (path, _) in &files {
        if path.symlink_metadata().is_ok() {
            return Err(format!(
                "{} already exists; nothing was written",
                path.display()
            ));
        }
    }
    let mut written = Vec::new();
    for (path, content) in &files {
        if let Err(error) = write_new(path, content) {
            for done in &written {
                let _ = std::fs::remove_file(done);
            }
            return Err(error);
        }
        written.push(path.clone());
    }
    Ok(index)
}
