use clap::{Arg, ArgAction, Command, error::ErrorKind};
use serde::Serialize;
use std::io::{self, Write};
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
                .help("Use schema-versioned JSON for version output and usage errors")
                .action(ArgAction::SetTrue),
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
        Ok(_) => usage_error("Specify --version or --help.", json),
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
