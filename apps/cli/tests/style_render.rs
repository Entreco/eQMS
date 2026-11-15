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

fn install(project: &str, dir: &Path, id: &str) {
    let mut template = ok(&["template", "show", project, "generic"], "")["template"].clone();
    template["id"] = json!(id);
    template["style"]["heading_numbering"] = json!(true);
    template["style"]["body_font"] = json!("serif");
    let file = dir.join(format!("{id}.json"));
    std::fs::write(&file, serde_json::to_vec_pretty(&template).unwrap()).unwrap();
    ok(
        &["template", "install", project, file.to_str().unwrap()],
        "",
    );
}

#[test]
fn render_applies_pinned_or_chosen_template_style_and_legacy_output_is_unchanged() {
    let dir = std::env::temp_dir().join(format!("eqms-cli-style-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir(&dir).unwrap();
    let _project = Project(dir.clone());
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
    install(path, &dir, "formal");

    let plain = ok(
        &["document", "create", path, "--title", "Plain"],
        "## A\n\n## B\n",
    );
    let plain_id = plain["id"].as_str().unwrap();
    let pinned = ok(
        &[
            "document",
            "create",
            path,
            "--title",
            "Pinned",
            "--template",
            "formal",
        ],
        "## A\n\n### B\n",
    );
    let pinned_id = pinned["id"].as_str().unwrap();

    // Legacy invocations carry no style.
    assert!(
        ok(&["document", "render", path, plain_id], "")
            .get("style")
            .is_none()
    );
    let legacy = ok(
        &["document", "render-buffer"],
        r#"{"title":"T","body":"x"}"#,
    );
    assert!(legacy.get("style").is_none());

    // Unpinned: generic default, no regions.
    let style = &ok(&["document", "render", path, plain_id, "--style"], "")["style"];
    assert_eq!(style["template"]["id"], "generic");
    assert_eq!(style["template"]["origin"], "default");
    assert!(style["regions"].is_null());

    // Pinned: its own style, numbering and regions.
    let style = &ok(&["document", "render", path, pinned_id, "--style"], "")["style"];
    assert_eq!(style["template"]["id"], "formal");
    assert_eq!(style["template"]["origin"], "pinned");
    assert_eq!(style["body_font"], "serif");
    assert_eq!(style["numbers"].as_object().unwrap().len(), 2);
    assert_eq!(
        style["regions"]["footer"]["right"]["segments"][0]["text"],
        "Page "
    );

    // Chosen preview overrides the pin (here: unpinned document previewed as formal).
    let chosen = ok(
        &[
            "document",
            "render",
            path,
            plain_id,
            "--template",
            "formal@1",
        ],
        "",
    );
    assert_eq!(chosen["style"]["template"]["origin"], "selected");
    assert!(
        fails(&["document", "render", path, plain_id, "--template", "nope"])
            .contains("unknown template")
    );
    assert!(
        fails(&[
            "document",
            "render",
            path,
            plain_id,
            "--template",
            "formal@0"
        ])
        .contains("version")
    );

    // Buffer render: with project, optional template and document context.
    let buffer = ok(
        &["document", "render-buffer", "--project", path],
        r##"{"title":"Dirty","body":"# H","template":{"id":"formal"},"document":{"id":"DOC-1","revision":"r","status":"Draft"}}"##,
    );
    assert_eq!(buffer["style"]["template"]["origin"], "selected");
    assert_eq!(
        buffer["style"]["regions"]["header"]["right"]["segments"][0]["text"],
        "DOC-1 rev r"
    );
    assert_eq!(buffer["source"]["kind"], "unsaved");
    let unsaved = ok(
        &["document", "render-buffer", "--project", path],
        r#"{"title":"Dirty","body":"x"}"#,
    );
    assert_eq!(unsaved["style"]["template"]["origin"], "default");
    assert!(
        fails_with_input(
            &["document", "render-buffer"],
            r#"{"title":"T","body":"x","template":{"id":"formal"}}"#
        )
        .contains("--project")
    );
}

fn fails_with_input(args: &[&str], stdin: &str) -> String {
    let output = qms(args, stdin);
    assert!(!output.status.success(), "{args:?} unexpectedly succeeded");
    String::from_utf8_lossy(&output.stdout).to_string() + &String::from_utf8_lossy(&output.stderr)
}
