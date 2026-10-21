use clap::{Arg, ArgAction, Command, error::ErrorKind};
use serde::Serialize;
use std::io::{self, Read, Write};
use std::path::Path;
use std::process::ExitCode;

#[derive(Serialize)]
struct VersionOutput {
    schema_version: u32,
    status: &'static str,
    version: &'static str,
}

#[derive(Serialize)]
struct ErrorOutput<'a> {
    schema_version: u32,
    status: &'static str,
    error: ErrorDetail<'a>,
}

#[derive(Serialize)]
struct ErrorDetail<'a> {
    code: &'static str,
    message: &'a str,
}

fn command() -> Command {
    Command::new("qms")
        .about("Native eQMS CLI foundation")
        .disable_version_flag(true)
        .arg(
            Arg::new("version")
                .long("version")
                .short('V')
                .help("Print the core version")
                .action(ArgAction::SetTrue),
        )
        .arg(
            Arg::new("json")
                .long("json")
                .global(true)
                .help("Use schema-versioned JSON for command results and errors")
                .action(ArgAction::SetTrue),
        )
        .subcommand(
            Command::new("document")
                .about("Create, save and inspect document drafts")
                .subcommand_required(true)
                .subcommand(
                    document_command("create").arg(Arg::new("title").long("title").required(true)),
                )
                .subcommand(document_command("list"))
                .subcommand(
                    document_command("show")
                        .arg(Arg::new("id").required(true))
                        .arg(Arg::new("revision").long("revision")),
                )
                .subcommand(
                    document_command("save")
                        .arg(Arg::new("id").required(true))
                        .arg(Arg::new("title").long("title").required(true))
                        .arg(Arg::new("expected").long("expected").required(true)),
                )
                .subcommand(document_command("history").arg(Arg::new("id").required(true)))
                .subcommand(
                    document_command("diff")
                        .arg(Arg::new("id").required(true))
                        .arg(Arg::new("from").required(true))
                        .arg(Arg::new("to").required(true)),
                ),
        )
        .subcommand(
            Command::new("init")
                .about("Initialize a Git-backed eQMS workspace")
                .arg(Arg::new("path").required(true)),
        )
        .subcommand(
            Command::new("index")
                .about("Manage the rebuildable SQLite query index")
                .subcommand_required(true)
                .subcommand(
                    Command::new("rebuild")
                        .about("Rebuild the query index from canonical Markdown documents")
                        .arg(Arg::new("path").required(true)),
                ),
        )
        .subcommand(
            Command::new("verify")
                .about("Verify workspace configuration and journal integrity")
                .arg(Arg::new("path").required(true)),
        )
        .subcommand(
            Command::new("recover")
                .about("Recover an interrupted workspace or journal update")
                .arg(Arg::new("path").required(true)),
        )
}

fn document_command(name: &'static str) -> Command {
    Command::new(name).arg(Arg::new("path").required(true))
}

