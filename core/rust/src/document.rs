use crate::workspace::{
    JournalRecord, Workspace, WorkspaceError, ensure_git_identity, error, git, git_text,
    parse_and_verify_journal, timestamp, with_hash,
};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::io::Write;
use std::path::Path;
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};

const PREFIX: &str = "refs/heads/eqms-drafts/";
const JOURNAL: &str = ".eqms/journal.jsonl";
static NEXT: AtomicU64 = AtomicU64::new(0);

#[cfg(test)]
thread_local! {
    static VALIDATED_RECORDS: std::cell::Cell<usize> = const { std::cell::Cell::new(0) };
}

fn verify_journal(bytes: &[u8]) -> Result<Vec<JournalRecord>, WorkspaceError> {
    let records = parse_and_verify_journal(bytes)?;
    #[cfg(test)]
    VALIDATED_RECORDS.with(|count| count.set(count.get() + records.len()));
    Ok(records)
}

struct VerifiedHistory {
    documents: Vec<Document>,
    journal: Vec<u8>,
    last: JournalRecord,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
struct Metadata {
    schema_version: u32,
    id: String,
    title: String,
    status: String,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct Document {
    pub id: String,
    pub title: String,
    pub body: String,
    pub status: String,
    pub revision: String,
    pub content_hash: String,
    pub event_hash: String,
    pub configured_author: String,
    pub local_timestamp: String,
}

fn valid_hex(value: &str, lengths: &[usize]) -> bool {
    lengths.contains(&value.len())
        && value
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}

fn validate_id(id: &str) -> Result<(), WorkspaceError> {
    if !id.strip_prefix("DOC-").is_some_and(|v| valid_hex(v, &[32])) {
        return Err(error("invalid document ID"));
    }
    Ok(())
}

fn canonical(id: &str, title: &str, body: &str) -> Result<Vec<u8>, WorkspaceError> {
    validate_id(id)?;
    let title = title.trim();
    if title.is_empty() || title.chars().any(char::is_control) {
        return Err(error("document title must be a nonblank single line"));
    }
    let metadata = Metadata {
        schema_version: 1,
        id: id.to_owned(),
        title: title.to_owned(),
        status: "Draft".to_owned(),
    };
    let line = serde_json::to_string(&metadata).map_err(|e| error(e.to_string()))?;
    Ok(format!(
        "---\n{line}\n---\n{}",
        body.replace("\r\n", "\n").replace('\r', "\n")
    )
    .into_bytes())
}

fn decode(bytes: &[u8], id: &str) -> Result<(String, String), WorkspaceError> {
    let text = std::str::from_utf8(bytes).map_err(|e| error(e.to_string()))?;
    let (metadata, body) = text
        .strip_prefix("---\n")
        .and_then(|v| v.split_once("\n---\n"))
        .ok_or_else(|| error("invalid canonical document header"))?;
    let metadata: Metadata = serde_json::from_str(metadata).map_err(|e| error(e.to_string()))?;
    if metadata.schema_version != 1
        || metadata.id != id
        || metadata.status != "Draft"
        || canonical(id, &metadata.title, body)? != bytes
    {
        return Err(error("unsupported or noncanonical document metadata"));
    }
    Ok((metadata.title, body.to_owned()))
}

fn hash(bytes: &[u8]) -> String {
    format!("sha256:{:x}", Sha256::digest(bytes))
}

fn blob(root: &Path, revision: &str, path: &str) -> Result<Vec<u8>, WorkspaceError> {
    Ok(git(root, &["show", &format!("{revision}:{path}")])?.stdout)
}

fn input_git(root: &Path, args: &[&str], input: &[u8]) -> Result<String, WorkspaceError> {
    let mut child = Command::new("git")
        .arg("--literal-pathspecs")
        .arg("-C")
        .arg(root)
        .args(args)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| error(format!("cannot execute Git: {e}")))?;
    let write = child
        .stdin
        .take()
        .ok_or_else(|| error("Git stdin unavailable"))?
        .write_all(input);
    let output = child.wait_with_output()?;
    if !output.status.success() {
        return Err(error(format!(
            "Git {} failed: {}",
            args[0],
            String::from_utf8_lossy(&output.stderr)
        )));
    }
    write?;
    Ok(String::from_utf8(output.stdout)
        .map_err(|e| error(e.to_string()))?
        .trim()
        .to_owned())
}

fn replace_tree(
    root: &Path,
    tree: &str,
    name: &str,
    kind: &str,
    oid: &str,
) -> Result<String, WorkspaceError> {
    let entries = git(root, &["ls-tree", "-z", tree])?.stdout;
    let mut updated = Vec::new();
    for entry in entries.split(|b| *b == 0).filter(|e| !e.is_empty()) {
        let (_, filename) = entry.split_at(
            entry
                .iter()
                .position(|b| *b == b'\t')
                .ok_or_else(|| error("invalid tree entry"))?
                + 1,
        );
        if filename != name.as_bytes() {
            updated.extend_from_slice(entry);
            updated.push(0);
        }
    }
    updated.extend_from_slice(
        format!(
            "{} {kind} {oid}\t{name}\0",
            if kind == "tree" { "040000" } else { "100644" }
        )
        .as_bytes(),
    );
    input_git(root, &["mktree", "-z"], &updated)
}

/// Drafts are independent ref histories; the project working tree and index are never written.
pub struct Documents {
    workspace: Workspace,
}

impl Documents {
    pub(crate) fn with_search_sources<T>(
        path: &Path,
        operation: impl FnOnce(
            &Self,
            &Workspace,
            &BTreeMap<String, String>,
            &str,
        ) -> Result<T, WorkspaceError>,
    ) -> Result<T, WorkspaceError> {
        let documents = Self {
            workspace: Workspace::open(path)?,
        };
        let _lock = documents.workspace.acquire_lock()?;
        documents.verify_baseline()?;
        let refs = git_text(
            documents.root(),
            &["for-each-ref", "--format=%(refname) %(objectname)", PREFIX],
        )?;
        let mut sources = BTreeMap::new();
        for line in refs.lines() {
            let (name, revision) = line
                .split_once(' ')
                .ok_or_else(|| error("invalid draft ref"))?;
            let id = name
                .strip_prefix(PREFIX)
                .ok_or_else(|| error("invalid draft ref"))?;
            validate_id(id)?;
            if !valid_hex(revision, &[40, 64]) {
                return Err(error("invalid saved revision"));
            }
            sources.insert(id.to_owned(), revision.to_owned());
        }
        let baseline = git_text(documents.root(), &["rev-parse", "HEAD"])?;
        operation(&documents, &documents.workspace, &sources, baseline.trim())
    }

