use serde_json::Value;
use std::fs;
use std::io::Write;
use std::path::PathBuf;
use std::process::{Command, Output, Stdio};
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT: AtomicUsize = AtomicUsize::new(0);
const GOLDEN: &str = "---\n{\"schema_version\":1,\"id\":\"DOC-0123456789abcdef0123456789abcdef\",\"title\":\"Policy\",\"status\":\"Draft\"}\n---\nBody";
const HASH: &str = "sha256:2866a39ccb18a2d807569fbbadb98f7b17a2cfc15e4155bc60efc4fc5bc44ccf";

struct Fixture(PathBuf);

impl Fixture {
    fn new() -> Self {
        let path = std::env::temp_dir().join(format!(
            "eqms markdown cli {} {}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&path).unwrap();
        Self(path)
    }

    fn invoke(&self, args: &[&str], input: &[u8]) -> Output {
        let mut child = Command::new(env!("CARGO_BIN_EXE_qms"))
            .current_dir(&self.0)
            .args(args)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .unwrap();
        child.stdin.take().unwrap().write_all(input).unwrap();
        child.wait_with_output().unwrap()
    }
}

impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}

fn success(output: &Output) {
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    assert!(output.stderr.is_empty());
}

fn envelope(bytes: &[u8], operation: &str, status: &str) -> Value {
    let value: Value = serde_json::from_slice(bytes).unwrap();
    assert_eq!(value["schema_version"], 2);
    assert_eq!(value["command"], format!("document.{operation}"));
    assert_eq!(value["status"], status);
    assert!(value["findings"].is_array());
    assert!(value["references"].is_array());
    assert!(value["timestamp_unix_ms"].is_i64());
    value
}

#[test]
fn file_and_stdin_operations_are_standalone_read_only_and_exact() {
    let fixture = Fixture::new();
    for body in ["", "Body", "Body\n", "Body  \n---\nUnicode: \u{03bb}"] {
        let canonical = GOLDEN.replace("Body", body);
        let source = canonical
            .replace("\"title\":\"Policy\"", "\"title\": \" Policy \"")
            .replace('\n', "\r\n");
        let file = fixture.0.join("policy with spaces.md");
        fs::write(&file, &source).unwrap();
        for input_path in ["-", file.to_str().unwrap()] {
            let input = if input_path == "-" {
                source.as_bytes()
            } else {
                b""
            };
            let formatted = fixture.invoke(&["document", "format", input_path], input);
            success(&formatted);
            assert_eq!(formatted.stdout, canonical.as_bytes());
            let formatted_json =
                fixture.invoke(&["--json", "document", "format", input_path], input);
            success(&formatted_json);
            let result = envelope(&formatted_json.stdout, "format", "ok");
            assert_eq!(result["data"]["content"], canonical);
            assert_eq!(result["data"]["schema_version"], 1);
            assert_eq!(result["data"]["canonical"], true);
            assert_eq!(result["data"]["id"], "DOC-0123456789abcdef0123456789abcdef");
            if body == "Body" {
                assert_eq!(result["data"]["content_hash"], HASH);
            }
            let validation =
                fixture.invoke(&["document", "validate", "-", "--json"], &formatted.stdout);
            success(&validation);
            let validated = envelope(&validation.stdout, "validate", "ok");
            assert_eq!(
                validated["data"]["content_hash"],
                result["data"]["content_hash"]
            );
            assert_eq!(validated["data"]["canonical"], true);
            assert!(validated["data"].get("content").is_none());
            let repeated = fixture.invoke(&["document", "format", "-"], &formatted.stdout);
            success(&repeated);
            assert_eq!(repeated.stdout, formatted.stdout);
        }
        assert_eq!(fs::read(&file).unwrap(), source.as_bytes());
        fs::write(&file, &canonical).unwrap();
        let validation = fixture.invoke(&["document", "validate", file.to_str().unwrap()], b"");
        success(&validation);
        let text = String::from_utf8(validation.stdout).unwrap();
        assert!(text.starts_with("Canonical Markdown format v1: DOC-"));
        assert!(text.ends_with('\n'));
        assert_eq!(fs::read(&file).unwrap(), canonical.as_bytes());
        assert_eq!(fs::read_dir(&fixture.0).unwrap().count(), 1);
    }
}

#[test]
fn invalid_input_errors_are_nonzero_without_partial_stdout() {
    let fixture = Fixture::new();
    let noncanonical = GOLDEN.replace("\"schema_version\":1", "\"schema_version\": 1");
    for (operation, input) in [
        ("validate", noncanonical.as_bytes()),
        ("format", b"\xff".as_slice()),
        ("validate", b"\xff".as_slice()),
        (
            "format",
            b"---\ntitle: Arbitrary YAML\n---\nBody".as_slice(),
        ),
        ("format", b"".as_slice()),
    ] {
        for json in [false, true] {
            let mut args = vec!["document", operation, "-"];
            if json {
                args.push("--json");
            }
            let output = fixture.invoke(&args, input);
            assert_eq!(output.status.code(), Some(1));
            assert!(output.stdout.is_empty());
            if json {
                let result = envelope(&output.stderr, operation, "error");
                assert_eq!(result["error"]["code"], "OPERATION_FAILED");
                assert_eq!(result["data"], Value::Null);
            } else {
                let text = String::from_utf8(output.stderr).unwrap();
                assert!(!text.is_empty());
                if input == noncanonical.as_bytes() {
                    assert!(text.contains("qms document format"));
                }
            }
        }
    }
    assert_eq!(fs::read_dir(&fixture.0).unwrap().count(), 0);
}

#[test]
fn file_and_usage_errors_follow_existing_exit_and_envelope_conventions() {
    let fixture = Fixture::new();
    for operation in ["validate", "format"] {
        for path in ["missing.md", "."] {
            let output = fixture.invoke(&["document", operation, path, "--json"], b"");
            assert_eq!(output.status.code(), Some(1));
            assert!(output.stdout.is_empty());
            let result = envelope(&output.stderr, operation, "error");
            assert_eq!(result["error"]["code"], "OPERATION_FAILED");
            assert!(
                result["error"]["message"]
                    .as_str()
                    .unwrap()
                    .contains("cannot read Markdown file")
            );
        }
        let output = fixture.invoke(&["document", operation, "--json"], b"");
        assert_eq!(output.status.code(), Some(2));
        assert!(output.stdout.is_empty());
        let result: Value = serde_json::from_slice(&output.stderr).unwrap();
        assert_eq!(result["schema_version"], 2);
        assert_eq!(result["error"]["code"], "INVALID_ARGUMENT");
    }
}
