use serde_json::{Value, json};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Output, Stdio};

struct Project(PathBuf);

impl Drop for Project {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

fn qms(args: &[&str], stdin: &str) -> Output {
    let mut child = Command::new(env!("CARGO_BIN_EXE_qms"))
        .env("GIT_CONFIG_NOSYSTEM", "1")
        .env("GIT_CONFIG_COUNT", "2")
        .env("GIT_CONFIG_KEY_0", "user.name")
        .env("GIT_CONFIG_VALUE_0", "CLI Test")
        .env("GIT_CONFIG_KEY_1", "user.email")
        .env("GIT_CONFIG_VALUE_1", "cli-test@example.invalid")
        .arg("--json")
        .args(args)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .unwrap();
    child
        .stdin
        .take()
        .unwrap()
        .write_all(stdin.as_bytes())
        .unwrap();
    child.wait_with_output().unwrap()
}

fn ok(args: &[&str], stdin: &str) -> Value {
    let output = qms(args, stdin);
    assert!(
        output.status.success(),
        "{args:?}: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    serde_json::from_slice::<Value>(&output.stdout).unwrap()["data"].clone()
}

fn fails(args: &[&str]) -> String {
    let output = qms(args, "");
    assert!(!output.status.success(), "{args:?} unexpectedly succeeded");
    String::from_utf8_lossy(&output.stdout).to_string() + &String::from_utf8_lossy(&output.stderr)
}

fn install(project: &str, dir: &Path, id: &str, version: u32, document_type: &str) -> Value {
    let mut template = ok(&["template", "show", project, "generic"], "")["template"].clone();
    template["id"] = json!(id);
    template["version"] = json!(version);
    template["document_type"] = json!(document_type);
    template["page"] = json!({"size": "Letter", "orientation": "landscape", "margins_tenths_mm": [200, 210, 220, 230]});
    let file = dir.join(format!("{id}-{version}.json"));
    std::fs::write(&file, serde_json::to_vec_pretty(&template).unwrap()).unwrap();
    ok(
        &["template", "install", project, file.to_str().unwrap()],
        "",
    )
}

#[test]
fn documents_bind_to_templates_and_template_changes_are_explicit() {
    let dir = std::env::temp_dir().join(format!("eqms-cli-binding-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir(&dir).unwrap();
    let project = Project(dir.clone());
    for args in [
        &["init", "--quiet"][..],
        &["config", "user.name", "CLI Test"],
        &["config", "user.email", "cli@example.invalid"],
    ] {
        assert!(
            Command::new("git")
                .arg("-C")
                .arg(&dir)
                .args(args)
                .status()
                .unwrap()
                .success()
        );
    }
    let path = dir.to_str().unwrap();
    ok(&["init", path], "");
    let v1 = install(path, &dir, "sop", 1, "SOP");
    let v1_hash = v1["content_hash"].as_str().unwrap().to_owned();
    install(path, &dir, "work-instruction", 1, "SOP");

    // Unbound documents carry no template field.
    let plain = ok(&["document", "create", path, "--title", "Plain"], "x");
    assert!(plain.get("template").is_none());

    // Ambiguous and unknown selections fail without writing.
    assert!(
        fails(&["document", "create", path, "--title", "T", "--type", "SOP"])
            .contains("--template")
    );
    assert!(
        fails(&["document", "create", path, "--title", "T", "--type", "Nope"])
            .contains("no installed template")
    );
    assert!(
        fails(&[
            "document",
            "create",
            path,
            "--title",
            "T",
            "--template",
            "sop",
            "--template-version",
            "9"
        ])
        .contains("unknown template")
    );
    assert!(
        fails(&[
            "document",
            "create",
            path,
            "--title",
            "T",
            "--type",
            "SOP",
            "--template",
            "sop"
        ])
        .contains("cannot be used")
    );

    let created = ok(
        &[
            "document",
            "create",
            path,
            "--title",
            "Cleaning",
            "--template",
            "sop",
        ],
        "",
    );
    assert_eq!(
        created["template"],
        json!({"id": "sop", "version": 1, "hash": v1_hash})
    );
    assert_eq!(created["page"]["size"], "Letter");
    assert_eq!(created["page"]["margin_left_mm"], 23.0);
    assert!(created["body"].as_str().unwrap().starts_with("## Purpose"));

    // Explicit page options override template defaults; a supplied body wins over the scaffold.
    let overridden = ok(
        &[
            "document",
            "create",
            path,
            "--title",
            "Own",
            "--template",
            "sop",
            "--page-size",
            "A4",
            "--margin-left",
            "30",
        ],
        "mine",
    );
    assert_eq!(overridden["page"]["size"], "A4");
    assert_eq!(overridden["page"]["margin_left_mm"], 30.0);
    assert_eq!(overridden["page"]["orientation"], "landscape");
    assert_eq!(overridden["body"], "mine");

    // Generic type resolves uniquely.
    let generic = ok(
        &[
            "document", "create", path, "--title", "G", "--type", "Document",
        ],
        "",
    );
    assert_eq!(generic["template"]["id"], "generic");

    // Plain save keeps the pin; set-template is explicit and journaled.
    let id = created["id"].as_str().unwrap();
    let saved = ok(
        &[
            "document",
            "save",
            path,
            id,
            "--title",
            "Cleaning",
            "--expected",
            created["revision"].as_str().unwrap(),
        ],
        "edited",
    );
    assert_eq!(saved["template"]["id"], "sop");
    install(path, &dir, "sop", 2, "SOP");
    let moved = ok(
        &[
            "document",
            "set-template",
            path,
            id,
            "--expected",
            saved["revision"].as_str().unwrap(),
            "--template",
            "sop",
        ],
        "",
    );
    assert_eq!(moved["template"]["version"], 2);
    assert_eq!(moved["body"], "edited");
    assert_eq!(moved["page"], saved["page"]);
    let cleared = ok(
        &[
            "document",
            "set-template",
            path,
            id,
            "--expected",
            moved["revision"].as_str().unwrap(),
            "--clear",
        ],
        "",
    );
    assert!(cleared.get("template").is_none());
    assert!(
        fails(&[
            "document",
            "set-template",
            path,
            id,
            "--expected",
            moved["revision"].as_str().unwrap(),
            "--clear"
        ])
        .contains("changed since")
    );
    assert!(
        fails(&[
            "document",
            "set-template",
            path,
            id,
            "--expected",
            cleared["revision"].as_str().unwrap()
        ])
        .contains("--template")
    );

    let old = ok(
        &[
            "document",
            "show",
            path,
            id,
            "--revision",
            created["revision"].as_str().unwrap(),
        ],
        "",
    );
    assert_eq!(old["template"]["version"], 1);
    let history = ok(&["document", "history", path, id], "");
    let pins: Vec<_> = history
        .as_array()
        .unwrap()
        .iter()
        .map(|d| d.get("template").map(|t| t["version"].clone()))
        .collect();
    assert_eq!(pins, [None, Some(json!(2)), Some(json!(1)), Some(json!(1))]);
    drop(project);
}