    pub fn open(path: impl AsRef<Path>) -> Result<Self, WorkspaceError> {
        let workspace = Workspace::open(path)?;
        workspace.verify()?;
        Ok(Self { workspace })
    }

    fn root(&self) -> &Path {
        self.workspace.path()
    }

    fn verify_baseline(&self) -> Result<(), WorkspaceError> {
        let current = git_text(self.root(), &["rev-parse", "--symbolic-full-name", "HEAD"])?;
        if current.trim().starts_with(PREFIX) {
            return Err(error(
                "open the project on its baseline, not an internal document draft branch",
            ));
        }
        self.workspace.verify_unlocked()?;
        Ok(())
    }

    fn head(&self, id: &str) -> Result<String, WorkspaceError> {
        validate_id(id)?;
        Ok(git_text(
            self.root(),
            &["rev-parse", "--verify", &format!("{PREFIX}{id}")],
        )?
        .trim()
        .to_owned())
    }

    pub fn list(&self) -> Result<Vec<Document>, WorkspaceError> {
        let _lock = self.workspace.acquire_lock()?;
        self.verify_baseline()?;
        let refs = git_text(
            self.root(),
            &["for-each-ref", "--format=%(refname)", PREFIX],
        )?;
        refs.lines()
            .map(|name| {
                let id = name
                    .strip_prefix(PREFIX)
                    .ok_or_else(|| error("invalid draft ref"))?;
                let revision = self.head(id)?;
                self.history_at(id, &revision)?
                    .into_iter()
                    .next()
                    .ok_or_else(|| error("empty history"))
            })
            .collect()
    }

    pub fn history(&self, id: &str) -> Result<Vec<Document>, WorkspaceError> {
        let _lock = self.workspace.acquire_lock()?;
        self.verify_baseline()?;
        self.history_at(id, &self.head(id)?)
    }

    pub fn show(&self, id: &str, revision: Option<&str>) -> Result<Document, WorkspaceError> {
        let history = self.history(id)?;
        match revision {
            Some(revision) => history.into_iter().find(|d| d.revision == revision),
            None => history.into_iter().next(),
        }
        .ok_or_else(|| error("revision is not a saved revision of this document"))
    }

