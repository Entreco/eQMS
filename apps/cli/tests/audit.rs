use serde_json::Value;
use std::collections::BTreeMap;
use std::fs;
use std::path::PathBuf;
use std::process::{Command, Output, Stdio};
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT: AtomicUsize = AtomicUsize::new(0);

struct Fixture(PathBuf);

impl Fixture {
    fn new() -> Self {
        let path = std::env::temp_dir().join(format!(
            "eqms audit cli {} {}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&path).unwrap();
        for args in [
            &["init", "--quiet"][..],
            &["config", "user.name", "Audit CLI Test"],
            &["config", "user.email", "audit-cli@example.invalid"],
        ] {
            assert!(
                Command::new("git")
                    .arg("-C")
                    .arg(&path)
                    .args(args)
                    .output()
                    .unwrap()
                    .status
                    .success()
            );
        }
        eqms_core::workspace::Workspace::initialize(&path).unwrap();
        Self(path)
    }

    fn command(&self, args: &[&str]) -> Command {
        let mut command = Command::new(env!("CARGO_BIN_EXE_qms"));
        command
            .args(["audit", "verify-ledger"])
            .arg(&self.0)
            .args(args)
            .stdin(Stdio::null());
        command
    }

    fn invoke(&self, args: &[&str]) -> Output {
        self.command(args).output().unwrap()
    }

    fn git(&self, args: &[&str]) -> Vec<u8> {
        let output = Command::new("git")
            .arg("-C")
            .arg(&self.0)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        output.stdout
    }

    fn traced(&self) -> (Output, Vec<Value>) {
        let trace = self.0.join("audit-trace.jsonl");
        let output = self
            .command(&["--json"])
            .env("GIT_TRACE2_EVENT", &trace)
            .output()
            .unwrap();
        let events = fs::read_to_string(trace)
            .unwrap()
            .lines()
            .map(|line| serde_json::from_str(line).unwrap())
            .collect();
        (output, events)
    }
}

impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}

#[test]
fn human_json_checkpoint_and_draft_contracts_are_explicit() {
    let f = Fixture::new();
    let draft = eqms_core::document::Documents::open(&f.0)
        .unwrap()
        .create("Policy", "body")
        .unwrap();
    let result = f.invoke(&["--json"]);
    assert!(
        result.status.success(),
        "{}",
        String::from_utf8_lossy(&result.stderr)
    );
    assert!(result.stderr.is_empty());
    let value: Value = serde_json::from_slice(&result.stdout).unwrap();
    assert_eq!(value["schema_version"], 2);
    assert_eq!(value["command"], "audit.verify-ledger");
    assert_eq!(value["status"], "ok");
    assert!(value["findings"].is_array() && value["references"].is_array());
    assert!(value["timestamp_unix_ms"].is_i64());
    assert_eq!(value["error"], Value::Null);
    let data = &value["data"];
    assert_eq!(data["baseline_commits"], 1);
    assert_eq!(data["baseline_records"], 1);
    assert_eq!(data["drafts"][0]["id"], draft.id);
    assert_eq!(data["drafts"][0]["revision"], draft.revision);
    assert_eq!(data["drafts"][0]["saved_revisions"], 1);
    assert_eq!(data["trust"], "local_consistency");
    assert_eq!(data["checkpoint"], Value::Null);
    let checkpoint = data["baseline_revision"].as_str().unwrap();
    let checked = f.invoke(&["--checkpoint", checkpoint, "--json"]);
    assert!(checked.status.success());
    let checked: Value = serde_json::from_slice(&checked.stdout).unwrap();
    assert_eq!(checked["data"]["checkpoint"], checkpoint);
    assert_eq!(checked["data"]["trust"], "checkpoint_ancestry");
    for args in [vec![], vec!["--checkpoint", checkpoint]] {
        let plain = f.invoke(&args);
        assert!(plain.status.success() && plain.stderr.is_empty());
        let plain = String::from_utf8(plain.stdout).unwrap();
        assert!(plain.contains("1 isolated draft chains"));
        assert!(plain.contains("Does not prove identity or approval"));
        assert!(plain.contains("deleted draft refs"));
        if args.is_empty() {
            assert!(plain.contains("no independently trusted checkpoint"));
        } else {
            assert!(plain.contains(checkpoint));
        }
    }
}

#[test]
fn corruption_and_invalid_checkpoints_fail_with_no_success_stdout() {
    let f = Fixture::new();
    let baseline = String::from_utf8(
        Command::new("git")
            .arg("-C")
            .arg(&f.0)
            .args(["rev-parse", "HEAD"])
            .output()
            .unwrap()
            .stdout,
    )
    .unwrap();
    for checkpoint in ["HEAD", "--help", &"f".repeat(40)] {
        let argument = format!("--checkpoint={checkpoint}");
        let result = f.invoke(&[&argument, "--json"]);
        assert_eq!(result.status.code(), Some(1));
        assert!(result.stdout.is_empty());
        let value: Value = serde_json::from_slice(&result.stderr).unwrap();
        assert_eq!(value["command"], "audit.verify-ledger");
        assert_eq!(value["data"], Value::Null);
        assert_eq!(value["error"]["code"], "OPERATION_FAILED");
    }
    // Corrupt the committed baseline, not merely its working copy.
    fs::write(f.0.join(".eqms/journal.jsonl"), b"corrupt\n").unwrap();
    for args in [
        &["add", ".eqms/journal.jsonl"][..],
        &["commit", "--quiet", "-m", "Corrupt fixture"],
    ] {
        assert!(
            Command::new("git")
                .arg("-C")
                .arg(&f.0)
                .args(args)
                .output()
                .unwrap()
                .status
                .success()
        );
    }
    let broken = String::from_utf8(
        Command::new("git")
            .arg("-C")
            .arg(&f.0)
            .args(["rev-parse", "HEAD"])
            .output()
            .unwrap()
            .stdout,
    )
    .unwrap();
    assert_ne!(broken, baseline);
    for json in [false, true] {
        let result = f.invoke(if json { &["--json"] } else { &[] });
        assert_eq!(result.status.code(), Some(1));
        assert!(result.stdout.is_empty());
        let message = if json {
            let value: Value = serde_json::from_slice(&result.stderr).unwrap();
            assert_eq!(value["schema_version"], 2);
            assert_eq!(value["error"]["code"], "OPERATION_FAILED");
            value["error"]["message"].as_str().unwrap().to_owned()
        } else {
            String::from_utf8(result.stderr).unwrap()
        };
        assert!(
            message.contains(broken.trim()) && message.contains("line 0"),
            "{message}"
        );
    }
}

#[test]
fn usage_and_unsupported_history_overrides_fail_explicitly() {
    for args in [
        vec!["audit", "--json"],
        vec!["audit", "verify-ledger", "--json"],
        vec!["audit", "unknown", "--json"],
    ] {
        let output = Command::new(env!("CARGO_BIN_EXE_qms"))
            .args(args)
            .output()
            .unwrap();
        assert_eq!(output.status.code(), Some(2));
        assert!(output.stdout.is_empty());
        let value: Value = serde_json::from_slice(&output.stderr).unwrap();
        assert_eq!(value["error"]["code"], "INVALID_ARGUMENT");
    }
    let f = Fixture::new();
    for variable in ["GIT_SHALLOW_FILE", "GIT_GRAFT_FILE", "GIT_REPLACE_REF_BASE"] {
        let result = f
            .command(&["--json"])
            .env(variable, "unused-override")
            .output()
            .unwrap();
        assert_eq!(result.status.code(), Some(1));
        assert!(result.stdout.is_empty());
        let value: Value = serde_json::from_slice(&result.stderr).unwrap();
        assert!(
            value["error"]["message"]
                .as_str()
                .unwrap()
                .contains(variable)
        );
    }
}

#[test]
fn partial_clone_missing_historical_objects_fails_without_fetching() {
    let source = Fixture::new();
    source.git(&["config", "uploadpack.allowFilter", "true"]);
    let workspace = eqms_core::workspace::Workspace::open(&source.0).unwrap();
    for body in ["historical content", "current content"] {
        let document = eqms_core::markdown::Markdown::new(
            "DOC-00000000000000000000000000000000",
            "Policy",
            body,
        )
        .unwrap();
        let bytes = document.canonical_bytes().unwrap();
        fs::write(source.0.join("documents/policy.md"), &bytes).unwrap();
        workspace
            .record_event(
                "content.saved",
                BTreeMap::new(),
                BTreeMap::from([(
                    "documents/policy.md".to_owned(),
                    document.content_hash().unwrap(),
                )]),
            )
            .unwrap();
    }
    let clone_parent = Fixture::new();
    let destination = clone_parent.0.join("partial");
    let cloned = Command::new("git")
        .args(["clone", "--quiet", "--filter=blob:none", "--no-local"])
        .arg(format!("file://{}", source.0.display()))
        .arg(&destination)
        .output()
        .unwrap();
    assert!(
        cloned.status.success(),
        "{}",
        String::from_utf8_lossy(&cloned.stderr)
    );
    let clone = Fixture(destination);
    let historical = source.git(&["rev-parse", "HEAD~1:documents/policy.md"]);
    let missing = Command::new("git")
        .env("GIT_NO_LAZY_FETCH", "1")
        .arg("-C")
        .arg(&clone.0)
        .args([
            "cat-file",
            "-e",
            std::str::from_utf8(&historical).unwrap().trim(),
        ])
        .output()
        .unwrap();
    assert!(
        !missing.status.success(),
        "fixture must omit a historical object"
    );
    fn packs(path: &std::path::Path) -> BTreeMap<std::ffi::OsString, Vec<u8>> {
        fs::read_dir(path.join(".git/objects/pack"))
            .unwrap()
            .map(|entry| {
                let entry = entry.unwrap();
                (entry.file_name(), fs::read(entry.path()).unwrap())
            })
            .collect()
    }
    let before = packs(&clone.0);
    let (output, trace) = clone.traced();
    assert_eq!(output.status.code(), Some(1));
    assert!(output.stdout.is_empty());
    let error: Value = serde_json::from_slice(&output.stderr).unwrap();
    assert!(
        error["error"]["message"]
            .as_str()
            .unwrap()
            .contains("partial/promisor")
    );
    assert_eq!(packs(&clone.0), before);
    let starts: Vec<_> = trace
        .iter()
        .filter(|event| event["event"] == "cmd_name")
        .filter_map(|event| event["name"].as_str())
        .collect();
    assert!(
        !starts.contains(&"fetch") && !starts.contains(&"upload-pack"),
        "{starts:?}"
    );
    assert!(!starts.contains(&"fsck"), "reject before object traversal");
}

#[test]
fn growing_bindings_batch_subprocesses_and_reuse_identical_blobs() {
    let f = Fixture::new();
    let workspace = eqms_core::workspace::Workspace::open(&f.0).unwrap();
    let document = eqms_core::markdown::Markdown::new(
        "DOC-00000000000000000000000000000000",
        "Shared",
        "same bytes",
    )
    .unwrap();
    let bytes = document.canonical_bytes().unwrap();
    let hash = document.content_hash().unwrap();
    const PATHS: usize = 12;
    for index in 0..PATHS {
        let path = format!("documents/path-{index}.md");
        fs::write(f.0.join(&path), &bytes).unwrap();
        workspace
            .record_event(
                "content.saved",
                BTreeMap::new(),
                BTreeMap::from([(path, hash.clone())]),
            )
            .unwrap();
    }
    // An unchanged journal commit still checks tree paths and modes.
    f.git(&[
        "commit",
        "--quiet",
        "--allow-empty",
        "-m",
        "No journal change",
    ]);
    let (output, trace) = f.traced();
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    let commands: Vec<_> = trace
        .iter()
        .filter(|event| event["event"] == "start")
        .map(|event| event["argv"].as_array().unwrap())
        .collect();
    let count = |command: &str| {
        commands
            .iter()
            .filter(|args| args.iter().any(|arg| arg == command))
            .count()
    };
    assert_eq!(
        count("ls-tree"),
        PATHS + 2,
        "one tree read per baseline commit"
    );
    assert_eq!(
        count("--batch"),
        2,
        "one content-blob batch plus current-workspace batch"
    );
    let journal_reads = commands
        .iter()
        .filter(|args| {
            args.iter().any(|arg| arg == "cat-file") && args.iter().any(|arg| arg == "blob")
        })
        .count();
    assert_eq!(
        journal_reads,
        PATHS + 1,
        "unchanged journal object is not reread"
    );
    println!(
        "Growing-path fixture: {} Git processes, {} tree reads, {} blob batches, {journal_reads} journal reads",
        commands.len(),
        count("ls-tree"),
        count("--batch")
    );
    assert!(
        commands.len() <= 8 * PATHS + 40,
        "linear subprocess bound: {}",
        commands.len()
    );
}
