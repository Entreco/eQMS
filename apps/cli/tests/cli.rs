use std::process::{Command, Output};
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT: AtomicUsize = AtomicUsize::new(0);

fn qms_with_test_identity() -> Command {
    let mut command = Command::new(env!("CARGO_BIN_EXE_qms"));
    command
        .env("GIT_CONFIG_NOSYSTEM", "1")
        .env("GIT_CONFIG_COUNT", "2")
        .env("GIT_CONFIG_KEY_0", "user.name")
        .env("GIT_CONFIG_VALUE_0", "CLI Test")
        .env("GIT_CONFIG_KEY_1", "user.email")
        .env("GIT_CONFIG_VALUE_1", "cli-test@example.invalid");
    command
}

#[test]
fn document_commands_persist_drafts_and_report_conflicts() {
    use std::io::Write;
    use std::process::Stdio;
    let root = std::env::temp_dir().join(format!(
        "eqms-cli-documents-{}-{}",
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
    assert!(
        Command::new(env!("CARGO_BIN_EXE_qms"))
            .arg("init")
            .arg(&root)
            .output()
            .unwrap()
            .status
            .success()
    );
    let invoke = |args: &[&str], body: &str| {
        let mut child = Command::new(env!("CARGO_BIN_EXE_qms"))
            .args(["--json", "document", args[0]])
            .arg(&root)
            .args(&args[1..])
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .unwrap();
        child
            .stdin
            .take()
            .unwrap()
            .write_all(body.as_bytes())
            .unwrap();
        child.wait_with_output().unwrap()
    };
    let created = invoke(&["create", "--title", "Quality policy"], "First\n");
    assert!(
        created.status.success(),
        "{}",
        String::from_utf8_lossy(&created.stderr)
    );
    let json: serde_json::Value = serde_json::from_slice(&created.stdout).unwrap();
    assert_cli_envelope(&json, "ok");
    assert_eq!(json["command"], "document.create");
    let id = json["data"]["id"].as_str().unwrap();
    let first = json["data"]["revision"].as_str().unwrap();
    let saved = invoke(
        &["save", id, "--title", "Quality policy", "--expected", first],
        "Second\n",
    );
    assert!(saved.status.success());
    let saved: serde_json::Value = serde_json::from_slice(&saved.stdout).unwrap();
    let second = saved["data"]["revision"].as_str().unwrap();
    for (args, command) in [
        (vec!["list"], "document.list"),
        (vec!["show", id, "--revision", first], "document.show"),
        (vec!["history", id], "document.history"),
        (vec!["diff", id, first, second], "document.diff"),
    ] {
        let output = invoke(&args, "");
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        let result: serde_json::Value = serde_json::from_slice(&output.stdout).unwrap();
        assert_cli_envelope(&result, "ok");
        assert_eq!(result["command"], command);
        assert_eq!(result["status"], "ok");
        if command == "document.diff" {
            assert!(result["data"].as_str().unwrap().contains("+Second"));
        }
        if command == "document.history" {
            assert_eq!(result["data"].as_array().unwrap().len(), 2);
        }
    }
    let conflict = invoke(
        &["save", id, "--title", "Quality policy", "--expected", first],
        "Lost",
    );
    assert_eq!(conflict.status.code(), Some(1));
    assert!(conflict.stdout.is_empty());
    let error: serde_json::Value = serde_json::from_slice(&conflict.stderr).unwrap();
    assert_cli_envelope(&error, "error");
    assert_eq!(error["error"]["code"], "OPERATION_FAILED");
    let human = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["document", "show"])
        .arg(&root)
        .arg(id)
        .output()
        .unwrap();
    assert!(human.status.success());
    assert!(
        String::from_utf8(human.stdout)
            .unwrap()
            .contains("Quality policy")
    );
    std::fs::remove_dir_all(root).unwrap();
}

fn assert_cli_envelope(value: &serde_json::Value, status: &str) {
    assert_eq!(value["schema_version"], 2);
    assert_eq!(value["status"], status);
    assert!(
        value["command"].is_null() || value["command"].as_str().is_some(),
        "command must be a string or null"
    );
    assert!(value["timestamp_unix_ms"].as_i64().is_some());
    assert!(value["findings"].is_array());
    assert!(value["references"].is_array());
    assert!(value.get("data").is_some());
    assert!(value.get("error").is_some());
}

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
        assert_cli_envelope(&value, "ok");
        assert_eq!(value["command"], "version");
        assert_eq!(value["data"]["version"], eqms_core::version());
        assert_eq!(value["error"], serde_json::Value::Null);
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
        assert_cli_envelope(&value, "error");
        assert_eq!(value["schema_version"], 2);
        assert_eq!(value["status"], "error");
        assert_eq!(value["error"]["code"], "INVALID_ARGUMENT");
        assert_eq!(value["data"], serde_json::Value::Null);
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
    assert_cli_envelope(&init_json, "ok");
    assert_eq!(init_json["command"], "init");
    assert_eq!(
        init_json["data"]["path"],
        workspace.canonicalize().unwrap().display().to_string()
    );
    assert_eq!(
        init_json["data"]["created_resources"],
        serde_json::json!([
            ".git/",
            ".gitignore",
            ".eqms/",
            ".eqms/config.json",
            ".eqms/journal.jsonl",
            "documents/",
            "modules/",
            "modules/manifest.json"
        ])
    );
    assert!(workspace.join(".eqms/journal.jsonl").is_file());
    assert!(!workspace.join(".eqms/index.sqlite3").exists());

    let initial_verify = Command::new(env!("CARGO_BIN_EXE_qms"))
        .arg("verify")
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(initial_verify.status.code(), Some(0));

    let branch = Command::new("git")
        .arg("-C")
        .arg(&workspace)
        .args(["symbolic-ref", "--short", "HEAD"])
        .output()
        .unwrap();
    assert!(branch.status.success());
    let branch = String::from_utf8(branch.stdout).unwrap();
    let deleted_ref = Command::new("git")
        .arg("-C")
        .arg(&workspace)
        .args(["update-ref", "-d"])
        .arg(format!("refs/heads/{}", branch.trim()))
        .output()
        .unwrap();
    assert!(deleted_ref.status.success());
    let recover = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["--json", "recover"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(recover.status.code(), Some(0));
    let recover_json: serde_json::Value = serde_json::from_slice(&recover.stdout).unwrap();
    assert_cli_envelope(&recover_json, "ok");
    assert_eq!(recover_json["command"], "recover");
    assert_eq!(recover_json["status"], "ok");
    let recovered_verify = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["--json", "verify"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(recovered_verify.status.code(), Some(0));
    let verify_json: serde_json::Value = serde_json::from_slice(&recovered_verify.stdout).unwrap();
    assert_cli_envelope(&verify_json, "ok");
    assert_eq!(verify_json["command"], "verify");

    std::fs::write(workspace.join("documents/quality-policy.md"), "# Quality\n").unwrap();
    let rebuild = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["--json", "index", "rebuild"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(rebuild.status.code(), Some(0));
    let rebuild_json: serde_json::Value = serde_json::from_slice(&rebuild.stdout).unwrap();
    assert_cli_envelope(&rebuild_json, "ok");
    assert_eq!(rebuild_json["command"], "index.rebuild");
    assert_eq!(rebuild_json["data"]["indexed_documents"], 1);
    assert!(
        rebuild_json["data"]["index_path"]
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
    assert_cli_envelope(&error_json, "error");
    assert_eq!(error_json["command"], "index.rebuild");
    assert_eq!(error_json["error"]["code"], "OPERATION_FAILED");
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn init_reports_resources_and_preserves_existing_data_on_failure() {
    let root = std::env::temp_dir().join(format!(
        "eqms-cli-init-results-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    std::fs::create_dir(&root).unwrap();

    let workspace = root.join("workspace with spaces");
    let init = qms_with_test_identity()
        .args(["--json", "init"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(
        init.status.code(),
        Some(0),
        "{}",
        String::from_utf8_lossy(&init.stderr)
    );
    let init_json: serde_json::Value = serde_json::from_slice(&init.stdout).unwrap();
    assert_cli_envelope(&init_json, "ok");
    assert_eq!(init_json["command"], "init");
    assert_eq!(
        init_json["data"]["created_resources"],
        serde_json::json!(eqms_core::workspace::INITIALIZED_RESOURCES)
    );

    let head_before = Command::new("git")
        .arg("-C")
        .arg(&workspace)
        .args(["rev-parse", "HEAD"])
        .output()
        .unwrap();
    assert!(head_before.status.success());
    let repeat = qms_with_test_identity()
        .args(["--json", "init"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(repeat.status.code(), Some(1));
    let repeat_error: serde_json::Value = serde_json::from_slice(&repeat.stderr).unwrap();
    assert_cli_envelope(&repeat_error, "error");
    assert!(
        repeat_error["error"]["message"]
            .as_str()
            .unwrap()
            .contains("not empty")
    );
    let head_after = Command::new("git")
        .arg("-C")
        .arg(&workspace)
        .args(["rev-parse", "HEAD"])
        .output()
        .unwrap();
    assert!(head_after.status.success());
    assert_eq!(head_before.stdout, head_after.stdout);

    let nonempty = root.join("nonempty");
    std::fs::create_dir(&nonempty).unwrap();
    let marker = nonempty.join("keep.txt");
    std::fs::write(&marker, b"keep").unwrap();
    let existing = qms_with_test_identity()
        .args(["--json", "init"])
        .arg(&nonempty)
        .output()
        .unwrap();
    assert_eq!(existing.status.code(), Some(1));
    let existing_error: serde_json::Value = serde_json::from_slice(&existing.stderr).unwrap();
    assert_cli_envelope(&existing_error, "error");
    assert_eq!(std::fs::read(&marker).unwrap(), b"keep");
    assert!(!nonempty.join(".git").exists());

    let blocker = root.join("existing-file");
    std::fs::write(&blocker, b"do not overwrite").unwrap();
    let failed_path = blocker.join("workspace");
    let failed = qms_with_test_identity()
        .args(["--json", "init"])
        .arg(&failed_path)
        .output()
        .unwrap();
    assert_eq!(failed.status.code(), Some(1));
    let failed_error: serde_json::Value = serde_json::from_slice(&failed.stderr).unwrap();
    assert_cli_envelope(&failed_error, "error");
    assert!(
        failed_error["error"]["message"]
            .as_str()
            .unwrap()
            .contains("workspace path component is not a directory")
    );
    assert_eq!(std::fs::read(&blocker).unwrap(), b"do not overwrite");
    assert!(!failed_path.exists());

    let human_workspace = root.join("human workspace");
    let human = qms_with_test_identity()
        .arg("init")
        .arg(&human_workspace)
        .output()
        .unwrap();
    assert!(human.status.success());
    let human_output = String::from_utf8(human.stdout).unwrap();
    assert!(
        human_output.contains(
            &human_workspace
                .canonicalize()
                .unwrap()
                .display()
                .to_string()
        )
    );
    assert!(human_output.contains("Created resources:"));
    assert!(human_output.contains(".eqms/config.json"));

    std::fs::remove_dir_all(root).unwrap();
}

#[cfg(unix)]
#[test]
fn init_preserves_and_recovers_workspace_after_commit_failure() {
    use std::os::unix::fs::PermissionsExt;

    let root = std::env::temp_dir().join(format!(
        "eqms-cli-init-commit-failure-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    let workspace = root.join("workspace");
    let hooks = workspace.join(".git/hooks");
    std::fs::create_dir_all(&hooks).unwrap();
    let init_git = Command::new("git")
        .arg("-C")
        .arg(&workspace)
        .args(["init", "--quiet"])
        .output()
        .unwrap();
    assert!(init_git.status.success());

    let hook = hooks.join("pre-commit");
    std::fs::write(&hook, "#!/bin/sh\nexit 1\n").unwrap();
    let mut permissions = std::fs::metadata(&hook).unwrap().permissions();
    permissions.set_mode(0o755);
    std::fs::set_permissions(&hook, permissions).unwrap();

    let init = qms_with_test_identity()
        .args(["--json", "init"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(init.status.code(), Some(1));
    let init_error: serde_json::Value = serde_json::from_slice(&init.stderr).unwrap();
    assert_cli_envelope(&init_error, "error");
    assert!(
        init_error["error"]["message"]
            .as_str()
            .unwrap()
            .contains("commit outcome may be uncertain")
    );
    assert!(workspace.join(".eqms/config.json").is_file());
    assert!(workspace.join(".eqms/journal.jsonl").is_file());
    assert!(workspace.join(".git").is_dir());
    assert!(
        !Command::new("git")
            .arg("-C")
            .arg(&workspace)
            .args(["rev-parse", "--verify", "HEAD"])
            .output()
            .unwrap()
            .status
            .success()
    );

    std::fs::remove_file(hook).unwrap();
    let recover = qms_with_test_identity()
        .args(["--json", "recover"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(
        recover.status.code(),
        Some(0),
        "{}",
        String::from_utf8_lossy(&recover.stderr)
    );
    let recovered: serde_json::Value = serde_json::from_slice(&recover.stdout).unwrap();
    assert_cli_envelope(&recovered, "ok");
    let verify = qms_with_test_identity()
        .args(["--json", "verify"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(verify.status.code(), Some(0));
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn recovery_refuses_invalid_partial_initialization_without_a_commit() {
    let root = std::env::temp_dir().join(format!(
        "eqms-cli-partial-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    std::fs::create_dir_all(root.join(".eqms")).unwrap();
    std::fs::create_dir_all(root.join("modules")).unwrap();
    std::fs::write(root.join(".eqms/config.json"), b"{}").unwrap();
    std::fs::write(root.join("modules/manifest.json"), b"{}").unwrap();
    std::fs::write(root.join(".eqms/journal.jsonl"), b"{}\n").unwrap();
    let original_journal = std::fs::read(root.join(".eqms/journal.jsonl")).unwrap();

    let output = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["--json", "recover"])
        .arg(&root)
        .output()
        .unwrap();

    assert_eq!(output.status.code(), Some(1));
    let error: serde_json::Value = serde_json::from_slice(&output.stderr).unwrap();
    assert_eq!(error["error"]["code"], "OPERATION_FAILED");
    assert_eq!(
        std::fs::read(root.join(".eqms/journal.jsonl")).unwrap(),
        original_journal
    );
    let head = Command::new("git")
        .arg("-C")
        .arg(&root)
        .args(["rev-parse", "--verify", "HEAD"])
        .output()
        .unwrap();
    assert!(!head.status.success());
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn initialization_requires_explicit_git_identity_configuration() {
    let root = std::env::temp_dir().join(format!(
        "eqms-cli-no-identity-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    let config = root.join("empty-git-config");
    std::fs::create_dir_all(&root).unwrap();
    std::fs::write(&config, "").unwrap();

    let output = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["--json", "init"])
        .arg(root.join("workspace"))
        .env("GIT_CONFIG_GLOBAL", &config)
        .env("GIT_CONFIG_SYSTEM", &config)
        .env("GIT_CONFIG_NOSYSTEM", "1")
        .env_remove("GIT_AUTHOR_IDENT")
        .env_remove("GIT_COMMITTER_IDENT")
        .output()
        .unwrap();

    assert_eq!(output.status.code(), Some(1));
    let error: serde_json::Value = serde_json::from_slice(&output.stderr).unwrap();
    assert_eq!(error["error"]["code"], "OPERATION_FAILED");
    assert!(
        error["error"]["message"]
            .as_str()
            .unwrap()
            .contains("identity")
    );
    std::fs::remove_dir_all(root).unwrap();
}

#[test]
fn concurrent_cli_initialization_creates_exactly_one_workspace() {
    let root = std::env::temp_dir().join(format!(
        "eqms-cli-concurrent-init-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    std::fs::create_dir_all(&root).unwrap();
    let workspace = root.join("workspace");
    let barrier = std::sync::Arc::new(std::sync::Barrier::new(2));
    let first_workspace = workspace.clone();
    let first_barrier = barrier.clone();
    let first = std::thread::spawn(move || {
        first_barrier.wait();
        Command::new(env!("CARGO_BIN_EXE_qms"))
            .args(["--json", "init"])
            .arg(first_workspace)
            .env("GIT_CONFIG_COUNT", "2")
            .env("GIT_CONFIG_KEY_0", "user.name")
            .env("GIT_CONFIG_VALUE_0", "CLI Test")
            .env("GIT_CONFIG_KEY_1", "user.email")
            .env("GIT_CONFIG_VALUE_1", "cli-test@example.invalid")
            .output()
            .unwrap()
    });
    let second_workspace = workspace.clone();
    let second = std::thread::spawn(move || {
        barrier.wait();
        Command::new(env!("CARGO_BIN_EXE_qms"))
            .args(["--json", "init"])
            .arg(second_workspace)
            .env("GIT_CONFIG_COUNT", "2")
            .env("GIT_CONFIG_KEY_0", "user.name")
            .env("GIT_CONFIG_VALUE_0", "CLI Test")
            .env("GIT_CONFIG_KEY_1", "user.email")
            .env("GIT_CONFIG_VALUE_1", "cli-test@example.invalid")
            .output()
            .unwrap()
    });
    let outputs = [first.join().unwrap(), second.join().unwrap()];

    let successful_initializations = outputs
        .iter()
        .filter(|output| output.status.success())
        .count();
    assert_eq!(
        successful_initializations,
        1,
        "init outputs: {}",
        outputs
            .iter()
            .map(|output| format!(
                "status: {}\nstdout: {}\nstderr: {}",
                output.status,
                String::from_utf8_lossy(&output.stdout),
                String::from_utf8_lossy(&output.stderr)
            ))
            .collect::<Vec<_>>()
            .join("\n")
    );
    let verify = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["--json", "verify"])
        .arg(&workspace)
        .output()
        .unwrap();
    assert_eq!(verify.status.code(), Some(0));
    std::fs::remove_dir_all(root).unwrap();
}