    pub fn diff(&self, id: &str, from: &str, to: &str) -> Result<String, WorkspaceError> {
        let history = self.history(id)?;
        if ![from, to]
            .iter()
            .all(|r| history.iter().any(|d| d.revision == *r))
        {
            return Err(error(
                "comparison requires saved revisions of this document",
            ));
        }
        git_text(
            self.root(),
            &[
                "diff",
                "--no-ext-diff",
                "--no-textconv",
                "--no-color",
                "--text",
                "--no-renames",
                from,
                to,
                "--",
                &format!("documents/{id}.md"),
            ],
        )
    }

    pub fn create(&self, title: &str, body: &str) -> Result<Document, WorkspaceError> {
        let _lock = self.workspace.acquire_lock()?;
        self.verify_baseline()?;
        let seed = format!(
            "{}:{}:{}",
            timestamp()?,
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        );
        let id = format!("DOC-{}", &format!("{:x}", Sha256::digest(seed))[..32]);
        let base = git_text(self.root(), &["rev-parse", "HEAD"])?
            .trim()
            .to_owned();
        let content = canonical(&id, title, body)?;
        self.record(&id, &base, None, &content, None)
    }

    pub fn save(
        &self,
        id: &str,
        expected: &str,
        title: &str,
        body: &str,
    ) -> Result<Document, WorkspaceError> {
        let _lock = self.workspace.acquire_lock()?;
        self.verify_baseline()?;
        let current = self.head(id)?;
        if expected != current {
            return Err(error(
                "document changed since it was opened; reopen before saving (your edits were not saved)",
            ));
        }
        let verified = self.verified_history(id, &current)?;
        let previous = &verified.documents[0];
        let content = canonical(id, title, body)?;
        if previous.content_hash == hash(&content) {
            return Ok(previous.clone());
        }
        self.record(id, &current, Some(expected), &content, Some(&verified))
    }

    fn record(
        &self,
        id: &str,
        parent: &str,
        expected: Option<&str>,
        content: &[u8],
        verified_parent: Option<&VerifiedHistory>,
    ) -> Result<Document, WorkspaceError> {
        ensure_git_identity(self.root())?;
        let (mut journal, last) = match verified_parent {
            Some(verified) => (verified.journal.clone(), verified.last.clone()),
            None => {
                let journal = blob(self.root(), parent, JOURNAL)?;
                let records = verify_journal(&journal)?;
                let last = records
                    .last()
                    .ok_or_else(|| error("missing journal genesis"))?
                    .clone();
                (journal, last)
            }
        };
        let parent_length = journal.len();
        let path = format!("documents/{id}.md");
        let mut payload = BTreeMap::from([("document_id".to_owned(), id.to_owned())]);
        if expected.is_none() {
            payload.insert("base_revision".to_owned(), parent.to_owned());
            if !git(self.root(), &["ls-tree", "-z", parent, "--", &path])?
                .stdout
                .is_empty()
            {
                return Err(error("document ID already exists"));
            }
        }
        let event = with_hash(JournalRecord {
            schema_version: 1,
            sequence: last
                .sequence
                .checked_add(1)
                .ok_or_else(|| error("journal sequence overflow"))?,
            previous_hash: Some(last.hash.clone()),
            timestamp: timestamp()?,
            event_type: if expected.is_some() {
                "document.saved"
            } else {
                "document.created"
            }
            .to_owned(),
            payload,
            content_hashes: BTreeMap::from([(path, hash(content))]),
            hash: String::new(),
        })?;
        serde_json::to_writer(&mut journal, &event).map_err(|e| error(e.to_string()))?;
        journal.push(b'\n');
        let content_oid = input_git(self.root(), &["hash-object", "-w", "--stdin"], content)?;
        let journal_oid = input_git(self.root(), &["hash-object", "-w", "--stdin"], &journal)?;
        let documents = replace_tree(
            self.root(),
            &format!("{parent}:documents"),
            &format!("{id}.md"),
            "blob",
            &content_oid,
        )?;
        let eqms = replace_tree(
            self.root(),
            &format!("{parent}:.eqms"),
            "journal.jsonl",
            "blob",
            &journal_oid,
        )?;
        let tree = replace_tree(self.root(), parent, "documents", "tree", &documents)?;
        let tree = replace_tree(self.root(), &tree, ".eqms", "tree", &eqms)?;
        let revision = input_git(
            self.root(),
            &[
                "-c",
                "user.useConfigOnly=true",
                "commit-tree",
                &tree,
                "-p",
                parent,
            ],
            format!("Save draft {id}\n").as_bytes(),
        )?;
        if blob(self.root(), &revision, JOURNAL)? != journal {
            return Err(error("candidate journal differs from constructed event"));
        }
        let (document, actual_parent, _) =
            self.verify_revision(id, &revision, &event, &journal[..parent_length])?;
        if actual_parent != parent {
            return Err(error("candidate parent differs from verified parent"));
        }
        let absent = "0".repeat(revision.len());
        git(
            self.root(),
            &[
                "update-ref",
                "--no-deref",
                &format!("{PREFIX}{id}"),
                &revision,
                expected.unwrap_or(&absent),
            ],
        )?;
        Ok(document)
    }

