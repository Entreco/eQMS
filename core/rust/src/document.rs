use crate::markdown::{self, Markdown, hash};
use crate::workspace::{
    JournalRecord, Workspace, WorkspaceError, ensure_git_identity, error, git, git_text,
    parse_and_verify_journal, read_git_blobs, timestamp, with_hash,
};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::io::Write;
use std::path::Path;
use std::process::{Command, Stdio};
use std::str::FromStr;
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
    lifecycle_events: Vec<LifecycleEvent>,
}

struct HistoryCommit {
    revision: String,
    parent: String,
    author: String,
    changes: BTreeMap<String, HistoryChange>,
}

struct HistoryChange {
    old_mode: String,
    new_mode: String,
    old_oid: String,
    new_oid: String,
    status: String,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LifecycleState {
    Draft,
    InReview,
    Approved,
    Effective,
    Obsolete,
    Superseded,
}

impl LifecycleState {
    fn as_str(self) -> &'static str {
        match self {
            Self::Draft => "Draft",
            Self::InReview => "In Review",
            Self::Approved => "Approved",
            Self::Effective => "Effective",
            Self::Obsolete => "Obsolete",
            Self::Superseded => "Superseded",
        }
    }

    fn allows(self, next: Self) -> bool {
        matches!(
            (self, next),
            (Self::Draft, Self::InReview)
                | (Self::InReview, Self::Approved)
                | (Self::Approved, Self::Effective)
                | (Self::Effective, Self::Obsolete)
                | (Self::Effective, Self::Superseded)
        )
    }
}

