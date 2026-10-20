use clap::{Arg, ArgAction, Command, error::ErrorKind};
use serde::Serialize;
use std::io::{self, Write};
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
