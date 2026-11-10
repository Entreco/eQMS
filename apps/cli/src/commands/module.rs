use clap::{Arg, ArgMatches, Command};
use eqms_core::module::{self, ArtifactRef};

pub(crate) fn command() -> Command {
    Command::new("module")
        .about("Install bundled declarative modules as Draft scaffolds")
        .subcommand_required(true)
        .subcommand(
            Command::new("list")
                .about("List bundled modules and installed versions")
                .arg(Arg::new("path").required(true)),
        )
        .subcommand(
            Command::new("add")
                .about("Install or upgrade a bundled module without overwriting existing documents")
                .arg(Arg::new("module").required(true))
                .arg(Arg::new("path").required(true)),
        )
}

pub(crate) fn run(matches: &ArgMatches, json: bool) -> Result<String, String> {
    match matches.subcommand() {
        Some(("list", args)) => {
            let modules = module::list(path(args)).map_err(|cause| cause.to_string())?;
            if json {
                return serde_json::to_string(&serde_json::json!({ "modules": modules }))
                    .map_err(|cause| cause.to_string());
            }
            Ok(modules
                .iter()
                .map(|item| {
                    format!(
                        "{} {} (installed: {}) - {}",
                        item.id,
                        item.bundled_version,
                        item.installed_version.as_deref().unwrap_or("no"),
                        item.title
                    )
                })
                .collect::<Vec<_>>()
                .join("\n"))
        }
        Some(("add", args)) => {
            let id = args.get_one::<String>("module").expect("required module");
            let report = module::add(path(args), id).map_err(|cause| cause.to_string())?;
            if json {
                return serde_json::to_string(&report).map_err(|cause| cause.to_string());
            }
            let list = |items: &[ArtifactRef]| {
                if items.is_empty() {
                    "none".to_owned()
                } else {
                    items
                        .iter()
                        .map(|item| format!("{} ({})", item.key, item.document_id))
                        .collect::<Vec<_>>()
                        .join(", ")
                }
            };
            Ok(format!(
                "Module {} {}: {}\nCreated Draft documents: {}\nPreserved existing documents: {}\nConflicts (template changed, existing document kept): {}\nScaffolds only: documents remain Draft; this is not an implemented process or compliance evidence.",
                report.module,
                report.version,
                report.status,
                list(&report.created),
                list(&report.preserved),
                list(&report.conflicts)
            ))
        }
        _ => unreachable!("clap requires a module operation"),
    }
}

fn path(args: &ArgMatches) -> &str {
    args.get_one::<String>("path").expect("required path")
}
