use crate::commands::{document, search, workspace};
use crate::output::{self, Response};
use clap::{Arg, ArgAction, Command, error::ErrorKind};
use std::ffi::OsString;
use std::io::Read;

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
        .subcommand(document::command())
        .subcommand(search::command())
        .subcommands(workspace::commands())
}

pub(crate) fn response(args: Vec<OsString>, input: &mut dyn Read) -> Response {
    let json = args
        .iter()
        .skip(1)
        .take_while(|arg| *arg != "--")
        .any(|arg| arg == "--json");
    if args.len() == 1 {
        return Response::success(command().render_help().to_string());
    }
    match command().try_get_matches_from(args) {
        Err(error) if error.kind() == ErrorKind::DisplayHelp => {
            Response::success(error.to_string())
        }
        Err(error) => Response::usage_error(&error.to_string(), json),
        Ok(matches) if matches.get_flag("version") => {
            Response::from_result(Ok(output::version(json)), json, "version")
        }
        Ok(matches) => match matches.subcommand() {
            Some(("search", args)) => Response::from_result(
                search::run(args, json),
                json,
                if args.get_flag("prepare") {
                    "search.prepare"
                } else {
                    "search"
                },
            ),
            Some(("document", args)) => {
                let command = args
                    .subcommand_name()
                    .map(|operation| format!("document.{operation}"))
                    .unwrap_or_else(|| "document".to_owned());
                Response::from_result(document::run(args, json, input), json, &command)
            }
            Some((operation @ ("init" | "index" | "verify" | "recover"), args)) => {
                let command = if operation == "index" {
                    "index.rebuild"
                } else {
                    operation
                };
                Response::from_result(workspace::run(operation, args, json), json, command)
            }
            _ => Response::usage_error("Specify --version or a command.", json),
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{self, Cursor};

    fn run(args: &[&str]) -> Response {
        response(
            args.iter().map(OsString::from).collect(),
            &mut Cursor::new([]),
        )
    }

    #[test]
    fn help_version_and_usage_are_testable_without_process_io() {
        for args in [
            &["qms"][..],
            &["qms", "--help"],
            &["qms", "--json", "--help"],
        ] {
            let result = run(args);
            assert_eq!(result.code, 0);
            assert!(!result.error);
            assert!(result.text.contains("Usage:"));
        }
        assert_eq!(
            run(&["qms", "--version"]).text,
            format!("qms {}", eqms_core::version())
        );
        for args in [
            &["qms", "--json", "--unknown"][..],
            &["qms", "--unknown", "--json"],
        ] {
            let result = run(args);
            assert_eq!(result.code, 2);
            assert!(result.error);
            let json: serde_json::Value = serde_json::from_str(&result.text).unwrap();
            assert_eq!(json["schema_version"], 2);
            assert_eq!(json["status"], "error");
            assert_eq!(json["data"], serde_json::Value::Null);
            assert!(json["findings"].is_array());
            assert!(json["references"].is_array());
            assert!(json["timestamp_unix_ms"].as_i64().is_some());
            assert_eq!(json["error"]["code"], "INVALID_ARGUMENT");
        }
    }

    #[test]
    fn informational_commands_never_read_input() {
        struct Unreadable;
        impl Read for Unreadable {
            fn read(&mut self, _: &mut [u8]) -> io::Result<usize> {
                panic!("informational commands must not read stdin");
            }
        }
        for args in [
            vec!["qms", "--help"],
            vec!["qms", "--version"],
            vec!["qms", "document", "--help"],
            vec!["qms", "index", "--help"],
            vec!["qms", "search", "--help"],
        ] {
            assert_eq!(
                response(
                    args.into_iter().map(OsString::from).collect(),
                    &mut Unreadable
                )
                .code,
                0
            );
        }
    }
}
