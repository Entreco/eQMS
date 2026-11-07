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
        .subcommand(page_arguments(operation("create").arg(title_argument())))
        .subcommand(operation("list"))
        .subcommand(
            operation("show")
                .arg(Arg::new("id").required(true))
                .arg(Arg::new("revision").long("revision")),
        )
        .subcommand(page_arguments(
            operation("save")
                .arg(Arg::new("id").required(true))
                .arg(title_argument())
                .arg(Arg::new("expected").long("expected").required(true)),
        ))
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
        .subcommand(operation("history-snapshot").arg(Arg::new("id").required(true)))
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

const PAGE_OPTIONS: [&str; 8] = [
    "page-size",
    "orientation",
    "margin-top",
    "margin-right",
    "margin-bottom",
    "margin-left",
    "header",
    "footer",
];

fn page_arguments(command: Command) -> Command {
    command
        .arg(
            Arg::new("page-size")
                .long("page-size")
                .value_parser(["A4", "Letter"])
                .help("Page size; defaults to A4 (create) or the previous revision (save)"),
        )
        .arg(
            Arg::new("orientation")
                .long("orientation")
                .value_parser(["portrait", "landscape"]),
        )
        .arg(margin_argument("margin-top"))
        .arg(margin_argument("margin-right"))
        .arg(margin_argument("margin-bottom"))
        .arg(margin_argument("margin-left"))
        .arg(
            Arg::new("header")
                .long("header")
                .allow_hyphen_values(true)
                .help("Header template using {title}, {page}, {total}"),
        )
        .arg(
            Arg::new("footer")
                .long("footer")
                .allow_hyphen_values(true)
                .help("Footer template using {title}, {page}, {total}"),
        )
}

fn margin_argument(name: &'static str) -> Arg {
    Arg::new(name)
        .long(name)
        .value_parser(clap::value_parser!(f64))
        .allow_hyphen_values(true)
        .help("Margin in millimetres (5.0-50.0, 0.1 steps)")
}

/// Apply explicitly supplied page options over `base`; `None` when none were supplied.
fn page_override(
    args: &ArgMatches,
    base: eqms_core::markdown::PageSetup,
) -> Option<eqms_core::markdown::PageSetup> {
    use eqms_core::markdown::{Orientation, PageSize};
    if !PAGE_OPTIONS.iter().any(|name| args.contains_id(name)) {
        return None;
    }
    let mut page = base;
    if let Some(size) = args.get_one::<String>("page-size") {
        page.size = if size == "A4" {
            PageSize::A4
        } else {
            PageSize::Letter
        };
    }
    if let Some(orientation) = args.get_one::<String>("orientation") {
        page.orientation = if orientation == "portrait" {
            Orientation::Portrait
        } else {
            Orientation::Landscape
        };
    }
    for (name, slot) in [
        ("margin-top", &mut page.margin_top_mm),
        ("margin-right", &mut page.margin_right_mm),
        ("margin-bottom", &mut page.margin_bottom_mm),
        ("margin-left", &mut page.margin_left_mm),
    ] {
        if let Some(value) = args.get_one::<f64>(name) {
            *slot = *value;
        }
    }
    if let Some(header) = args.get_one::<String>("header") {
        page.header = header.clone();
    }
    if let Some(footer) = args.get_one::<String>("footer") {
        page.footer = footer.clone();
    }
    Some(page)
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
                match page_override(args, Default::default()) {
                    Some(page) => docs.create_with_page(value("title"), &body, &page),
                    None => docs.create(value("title"), &body),
                }
            } else {
                let id = value("id");
                let has_page = PAGE_OPTIONS.iter().any(|name| args.contains_id(name));
                let current = if has_page {
                    docs.show(id, None).map_err(|e| e.to_string())?.page
                } else {
                    Default::default()
                };
                match page_override(args, current) {
                    Some(page) => {
                        docs.save_with_page(id, value("expected"), value("title"), &body, &page)
                    }
                    None => docs.save(id, value("expected"), value("title"), &body),
                }
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
        "history-snapshot" => serde_json::to_value(
            docs.history_snapshot(value("id"))
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
            image::RgbImage::new(4001, 4000)
                .save(fixture.0.join("assets/too-many-pixels.png"))
                .unwrap();
            assert!(
                fixture
                    .run(
                        &["render-asset", "assets/too-many-pixels.png"],
                        &mut Cursor::new("")
                    )
                    .unwrap_err()
                    .contains("16 megapixels")
            );
        }
    }

    impl Read for FailingReader {
        fn read(&mut self, _: &mut [u8]) -> io::Result<usize> {
            Err(io::Error::other("injected input failure"))
        }
    }

    #[test]
    fn page_options_are_validated_and_omitted_options_keep_previous_values() {
        let fixture = Fixture::new();
        let created = fixture
            .run(&["create", "--title", "Policy"], &mut Cursor::new("body"))
            .unwrap();
        assert_eq!(created["page"]["size"], "A4");
        assert_eq!(created["page"]["margin_top_mm"], 25.4);
        let id = created["id"].as_str().unwrap();
        let revision = created["revision"].as_str().unwrap();
        let saved = fixture
            .run(
                &[
                    "save",
                    id,
                    "--title",
                    "Policy",
                    "--expected",
                    revision,
                    "--page-size",
                    "Letter",
                    "--orientation",
                    "landscape",
                    "--margin-top",
                    "10",
                    "--header",
                    "Doc {title}",
                ],
                &mut Cursor::new("body"),
            )
            .unwrap();
        assert_ne!(saved["revision"], created["revision"]);
        assert_ne!(saved["content_hash"], created["content_hash"]);
        assert_eq!(saved["page"]["size"], "Letter");
        assert_eq!(saved["page"]["orientation"], "landscape");
        assert_eq!(saved["page"]["margin_top_mm"], 10.0);
        assert_eq!(saved["page"]["margin_left_mm"], 25.4);
        assert_eq!(saved["page"]["header"], "Doc {title}");
        let next = saved["revision"].as_str().unwrap();
        let kept = fixture
            .run(
                &["save", id, "--title", "Policy", "--expected", next],
                &mut Cursor::new("body two"),
            )
            .unwrap();
        assert_eq!(kept["page"], saved["page"]);
        let kept_revision = kept["revision"].as_str().unwrap();
        for bad in [
            ["--margin-left", "4"],
            ["--margin-left", "50.05"],
            ["--header", "{nope}"],
            ["--footer", "}"],
        ] {
            let mut args = vec!["save", id, "--title", "Policy", "--expected", kept_revision];
            args.extend(bad);
            assert!(fixture.run(&args, &mut Cursor::new("body two")).is_err());
        }
        assert!(
            command()
                .try_get_matches_from(["document", "save", "ws", "x", "--page-size", "A5"])
                .is_err()
        );
        assert_eq!(
            fixture.run(&["show", id], &mut Cursor::new("")).unwrap(),
            kept
        );
        assert!(
            fixture
                .run(
                    &["create", "--title", "Bad", "--margin-top", "99"],
                    &mut Cursor::new("x"),
                )
                .is_err()
        );
        assert_eq!(
            fixture
                .run(&["list"], &mut Cursor::new(""))
                .unwrap()
                .as_array()
                .unwrap()
                .len(),
            1
        );
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