    pub(crate) fn history_at(&self, id: &str, head: &str) -> Result<Vec<Document>, WorkspaceError> {
        Ok(self.verified_history(id, head)?.documents)
    }

    fn verified_history(&self, id: &str, head: &str) -> Result<VerifiedHistory, WorkspaceError> {
        validate_id(id)?;
        if !valid_hex(head, &[40, 64]) {
            return Err(error("invalid saved revision"));
        }
        let journal = blob(self.root(), head, JOURNAL)?;
        let records = verify_journal(&journal)?;
        let last = records
            .last()
            .ok_or_else(|| error("missing document event"))?
            .clone();
        let boundaries: Vec<_> = journal
            .iter()
            .enumerate()
            .filter_map(|(index, byte)| (*byte == b'\n').then_some(index + 1))
            .collect();
        let mut event_index = records.len() - 1;
        let mut revision = head.to_owned();
        let mut history = Vec::new();
        loop {
            if event_index == 0 {
                return Err(error("missing document creation event"));
            }
            let event = &records[event_index];
            // Each parent must equal the head's verified prefix, so it needs no reparse.
            let (document, parent, created) = self.verify_revision(
                id,
                &revision,
                event,
                &journal[..boundaries[event_index - 1]],
            )?;
            history.push(document);
            if created {
                return Ok(VerifiedHistory {
                    documents: history,
                    journal,
                    last,
                });
            }
            revision = parent;
            event_index -= 1;
        }
    }

