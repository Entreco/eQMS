use crate::output;
use clap::{Arg, ArgMatches, Command};
use std::io::Read;

pub(crate) fn command() -> Command {
    Command::new("document")
        .about("Create, save and inspect document drafts")
        .subcommand_required(true)
        .subcommands(super::render::commands())
        .subcommand(
            operation("validate")
                .about("Check exact canonical v1 Markdown (no workspace)")
                .mut_arg("path", |arg| {
                    arg.help("v1 Markdown file, or - for UTF-8 stdin")
                }),
        )
        .subcommand(
            operation("format")
                .about("Write canonical v1 Markdown to stdout (no file writes)")
                .mut_arg("path", |arg| {
                    arg.help("v1 Markdown file, or - for UTF-8 stdin")
                }),
        )
        .subcommand(operation("create").arg(title_argument()))
        .subcommand(operation("list"))
        .subcommand(
            operation("show")
                .arg(Arg::new("id").required(true))
                .arg(Arg::new("revision").long("revision")),
        )
        .subcommand(
            operation("save")
                .arg(Arg::new("id").required(true))
                .arg(title_argument())
                .arg(Arg::new("expected").long("expected").required(true)),
        )
        .subcommand(
            operation("transition")
                .about("Apply an allowed lifecycle transition to a document")
                .arg(Arg::new("id").required(true))
                .arg(Arg::new("expected").long("expected").required(true))
                .arg(
                    Arg::new("to").long("to").required(true).help(
                        "Target state: In Review, Approved, Effective, Obsolete, or Superseded",
                    ),
                ),
        )
        .subcommand(operation("status").arg(Arg::new("id").required(true)))
        .subcommand(operation("lifecycle-history").arg(Arg::new("id").required(true)))
        .subcommand(operation("history").arg(Arg::new("id").required(true)))
        .subcommand(
            operation("diff")
                .arg(Arg::new("id").required(true))
                .arg(Arg::new("from").required(true))
                .arg(Arg::new("to").required(true)),
        )
}

fn title_argument() -> Arg {
    Arg::new("title")
        .long("title")
        .required(true)
        .allow_hyphen_values(true)
}

fn operation(name: &'static str) -> Command {
    Command::new(name).arg(Arg::new("path").required(true))
}

