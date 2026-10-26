use clap::{Arg, ArgMatches, Command};

pub(crate) fn command() -> Command {
    Command::new("audit")
        .about("Inspect recorded audit integrity")
        .subcommand_required(true)
        .subcommand(
            Command::new("verify-ledger")
                .about("Verify baseline and all saved draft chains without repairing history")
                .arg(Arg::new("path").required(true))
                .arg(
                    Arg::new("checkpoint")
                        .long("checkpoint")
                        .help("Full baseline commit ID from an independently trusted source"),
                ),
        )
}

pub(crate) fn run(matches: &ArgMatches, json: bool) -> Result<String, String> {
    let args = matches
        .subcommand_matches("verify-ledger")
        .expect("required audit operation");
    let path = args
        .get_one::<String>("path")
        .expect("required workspace path");
    let result = eqms_core::audit::verify_ledger(
        path,
        args.get_one::<String>("checkpoint").map(String::as_str),
    )
    .map_err(|cause| cause.to_string())?;
    if json {
        serde_json::to_string(&result).map_err(|cause| cause.to_string())
    } else {
        let trust = match result.checkpoint {
            Some(checkpoint) => format!("Checkpoint ancestry verified: {checkpoint}"),
            None => {
                "Local consistency only: no independently trusted checkpoint supplied".to_owned()
            }
        };
        Ok(format!(
            "Verified recorded ledger at {}\nBaseline: {} commits, {} records; {} isolated draft chains\n{trust}\nDoes not prove identity or approval, or detect deleted draft refs.",
            result.baseline_revision,
            result.baseline_commits,
            result.baseline_records,
            result.drafts.len(),
        ))
    }
}
