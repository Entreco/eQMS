use std::io::Write;
use std::process::{Command, Output, Stdio};

#[test]
fn search_cli_prepares_queries_pages_and_reports_stale_sources() {
    let root = std::env::temp_dir().join(format!("eqms-search-cli-{}", std::process::id()));
    std::fs::create_dir(&root).unwrap();
    let git = |args: &[&str]| {
        let result = Command::new("git")
            .arg("-C")
            .arg(&root)
            .args(args)
            .output()
            .unwrap();
        assert!(
            result.status.success(),
            "{}",
            String::from_utf8_lossy(&result.stderr)
        );
    };
    git(&["init", "--quiet"]);
    git(&["config", "user.name", "Andi"]);
    git(&["config", "user.email", "search@example.invalid"]);
    let qms = |args: &[&str]| {
        Command::new(env!("CARGO_BIN_EXE_qms"))
            .args(args)
            .arg(&root)
            .output()
            .unwrap()
    };
    let success = |output: Output| {
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        serde_json::from_slice::<serde_json::Value>(&output.stdout).unwrap()
    };
    success(qms(&["init", "--json"]));
    let missing = qms(&["search", "--query", "And", "--json"]);
    assert_eq!(missing.status.code(), Some(1));
    assert!(
        String::from_utf8(missing.stderr)
            .unwrap()
            .contains("--prepare")
    );
    for n in 0..7 {
        let mut child = Command::new(env!("CARGO_BIN_EXE_qms"))
            .args([
                "document",
                "create",
                "--json",
                "--title",
                &format!("Research And Development {n}"),
            ])
            .arg(&root)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .spawn()
            .unwrap();
        child
            .stdin
            .take()
            .unwrap()
            .write_all(b"This and That")
            .unwrap();
        success(child.wait_with_output().unwrap());
    }
    let prepared = success(qms(&["search", "--prepare", "--json"]));
    assert_eq!(prepared["schema_version"], 2);
    assert_eq!(prepared["command"], "search.prepare");
    assert_eq!(prepared["data"]["indexed_revisions"], 7);
    let first = success(qms(&["search", "--query", "And", "--json"]));
    assert_eq!(first["command"], "search");
    assert_eq!(first["data"]["historical"], false);
    for group in ["documents", "authors", "content"] {
        assert_eq!(first["data"][group]["hits"].as_array().unwrap().len(), 5);
        assert_eq!(first["data"][group]["next_offset"], 5);
    }
    let snapshot = first["data"]["snapshot"].as_str().unwrap();
    let second = success(qms(&[
        "search",
        "--query",
        "And",
        "--snapshot",
        snapshot,
        "--documents-offset",
        "5",
        "--json",
    ]));
    assert_eq!(
        second["data"]["documents"]["hits"]
            .as_array()
            .unwrap()
            .len(),
        2
    );
    assert!(second["data"]["documents"]["next_offset"].is_null());
    let unsafe_text = success(qms(&["search", "--query", "\"AND\" OR *", "--json"]));
    assert!(
        unsafe_text["data"]["documents"]["hits"]
            .as_array()
            .unwrap()
            .is_empty()
    );
    let human = qms(&["search", "--query", "And"]);
    assert!(human.status.success());
    let human = String::from_utf8(human.stdout).unwrap();
    assert!(
        human.contains("Documents:\n")
            && human.contains("Authors:\n")
            && human.contains("Content:\n")
    );
    let id = first["data"]["documents"]["hits"][0]["id"]
        .as_str()
        .unwrap();
    git(&["update-ref", "-d", &format!("refs/heads/eqms-drafts/{id}")]);
    let stale = qms(&["search", "--query", "And", "--snapshot", snapshot, "--json"]);
    assert_eq!(stale.status.code(), Some(1));
    let error: serde_json::Value = serde_json::from_slice(&stale.stderr).unwrap();
    assert_eq!(error["error"]["code"], "OPERATION_FAILED");
    success(qms(&["search", "--prepare", "--rebuild", "--json"]));
    assert_eq!(qms(&["search", "--json"]).status.code(), Some(2));
    std::fs::remove_dir_all(root).unwrap();
}