pub(crate) fn run(
    matches: &ArgMatches,
    json: bool,
    input: &mut dyn Read,
) -> Result<String, String> {
    let (operation, args) = matches.subcommand().expect("required document operation");
    if matches!(operation, "validate" | "format") {
        return super::markdown::run(operation, args, json, input);
    }
    if matches!(operation, "render" | "render-buffer" | "render-asset") {
        return super::render::run(operation, args, json, input);
    }
    let value = |name: &str| {
        args.get_one::<String>(name)
            .expect("required argument")
            .as_str()
    };
    let docs = eqms_core::document::Documents::open(value("path")).map_err(|e| e.to_string())?;
    let data = match operation {
        "create" | "save" => {
            let mut body = String::new();
            input
                .read_to_string(&mut body)
                .map_err(|e| format!("cannot read UTF-8 document body: {e}"))?;
            let document = if operation == "create" {
                docs.create(value("title"), &body)
            } else {
                docs.save(value("id"), value("expected"), value("title"), &body)
            }
            .map_err(|e| e.to_string())?;
            serde_json::to_value(document)
        }
        "transition" => {
            let target = value("to").parse::<eqms_core::document::LifecycleState>()?;
            serde_json::to_value(
                docs.transition(value("id"), value("expected"), target)
                    .map_err(|e| e.to_string())?,
            )
        }
        "list" => serde_json::to_value(docs.list().map_err(|e| e.to_string())?),
        "show" | "status" => {
            let revision = if operation == "show" {
                args.get_one::<String>("revision").map(String::as_str)
            } else {
                None
            };
            serde_json::to_value(
                docs.show(value("id"), revision)
                    .map_err(|e| e.to_string())?,
            )
        }
        "lifecycle-history" => serde_json::to_value(
            docs.lifecycle_history(value("id"))
                .map_err(|e| e.to_string())?,
        ),
        "history" => serde_json::to_value(docs.history(value("id")).map_err(|e| e.to_string())?),
        "diff" => serde_json::to_value(
            docs.diff(value("id"), value("from"), value("to"))
                .map_err(|e| e.to_string())?,
        ),
        _ => unreachable!("known document operation"),
    }
    .map_err(|e| e.to_string())?;
    output::document(data, json)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::io::{self, Cursor};
    use std::path::PathBuf;
    use std::process::Command as ProcessCommand;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static NEXT: AtomicUsize = AtomicUsize::new(0);

    struct Fixture(PathBuf);

    impl Fixture {
        fn new() -> Self {
            let path = std::env::temp_dir().join(format!(
                "eqms-command-test-{}-{}",
                std::process::id(),
                NEXT.fetch_add(1, Ordering::Relaxed),
            ));
            fs::create_dir(&path).unwrap();
            for args in [
                &["init", "--quiet"][..],
                &["config", "user.name", "CLI Test"],
                &["config", "user.email", "cli@example.invalid"],
            ] {
                assert!(
                    ProcessCommand::new("git")
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

        fn run(&self, args: &[&str], input: &mut dyn Read) -> Result<serde_json::Value, String> {
            let mut arguments = vec!["document", args[0], self.0.to_str().unwrap()];
            arguments.extend_from_slice(&args[1..]);
            let matches = command().try_get_matches_from(arguments).unwrap();
            serde_json::from_str(&run(&matches, true, input)?).map_err(|e| e.to_string())
        }
    }

    impl Drop for Fixture {
        fn drop(&mut self) {
            fs::remove_dir_all(&self.0).unwrap();
        }
    }

    struct FailingReader;

    #[test]
    fn saved_render_is_source_bound_read_only_and_assets_are_bounded() {
        let fixture = Fixture::new();
        let saved = fixture
            .run(
                &["create", "--title", "Rendered policy"],
                &mut Cursor::new("# Purpose\n\n**Quality** work\n"),
            )
            .unwrap();
        let id = saved["id"].as_str().unwrap();
        let revision = saved["revision"].as_str().unwrap();
        let refs = || {
            ProcessCommand::new("git")
                .arg("-C")
                .arg(&fixture.0)
                .args(["show-ref"])
                .output()
                .unwrap()
                .stdout
        };
        let before = refs();
        let model = fixture
            .run(
                &["render", id, "--revision", revision],
                &mut Cursor::new(""),
            )
            .unwrap();
        assert_eq!(model["source"]["revision"], revision);
        assert_eq!(model["source"]["content_hash"], saved["content_hash"]);
        assert_eq!(model["source"]["kind"], "saved");
        let html = fixture
            .run(&["render", id, "--output", "html"], &mut Cursor::new(""))
            .unwrap();
        assert!(
            html["html"]
                .as_str()
                .unwrap()
                .contains("<strong>Quality</strong>")
        );
        assert_eq!(before, refs());
        assert_eq!(
            fixture.run(&["show", id], &mut Cursor::new("")).unwrap(),
            saved
        );
        assert!(
            fixture
                .run(&["render-asset", "../secret"], &mut Cursor::new(""))
                .is_err()
        );
        #[cfg(unix)]
        {
            fs::create_dir(fixture.0.join("assets")).unwrap();
            image::RgbImage::new(4, 3)
                .save(fixture.0.join("assets/sample.png"))
                .unwrap();
            let image = fixture
                .run(&["render-asset", "assets/sample.png"], &mut Cursor::new(""))
                .unwrap();
            assert_eq!(image["width"], 4);
            assert_eq!(image["height"], 3);
            std::os::unix::fs::symlink(
                fixture.0.join("assets/sample.png"),
                fixture.0.join("assets/link.png"),
            )
            .unwrap();
            assert!(
                fixture
                    .run(&["render-asset", "assets/link.png"], &mut Cursor::new(""))
                    .is_err()
            );
            fs::write(fixture.0.join("assets/bad.png"), b"not a PNG").unwrap();
            assert!(
                fixture
                    .run(&["render-asset", "assets/bad.png"], &mut Cursor::new(""))
                    .is_err()
            );
            let oversized = fs::File::create(fixture.0.join("assets/large.png")).unwrap();
            oversized.set_len(10 * 1024 * 1024 + 1).unwrap();
            assert!(
                fixture
                    .run(&["render-asset", "assets/large.png"], &mut Cursor::new(""))
                    .unwrap_err()
                    .contains("10 MiB")
            );
        }
    }

    impl Read for FailingReader {
        fn read(&mut self, _: &mut [u8]) -> io::Result<usize> {
            Err(io::Error::other("injected input failure"))
        }
    }

    #[test]
    fn create_and_save_accept_hyphen_leading_titles() {
        let fixture = Fixture::new();
        for create_args in [
            vec!["create", "--title", "-20\u{00b0}C storage"],
            vec!["create", "--title=-20\u{00b0}C storage"],
        ] {
            let created = fixture
                .run(&create_args, &mut Cursor::new("original"))
                .unwrap();
            assert_eq!(created["title"], "-20\u{00b0}C storage");
            let id = created["id"].as_str().unwrap();
            let revision = created["revision"].as_str().unwrap();
            let saved = fixture
                .run(
                    &[
                        "save",
                        id,
                        "--title",
                        "-20\u{00b0}C storage",
                        "--expected",
                        revision,
                    ],
                    &mut Cursor::new("updated"),
                )
                .unwrap();
            assert_eq!(saved["title"], "-20\u{00b0}C storage");
            assert_eq!(saved["body"], "updated");
        }
    }

    #[test]
    fn create_and_save_use_in_memory_utf8_input() {
        let fixture = Fixture::new();
        let created = fixture
            .run(
                &["create", "--title", "Policy"],
                &mut Cursor::new("# Policy\n"),
            )
            .unwrap();
        assert_eq!(created["body"], "# Policy\n");
        let id = created["id"].as_str().unwrap();
        let revision = created["revision"].as_str().unwrap();
        let saved = fixture
            .run(
                &[
                    "save",
                    id,
                    "--title",
                    "Updated policy",
                    "--expected",
                    revision,
                ],
                &mut Cursor::new("Updated\n"),
            )
            .unwrap();
        assert_eq!(saved["body"], "Updated\n");
        assert_eq!(saved["title"], "Updated policy");
        assert_ne!(saved["revision"], revision);

        for args in [
            vec!["list"],
            vec!["show", id],
            vec!["history", id],
            vec!["diff", id, revision, saved["revision"].as_str().unwrap()],
        ] {
            assert!(fixture.run(&args, &mut FailingReader).is_ok());
        }
    }

    #[test]
    fn invalid_utf8_and_read_failures_do_not_create_or_save() {
        let fixture = Fixture::new();
        let error = fixture
            .run(&["create", "--title", "Policy"], &mut FailingReader)
            .unwrap_err();
        assert!(error.contains("cannot read UTF-8 document body: injected input failure"));
        assert!(
            fixture
                .run(&["create", "--title", "Policy"], &mut Cursor::new([0xff]))
                .is_err()
        );
        assert_eq!(
            fixture.run(&["list"], &mut FailingReader).unwrap(),
            serde_json::json!([])
        );

        let created = fixture
            .run(
                &["create", "--title", "Policy"],
                &mut Cursor::new("original"),
            )
            .unwrap();
        let id = created["id"].as_str().unwrap();
        let revision = created["revision"].as_str().unwrap();
        let args = ["save", id, "--title", "Policy", "--expected", revision];
        assert!(fixture.run(&args, &mut FailingReader).is_err());
        assert!(fixture.run(&args, &mut Cursor::new([0xff])).is_err());
        let shown = fixture.run(&["show", id], &mut FailingReader).unwrap();
        assert_eq!(shown["revision"], revision);
        assert_eq!(shown["body"], "original");
    }
}
