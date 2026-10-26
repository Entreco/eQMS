use crate::document::Documents;
use crate::markdown::hash;
use crate::workspace::{
    WorkspaceError, error, git, git_text, parse_and_verify_journal, read_git_blobs,
};
use serde::Serialize;
use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

const JOURNAL: &str = ".eqms/journal.jsonl";

#[derive(Debug, Serialize)]
pub struct DraftVerification {
    pub id: String,
    pub revision: String,
    pub saved_revisions: usize,
}

#[derive(Debug, Serialize)]
pub struct LedgerVerification {
    pub baseline_revision: String,
    pub baseline_commits: usize,
    pub baseline_records: usize,
    pub drafts: Vec<DraftVerification>,
    pub checkpoint: Option<String>,
    pub trust: &'static str,
}

fn full_commit(value: &str) -> bool {
    matches!(value.len(), 40 | 64)
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

struct TreeEntry {
    regular: bool,
    object: String,
}

fn read_tree(root: &Path, commit: &str) -> Result<BTreeMap<Vec<u8>, TreeEntry>, WorkspaceError> {
    let output = git(
        root,
        &[
            "ls-tree",
            "-r",
            "-z",
            commit,
            "--",
            JOURNAL,
            "documents",
            "modules",
        ],
    )?;
    let mut tree = BTreeMap::new();
    for entry in output
        .stdout
        .split(|byte| *byte == 0)
        .filter(|entry| !entry.is_empty())
    {
        let tab = entry
            .iter()
            .position(|byte| *byte == b'\t')
            .ok_or_else(|| error("invalid Git tree entry"))?;
        let header =
            std::str::from_utf8(&entry[..tab]).map_err(|cause| error(cause.to_string()))?;
        let fields: Vec<_> = header.split_whitespace().collect();
        if fields.len() != 3 || !full_commit(fields[2]) {
            return Err(error("invalid Git tree object entry"));
        }
        tree.insert(
            entry[tab + 1..].to_vec(),
            TreeEntry {
                regular: fields[0] == "100644" && fields[1] == "blob",
                object: fields[2].to_owned(),
            },
        );
    }
    Ok(tree)
}

fn regular_object<'a>(
    tree: &'a BTreeMap<Vec<u8>, TreeEntry>,
    path: &str,
) -> Result<&'a str, WorkspaceError> {
    tree.get(path.as_bytes())
        .filter(|entry| entry.regular)
        .map(|entry| entry.object.as_str())
        .ok_or_else(|| error(format!("expected regular Git blob for {path}")))
}

fn reject_promisor(root: &Path) -> Result<(), WorkspaceError> {
    let config = git_text(root, &["config", "--null", "--list"])?;
    for setting in config.split('\0') {
        let key = setting
            .split('\n')
            .next()
            .unwrap_or_default()
            .to_ascii_lowercase();
        if key == "extensions.partialclone"
            || (key.starts_with("remote.") && key.ends_with(".promisor"))
        {
            return Err(error(
                "ledger verification does not support partial/promisor repositories",
            ));
        }
    }
    Ok(())
}

fn cache_hashes(
    root: &Path,
    tree: &BTreeMap<Vec<u8>, TreeEntry>,
    bindings: &BTreeMap<String, String>,
    hashes: &mut BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    let mut missing = BTreeSet::new();
    for path in bindings.keys() {
        let object = regular_object(tree, path)?;
        if !hashes.contains_key(object) {
            missing.insert(object);
        }
    }
    let missing: Vec<_> = missing.into_iter().collect();
    for (object, bytes) in read_git_blobs(root, None, &missing)? {
        hashes.insert(object, hash(&bytes));
    }
    Ok(())
}

fn verify_bindings(
    root: &Path,
    tree: &BTreeMap<Vec<u8>, TreeEntry>,
    bindings: &BTreeMap<String, String>,
    hashes: &mut BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    cache_hashes(root, tree, bindings, hashes)?;
    for (path, expected) in bindings {
        if hashes[regular_object(tree, path)?] != *expected {
            return Err(error(format!(
                "latest recorded content hash mismatch for {path}"
            )));
        }
    }
    Ok(())
}

