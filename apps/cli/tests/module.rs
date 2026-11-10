use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT: AtomicUsize = AtomicUsize::new(0);

fn workspace() -> PathBuf {
    let root = std::env::temp_dir().join(format!(
        "eqms-cli-module-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    std::fs::create_dir(&root).unwrap();
    for args in [
        &["init", "--quiet"][..],
        &["config", "user.name", "CLI Test"],
        &["config", "user.email", "cli@example.invalid"],
    ] {
        assert!(
            Command::new("git")
                .arg("-C")
                .arg(&root)
                .args(args)
                .status()
                .unwrap()
                .success()
        );
    }
    assert!(qms(&root, &["init"]).status.success());
    root
}

fn qms(root: &Path, args: &[&str]) -> Output {
    let mut command = Command::new(env!("CARGO_BIN_EXE_qms"));
    let (name, rest) = args.split_first().unwrap();
    command.arg(name);
    if *name == "module" || *name == "document" || *name == "audit" {
        command.arg(rest[0]);
        command.args(&rest[1..]);
    } else {
        command.args(rest);
    }
    command.arg(root).output().unwrap()
}

fn json(root: &Path, args: &[&str]) -> (Output, serde_json::Value) {
    let mut command = Command::new(env!("CARGO_BIN_EXE_qms"));
    command.arg("--json").args(args).arg(root);
    let output = command.output().unwrap();
    let text = if output.status.success() {
        &output.stdout
    } else {
        &output.stderr
    };
    let value = serde_json::from_slice(text).unwrap();
    (output, value)
}

#[test]
fn module_list_and_add_report_drafts_and_preserve_on_repeat() {
    let root = workspace();
    let (_, listed) = json(&root, &["module", "list"]);
    assert_eq!(listed["schema_version"], 2);
    assert_eq!(listed["command"], "module.list");
    assert_eq!(listed["data"]["modules"][0]["id"], "qms-starter");
    assert!(listed["data"]["modules"][0]["installed_version"].is_null());

    let mut add = Command::new(env!("CARGO_BIN_EXE_qms"));
    let output = add
        .args(["--json", "module", "add", "qms-starter"])
        .arg(&root)
        .output()
        .unwrap();
    assert!(output.status.success(), "{output:?}");
    let added: serde_json::Value = serde_json::from_slice(&output.stdout).unwrap();
    assert_eq!(added["status"], "ok");
    assert_eq!(added["command"], "module.add");
    assert_eq!(added["data"]["status"], "installed");
    assert_eq!(added["data"]["created"].as_array().unwrap().len(), 3);
    assert!(added["data"]["conflicts"].as_array().unwrap().is_empty());

    let (_, documents) = json(&root, &["document", "list"]);
    assert_eq!(documents["data"].as_array().unwrap().len(), 3);
    assert!(
        documents["data"]
            .as_array()
            .unwrap()
            .iter()
            .all(|document| document["status"] == "Draft")
    );
    assert!(qms(&root, &["verify"]).status.success());
    assert!(
        Command::new(env!("CARGO_BIN_EXE_qms"))
            .args(["audit", "verify-ledger"])
            .arg(&root)
            .output()
            .unwrap()
            .status
            .success()
    );

    let repeat = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["module", "add", "qms-starter"])
        .arg(&root)
        .output()
        .unwrap();
    let text = String::from_utf8(repeat.stdout).unwrap();
    assert!(repeat.status.success());
    assert!(text.contains("qms-starter 1.0.0: unchanged"), "{text}");
    assert!(text.contains("not an implemented process or compliance evidence"));
    let (_, listed) = json(&root, &["module", "list"]);
    assert_eq!(listed["data"]["modules"][0]["installed_version"], "1.0.0");
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn unknown_module_is_a_nonzero_error() {
    let root = workspace();
    let output = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["--json", "module", "add", "nope"])
        .arg(&root)
        .output()
        .unwrap();
    assert!(!output.status.success());
    let value: serde_json::Value = serde_json::from_slice(&output.stderr).unwrap();
    assert_eq!(value["status"], "error");
    assert_eq!(value["command"], "module.add");
    assert!(
        value["error"]["message"]
            .as_str()
            .unwrap()
            .contains("unknown module")
    );
    std::fs::remove_dir_all(root).unwrap();
}
