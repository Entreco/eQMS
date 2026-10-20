use std::process::{Command, Output};
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT: AtomicUsize = AtomicUsize::new(0);

fn run(args: &[&str]) -> Output {
    let dir = std::env::temp_dir().join(format!(
        "eqms-cli-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    std::fs::create_dir(&dir).unwrap();
    let output = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(args)
        .current_dir(&dir)
        .output()
        .unwrap();
    assert_eq!(std::fs::read_dir(&dir).unwrap().count(), 0);
    std::fs::remove_dir(&dir).unwrap();
    output
}

#[test]
fn help_and_empty_invocation() {
    for args in [&[][..], &["--help"], &["-h"], &["--json", "--help"]] {
        let output = run(args);
        assert_eq!(output.status.code(), Some(0));
        assert!(output.stderr.is_empty());
        assert!(String::from_utf8(output.stdout).unwrap().contains("Usage:"));
    }
}

#[test]
fn plain_version() {
    for args in [&["--version"][..], &["-V"]] {
        let output = run(args);
        assert_eq!(output.status.code(), Some(0));
        assert!(output.stderr.is_empty());
        assert_eq!(
            String::from_utf8(output.stdout).unwrap().trim(),
            format!("qms {}", eqms_core::version())
        );
    }
}

#[test]
fn structured_version() {
    for args in [&["--json", "--version"][..], &["--version", "--json"]] {
        let output = run(args);
        assert_eq!(output.status.code(), Some(0));
        assert!(output.stderr.is_empty());
        let value: serde_json::Value = serde_json::from_slice(&output.stdout).unwrap();
        assert_eq!(
            value,
            serde_json::json!({
                "schema_version": 1, "status": "ok", "version": eqms_core::version()
            })
        );
    }
}

#[test]
fn plain_usage_errors() {
    for args in [&["init"][..], &["--unknown"], &["--version", "extra"]] {
        let output = run(args);
        assert_eq!(output.status.code(), Some(2));
        assert!(output.stdout.is_empty());
        assert!(!output.stderr.is_empty());
    }
}

#[test]
fn structured_errors_regardless_of_flag_order() {
    for args in [
        &["--json"][..],
        &["--json", "init"],
        &["init", "--json"],
        &["--unknown", "--json"],
        &["--json", "--unknown"],
        &["--version", "extra", "--json"],
    ] {
        let output = run(args);
        assert_eq!(output.status.code(), Some(2));
        assert!(output.stdout.is_empty());
        let value: serde_json::Value = serde_json::from_slice(&output.stderr).unwrap();
        assert_eq!(value["schema_version"], 1);
        assert_eq!(value["status"], "error");
        assert_eq!(value["error"]["code"], "INVALID_ARGUMENT");
        assert!(!value["error"]["message"].as_str().unwrap().is_empty());
    }
}

#[test]
fn initializes_and_rebuilds_a_workspace_index() {
    let root = std::env::temp_dir().join(format!(
        "eqms-cli-workspace-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    let workspace = root.join("workspace with spaces");
    std::fs::create_dir_all(&workspace).unwrap();
    for args in [
        &["init", "--quiet"][..],
        &["config", "user.name", "CLI Test"][..],
        &["config", "user.email", "cli-test@example.invalid"][..],
    ] {
        let output = Command::new("git")
            .arg("-C")
            .arg(&workspace)
            .args(args)
            .output()
            .unwrap();
        assert!(output.status.success());
    }

    let init = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["--json", "init"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(init.status.code(), Some(0));
    let init_json: serde_json::Value = serde_json::from_slice(&init.stdout).unwrap();
    assert_eq!(init_json["command"], "init");
    assert!(workspace.join(".eqms/journal.jsonl").is_file());

    std::fs::write(workspace.join("documents/quality-policy.md"), "# Quality\n").unwrap();
    let rebuild = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["--json", "index", "rebuild"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(rebuild.status.code(), Some(0));
    let rebuild_json: serde_json::Value = serde_json::from_slice(&rebuild.stdout).unwrap();
    assert_eq!(rebuild_json["command"], "index.rebuild");
    assert_eq!(rebuild_json["indexed_documents"], 1);
    assert!(
        rebuild_json["index_path"]
            .as_str()
            .unwrap()
            .ends_with(".eqms/index.sqlite3")
    );
    assert!(workspace.join(".eqms/index.sqlite3").is_file());

    let verify = Command::new(env!("CARGO_BIN_EXE_qms"))
        .arg("verify")
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(verify.status.code(), Some(0));

    let invalid_index = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["index", "rebuild", "--json"])
        .arg(root.join("missing"))
        .output()
        .unwrap();
    assert_eq!(invalid_index.status.code(), Some(1));
    let error_json: serde_json::Value = serde_json::from_slice(&invalid_index.stderr).unwrap();
    assert_eq!(error_json["error"]["code"], "OPERATION_FAILED");
    std::fs::remove_dir_all(root).unwrap();
}