    fn verify_revision(
        &self,
        id: &str,
        revision: &str,
        event: &JournalRecord,
        parent_journal: &[u8],
    ) -> Result<(Document, String, bool), WorkspaceError> {
        let path = format!("documents/{id}.md");
        let parents = git_text(self.root(), &["rev-list", "--parents", "-n", "1", revision])?;
        let parents: Vec<_> = parents.split_whitespace().collect();
        if parents.len() != 2 {
            return Err(error("draft revisions require exactly one parent"));
        }
        let parent = parents[1];
        let bytes = blob(self.root(), revision, &path)?;
        for canonical_path in [&path, JOURNAL] {
            let entry = git(self.root(), &["ls-tree", revision, "--", canonical_path])?;
            if !entry.stdout.starts_with(b"100644 blob ") {
                return Err(error("document and journal must be regular Git blobs"));
            }
        }
        let (title, body) = decode(&bytes, id)?;
        if blob(self.root(), parent, JOURNAL)? != parent_journal {
            return Err(error("ancestor journal differs from verified head prefix"));
        }
        let created = event.event_type == "document.created";
        let expected_payload = if created {
            BTreeMap::from([
                ("document_id".to_owned(), id.to_owned()),
                ("base_revision".to_owned(), parent.to_owned()),
            ])
        } else {
            BTreeMap::from([("document_id".to_owned(), id.to_owned())])
        };
        let changed = git(
            self.root(),
            &[
                "diff-tree",
                "--no-commit-id",
                "--name-only",
                "-r",
                "-z",
                parent,
                revision,
            ],
        )?
        .stdout;
        let changed: Vec<_> = changed
            .split(|b| *b == 0)
            .filter(|p| !p.is_empty())
            .collect();
        if (!created && event.event_type != "document.saved")
            || event.payload != expected_payload
            || event.content_hashes != BTreeMap::from([(path.clone(), hash(&bytes))])
            || changed.len() != 2
            || !changed.contains(&JOURNAL.as_bytes())
            || !changed.contains(&path.as_bytes())
        {
            return Err(error("invalid document revision or audit linkage"));
        }
        if !created && blob(self.root(), parent, &path)? == bytes {
            return Err(error("unchanged content recorded as a new revision"));
        }
        let author = git_text(self.root(), &["show", "-s", "--format=%an <%ae>", revision])?
            .trim()
            .to_owned();
        let document = Document {
            id: id.to_owned(),
            title,
            body,
            status: "Draft".to_owned(),
            revision: revision.to_owned(),
            content_hash: hash(&bytes),
            event_hash: event.hash.clone(),
            configured_author: author,
            local_timestamp: event.timestamp.clone(),
        };
        if created
            && !git(self.root(), &["ls-tree", "-z", parent, "--", &path])?
                .stdout
                .is_empty()
        {
            return Err(error("document creation overwrites an existing document"));
        }
        Ok((document, parent.to_owned(), created))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::path::PathBuf;

    struct Fixture(PathBuf);
    impl Fixture {
        fn new() -> Self {
            let seed = format!(
                "{}-{}",
                std::process::id(),
                NEXT.fetch_add(1, Ordering::Relaxed)
            );
            let path = std::env::temp_dir().join(format!("eqms-doc-{seed}"));
            fs::create_dir(&path).unwrap();
            git(&path, &["init", "--quiet"]).unwrap();
            git(&path, &["config", "user.name", "Document Test"]).unwrap();
            git(&path, &["config", "user.email", "document@example.invalid"]).unwrap();
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

    #[test]
    fn symbolic_draft_publication_does_not_advance_its_target() {
        let f = Fixture::new();
        let first = f.docs().create("Policy", "original").unwrap();
        let target = "refs/heads/unrelated";
        let reference = format!("{PREFIX}{}", first.id);
        git(&f.0, &["update-ref", target, &first.revision]).unwrap();
        git(&f.0, &["symbolic-ref", &reference, target]).unwrap();
        let saved = f
            .docs()
            .save(&first.id, &first.revision, "Policy", "updated")
            .unwrap();
        assert_ne!(saved.revision, first.revision);
        assert_eq!(
            git_text(&f.0, &["rev-parse", target]).unwrap().trim(),
            first.revision
        );
        assert_eq!(f.docs().head(&first.id).unwrap(), saved.revision);
        assert!(git(&f.0, &["symbolic-ref", &reference]).is_err());
        assert_eq!(f.docs().show(&first.id, None).unwrap(), saved);
    }

    #[test]
    fn history_validates_head_records_once_and_save_reuses_verified_parent() {
        let f = Fixture::new();
        let mut latest = f.docs().create("Policy", "0").unwrap();
        for revisions in 1..=8 {
            VALIDATED_RECORDS.with(|count| count.set(0));
            assert_eq!(f.docs().history(&latest.id).unwrap().len(), revisions);
            assert_eq!(VALIDATED_RECORDS.with(|count| count.get()), revisions + 1);
            VALIDATED_RECORDS.with(|count| count.set(0));
            latest = f
                .docs()
                .save(
                    &latest.id,
                    &latest.revision,
                    "Policy",
                    &revisions.to_string(),
                )
                .unwrap();
            assert_eq!(VALIDATED_RECORDS.with(|count| count.get()), revisions + 1);
        }
        VALIDATED_RECORDS.with(|count| count.set(0));
        assert_eq!(f.docs().list().unwrap(), vec![latest]);
        assert_eq!(VALIDATED_RECORDS.with(|count| count.get()), 10);
    }

    #[test]
    fn ancestor_journal_must_match_verified_head_prefix() {
        let f = Fixture::new();
        let first = f.docs().create("Policy", "original").unwrap();
        let second = f
            .docs()
            .save(&first.id, &first.revision, "Policy", "updated")
            .unwrap();
        let base = git_text(&f.0, &["rev-parse", "HEAD"]).unwrap();
        let forged_tree = replace_tree(
            &f.0,
            &first.revision,
            ".eqms",
            "tree",
            git_text(&f.0, &["rev-parse", &format!("{}:.eqms", base.trim())])
                .unwrap()
                .trim(),
        )
        .unwrap();
        let forged_parent = input_git(
            &f.0,
            &["commit-tree", &forged_tree, "-p", base.trim()],
            b"Wrong prefix\n",
        )
        .unwrap();
        let head_tree = git_text(
            &f.0,
            &["rev-parse", &format!("{}^{{tree}}", second.revision)],
        )
        .unwrap();
        let forged_head = input_git(
            &f.0,
            &["commit-tree", head_tree.trim(), "-p", &forged_parent],
            b"Preserve head journal\n",
        )
        .unwrap();
        git(
            &f.0,
            &["update-ref", &format!("{PREFIX}{}", first.id), &forged_head],
        )
        .unwrap();
        let failure = f.docs().history(&first.id).unwrap_err();
        assert!(failure.to_string().contains("ancestor journal differs"));
    }

    #[test]
    fn canonical_metadata_is_strict_and_roundtrips() {
        let id = "DOC-00000000000000000000000000000000";
        let bytes = canonical(id, " Quoted \"title\" ", "a\r\nb\r").unwrap();
        assert_eq!(
            decode(&bytes, id).unwrap(),
            ("Quoted \"title\"".into(), "a\nb\n".into())
        );
        assert!(canonical(id, " \n ", "").is_err());
        assert!(canonical("../escape", "title", "").is_err());
        let invalid = String::from_utf8(bytes)
            .unwrap()
            .replace("\"schema_version\":1", "\"schema_version\":2");
        assert!(decode(invalid.as_bytes(), id).is_err());
    }

    #[test]
    fn drafts_persist_without_touching_baseline_or_index() {
        let f = Fixture::new();
        fs::write(f.0.join("unrelated"), "staged").unwrap();
        git(&f.0, &["add", "unrelated"]).unwrap();
        fs::write(f.0.join("untracked"), "keep").unwrap();
        let head = git_text(&f.0, &["rev-parse", "HEAD"]).unwrap();
        let index = fs::read(f.0.join(".git/index")).unwrap();
        let journal = fs::read(f.0.join(JOURNAL)).unwrap();
        let first = f.docs().create("Policy", "one\n").unwrap();
        let other = f.docs().create("Procedure", "other\n").unwrap();
        let updated = f
            .docs()
            .save(&first.id, &first.revision, "Policy", "two\n")
            .unwrap();
        assert_eq!(
            f.docs()
                .save(&first.id, &updated.revision, "Policy", "two\n")
                .unwrap(),
            updated
        );
        assert!(
            f.docs()
                .save(&first.id, &first.revision, "Policy", "stale")
                .is_err()
        );
        assert_eq!(f.docs().list().unwrap().len(), 2);
        assert_eq!(
            f.docs().history(&first.id).unwrap(),
            vec![updated.clone(), first.clone()]
        );
        assert_eq!(
            f.docs().show(&first.id, Some(&first.revision)).unwrap(),
            first
        );
        assert!(f.docs().show(&first.id, Some(&other.revision)).is_err());
        assert!(f.docs().diff(&first.id, "HEAD", &updated.revision).is_err());
        let diff = f
            .docs()
            .diff(&first.id, &first.revision, &updated.revision)
            .unwrap();
        assert!(diff.contains("-one\n") && diff.contains("+two\n"));
        assert_eq!(git_text(&f.0, &["rev-parse", "HEAD"]).unwrap(), head);
        assert_eq!(fs::read(f.0.join(".git/index")).unwrap(), index);
        assert_eq!(fs::read(f.0.join(JOURNAL)).unwrap(), journal);
        assert_eq!(fs::read_to_string(f.0.join("untracked")).unwrap(), "keep");
        Workspace::open(&f.0).unwrap().verify().unwrap();
    }

    #[test]
    fn concurrent_saves_use_expected_revision() {
        let f = Fixture::new();
        let first = f.docs().create("Policy", "").unwrap();
        std::thread::scope(|scope| {
            let handles: Vec<_> = ["one", "two"]
                .into_iter()
                .map(|body| {
                    let first = &first;
                    let path = &f.0;
                    scope.spawn(move || {
                        Documents::open(path).unwrap().save(
                            &first.id,
                            &first.revision,
                            "Policy",
                            body,
                        )
                    })
                })
                .collect();
            let results: Vec<_> = handles.into_iter().map(|h| h.join().unwrap()).collect();
            assert_eq!(results.iter().filter(|r| r.is_ok()).count(), 1);
            assert_eq!(results.iter().filter(|r| r.is_err()).count(), 1);
        });
        assert_eq!(f.docs().history(&first.id).unwrap().len(), 2);
    }

    #[test]
    fn rejected_ref_update_exposes_no_partial_save() {
        let f = Fixture::new();
        let first = f.docs().create("Policy", "old").unwrap();
        let lock =
            f.0.join(".git")
                .join(format!("refs/heads/eqms-drafts/{}.lock", first.id));
        fs::write(&lock, "").unwrap();
        assert!(
            f.docs()
                .save(&first.id, &first.revision, "Policy", "new")
                .is_err()
        );
        assert_eq!(f.docs().show(&first.id, None).unwrap(), first);
        fs::remove_file(lock).unwrap();
        let saved = f
            .docs()
            .save(&first.id, &first.revision, "Policy", "new")
            .unwrap();
        assert_eq!(f.docs().show(&first.id, None).unwrap(), saved);
    }

    #[test]
    fn tampered_ref_and_missing_identity_are_rejected() {
        let f = Fixture::new();
        let first = f.docs().create("Policy", "body").unwrap();
        let base = git_text(&f.0, &["rev-parse", "HEAD"]).unwrap();
        git(
            &f.0,
            &["update-ref", &format!("{PREFIX}{}", first.id), base.trim()],
        )
        .unwrap();
        assert!(f.docs().list().is_err());
        git(&f.0, &["config", "user.name", ""]).unwrap();
        assert!(f.docs().create("Policy", "").is_err());
    }

    #[test]
    fn corrupted_content_journal_and_file_modes_are_rejected() {
        let f = Fixture::new();
        let first = f.docs().create("Policy", "original").unwrap();
        let path = format!("documents/{}.md", first.id);
        let reference = format!("{PREFIX}{}", first.id);
        for corrupt in ["content", "journal", "mode"] {
            let tree = if corrupt == "journal" {
                let mut bytes = blob(&f.0, &first.revision, JOURNAL).unwrap();
                bytes[10] = b'X';
                let oid = input_git(&f.0, &["hash-object", "-w", "--stdin"], &bytes).unwrap();
                let subtree = replace_tree(
                    &f.0,
                    &format!("{}:.eqms", first.revision),
                    "journal.jsonl",
                    "blob",
                    &oid,
                )
                .unwrap();
                replace_tree(&f.0, &first.revision, ".eqms", "tree", &subtree).unwrap()
            } else {
                let bytes = canonical(
                    &first.id,
                    "Policy",
                    if corrupt == "mode" {
                        "original"
                    } else {
                        "tampered"
                    },
                )
                .unwrap();
                let oid = input_git(&f.0, &["hash-object", "-w", "--stdin"], &bytes).unwrap();
                let subtree = if corrupt == "mode" {
                    let mut entries = git(
                        &f.0,
                        &["ls-tree", "-z", &format!("{}:documents", first.revision)],
                    )
                    .unwrap()
                    .stdout;
                    let target = format!("100644 blob {oid}\t{}.md", first.id);
                    let offset = entries
                        .windows(target.len())
                        .position(|v| v == target.as_bytes())
                        .unwrap();
                    entries[offset..offset + 6].copy_from_slice(b"120000");
                    input_git(&f.0, &["mktree", "-z"], &entries).unwrap()
                } else {
                    replace_tree(
                        &f.0,
                        &format!("{}:documents", first.revision),
                        &format!("{}.md", first.id),
                        "blob",
                        &oid,
                    )
                    .unwrap()
                };
                replace_tree(&f.0, &first.revision, "documents", "tree", &subtree).unwrap()
            };
            let commit = input_git(
                &f.0,
                &["commit-tree", &tree, "-p", &first.revision],
                b"Tamper\n",
            )
            .unwrap();
            git(&f.0, &["update-ref", &reference, &commit]).unwrap();
            assert!(f.docs().history(&first.id).is_err(), "{corrupt}: {path}");
            git(&f.0, &["update-ref", &reference, &first.revision]).unwrap();
        }
    }

    #[test]
    fn authoring_refuses_checked_out_internal_draft() {
        let f = Fixture::new();
        let first = f.docs().create("Policy", "original").unwrap();
        git(
            &f.0,
            &["checkout", "--quiet", &format!("eqms-drafts/{}", first.id)],
        )
        .unwrap();
        let docs = f.docs();
        assert!(docs.list().is_err());
        assert!(docs.create("Other", "").is_err());
        assert!(
            docs.save(&first.id, &first.revision, "Policy", "changed")
                .is_err()
        );
        assert_eq!(docs.head(&first.id).unwrap(), first.revision);
    }
}
