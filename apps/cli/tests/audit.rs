use serde_json::Value;
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
