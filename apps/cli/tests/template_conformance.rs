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

fn envelope(args: &[&str], stdin: &str) -> Value {
    let output = qms(args, stdin);
    assert!(output.status.success(), "{args:?}");
    serde_json::from_slice(&output.stdout).unwrap()
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
fn conformance_findings_are_advisory_warnings_in_the_envelope() {
    let dir = std::env::temp_dir().join(format!("eqms-cli-conformance-{}", std::process::id()));
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
    install(path, &dir, "sop", 1, "SOP");

    // Unbound documents behave as before: no findings on save.
    let plain = envelope(&["document", "create", path, "--title", "Plain"], "x");
    assert_eq!(plain["findings"], json!([]));

    // A bound, non-conforming document still saves, with warnings.
    let created = envelope(
        &[
            "document",
            "create",
            path,
            "--title",
            "T",
            "--template",
            "sop",
        ],
        "## Scope\n## Purpose\n",
    );
    assert_eq!(created["status"], "ok");
    let codes: Vec<_> = created["findings"]
        .as_array()
        .unwrap()
        .iter()
        .map(|f| f["code"].as_str().unwrap())
        .collect();
    assert_eq!(
        codes,
        ["structure.section_order", "structure.section_missing"]
    );
    assert_eq!(created["findings"][0]["severity"], "warning");
    let id = created["data"]["id"].as_str().unwrap();
    let first = created["data"]["revision"].as_str().unwrap();

    let checked = envelope(&["document", "check", path, id], "");
    assert_eq!(checked["data"]["conforming"], false);
    assert_eq!(checked["findings"].as_array().unwrap().len(), 2);
    assert_eq!(checked["data"]["findings"], checked["findings"]);

    let saved = envelope(
        &[
            "document",
            "save",
            path,
            id,
            "--title",
            "T",
            "--expected",
            first,
        ],
        "## Purpose\n## Scope\n## Content\n",
    );
    assert_eq!(saved["findings"], json!([]));
    let latest = saved["data"]["revision"].as_str().unwrap();
    assert_eq!(
        envelope(&["document", "check", path, id], "")["data"]["conforming"],
        true
    );
    let old = envelope(&["document", "check", path, id, "--revision", first], "");
    assert_eq!(old["data"]["findings"].as_array().unwrap().len(), 2);
    assert!(fails(&["document", "check", path, id, "--revision", "deadbeef"]).contains("revision"));

    let cleared = envelope(
        &[
            "document",
            "set-template",
            path,
            id,
            "--expected",
            latest,
            "--clear",
        ],
        "",
    );
    assert_eq!(cleared["findings"], json!([]));
    let unbound = envelope(&["document", "check", path, id], "");
    assert_eq!(unbound["findings"][0]["code"], "metadata.template_unbound");

    // Text mode appends warnings as lines.
    let output = Command::new(env!("CARGO_BIN_EXE_qms"))
        .args(["document", "check", path, id])
        .output()
        .unwrap();
    assert!(output.status.success());
    assert!(
        String::from_utf8_lossy(&output.stdout).contains("\nwarning: metadata.template_unbound:")
    );
    drop(project);
}
