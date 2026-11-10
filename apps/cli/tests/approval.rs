use std::process::{Command, Output};

const MATRIX: &str = r#"{"schema_version":1,"artifacts":{"policy":["reviewer","approver"]},"release":["qa","release_authority"]}"#;

fn matrix_file(name: &str, body: &str) -> std::path::PathBuf {
    let path =
        std::env::temp_dir().join(format!("eqms-approval-{}-{name}.json", std::process::id()));
    std::fs::write(&path, body).unwrap();
    path
}

fn qms(args: &[&str]) -> Output {
    Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(args)
        .output()
        .unwrap()
}

fn text(bytes: &[u8]) -> String {
    String::from_utf8_lossy(bytes).into_owned()
}

#[test]
fn test_013_005_validate_matrix() {
    let good = matrix_file("good", MATRIX);
    let out = qms(&["approval", "validate-matrix", good.to_str().unwrap()]);
    assert!(out.status.success());
    assert!(text(&out.stdout).contains("valid"));
    let out = qms(&[
        "--json",
        "approval",
        "validate-matrix",
        good.to_str().unwrap(),
    ]);
    let json: serde_json::Value = serde_json::from_slice(&out.stdout).unwrap();
    assert_eq!(json["command"], "approval.validate-matrix");
    assert_eq!(json["data"]["valid"], true);

    let bad = matrix_file("bad", r#"{"schema_version":1,"artifacts":{},"release":[]}"#);
    let out = qms(&["approval", "validate-matrix", bad.to_str().unwrap()]);
    assert_eq!(out.status.code(), Some(1));
    assert!(text(&out.stderr).contains("at least one role"));
    let out = qms(&["approval", "validate-matrix", "/nonexistent/matrix.json"]);
    assert_eq!(out.status.code(), Some(1));
}

#[test]
fn test_013_005_and_007_check_permits_and_blocks() {
    let file = matrix_file("check", MATRIX);
    let file = file.to_str().unwrap();
    let out = qms(&[
        "approval",
        "check",
        file,
        "--artifact-type",
        "policy",
        "--role",
        "reviewer",
        "--role",
        "approver",
    ]);
    assert!(out.status.success());
    let stdout = text(&out.stdout);
    assert!(stdout.contains("permitted") && stdout.contains("does not authenticate identity"));

    let out = qms(&[
        "--json",
        "approval",
        "check",
        file,
        "--release",
        "--role",
        "qa",
        "--role",
        "release_authority",
    ]);
    let json: serde_json::Value = serde_json::from_slice(&out.stdout).unwrap();
    assert_eq!(json["data"]["permitted"], true);
    assert_eq!(json["data"]["subject"], "release");

    let out = qms(&[
        "approval",
        "check",
        file,
        "--artifact-type",
        "policy",
        "--role",
        "reviewer",
    ]);
    assert_eq!(out.status.code(), Some(1));
    let stderr = text(&out.stderr);
    assert!(stderr.contains("blocked") && stderr.contains("approver"));

    let out = qms(&["--json", "approval", "check", file, "--release"]);
    assert_eq!(out.status.code(), Some(1));
    let json: serde_json::Value = serde_json::from_str(&text(&out.stderr)).unwrap();
    assert_eq!(json["status"], "error");

    for args in [
        vec![
            "approval",
            "check",
            file,
            "--artifact-type",
            "policy",
            "--role",
            "owner",
        ],
        vec![
            "approval",
            "check",
            file,
            "--artifact-type",
            "sop",
            "--role",
            "approver",
        ],
    ] {
        assert_eq!(qms(&args).status.code(), Some(1));
    }
    let out = qms(&["approval", "check", file]);
    assert_eq!(out.status.code(), Some(2));
}