fn run_document(matches: &clap::ArgMatches, json: bool) -> Result<String, String> {
    let (operation, args) = matches.subcommand().expect("required document operation");
    let value = |name: &str| {
        args.get_one::<String>(name)
            .expect("required argument")
            .as_str()
    };
    let docs = eqms_core::document::Documents::open(value("path")).map_err(|e| e.to_string())?;
    let data = match operation {
        "create" | "save" => {
            let mut body = String::new();
            io::stdin()
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
        "list" => serde_json::to_value(docs.list().map_err(|e| e.to_string())?),
        "show" => serde_json::to_value(
            docs.show(
                value("id"),
                args.get_one::<String>("revision").map(String::as_str),
            )
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
    if json {
        Ok(serde_json::json!({
            "schema_version": 1, "status": "ok", "command": format!("document.{operation}"), "data": data
        }).to_string())
    } else if let Some(diff) = data.as_str() {
        Ok(diff.to_owned())
    } else {
        serde_json::to_string_pretty(&data).map_err(|e| e.to_string())
    }
}

fn usage_error(message: &str, json: bool) -> (u8, String, bool) {
    let text = if json {
        serde_json::to_string(&ErrorOutput {
            schema_version: 1,
            status: "error",
            error: ErrorDetail {
                code: "INVALID_ARGUMENT",
                message,
            },
        })
        .expect("serializing a string-only error cannot fail")
    } else {
        message.to_owned()
    };
    (2, text, true)
}

fn response(args: Vec<std::ffi::OsString>) -> (u8, String, bool) {
    let json = args
        .iter()
        .skip(1)
        .take_while(|arg| *arg != "--")
        .any(|arg| arg == "--json");
    if args.len() == 1 {
        return (0, command().render_help().to_string(), false);
    }
    match command().try_get_matches_from(args) {
        Err(error) if error.kind() == ErrorKind::DisplayHelp => (0, error.to_string(), false),
        Err(error) => usage_error(&error.to_string(), json),
        Ok(matches) if matches.get_flag("version") => {
            let text = if json {
                serde_json::to_string(&VersionOutput {
                    schema_version: 1,
                    status: "ok",
                    version: eqms_core::version(),
                })
                .expect("serializing static version strings cannot fail")
            } else {
                format!("qms {}", eqms_core::version())
            };
            (0, text, false)
        }
        Ok(matches) => run_command(matches, json),
    }
}

fn run_command(matches: clap::ArgMatches, json: bool) -> (u8, String, bool) {
    let result = match matches.subcommand() {
        Some(("document", sub_matches)) => run_document(sub_matches, json),
        Some(("init", sub_matches)) => {
            let path = Path::new(
                sub_matches
                    .get_one::<String>("path")
                    .expect("required path"),
            );
            eqms_core::workspace::Workspace::initialize(path)
                .map(|workspace| {
                    if json {
                        serde_json::json!({
                            "schema_version": 1,
                            "status": "ok",
                            "command": "init",
                            "path": workspace.path().display().to_string()
                        })
                        .to_string()
                    } else {
                        format!(
                            "Initialized eQMS workspace at {}",
                            workspace.path().display()
                        )
                    }
                })
                .map_err(|error| error.to_string())
        }
        Some(("index", sub_matches)) => match sub_matches.subcommand() {
            Some(("rebuild", rebuild_matches)) => {
                let path = Path::new(
                    rebuild_matches
                        .get_one::<String>("path")
                        .expect("required path"),
                );
                eqms_core::workspace::Workspace::open(path)
                    .map_err(|error| error.to_string())
                    .and_then(|workspace| {
                        eqms_core::index::Index::rebuild(&workspace)
                            .map(|count| (workspace, count))
                            .map_err(|error| error.to_string())
                    })
                    .map(|(workspace, count)| {
                        if json {
                            serde_json::json!({
                                "schema_version": 1,
                                "status": "ok",
                                "command": "index.rebuild",
                                "index_path": workspace.index_path().display().to_string(),
                                "indexed_documents": count
                            })
                            .to_string()
                        } else {
                            format!(
                                "Rebuilt derived index at {} ({count} documents)",
                                workspace.index_path().display()
                            )
                        }
                    })
            }
            _ => unreachable!("clap requires an index subcommand"),
        },
        Some(("verify", sub_matches)) => {
            let path = Path::new(
                sub_matches
                    .get_one::<String>("path")
                    .expect("required path"),
            );
            eqms_core::workspace::Workspace::open(path)
                .map_err(|error| error.to_string())
                .and_then(|workspace| {
                    workspace
                        .verify()
                        .map(|records| (workspace, records.len()))
                        .map_err(|error| error.to_string())
                })
                .map(|(workspace, count)| {
                    if json {
                        serde_json::json!({
                            "schema_version": 1,
                            "status": "ok",
                            "command": "verify",
                            "path": workspace.path().display().to_string(),
                            "journal_records": count
                        })
                        .to_string()
                    } else {
                        format!(
                            "Verified workspace {} ({count} journal records)",
                            workspace.path().display()
                        )
                    }
                })
        }
        Some(("recover", sub_matches)) => {
            let path = Path::new(
                sub_matches
                    .get_one::<String>("path")
                    .expect("required path"),
            );
            eqms_core::workspace::Workspace::open(path)
                .map_err(|error| error.to_string())
                .and_then(|workspace| {
                    workspace
                        .recover()
                        .map(|()| workspace)
                        .map_err(|error| error.to_string())
                })
                .map(|workspace| {
                    if json {
                        serde_json::json!({
                            "schema_version": 1,
                            "status": "ok",
                            "command": "recover",
                            "path": workspace.path().display().to_string()
                        })
                        .to_string()
                    } else {
                        format!(
                            "Workspace recovery complete at {}",
                            workspace.path().display()
                        )
                    }
                })
        }
        _ => return usage_error("Specify --version or a command.", json),
    };

    match result {
        Ok(text) => (0, text, false),
        Err(message) => {
            let text = if json {
                serde_json::to_string(&ErrorOutput {
                    schema_version: 1,
                    status: "error",
                    error: ErrorDetail {
                        code: "OPERATION_FAILED",
                        message: &message,
                    },
                })
                .expect("serializing a string-only error cannot fail")
            } else {
                message
            };
            (1, text, true)
        }
    }
}

fn main() -> ExitCode {
    let (code, text, error) = response(std::env::args_os().collect());
    let result = if error {
        writeln!(io::stderr().lock(), "{text}")
    } else {
        writeln!(io::stdout().lock(), "{text}")
    };
    if let Err(error) = result {
        let _ = writeln!(io::stderr().lock(), "qms: output failed: {error}");
        return ExitCode::from(1);
    }
    ExitCode::from(code)
}
