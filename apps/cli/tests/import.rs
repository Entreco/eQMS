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
            "eqms import cli {} {}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&path).unwrap();
        for args in [
            &["init", "--quiet"][..],
            &["config", "user.name", "Import CLI Test"],
            &["config", "user.email", "import-cli@example.invalid"],
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

    fn invoke(&self, args: &[&str]) -> Output {
        Command::new(env!("CARGO_BIN_EXE_qms"))
            .args(args)
            .stdin(Stdio::null())
            .output()
            .unwrap()
    }

    fn source(&self, name: &str, text: &str) -> String {
        let path = std::env::temp_dir().join(format!(
            "eqms-import-source-{}-{}-{name}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::write(&path, text).unwrap();
        path.to_str().unwrap().to_owned()
    }
}

impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}

const NEEDS: &str =
    "---\ntitle: User Needs\n---\n- **UN-001**: need\n- **UN-002**: need (EQMS-016)\n";

#[test]
fn imports_idempotently_with_json_and_human_output() {
    let fixture = Fixture::new();
    let project = fixture.0.to_str().unwrap();
    let needs = fixture.source("needs.md", NEEDS);
    let plan = fixture.source("plan.md", "# Plan\r\nEQMS-016\r\n");

    let output = fixture.invoke(&["--json", "document", "import", project, &needs, &plan]);
    assert!(output.status.success(), "{output:?}");
    assert!(output.stderr.is_empty());
    let value: Value = serde_json::from_slice(&output.stdout).unwrap();
    assert_eq!(value["schema_version"], 2);
    assert_eq!(value["command"], "document.import");
    let imported = value["data"]["imported"].as_array().unwrap();
    assert_eq!(imported.len(), 2);
    assert_eq!(imported[0]["title"], "User Needs");
    assert_eq!(imported[0]["status"], "Draft");
    assert_eq!(
        imported[0]["identifiers"],
        serde_json::json!(["EQMS-016", "UN-001", "UN-002"])
    );
    assert_eq!(imported[0]["already_imported"], false);
    assert_eq!(imported[1]["line_endings_normalized"], true);
    assert!(
        imported[0]["source_hash"]
            .as_str()
            .unwrap()
            .starts_with("sha256:")
    );

    let id = imported[0]["id"].as_str().unwrap();
    let shown = fixture.invoke(&["--json", "document", "show", project, id]);
    let shown: Value = serde_json::from_slice(&shown.stdout).unwrap();
    assert_eq!(shown["data"]["body"], NEEDS);

    let human = fixture.invoke(&["document", "import", project, &needs, &plan]);
    assert!(human.status.success());
    let text = String::from_utf8(human.stdout).unwrap();
    assert!(text.contains("exists "), "{text}");
    assert!(
        text.contains("Imported 0 new, 2 already imported"),
        "{text}"
    );
    assert!(text.contains("not review or approval"));

    let list = fixture.invoke(&["--json", "document", "list", project]);
    let list: Value = serde_json::from_slice(&list.stdout).unwrap();
    assert_eq!(list["data"].as_array().unwrap().len(), 2);
    assert_eq!(fs::read_to_string(&needs).unwrap(), NEEDS);
}

#[test]
fn failures_use_existing_exit_conventions_and_create_nothing() {
    let fixture = Fixture::new();
    let project = fixture.0.to_str().unwrap();
    let good = fixture.source("good.md", "# Good\n");
    let missing = fixture.0.join("missing.md");

    let usage = fixture.invoke(&["--json", "document", "import", project]);
    assert_eq!(usage.status.code(), Some(2));
    let output = fixture.invoke(&[
        "--json",
        "document",
        "import",
        project,
        &good,
        missing.to_str().unwrap(),
    ]);
    assert_eq!(output.status.code(), Some(1));
    assert!(output.stdout.is_empty());
    let error: Value = serde_json::from_slice(&output.stderr).unwrap();
    assert_eq!(error["error"]["code"], "OPERATION_FAILED");

    let invalid = fixture.source("invalid.md", "ok");
    fs::write(&invalid, [0xff, 0xfe]).unwrap();
    let output = fixture.invoke(&["document", "import", project, &good, &invalid]);
    assert_eq!(output.status.code(), Some(1));
    assert!(output.stdout.is_empty());
    assert!(String::from_utf8_lossy(&output.stderr).contains("not valid UTF-8"));

    let not_workspace = std::env::temp_dir();
    let output = fixture.invoke(&["document", "import", not_workspace.to_str().unwrap(), &good]);
    assert_eq!(output.status.code(), Some(1));

    let list = fixture.invoke(&["--json", "document", "list", project]);
    let list: Value = serde_json::from_slice(&list.stdout).unwrap();
    assert!(list["data"].as_array().unwrap().is_empty());
}
