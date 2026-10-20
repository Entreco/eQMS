use std::process::{Command, Output};
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT: AtomicUsize = AtomicUsize::new(0);

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
        assert_eq!(
            value,
            serde_json::json!({
                "schema_version": 1, "status": "ok", "version": eqms_core::version()
            })
        );
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
        assert_eq!(value["schema_version"], 1);
        assert_eq!(value["status"], "error");
        assert_eq!(value["error"]["code"], "INVALID_ARGUMENT");
        assert!(!value["error"]["message"].as_str().unwrap().is_empty());
    }
}