impl FromStr for LifecycleState {
    type Err = String;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        match value {
            "Draft" => Ok(Self::Draft),
            "In Review" | "InReview" => Ok(Self::InReview),
            "Approved" => Ok(Self::Approved),
            "Effective" => Ok(Self::Effective),
            "Obsolete" => Ok(Self::Obsolete),
            "Superseded" => Ok(Self::Superseded),
            _ => Err(format!("unknown document lifecycle state: {value}")),
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct LifecycleEvent {
    pub revision: String,
    pub event_hash: String,
    pub content_hash: String,
    pub from_status: String,
    pub to_status: String,
    pub local_timestamp: String,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct DocumentHistorySnapshot {
    pub revisions: Vec<Document>,
    pub lifecycle_events: Vec<LifecycleEvent>,
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
    markdown::validate_id(id).map_err(|cause| error(cause.to_string()))
}

fn canonical(id: &str, title: &str, body: &str) -> Result<Vec<u8>, WorkspaceError> {
    Markdown::new(id, title, body)
        .and_then(|document| document.canonical_bytes())
        .map_err(|cause| error(cause.to_string()))
}

fn decode(bytes: &[u8], id: &str) -> Result<(String, String), WorkspaceError> {
    let document = Markdown::validate(bytes).map_err(|cause| error(cause.to_string()))?;
    if document.id() != id {
        return Err(error("document ID differs from expected document"));
    }
    Ok((document.title().to_owned(), document.body().to_owned()))
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
        Self::with_sources(
            path,
            |_| Ok(()),
            |documents, workspace, sources, baseline| {
                documents.verify_baseline()?;
                operation(documents, workspace, sources, baseline)
            },
        )
    }

    pub(crate) fn with_sources<T>(
        path: &Path,
        validate_inventory: impl FnOnce(&Workspace) -> Result<(), WorkspaceError>,
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
        validate_inventory(&documents.workspace)?;
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
        Ok(Self {
            workspace: Workspace::open(path)?,
        })
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
            &["for-each-ref", "--format=%(refname) %(objectname)", PREFIX],
        )?;
        refs.lines()
            .map(|line| {
                let (name, revision) = line
                    .split_once(' ')
                    .ok_or_else(|| error("invalid draft ref"))?;
                let id = name
                    .strip_prefix(PREFIX)
                    .ok_or_else(|| error("invalid draft ref"))?;
                self.history_at(id, revision)?
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

    pub fn lifecycle_history(&self, id: &str) -> Result<Vec<LifecycleEvent>, WorkspaceError> {
        let _lock = self.workspace.acquire_lock()?;
        self.verify_baseline()?;
        Ok(self.verified_history(id, &self.head(id)?)?.lifecycle_events)
    }

    pub fn history_snapshot(&self, id: &str) -> Result<DocumentHistorySnapshot, WorkspaceError> {
        let _lock = self.workspace.acquire_lock()?;
        self.verify_baseline()?;
        let history = self.verified_history(id, &self.head(id)?)?;
        Ok(DocumentHistorySnapshot {
            revisions: history.documents,
            lifecycle_events: history.lifecycle_events,
        })
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
        if previous.status != LifecycleState::Draft.as_str() {
            return Err(error(
                "document content cannot be saved after leaving Draft; lifecycle rework is not supported",
            ));
        }
        let content = canonical(id, title, body)?;
        if previous.content_hash == hash(&content) {
            return Ok(previous.clone());
        }
        self.record(id, &current, Some(expected), &content, Some(&verified))
    }

    pub fn transition(
        &self,
        id: &str,
        expected: &str,
        target: LifecycleState,
    ) -> Result<Document, WorkspaceError> {
        let _lock = self.workspace.acquire_lock()?;
        self.verify_baseline()?;
        let current = self.head(id)?;
        if expected != current {
            return Err(error(
                "document changed since it was opened; reopen before transitioning (no lifecycle state was changed)",
            ));
        }
        let verified = self.verified_history(id, &current)?;
        let previous = &verified.documents[0];
        let from = LifecycleState::from_str(&previous.status).map_err(error)?;
        if !from.allows(target) {
            return Err(error(format!(
                "invalid document lifecycle transition: {} -> {}",
                from.as_str(),
                target.as_str()
            )));
        }
        self.record_lifecycle(id, expected, from, target, previous, &verified)
    }

    fn record_lifecycle(
        &self,
        id: &str,
        expected: &str,
        from: LifecycleState,
        target: LifecycleState,
        previous: &Document,
        verified_parent: &VerifiedHistory,
    ) -> Result<Document, WorkspaceError> {
        let parent = expected;
        ensure_git_identity(self.root())?;
        let mut journal = verified_parent.journal.clone();
        let parent_length = journal.len();
        let path = format!("documents/{id}.md");
        let event = with_hash(JournalRecord {
            schema_version: verified_parent.last.schema_version,
            sequence: verified_parent
                .last
                .sequence
                .checked_add(1)
                .ok_or_else(|| error("journal sequence overflow"))?,
            previous_hash: Some(verified_parent.last.hash.clone()),
            timestamp: timestamp()?,
            event_type: "document.lifecycle.transition".to_owned(),
            payload: BTreeMap::from([
                ("document_id".to_owned(), id.to_owned()),
                ("expected_revision".to_owned(), expected.to_owned()),
                ("from_status".to_owned(), from.as_str().to_owned()),
                ("to_status".to_owned(), target.as_str().to_owned()),
            ]),
            content_hashes: BTreeMap::from([(path, previous.content_hash.clone())]),
            hash: String::new(),
        })?;
        serde_json::to_writer(&mut journal, &event).map_err(|e| error(e.to_string()))?;
        journal.push(b'\n');
        let journal_oid = input_git(self.root(), &["hash-object", "-w", "--stdin"], &journal)?;
        let eqms = replace_tree(
            self.root(),
            &format!("{parent}:.eqms"),
            "journal.jsonl",
            "blob",
            &journal_oid,
        )?;
        let tree = replace_tree(self.root(), parent, ".eqms", "tree", &eqms)?;
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
            format!("Transition document {id} to {}\n", target.as_str()).as_bytes(),
        )?;
        if blob(self.root(), &revision, JOURNAL)? != journal {
            return Err(error(
                "candidate journal differs from constructed lifecycle event",
            ));
        }
        let (mut document, actual_parent, created) =
            self.verify_revision(id, &revision, &event, &journal[..parent_length])?;
        if actual_parent != parent || created {
            return Err(error("candidate parent differs from verified parent"));
        }
        document.status = target.as_str().to_owned();
        git(
            self.root(),
            &[
                "update-ref",
                "--no-deref",
                &format!("{PREFIX}{id}"),
                &revision,
                expected,
            ],
        )?;
        Ok(document)
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

    pub(crate) fn history_with_saved_revision_count_at(
        &self,
        id: &str,
        head: &str,
    ) -> Result<(Vec<Document>, usize), WorkspaceError> {
        let history = self.verified_history(id, head)?;
        let saved_revisions = history.documents.len() - history.lifecycle_events.len();
        Ok((history.documents, saved_revisions))
    }

    fn verified_history(&self, id: &str, head: &str) -> Result<VerifiedHistory, WorkspaceError> {
        validate_id(id)?;
        if !valid_hex(head, &[40, 64]) {
            return Err(error("invalid saved revision"));
        }
        let journal = blob(self.root(), head, JOURNAL)?;
        let records = verify_journal(&journal)
            .map_err(|cause| error(format!("draft revision {head}: {cause}")))?;
        let last = records
            .last()
            .ok_or_else(|| error("missing document event"))?
            .clone();
        let boundaries: Vec<_> = journal
            .iter()
            .enumerate()
            .filter_map(|(index, byte)| (*byte == b'\n').then_some(index + 1))
            .collect();
        let document_events: Vec<_> = records
            .iter()
            .enumerate()
            .filter(|(_, event)| {
                event
                    .payload
                    .get("document_id")
                    .is_some_and(|value| value == id)
            })
            .collect();
        if document_events.is_empty() || document_events[0].0 == 0 {
            return Err(error("missing document creation event"));
        }

        let count = document_events.len().to_string();
        let output = git_text(
            self.root(),
            &[
                "log",
                "--first-parent",
                "--no-renames",
                "--no-abbrev",
                "--max-count",
                &count,
                "--format=%H%x00%P%x00%an <%ae>",
                "--raw",
                head,
            ],
        )?;
        let mut commits = Vec::<HistoryCommit>::with_capacity(document_events.len());
        for line in output.lines().filter(|line| !line.is_empty()) {
            if line.starts_with(':') {
                let (metadata, path) = line
                    .split_once('\t')
                    .ok_or_else(|| error("invalid raw Git history change"))?;
                let fields: Vec<_> = metadata
                    .strip_prefix(':')
                    .ok_or_else(|| error("invalid raw Git history change"))?
                    .split_whitespace()
                    .collect();
                if fields.len() != 5 || path.is_empty() {
                    return Err(error("invalid raw Git history change"));
                }
                let change = HistoryChange {
                    old_mode: fields[0].to_owned(),
                    new_mode: fields[1].to_owned(),
                    old_oid: fields[2].to_owned(),
                    new_oid: fields[3].to_owned(),
                    status: fields[4].to_owned(),
                };
                if !valid_hex(&change.old_oid, &[40, 64]) || !valid_hex(&change.new_oid, &[40, 64])
                {
                    return Err(error("invalid object ID in raw Git history"));
                }
                let commit = commits
                    .last_mut()
                    .ok_or_else(|| error("raw Git history change has no commit"))?;
                if commit.changes.insert(path.to_owned(), change).is_some() {
                    return Err(error("duplicate path in raw Git history"));
                }
            } else {
                let fields: Vec<_> = line.split('\0').collect();
                if fields.len() != 3 || !valid_hex(fields[0], &[40, 64]) {
                    return Err(error("invalid raw Git history commit"));
                }
                let parents: Vec<_> = fields[1].split_whitespace().collect();
                if parents.len() != 1 || !valid_hex(parents[0], &[40, 64]) {
                    return Err(error("draft revisions require exactly one parent"));
                }
                commits.push(HistoryCommit {
                    revision: fields[0].to_owned(),
                    parent: parents[0].to_owned(),
                    author: fields[2].trim().to_owned(),
                    changes: BTreeMap::new(),
                });
            }
        }
        if commits.len() != document_events.len()
            || commits.first().is_none_or(|commit| commit.revision != head)
        {
            return Err(error("Git history does not match the document event chain"));
        }
        commits.reverse();
        for pair in commits.windows(2) {
            if pair[1].parent != pair[0].revision {
                return Err(error("document revisions are not a single parent chain"));
            }
        }

        let document_path = format!("documents/{id}.md");
        let mut blob_selectors = Vec::with_capacity(commits.len() * 2 + 1);
        for ((_, event), commit) in document_events.iter().zip(&commits) {
            let created = event.event_type == "document.created";
            let saved = event.event_type == "document.saved";
            let lifecycle = event.event_type == "document.lifecycle.transition";
            if !created && !saved && !lifecycle {
                return Err(error("invalid document event in draft history"));
            }
            blob_selectors.push(format!("{}:{JOURNAL}", commit.parent));
            if (created || saved)
                && let Some(document_change) = commit.changes.get(&document_path)
            {
                blob_selectors.push(document_change.new_oid.clone());
            }
        }
        let head_journal_selector = format!("{head}:{JOURNAL}");
        blob_selectors.push(head_journal_selector.clone());
        let selectors: Vec<_> = blob_selectors.iter().map(String::as_str).collect();
        let blobs = read_git_blobs(self.root(), None, &selectors)?;
        if blobs.get(&head_journal_selector) != Some(&journal) {
            return Err(error("head journal differs from the verified Git blob"));
        }
        for ((event_index, _), commit) in document_events.iter().zip(&commits) {
            if *event_index == 0 || *event_index >= boundaries.len() {
                return Err(error("invalid document event position in journal"));
            }
            let parent_journal_selector = format!("{}:{JOURNAL}", commit.parent);
            let parent_journal = blobs
                .get(&parent_journal_selector)
                .ok_or_else(|| error("missing parent journal blob"))?;
            if parent_journal != &journal[..boundaries[event_index - 1]] {
                return Err(error("ancestor journal differs from verified head prefix"));
            }
        }

        let mut history = Vec::with_capacity(commits.len());
        let mut previous_journal_oid: Option<String> = None;
        let mut previous_document_oid: Option<String> = None;
        let mut previous_document_bytes: Option<Vec<u8>> = None;
        for ((_, event), commit) in document_events.iter().zip(&commits) {
            let created = event.event_type == "document.created";
            let saved = event.event_type == "document.saved";
            let lifecycle = event.event_type == "document.lifecycle.transition";
            let expected_paths = if lifecycle {
                vec![JOURNAL]
            } else {
                vec![JOURNAL, document_path.as_str()]
            };
            if commit.changes.len() != expected_paths.len()
                || expected_paths
                    .iter()
                    .any(|path| !commit.changes.contains_key(*path))
            {
                return Err(error("invalid document revision or audit linkage"));
            }
            let journal_change = &commit.changes[JOURNAL];
            if journal_change.old_mode != "100644"
                || journal_change.new_mode != "100644"
                || journal_change.status != "M"
            {
                return Err(error("document and journal must be regular Git blobs"));
            }
            if previous_journal_oid
                .as_ref()
                .is_some_and(|previous| previous != &journal_change.old_oid)
            {
                return Err(error("document revisions do not form a journal chain"));
            }
            let document = if created || saved {
                let change = &commit.changes[&document_path];
                if change.new_mode != "100644"
                    || (created
                        && (change.old_mode != "000000"
                            || change.status != "A"
                            || !change.old_oid.bytes().all(|byte| byte == b'0')))
                    || (saved && (change.old_mode != "100644" || change.status != "M"))
                {
                    return Err(error("document and journal must be regular Git blobs"));
                }
                if saved && previous_document_oid.as_ref() != Some(&change.old_oid) {
                    return Err(error("document revisions do not form a content chain"));
                }
                if created && previous_document_oid.is_some() {
                    return Err(error("document creation overwrites an existing document"));
                }
                let bytes = blobs
                    .get(&change.new_oid)
                    .ok_or_else(|| error("missing document content blob"))?
                    .clone();
                let (title, body) = decode(&bytes, id)?;
                let expected_payload = if created {
                    BTreeMap::from([
                        ("document_id".to_owned(), id.to_owned()),
                        ("base_revision".to_owned(), commit.parent.clone()),
                    ])
                } else {
                    BTreeMap::from([("document_id".to_owned(), id.to_owned())])
                };
                if event.payload != expected_payload {
                    return Err(error("invalid document revision payload"));
                }
                if event.content_hashes != BTreeMap::from([(document_path.clone(), hash(&bytes))]) {
                    return Err(error("invalid document content hash linkage"));
                }
                if saved
                    && previous_document_bytes
                        .as_ref()
                        .is_some_and(|previous| previous == &bytes)
                {
                    return Err(error("invalid document revision or audit linkage"));
                }
                let document = Document {
                    id: id.to_owned(),
                    title,
                    body,
                    status: "Draft".to_owned(),
                    revision: commit.revision.clone(),
                    content_hash: hash(&bytes),
                    event_hash: event.hash.clone(),
                    configured_author: commit.author.clone(),
                    local_timestamp: event.timestamp.clone(),
                };
                previous_document_oid = Some(change.new_oid.clone());
                previous_document_bytes = Some(bytes);
                document
            } else if lifecycle {
                let bytes = previous_document_bytes
                    .as_ref()
                    .ok_or_else(|| error("lifecycle event precedes document creation"))?;
                if event.payload
                    != BTreeMap::from([
                        ("document_id".to_owned(), id.to_owned()),
                        ("expected_revision".to_owned(), commit.parent.clone()),
                        (
                            "from_status".to_owned(),
                            event
                                .payload
                                .get("from_status")
                                .cloned()
                                .unwrap_or_default(),
                        ),
                        (
                            "to_status".to_owned(),
                            event.payload.get("to_status").cloned().unwrap_or_default(),
                        ),
                    ])
                    || event.content_hashes
                        != BTreeMap::from([(document_path.clone(), hash(bytes))])
                {
                    return Err(error("invalid document revision or audit linkage"));
                }
                let (title, body) = decode(bytes, id)?;
                Document {
                    id: id.to_owned(),
                    title,
                    body,
                    status: "Draft".to_owned(),
                    revision: commit.revision.clone(),
                    content_hash: hash(bytes),
                    event_hash: event.hash.clone(),
                    configured_author: commit.author.clone(),
                    local_timestamp: event.timestamp.clone(),
                }
            } else {
                return Err(error("invalid document event in draft history"));
            };
            history.push((document, (*event).clone()));
            previous_journal_oid = Some(journal_change.new_oid.clone());
        }
        if previous_journal_oid.is_none() || blobs.get(&head_journal_selector) != Some(&journal) {
            return Err(error("verified document history does not reach its head"));
        }
        let mut state = LifecycleState::Draft;
        let mut lifecycle_events = Vec::new();
        for (document, event) in &mut history {
            match event.event_type.as_str() {
                "document.created" => state = LifecycleState::Draft,
                "document.saved" => {
                    if state != LifecycleState::Draft {
                        return Err(error(
                            "document content was saved after leaving Draft; lifecycle history is invalid",
                        ));
                    }
                }
                "document.lifecycle.transition" => {
                    let from = LifecycleState::from_str(
                        event
                            .payload
                            .get("from_status")
                            .ok_or_else(|| error("lifecycle event is missing from_status"))?,
                    )
                    .map_err(error)?;
                    let to = LifecycleState::from_str(
                        event
                            .payload
                            .get("to_status")
                            .ok_or_else(|| error("lifecycle event is missing to_status"))?,
                    )
                    .map_err(error)?;
                    if from != state || !from.allows(to) {
                        return Err(error("invalid document lifecycle event history"));
                    }
                    lifecycle_events.push(LifecycleEvent {
                        revision: document.revision.clone(),
                        event_hash: document.event_hash.clone(),
                        content_hash: document.content_hash.clone(),
                        from_status: from.as_str().to_owned(),
                        to_status: to.as_str().to_owned(),
                        local_timestamp: event.timestamp.clone(),
                    });
                    state = to;
                }
                _ => return Err(error("invalid document event in draft history")),
            }
            document.status = state.as_str().to_owned();
        }
        history.reverse();
        Ok(VerifiedHistory {
            documents: history.into_iter().map(|(document, _)| document).collect(),
            journal,
            last,
            lifecycle_events,
        })
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
        let saved = event.event_type == "document.saved";
        let lifecycle = event.event_type == "document.lifecycle.transition";
        let expected_payload = if created {
            BTreeMap::from([
                ("document_id".to_owned(), id.to_owned()),
                ("base_revision".to_owned(), parent.to_owned()),
            ])
        } else if saved {
            BTreeMap::from([("document_id".to_owned(), id.to_owned())])
        } else if lifecycle {
            BTreeMap::from([
                ("document_id".to_owned(), id.to_owned()),
                ("expected_revision".to_owned(), parent.to_owned()),
                (
                    "from_status".to_owned(),
                    event
                        .payload
                        .get("from_status")
                        .cloned()
                        .unwrap_or_default(),
                ),
                (
                    "to_status".to_owned(),
                    event.payload.get("to_status").cloned().unwrap_or_default(),
                ),
            ])
        } else {
            BTreeMap::new()
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
        let expected_changed = if lifecycle {
            vec![JOURNAL.as_bytes()]
        } else {
            vec![JOURNAL.as_bytes(), path.as_bytes()]
        };
        if (!created && !saved && !lifecycle)
            || event.payload != expected_payload
            || event.content_hashes != BTreeMap::from([(path.clone(), hash(&bytes))])
            || changed.len() != expected_changed.len()
            || expected_changed
                .iter()
                .any(|entry| !changed.contains(entry))
        {
            return Err(error("invalid document revision or audit linkage"));
        }
        if saved && blob(self.root(), parent, &path)? == bytes {
            return Err(error("unchanged content recorded as a new revision"));
        }
        if lifecycle && blob(self.root(), parent, &path)? != bytes {
            return Err(error(
                "lifecycle transition changed canonical document content",
            ));
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
    fn pre_codec_extraction_history_retains_exact_content_hash_and_revision() {
        let path = std::env::temp_dir().join(format!(
            "eqms-legacy-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&path).unwrap();
        let fixture = Fixture(path);
        git(&fixture.0, &["init", "--quiet"]).unwrap();
        let export: String =
            serde_json::from_str(include_str!("../tests/fixtures/eqms020-legacy.json")).unwrap();
        input_git(&fixture.0, &["fast-import", "--quiet"], export.as_bytes()).unwrap();
        git(&fixture.0, &["symbolic-ref", "HEAD", "refs/heads/main"]).unwrap();
        git(&fixture.0, &["read-tree", "HEAD"]).unwrap();
        git(&fixture.0, &["checkout-index", "-a"]).unwrap();
        let id = "DOC-3683c4441260979ff0733dd027d68911";
        let documents = fixture.docs();
        let legacy = documents.show(id, None).unwrap();
        assert_eq!(legacy.id, id);
        assert_eq!(legacy.title, "Legacy policy");
        assert_eq!(legacy.body, "Legacy body  \nNo final newline");
        assert_eq!(legacy.revision, "2c83fc82db4e2bbeb178361134478aa5d84a2cc6");
        assert_eq!(
            legacy.content_hash,
            "sha256:224514eb12998689344189b973cb15debd199e90ea748e0a70033fc4ff3a6308"
        );
        assert_eq!(documents.history(id).unwrap(), vec![legacy.clone()]);
        let bytes = blob(&fixture.0, &legacy.revision, &format!("documents/{id}.md")).unwrap();
        assert_eq!(canonical(id, &legacy.title, &legacy.body).unwrap(), bytes);
        assert!(decode(&bytes, "DOC-00000000000000000000000000000000").is_err());
        let noncanonical = String::from_utf8(bytes)
            .unwrap()
            .replace("\"schema_version\":1", "\"schema_version\": 1");
        assert!(decode(noncanonical.as_bytes(), id).is_err());
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
    fn history_snapshot_returns_revisions_and_lifecycle_from_one_verified_chain() {
        let f = Fixture::new();
        let docs = f.docs();
        let draft = docs.create("Policy", "content").unwrap();
        docs.transition(&draft.id, &draft.revision, LifecycleState::InReview)
            .unwrap();

        VALIDATED_RECORDS.with(|count| count.set(0));
        let snapshot = docs.history_snapshot(&draft.id).unwrap();
        assert_eq!(snapshot.revisions.len(), 2);
        assert_eq!(snapshot.lifecycle_events.len(), 1);
        assert_eq!(
            snapshot.lifecycle_events[0].revision,
            snapshot.revisions[0].revision
        );
        assert_eq!(
            VALIDATED_RECORDS.with(|count| count.get()),
            snapshot.revisions.len() + 1
        );
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
        assert!(
            failure.to_string().contains("ancestor journal differs"),
            "{failure}"
        );
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
    fn lifecycle_transitions_are_strict_durable_and_isolated() {
        let f = Fixture::new();
        fs::write(f.0.join("unrelated"), "staged").unwrap();
        git(&f.0, &["add", "unrelated"]).unwrap();
        fs::write(f.0.join("untracked"), "keep").unwrap();
        let baseline = git_text(&f.0, &["rev-parse", "HEAD"]).unwrap();
        let index = fs::read(f.0.join(".git/index")).unwrap();
        let journal = fs::read(f.0.join(JOURNAL)).unwrap();

        let docs = f.docs();
        let initial = docs.create("Policy", "unchanged bytes\n").unwrap();
        let content = blob(
            &f.0,
            &initial.revision,
            &format!("documents/{}.md", initial.id),
        )
        .unwrap();
        let allowed = [
            LifecycleState::InReview,
            LifecycleState::Approved,
            LifecycleState::Effective,
            LifecycleState::Superseded,
        ];
        let mut current = initial.clone();
        let mut observed = vec![LifecycleState::Draft.as_str().to_owned()];
        for target in allowed {
            let before = current.clone();
            for invalid in [
                LifecycleState::Draft,
                LifecycleState::InReview,
                LifecycleState::Approved,
                LifecycleState::Effective,
                LifecycleState::Obsolete,
                LifecycleState::Superseded,
            ] {
                if LifecycleState::from_str(&before.status)
                    .unwrap()
                    .allows(invalid)
                {
                    continue;
                }
                assert!(
                    docs.transition(&initial.id, &before.revision, invalid)
                        .is_err(),
                    "{} -> {} must be rejected",
                    before.status,
                    invalid.as_str()
                );
                assert_eq!(docs.show(&initial.id, None).unwrap(), before);
            }
            current = docs
                .transition(&initial.id, &before.revision, target)
                .unwrap();
            observed.push(target.as_str().to_owned());
            assert_eq!(current.status, target.as_str());
            assert_eq!(
                blob(
                    &f.0,
                    &current.revision,
                    &format!("documents/{}.md", initial.id)
                )
                .unwrap(),
                content
            );
        }

        let stale = docs
            .transition(&initial.id, &initial.revision, LifecycleState::InReview)
            .unwrap_err();
        assert!(stale.to_string().contains("changed since it was opened"));
        assert_eq!(docs.show(&initial.id, None).unwrap(), current);
        assert!(
            docs.save(&initial.id, &current.revision, "Changed", "overwrite")
                .is_err()
        );
        for target in [
            LifecycleState::Draft,
            LifecycleState::InReview,
            LifecycleState::Approved,
            LifecycleState::Effective,
            LifecycleState::Obsolete,
            LifecycleState::Superseded,
        ] {
            assert!(
                docs.transition(&initial.id, &current.revision, target)
                    .is_err()
            );
        }

        let reopened = Documents::open(&f.0).unwrap();
        assert_eq!(reopened.show(&initial.id, None).unwrap(), current);
        assert_eq!(
            reopened
                .history(&initial.id)
                .unwrap()
                .iter()
                .map(|document| document.status.clone())
                .rev()
                .collect::<Vec<_>>(),
            observed
        );
        let events = reopened.lifecycle_history(&initial.id).unwrap();
        assert_eq!(events.len(), 4);
        assert_eq!(events[0].from_status, "Draft");
        assert_eq!(events[0].to_status, "In Review");
        assert_eq!(events[2].to_status, "Effective");
        assert_eq!(events[3].to_status, "Superseded");
        assert!(
            events
                .iter()
                .all(|event| event.content_hash == initial.content_hash)
        );

        let second = reopened.create("Procedure", "same").unwrap();
        let mut second_current = second;
        for target in [
            LifecycleState::InReview,
            LifecycleState::Approved,
            LifecycleState::Effective,
            LifecycleState::Obsolete,
        ] {
            second_current = reopened
                .transition(&second_current.id, &second_current.revision, target)
                .unwrap();
        }
        assert_eq!(second_current.status, "Obsolete");
        for target in [
            LifecycleState::Draft,
            LifecycleState::InReview,
            LifecycleState::Approved,
            LifecycleState::Effective,
            LifecycleState::Obsolete,
            LifecycleState::Superseded,
        ] {
            assert!(
                reopened
                    .transition(&second_current.id, &second_current.revision, target)
                    .is_err()
            );
        }
        let audit = crate::audit::verify_ledger(&f.0, None).unwrap();
        assert!(audit.drafts.iter().all(|draft| draft.saved_revisions == 1));
        assert_eq!(git_text(&f.0, &["rev-parse", "HEAD"]).unwrap(), baseline);
        assert_eq!(fs::read(f.0.join(".git/index")).unwrap(), index);
        assert_eq!(fs::read(f.0.join(JOURNAL)).unwrap(), journal);
        assert_eq!(fs::read_to_string(f.0.join("untracked")).unwrap(), "keep");
        assert!(
            git_text(&f.0, &["status", "--porcelain"])
                .unwrap()
                .contains("A  unrelated")
        );
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
