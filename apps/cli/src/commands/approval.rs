use clap::{Arg, ArgAction, ArgGroup, ArgMatches, Command};
use eqms_core::roles::{ApprovalMatrix, ApprovalSubject, MAX_MATRIX_BYTES, Role};
use std::io::Read;

const DISCLAIMER: &str = "Roles are asserted input; this does not authenticate identity, verify signatures or enforce separation of duties.";

pub(crate) fn command() -> Command {
    Command::new("approval")
        .about("Validate role-based approval rules")
        .subcommand_required(true)
        .subcommand(
            Command::new("validate-matrix")
                .about("Validate an approval matrix file")
                .arg(Arg::new("file").required(true)),
        )
        .subcommand(
            Command::new("check")
                .about("Check whether asserted roles satisfy the matrix; exits nonzero if blocked")
                .arg(Arg::new("file").required(true))
                .arg(
                    Arg::new("artifact-type")
                        .long("artifact-type")
                        .value_name("TYPE"),
                )
                .arg(
                    Arg::new("release")
                        .long("release")
                        .action(ArgAction::SetTrue),
                )
                .group(
                    ArgGroup::new("subject")
                        .args(["artifact-type", "release"])
                        .required(true),
                )
                .arg(
                    Arg::new("role")
                        .long("role")
                        .action(ArgAction::Append)
                        .value_name("ROLE"),
                ),
        )
}

fn load(file: &str) -> Result<ApprovalMatrix, String> {
    let mut bytes = Vec::new();
    std::fs::File::open(file)
        .map_err(|cause| format!("cannot read approval matrix: {cause}"))?
        .take(MAX_MATRIX_BYTES as u64 + 1)
        .read_to_end(&mut bytes)
        .map_err(|cause| format!("cannot read approval matrix: {cause}"))?;
    ApprovalMatrix::parse(&bytes)
}

pub(crate) fn run(matches: &ArgMatches, json: bool) -> Result<String, String> {
    match matches.subcommand() {
        Some(("validate-matrix", args)) => {
            let matrix = load(args.get_one::<String>("file").expect("required file"))?;
            if json {
                Ok(serde_json::json!({
                    "valid": true,
                    "artifact_types": matrix.artifacts.len(),
                })
                .to_string())
            } else {
                Ok(format!(
                    "Approval matrix is valid: {} artifact types plus release rule.",
                    matrix.artifacts.len()
                ))
            }
        }
        Some(("check", args)) => {
            let matrix = load(args.get_one::<String>("file").expect("required file"))?;
            let held = args
                .get_many::<String>("role")
                .into_iter()
                .flatten()
                .map(|role| role.parse::<Role>())
                .collect::<Result<Vec<_>, _>>()?;
            let subject = match args.get_one::<String>("artifact-type") {
                Some(name) => ApprovalSubject::Artifact(name),
                None => ApprovalSubject::Release,
            };
            let check = matrix.check(subject, &held)?;
            if !check.permitted {
                let missing: Vec<_> = check.missing.iter().map(|r| r.as_str()).collect();
                return Err(format!(
                    "Approval blocked for {}: missing required roles: {}. {DISCLAIMER}",
                    check.subject,
                    missing.join(", ")
                ));
            }
            if json {
                serde_json::to_string(&check).map_err(|cause| cause.to_string())
            } else {
                Ok(format!(
                    "Approval permitted for {}: all required roles asserted. {DISCLAIMER}",
                    check.subject
                ))
            }
        }
        _ => unreachable!("required approval operation"),
    }
}
