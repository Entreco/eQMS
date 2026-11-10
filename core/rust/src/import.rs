//! Lossless, idempotent import of existing Markdown files as Draft documents.

use crate::document::{Document, Documents};
use crate::markdown::hash;
use crate::workspace::{WorkspaceError, error};
use std::collections::BTreeSet;

/// One source file to import; `name` is a caller-supplied label (usually its path).
#[derive(Clone, Debug)]
pub struct ImportSource {
    pub name: String,
    pub bytes: Vec<u8>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ImportOutcome {
    pub source: String,
    pub source_hash: String,
    pub document: Document,
    pub identifiers: Vec<String>,
    pub line_endings_normalized: bool,
    pub already_imported: bool,
}

struct Prepared {
    name: String,
    source_hash: String,
    title: String,
    body: String,
    identifiers: Vec<String>,
    line_endings_normalized: bool,
}

/// Stable identifiers: `UN-nnn`, `EQMS-nnn`, `Rnnn-nn` and `Tnnn-nn`, sorted and unique.
pub fn identifiers(text: &str) -> Vec<String> {
    let found: BTreeSet<&str> = text
        .split(|c: char| !(c.is_ascii_alphanumeric() || c == '-'))
        .filter(|token| is_identifier(token))
        .collect();
    found.into_iter().map(str::to_owned).collect()
}

fn is_identifier(token: &str) -> bool {
    let digits = |value: &str, count: usize| {
        value.len() == count && value.bytes().all(|byte| byte.is_ascii_digit())
    };
    if let Some(number) = token
        .strip_prefix("UN-")
        .or_else(|| token.strip_prefix("EQMS-"))
    {
        return digits(number, 3);
    }
    token
        .strip_prefix(['R', 'T'])
        .and_then(|rest| rest.split_once('-'))
        .is_some_and(|(issue, item)| digits(issue, 3) && digits(item, 2))
}

/// Title from front matter, else the first `# ` heading, else the file stem.
pub fn derive_title(text: &str, name: &str) -> String {
    let (front, body) = match text
        .strip_prefix("---\n")
        .and_then(|rest| rest.split_once("\n---\n"))
    {
        Some((front, body)) => (Some(front), body),
        None => (None, text),
    };
    let from_front = front.and_then(|front| {
        front
            .lines()
            .find_map(|line| line.strip_prefix("title:"))
            .map(|value| {
                let value = value.trim();
                value
                    .strip_prefix('"')
                    .and_then(|v| v.strip_suffix('"'))
                    .or_else(|| value.strip_prefix('\'').and_then(|v| v.strip_suffix('\'')))
                    .unwrap_or(value)
                    .trim()
                    .to_owned()
            })
    });
    let from_heading = || {
        body.lines()
            .find_map(|line| line.strip_prefix("# "))
            .map(|heading| heading.trim().to_owned())
    };
    from_front
        .into_iter()
        .chain(from_heading())
        .find(|title| !title.is_empty() && !title.chars().any(char::is_control))
        .unwrap_or_else(|| stem(name))
}

fn stem(name: &str) -> String {
    let file = name.rsplit(['/', '\\']).next().unwrap_or(name);
    let stem = file.rsplit_once('.').map_or(file, |(stem, _)| stem);
    let stem = stem.trim();
    if stem.is_empty() || stem.chars().any(char::is_control) {
        "Imported document".to_owned()
    } else {
        stem.to_owned()
    }
}

fn prepare(source: &ImportSource) -> Result<Prepared, WorkspaceError> {
    let text = std::str::from_utf8(&source.bytes)
        .map_err(|cause| error(format!("{} is not valid UTF-8: {cause}", source.name)))?;
    let body = text.replace("\r\n", "\n").replace('\r', "\n");
    Ok(Prepared {
        name: source.name.clone(),
        source_hash: hash(&source.bytes),
        title: derive_title(&body, &source.name),
        identifiers: identifiers(&body),
        line_endings_normalized: body != text,
        body,
    })
}

impl Documents {
    /// Import every source as a Draft document, or report it as already imported.
    ///
    /// All sources are checked before the first document is created. Documents already
    /// created are not rolled back (draft history is append-only); a mid-batch failure names
    /// them in the error.
    pub fn import(&self, sources: &[ImportSource]) -> Result<Vec<ImportOutcome>, WorkspaceError> {
        let prepared = sources.iter().map(prepare).collect::<Result<Vec<_>, _>>()?;
        let mut existing = self.list()?;
        let mut outcomes: Vec<ImportOutcome> = Vec::with_capacity(prepared.len());
        for item in prepared {
            let found = existing
                .iter()
                .find(|doc| doc.title == item.title && doc.body == item.body)
                .cloned();
            let (document, already_imported) = match found {
                Some(document) => (document, true),
                None => {
                    let document = self.create(&item.title, &item.body).map_err(|cause| {
                        error(format!(
                            "cannot import {}: {cause}{}",
                            item.name,
                            created(&outcomes)
                        ))
                    })?;
                    existing.push(document.clone());
                    (document, false)
                }
            };
            let stored = self.show(&document.id, None)?;
            if stored.body != item.body
                || stored.status != "Draft"
                || identifiers(&stored.body) != item.identifiers
            {
                return Err(error(format!(
                    "imported content of {} differs from its source{}",
                    item.name,
                    created(&outcomes)
                )));
            }
            outcomes.push(ImportOutcome {
                source: item.name,
                source_hash: item.source_hash,
                document: stored,
                identifiers: item.identifiers,
                line_endings_normalized: item.line_endings_normalized,
                already_imported,
            });
        }
        Ok(outcomes)
    }
}

fn created(outcomes: &[ImportOutcome]) -> String {
    let ids: Vec<_> = outcomes
        .iter()
        .filter(|outcome| !outcome.already_imported)
        .map(|outcome| outcome.document.id.as_str())
        .collect();
    if ids.is_empty() {
        String::new()
    } else {
        format!("; documents already created: {}", ids.join(", "))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::workspace::{Workspace, git};
    use std::fs;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT: AtomicU64 = AtomicU64::new(0);

    struct Fixture(PathBuf);
    impl Fixture {
        fn new() -> Self {
            let path = std::env::temp_dir().join(format!(
                "eqms-import-{}-{}",
                std::process::id(),
                NEXT.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir(&path).unwrap();
            git(&path, &["init", "--quiet"]).unwrap();
            git(&path, &["config", "user.name", "Import Test"]).unwrap();
            git(&path, &["config", "user.email", "import@example.invalid"]).unwrap();
            Workspace::initialize(&path).unwrap();
            Self(path)
        }
        fn docs(&self) -> Documents {
            Documents::open(&self.0).unwrap()
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) {
            fs::remove_dir_all(&self.0).unwrap();
        }
    }

    fn source(name: &str, text: &str) -> ImportSource {
        ImportSource {
            name: name.to_owned(),
            bytes: text.as_bytes().to_vec(),
        }
    }

    const NEEDS: &str = "---\r\ntitle: User Needs\r\nstatus: draft\r\n---\r\n# Heading\r\n- **UN-001**: first (EQMS-016, R016-01, T016-02)\r\n- **UN-002**: second\r\nUN-001 again; EQMS-0101 and UN-12 are not identifiers.\r\n";

    #[test]
    fn identifiers_are_stable_sorted_and_unique() {
        assert_eq!(
            identifiers(NEEDS),
            ["EQMS-016", "R016-01", "T016-02", "UN-001", "UN-002"]
        );
        assert!(identifiers("XUN-001 R01-01 T016-1 EQMS-01").is_empty());
    }

    #[test]
    fn imports_lossless_draft_with_identifiers_and_source_hash() {
        let fixture = Fixture::new();
        let outcomes = fixture
            .docs()
            .import(&[source("docs/requirements/UserNeeds.md", NEEDS)])
            .unwrap();
        let [outcome] = &outcomes[..] else { panic!() };
        let expected = NEEDS.replace("\r\n", "\n");
        assert_eq!(outcome.document.body, expected);
        assert_eq!(outcome.document.title, "User Needs");
        assert_eq!(outcome.document.status, "Draft");
        assert!(outcome.line_endings_normalized && !outcome.already_imported);
        assert_eq!(outcome.source_hash, hash(NEEDS.as_bytes()));
        assert_eq!(outcome.identifiers, identifiers(&expected));
        assert_eq!(
            fixture.docs().history(&outcome.document.id).unwrap().len(),
            1
        );
        assert!(
            fixture
                .docs()
                .lifecycle_history(&outcome.document.id)
                .unwrap()
                .is_empty()
        );
    }

    #[test]
    fn titles_come_from_front_matter_heading_or_file_stem() {
        for (text, name, title) in [
            (
                "---\ntitle: \"Quoted: Title\"\n---\n# Other\n",
                "a.md",
                "Quoted: Title",
            ),
            ("---\ntitle: 'Single'\n---\nbody", "a.md", "Single"),
            (
                "intro\n# First heading \n# Second\n",
                "a.md",
                "First heading",
            ),
            ("---\nstatus: x\n---\n# In body\n", "a.md", "In body"),
            ("no title here", "docs/plan notes.md", "plan notes"),
            ("---\ntitle:\n---\nbody", "docs/AGENTS.md", "AGENTS"),
            ("# bad\u{7}title\nbody", "docs/EQMS-016.md", "EQMS-016"),
        ] {
            assert_eq!(derive_title(text, name), title, "{text:?}");
        }
    }

    #[test]
    fn invalid_batch_creates_nothing_and_reimport_is_idempotent() {
        let fixture = Fixture::new();
        let docs = fixture.docs();
        let invalid = ImportSource {
            name: "bad.md".to_owned(),
            bytes: vec![0xff, 0xfe],
        };
        let error = docs
            .import(&[source("ok.md", "# Ok\nUN-001\n"), invalid])
            .unwrap_err()
            .to_string();
        assert!(error.contains("bad.md is not valid UTF-8"), "{error}");
        assert!(docs.list().unwrap().is_empty());

        let first = docs.import(&[source("ok.md", "# Ok\nUN-001\n")]).unwrap();
        let again = docs
            .import(&[
                source("ok.md", "# Ok\nUN-001\n"),
                source("copy.md", "# Ok\r\nUN-001\r\n"),
            ])
            .unwrap();
        assert!(again.iter().all(|outcome| outcome.already_imported));
        assert!(again.iter().all(|o| o.document.id == first[0].document.id));
        assert_eq!(docs.list().unwrap().len(), 1);
        assert_eq!(docs.history(&first[0].document.id).unwrap().len(), 1);

        let changed = docs
            .import(&[source("ok.md", "# Ok\nUN-001 changed\n")])
            .unwrap();
        assert!(!changed[0].already_imported);
        assert_ne!(changed[0].document.id, first[0].document.id);
        assert_eq!(docs.list().unwrap().len(), 2);
    }
}
