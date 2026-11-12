use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Output, Stdio};

struct Scratch(PathBuf);

impl Scratch {
    fn new(name: &str) -> Self {
        let path = std::env::temp_dir().join(format!("eqms-pdf-{}-{name}", std::process::id()));
        let _ = std::fs::remove_dir_all(&path);
        std::fs::create_dir_all(&path).unwrap();
        Self(path)
    }
    fn path(&self, name: &str) -> String {
        self.0.join(name).to_str().unwrap().to_owned()
    }
}

impl Drop for Scratch {
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

fn project(scratch: &Scratch) -> String {
    let project = scratch.path("project");
    ok(&["init", &project], "");
    project
}

fn create(project: &str, title: &str, body: &str) -> Value {
    ok(&["document", "create", project, "--title", title], body)
}

fn snapshot(project: &str) -> Vec<u8> {
    let out = Command::new("git")
        .args(["-C", project, "rev-parse", "HEAD"])
        .output()
        .unwrap();
    let mut state = out.stdout;
    state.extend(std::fs::read(Path::new(project).join(".eqms/journal.jsonl")).unwrap());
    state
}

fn sha(bytes: &[u8]) -> String {
    format!("sha256:{:x}", Sha256::digest(bytes))
}

#[test]
fn t082_01_04_export_is_exact_deterministic_traceable_and_read_only() {
    let scratch = Scratch::new("single");
    let project = project(&scratch);
    let doc = create(&project, "Quality Policy", "# Purpose\n\nControlled text.");
    let (id, revision, hash) = (
        doc["id"].as_str().unwrap(),
        doc["revision"].as_str().unwrap(),
        doc["content_hash"].as_str().unwrap(),
    );
    let before = snapshot(&project);

    let first = scratch.path("first.pdf");
    let result = ok(
        &["document", "export-pdf", &project, id, "--file", &first],
        "",
    );
    assert_eq!(result["revision"], revision);
    assert_eq!(result["content_hash"], hash);
    let bytes = std::fs::read(&first).unwrap();
    assert_eq!(result["pdf_sha256"], sha(&bytes));
    assert_eq!(result["bytes"], bytes.len());
    let text = String::from_utf8_lossy(&bytes);
    assert!(text.contains(&format!("/EQMS-ContentHash ({hash})")));
    assert!(text.contains(&format!("/EQMS-Revision ({revision})")));

    let second = scratch.path("second.pdf");
    ok(
        &[
            "document",
            "export-pdf",
            &project,
            id,
            "--revision",
            revision,
            "--file",
            &second,
        ],
        "",
    );
    assert_eq!(bytes, std::fs::read(&second).unwrap(), "deterministic");
    assert_eq!(before, snapshot(&project), "canonical state unchanged");

    let saved = ok(
        &[
            "document",
            "save",
            &project,
            id,
            "--expected",
            revision,
            "--title",
            "Quality Policy",
        ],
        "# Purpose\n\nChanged text.",
    );
    let third = scratch.path("third.pdf");
    ok(
        &["document", "export-pdf", &project, id, "--file", &third],
        "",
    );
    assert_ne!(bytes, std::fs::read(&third).unwrap());
    let old = scratch.path("old.pdf");
    ok(
        &[
            "document",
            "export-pdf",
            &project,
            id,
            "--revision",
            revision,
            "--file",
            &old,
        ],
        "",
    );
    assert_eq!(
        bytes,
        std::fs::read(&old).unwrap(),
        "historical revision is exact"
    );
    assert_ne!(saved["revision"], doc["revision"]);

    assert!(
        fails(&[
            "document",
            "export-pdf",
            &project,
            id,
            "--revision",
            "deadbeef",
            "--file",
            &scratch.path("n.pdf")
        ])
        .contains("not a saved revision")
    );
}

#[test]
fn t082_03_lifecycle_events_appear_in_summary() {
    let scratch = Scratch::new("lifecycle");
    let project = project(&scratch);
    let doc = create(&project, "SOP", "Body");
    let id = doc["id"].as_str().unwrap();
    let moved = ok(
        &[
            "document",
            "transition",
            &project,
            id,
            "--expected",
            doc["revision"].as_str().unwrap(),
            "--to",
            "In Review",
        ],
        "",
    );
    let file = scratch.path("review.pdf");
    ok(
        &["document", "export-pdf", &project, id, "--file", &file],
        "",
    );
    let bytes = std::fs::read(&file).unwrap();
    let text = String::from_utf8_lossy(&bytes);
    assert!(text.contains("Draft -> In Review"), "{moved}");
    assert!(text.contains("No cryptographic signature is recorded"));
    assert!(text.contains("In Review"));
}

#[test]
fn t082_07_output_paths_are_safe() {
    let scratch = Scratch::new("paths");
    let project = project(&scratch);
    let doc = create(&project, "SOP", "Body");
    let id = doc["id"].as_str().unwrap();
    let inside = format!("{project}/export.pdf");
    assert!(
        fails(&["document", "export-pdf", &project, id, "--file", &inside]).contains("outside")
    );
    let dotdot = format!("{project}/documents/../x.pdf");
    assert!(
        fails(&["document", "export-pdf", &project, id, "--file", &dotdot]).contains("outside")
    );
    assert!(
        fails(&[
            "document",
            "export-pdf",
            &project,
            id,
            "--file",
            &scratch.path("x.txt")
        ])
        .contains(".pdf")
    );
    let file = scratch.path("x.pdf");
    ok(
        &["document", "export-pdf", &project, id, "--file", &file],
        "",
    );
    let original = std::fs::read(&file).unwrap();
    assert!(
        fails(&["document", "export-pdf", &project, id, "--file", &file]).contains("cannot create")
    );
    assert_eq!(original, std::fs::read(&file).unwrap());
    assert!(!Path::new(&format!("{project}/export.pdf")).exists());
}

fn manifest(scratch: &Scratch, value: Value) -> String {
    let path = scratch.path("manifest.json");
    std::fs::write(&path, serde_json::to_vec(&value).unwrap()).unwrap();
    path
}

#[test]
fn t082_05_batch_export_for_release_manifest() {
    let scratch = Scratch::new("batch");
    let project = project(&scratch);
    let a = create(&project, "Policy A", "# A");
    let b = create(&project, "Policy B", "# B");
    let entry = |d: &Value| json!({"id": d["id"], "revision": d["revision"]});
    let path = manifest(
        &scratch,
        json!({"schema_version":1,"release":"R-1","documents":[entry(&a), entry(&b)]}),
    );
    let out = scratch.path("out");
    let before = snapshot(&project);
    let index = ok(
        &[
            "document",
            "export-pdf-batch",
            &project,
            &path,
            "--output-dir",
            &out,
        ],
        "",
    );
    assert_eq!(before, snapshot(&project));
    assert_eq!(index["release"], "R-1");
    assert_eq!(
        index["manifest_sha256"],
        sha(&std::fs::read(&path).unwrap())
    );
    let documents = index["documents"].as_array().unwrap();
    assert_eq!(documents.len(), 2);
    for (entry, source) in documents.iter().zip([&a, &b]) {
        assert_eq!(entry["content_hash"], source["content_hash"]);
        let pdf = std::fs::read(Path::new(&out).join(entry["file"].as_str().unwrap())).unwrap();
        assert_eq!(entry["pdf_sha256"], sha(&pdf));
    }
    let written: Value = serde_json::from_slice(
        &std::fs::read(Path::new(&out).join("pdf-export-index.json")).unwrap(),
    )
    .unwrap();
    assert_eq!(written, index);
    assert!(
        fails(&[
            "document",
            "export-pdf-batch",
            &project,
            &path,
            "--output-dir",
            &out
        ])
        .contains("already exists")
    );
}

#[test]
fn t082_05_batch_rejects_invalid_manifests_and_writes_nothing() {
    let scratch = Scratch::new("invalid");
    let project = project(&scratch);
    let a = create(&project, "Policy A", "# A");
    let id = a["id"].clone();
    let rev = a["revision"].clone();
    let out = scratch.path("out");
    let cases = [
        json!({"schema_version":2,"release":"R","documents":[{"id":id,"revision":rev}]}),
        json!({"schema_version":1,"release":"R","documents":[]}),
        json!({"schema_version":1,"release":"","documents":[{"id":id,"revision":rev}]}),
        json!({"schema_version":1,"release":"R","documents":[{"id":id,"revision":rev},{"id":id,"revision":rev}]}),
        json!({"schema_version":1,"release":"R","documents":[{"id":id}]}),
        json!({"schema_version":1,"release":"R","documents":[{"id":id,"revision":rev,"extra":1}]}),
        json!({"schema_version":1,"release":"R","documents":[{"id":id,"revision":"deadbeef"}]}),
        json!({"schema_version":1,"release":"R","documents":[{"id":"../x","revision":rev}]}),
        json!({"schema_version":1,"release":"R","documents":[{"id":id,"revision":rev}], "x": 1}),
        json!({"schema_version":1,"release":"R","documents":(0..501).map(|i| json!({"id": format!("DOC-{i:032x}"), "revision": rev})).collect::<Vec<_>>()}),
    ];
    for case in cases {
        let path = manifest(&scratch, case.clone());
        fails(&[
            "document",
            "export-pdf-batch",
            &project,
            &path,
            "--output-dir",
            &out,
        ]);
        assert!(!Path::new(&out).exists(), "nothing written for {case}");
    }
    // A valid first entry followed by an unknown second one still writes nothing.
    let path = manifest(
        &scratch,
        json!({"schema_version":1,"release":"R","documents":[{"id":id,"revision":rev},{"id":"DOC-00000000000000000000000000000000","revision":rev}]}),
    );
    fails(&[
        "document",
        "export-pdf-batch",
        &project,
        &path,
        "--output-dir",
        &out,
    ]);
    assert!(!Path::new(&out).exists());
    let inside = format!("{project}/exports");
    let path = manifest(
        &scratch,
        json!({"schema_version":1,"release":"R","documents":[{"id":id,"revision":rev}]}),
    );
    assert!(
        fails(&[
            "document",
            "export-pdf-batch",
            &project,
            &path,
            "--output-dir",
            &inside
        ])
        .contains("outside")
    );
}