fn supported_graph(root: &Path) -> Result<(), WorkspaceError> {
    if git_text(root, &["rev-parse", "--is-shallow-repository"])?.trim() != "false" {
        return Err(error(
            "ledger verification does not support shallow history",
        ));
    }
    if !git_text(
        root,
        &["for-each-ref", "--format=%(refname)", "refs/replace/"],
    )?
    .trim()
    .is_empty()
    {
        return Err(error(
            "ledger verification does not support replacement refs",
        ));
    }
    let grafts = git_text(root, &["rev-parse", "--git-path", "info/grafts"])?;
    let grafts = root.join(grafts.trim());
    match std::fs::symlink_metadata(&grafts) {
        Ok(_) => return Err(error("ledger verification does not support Git grafts")),
        Err(cause) if cause.kind() == std::io::ErrorKind::NotFound => {}
        Err(cause) => return Err(error(format!("cannot inspect Git grafts: {cause}"))),
    }
    let current = git_text(root, &["rev-parse", "--symbolic-full-name", "HEAD"])?;
    if current.trim().starts_with("refs/heads/eqms-drafts/") {
        return Err(error(
            "open the baseline, not an internal document draft branch",
        ));
    }
    Ok(())
}

pub fn verify_ledger(
    path: impl AsRef<Path>,
    checkpoint: Option<&str>,
) -> Result<LedgerVerification, WorkspaceError> {
    if checkpoint.is_some_and(|value| !full_commit(value)) {
        return Err(error("checkpoint must be a full lowercase Git commit ID"));
    }
    for variable in ["GIT_SHALLOW_FILE", "GIT_GRAFT_FILE", "GIT_REPLACE_REF_BASE"] {
        if std::env::var_os(variable).is_some() {
            return Err(error(format!(
                "ledger verification does not support {variable} overrides"
            )));
        }
    }
    // Ref enumeration itself can inspect objects; reject remote-backed stores first.
    reject_promisor(path.as_ref())?;
    Documents::with_sources(
        path.as_ref(),
        |workspace| {
            let root = workspace.path();
            reject_promisor(root)?;
            supported_graph(root)?;
            git(root, &["fsck", "--connectivity-only", "--no-dangling"])
                .map_err(|cause| error(format!("invalid local ref/object inventory: {cause}")))?;
            Ok(())
        },
        |documents, workspace, sources, baseline| {
            let root = workspace.path();
            let history = git_text(root, &["rev-list", "--reverse", "--parents", baseline])?;
            let mut ancestors = BTreeSet::new();
            let mut previous_commit: Option<&str> = None;
            let mut previous_journal = Vec::new();
            let mut previous_journal_object = String::new();
            let mut content_hashes = BTreeMap::<String, String>::new();
            let mut bindings = BTreeMap::new();
            let mut baseline_records = 0;
            for line in history.lines() {
                let commits: Vec<_> = line.split_whitespace().collect();
                let commit = commits[0];
                if commits.get(1).copied() != previous_commit || commits.len() > 2 {
                    return Err(error(format!(
                        "baseline commit {commit}: unsupported non-linear/merge history"
                    )));
                }
                let mut verify = || -> Result<(Vec<u8>, usize), WorkspaceError> {
                    let tree = read_tree(root, commit)?;
                    let journal_object = regular_object(&tree, JOURNAL)?;
                    if journal_object == previous_journal_object {
                        verify_bindings(root, &tree, &bindings, &mut content_hashes)?;
                        return Ok((previous_journal.clone(), baseline_records));
                    }
                    let bytes = git(root, &["cat-file", "blob", journal_object])?.stdout;
                    let records = parse_and_verify_journal(&bytes)?;
                    if !bytes.starts_with(&previous_journal) {
                        let matching_bytes = bytes
                            .iter()
                            .zip(&previous_journal)
                            .take_while(|(left, right)| left == right)
                            .count();
                        let line = bytes[..matching_bytes]
                            .iter()
                            .filter(|byte| **byte == b'\n')
                            .count();
                        return Err(error(format!(
                            "journal does not extend prior committed prefix at line {line} (after baseline commit {})",
                            previous_commit.unwrap_or("none")
                        )));
                    }
                    let mut needed = bindings.clone();
                    for record in records.iter().skip(baseline_records) {
                        needed.extend(record.content_hashes.clone());
                    }
                    cache_hashes(root, &tree, &needed, &mut content_hashes)?;
                    for record in records.iter().skip(baseline_records) {
                        for (path, expected) in &record.content_hashes {
                            let object = regular_object(&tree, path).map_err(|cause| {
                                error(format!("sequence {}: {cause}", record.sequence))
                            })?;
                            if content_hashes[object] != *expected {
                                return Err(error(format!(
                                    "sequence {}: content hash mismatch for {path}",
                                    record.sequence
                                )));
                            }
                        }
                        bindings.extend(record.content_hashes.clone());
                    }
                    verify_bindings(root, &tree, &bindings, &mut content_hashes)?;
                    previous_journal_object = journal_object.to_owned();
                    Ok((bytes, records.len()))
                };
                let (bytes, count) = verify()
                    .map_err(|cause| error(format!("baseline commit {commit}: {cause}")))?;
                previous_journal = bytes;
                baseline_records = count;
                ancestors.insert(commit.to_owned());
                previous_commit = Some(commit);
            }
            if checkpoint.is_some_and(|value| !ancestors.contains(value)) {
                return Err(error("trusted checkpoint is not in baseline ancestry"));
            }
            workspace.verify_unlocked().map_err(|cause| {
                error(format!("current workspace at baseline {baseline}: {cause}"))
            })?;
            let mut drafts = Vec::new();
            for (id, head) in sources {
                let revisions = documents
                    .history_at(id, head)
                    .map_err(|cause| error(format!("draft {id} at {head}: {cause}")))?;
                let creation = revisions
                    .last()
                    .ok_or_else(|| error("missing draft creation"))?;
                let base = git_text(root, &["rev-parse", &format!("{}^", creation.revision)])?;
                if !ancestors.contains(base.trim()) {
                    return Err(error(format!(
                        "draft {id} at {head}: creation baseline {} is not in verified baseline ancestry",
                        base.trim()
                    )));
                }
                drafts.push(DraftVerification {
                    id: id.clone(),
                    revision: head.clone(),
                    saved_revisions: revisions.len(),
                });
            }
            Ok(LedgerVerification {
                baseline_revision: baseline.to_owned(),
                baseline_commits: ancestors.len(),
                baseline_records,
                drafts,
                checkpoint: checkpoint.map(str::to_owned),
                trust: if checkpoint.is_some() {
                    "checkpoint_ancestry"
                } else {
                    "local_consistency"
                },
            })
        },
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::workspace::Workspace;
    use std::fs;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static NEXT: AtomicUsize = AtomicUsize::new(0);

    struct Fixture(PathBuf);

    impl Fixture {
        fn new() -> Self {
            let path = std::env::temp_dir().join(format!(
                "eqms-audit-{}-{}",
                std::process::id(),
                NEXT.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir(&path).unwrap();
            git(&path, &["init", "--quiet", "--initial-branch=main"]).unwrap();
            git(&path, &["config", "user.name", "Audit Test"]).unwrap();
            git(&path, &["config", "user.email", "audit@example.invalid"]).unwrap();
            Workspace::initialize(&path).unwrap();
            Self(path)
        }

        fn head(&self) -> String {
            git_text(&self.0, &["rev-parse", "HEAD"])
                .unwrap()
                .trim()
                .to_owned()
        }

        fn commit(&self) -> String {
            git(&self.0, &["add", "."]).unwrap();
            git(
                &self.0,
                &["commit", "--quiet", "--allow-empty", "-m", "Audit fixture"],
            )
            .unwrap();
            self.head()
        }

        fn event(&self, body: &str) {
            fs::write(self.0.join("documents/policy.md"), body).unwrap();
            Workspace::open(&self.0)
                .unwrap()
                .record_event(
                    "content.saved",
                    BTreeMap::new(),
                    BTreeMap::from([("documents/policy.md".to_owned(), hash(body.as_bytes()))]),
                )
                .unwrap();
        }

        fn audit(&self, checkpoint: Option<&str>) -> Result<LedgerVerification, WorkspaceError> {
            verify_ledger(&self.0, checkpoint)
        }
    }

    impl Drop for Fixture {
        fn drop(&mut self) {
            fs::remove_dir_all(&self.0).unwrap();
        }
    }

    #[test]
    fn verifies_all_recorded_baseline_and_independent_drafts_without_mutation() {
        let f = Fixture::new();
        let genesis = f.head();
        f.event("one");
        let intermediate = f.head();
        f.event("two");
        let documents = Documents::open(&f.0).unwrap();
        let first = documents.create("First", "original").unwrap();
        documents
            .save(&first.id, &first.revision, "First", "updated")
            .unwrap();
        documents.create("Second", "independent").unwrap();
        let refs = git_text(&f.0, &["show-ref"]).unwrap();
        let index = fs::read(f.0.join(".git/index")).unwrap();
        let journal = fs::read(f.0.join(JOURNAL)).unwrap();
        let report = f.audit(None).unwrap();
        assert_eq!(report.baseline_revision, f.head());
        assert_eq!(report.baseline_commits, 3);
        assert_eq!(report.baseline_records, 3);
        assert_eq!(report.trust, "local_consistency");
        assert!(report.checkpoint.is_none());
        assert_eq!(report.drafts.len(), 2);
        assert_eq!(
            report
                .drafts
                .iter()
                .map(|draft| draft.saved_revisions)
                .sum::<usize>(),
            3
        );
        for checkpoint in [genesis, intermediate, f.head()] {
            let verified = f.audit(Some(&checkpoint)).unwrap();
            assert_eq!(verified.trust, "checkpoint_ancestry");
            assert_eq!(verified.checkpoint.as_deref(), Some(checkpoint.as_str()));
        }
        assert_eq!(git_text(&f.0, &["show-ref"]).unwrap(), refs);
        assert_eq!(fs::read(f.0.join(".git/index")).unwrap(), index);
        assert_eq!(fs::read(f.0.join(JOURNAL)).unwrap(), journal);
        assert_eq!(
            fs::read_to_string(f.0.join("documents/policy.md")).unwrap(),
            "two"
        );
        assert!(!f.0.join(".eqms/index.sqlite3").exists());
    }

    #[test]
    fn rejects_changed_reordered_missing_duplicate_and_incomplete_records_with_context() {
        for mutation in 0..6 {
            let f = Fixture::new();
            f.event("one");
            f.event("two");
            let original = fs::read_to_string(f.0.join(JOURNAL)).unwrap();
            let lines: Vec<_> = original.lines().collect();
            let corrupted = match mutation {
                0 => original.replace("content.saved", "content.wrong"),
                1 => format!("{}\n{}\n{}\n", lines[0], lines[2], lines[1]),
                2 => format!("{}\n{}\n", lines[0], lines[2]),
                3 => format!("{}\n{}\n{}\n", lines[0], lines[1], lines[1]),
                4 => original.trim_end().to_owned(),
                _ => format!("{}\n", lines[0]),
            };
            fs::write(f.0.join(JOURNAL), &corrupted).unwrap();
            let broken_commit = f.commit();
            let failure = f.audit(None).unwrap_err().to_string();
            assert!(failure.contains(&broken_commit), "{failure}");
            assert!(
                failure.contains("line") || failure.contains("prior committed prefix"),
                "{failure}"
            );
            assert_eq!(fs::read_to_string(f.0.join(JOURNAL)).unwrap(), corrupted);
        }
    }

    #[test]
    fn detects_earliest_historical_content_damage_even_when_head_verifies() {
        for damage in ["changed", "missing", "executable"] {
            let f = Fixture::new();
            f.event("recorded");
            let path = f.0.join("documents/policy.md");
            match damage {
                "missing" => fs::remove_file(&path).unwrap(),
                "executable" => {
                    git(&f.0, &["update-index", "--chmod=+x", "documents/policy.md"]).unwrap();
                    git(&f.0, &["commit", "--quiet", "-m", "Wrong mode"]).unwrap();
                }
                _ => fs::write(&path, "unrecorded").unwrap(),
            }
            let broken = if damage == "executable" {
                f.head()
            } else {
                f.commit()
            };
            fs::write(&path, "recorded").unwrap();
            if damage == "executable" {
                git(&f.0, &["update-index", "--chmod=-x", "documents/policy.md"]).unwrap();
                git(&f.0, &["commit", "--quiet", "-m", "Restore mode"]).unwrap();
            } else {
                f.commit();
            }
            Workspace::open(&f.0).unwrap().verify().unwrap();
            let failure = f.audit(None).unwrap_err().to_string();
            assert!(failure.contains(&broken), "{failure}");
            assert!(failure.contains("documents/policy.md"), "{failure}");
        }
    }

    #[test]
    fn rejects_historical_chain_replacement_and_current_dirty_journal() {
        let f = Fixture::new();
        let original = fs::read_to_string(f.0.join(JOURNAL)).unwrap();
        // A correctly rehashed replacement is locally consistent but rewrites history.
        let mut records = parse_and_verify_journal(original.as_bytes()).unwrap();
        records[0].timestamp = "0.000000000".to_owned();
        let replacement = crate::workspace::with_hash(records.remove(0)).unwrap();
        let bytes = format!("{}\n", serde_json::to_string(&replacement).unwrap());
        fs::write(f.0.join(JOURNAL), bytes).unwrap();
        let broken = f.commit();
        assert!(f.audit(None).unwrap_err().to_string().contains(&broken));

        let clean = Fixture::new();
        fs::write(clean.0.join(JOURNAL), b"dirty").unwrap();
        let error = clean.audit(None).unwrap_err().to_string();
        assert!(error.contains("incomplete record"), "{error}");
    }

    #[test]
    fn first_invalid_record_is_reported_before_an_incomplete_tail() {
        let failure = parse_and_verify_journal(b"invalid\nincomplete")
            .unwrap_err()
            .to_string();
        assert!(
            failure.contains("invalid journal record at line 0"),
            "{failure}"
        );
        let f = Fixture::new();
        let mut journal = fs::read(f.0.join(JOURNAL)).unwrap();
        journal.extend_from_slice(b"incomplete");
        let failure = parse_and_verify_journal(&journal).unwrap_err().to_string();
        assert!(failure.contains("incomplete record at line 1"), "{failure}");
    }

    #[test]
    fn rejects_corrupt_drafts_and_drafts_from_unrelated_baseline() {
        let f = Fixture::new();
        let doc = Documents::open(&f.0)
            .unwrap()
            .create("Policy", "body")
            .unwrap();
        let reference = format!("refs/heads/eqms-drafts/{}", doc.id);
        git(&f.0, &["update-ref", &reference, &f.head()]).unwrap();
        let failure = f.audit(None).unwrap_err().to_string();
        assert!(
            failure.contains(&doc.id) && failure.contains(&f.head()),
            "{failure}"
        );

        let unrelated = Fixture::new();
        git(&unrelated.0, &["switch", "--quiet", "-c", "other"]).unwrap();
        unrelated.commit();
        let other = Documents::open(&unrelated.0)
            .unwrap()
            .create("Other", "body")
            .unwrap();
        git(&unrelated.0, &["switch", "--quiet", "main"]).unwrap();
        let failure = unrelated.audit(None).unwrap_err().to_string();
        assert!(
            failure.contains(&other.id) && failure.contains("creation baseline"),
            "{failure}"
        );
    }

    #[test]
    fn rejects_detectable_broken_refs_instead_of_omitting_drafts() {
        for kind in ["dangling", "cycle", "malformed", "missing-object"] {
            let f = Fixture::new();
            let reference = "refs/heads/eqms-drafts/DOC-00000000000000000000000000000000";
            match kind {
                "dangling" => {
                    git(&f.0, &["symbolic-ref", reference, "refs/heads/deleted"]).unwrap();
                }
                "cycle" => {
                    git(&f.0, &["symbolic-ref", reference, "refs/heads/cycle"]).unwrap();
                    git(&f.0, &["symbolic-ref", "refs/heads/cycle", reference]).unwrap();
                }
                _ => {
                    let path = f.0.join(".git").join(reference);
                    fs::create_dir_all(path.parent().unwrap()).unwrap();
                    fs::write(
                        &path,
                        if kind == "malformed" {
                            "broken\n".to_owned()
                        } else {
                            format!("{}\n", "f".repeat(40))
                        },
                    )
                    .unwrap();
                }
            }
            let failure = f.audit(None).unwrap_err().to_string();
            assert!(
                failure.contains("ref/object inventory"),
                "{kind}: {failure}"
            );
            assert!(failure.contains(reference), "{kind}: {failure}");
        }
    }

    #[test]
    fn validates_packed_draft_refs_with_local_objects() {
        let f = Fixture::new();
        let draft = Documents::open(&f.0)
            .unwrap()
            .create("Packed", "body")
            .unwrap();
        git(&f.0, &["pack-refs", "--all"]).unwrap();
        let report = f.audit(None).unwrap();
        assert_eq!(report.drafts[0].id, draft.id);
    }

    #[test]
    fn checkpoints_must_be_literal_known_baseline_commits() {
        let f = Fixture::new();
        let doc = Documents::open(&f.0)
            .unwrap()
            .create("Policy", "body")
            .unwrap();
        for checkpoint in [
            "HEAD",
            "HEAD~1",
            "--help",
            "abc",
            &"f".repeat(40),
            &doc.revision,
        ] {
            assert!(f.audit(Some(checkpoint)).is_err(), "{checkpoint}");
        }
    }

    #[test]
    fn rejects_shallow_replace_graft_merge_and_checked_out_draft_histories() {
        let f = Fixture::new();
        fs::write(f.0.join(".git/shallow"), format!("{}\n", f.head())).unwrap();
        assert!(f.audit(None).unwrap_err().to_string().contains("shallow"));
        fs::remove_file(f.0.join(".git/shallow")).unwrap();
        let genesis = f.head();
        f.commit();
        git(&f.0, &["replace", &f.head(), &genesis]).unwrap();
        assert!(
            f.audit(None)
                .unwrap_err()
                .to_string()
                .contains("replacement")
        );
        git(&f.0, &["replace", "-d", &f.head()]).unwrap();
        fs::write(f.0.join(".git/info/grafts"), "").unwrap();
        assert!(f.audit(None).unwrap_err().to_string().contains("grafts"));
        fs::remove_file(f.0.join(".git/info/grafts")).unwrap();
        let merge = git_text(
            &f.0,
            &[
                "commit-tree",
                "HEAD^{tree}",
                "-p",
                &f.head(),
                "-p",
                &genesis,
                "-m",
                "Merge",
            ],
        )
        .unwrap();
        git(&f.0, &["update-ref", "HEAD", merge.trim()]).unwrap();
        assert!(f.audit(None).unwrap_err().to_string().contains("merge"));

        let draft_workspace = Fixture::new();
        let draft = Documents::open(&draft_workspace.0)
            .unwrap()
            .create("Draft", "body")
            .unwrap();
        git(
            &draft_workspace.0,
            &["switch", "--quiet", &format!("eqms-drafts/{}", draft.id)],
        )
        .unwrap();
        assert!(
            draft_workspace
                .audit(None)
                .unwrap_err()
                .to_string()
                .contains("internal document draft")
        );
    }
}
