use clap::{Arg, ArgMatches, Command};
use std::path::Path;

pub(crate) fn commands() -> [Command; 4] {
    [
        Command::new("init")
            .about("Initialize a Git-backed eQMS workspace")
            .arg(Arg::new("path").required(true)),
        Command::new("index")
            .about("Manage the rebuildable SQLite query index")
            .subcommand_required(true)
            .subcommand(
                Command::new("rebuild")
                    .about("Rebuild the query index from canonical Markdown documents")
                    .arg(Arg::new("path").required(true)),
            ),
        Command::new("verify")
            .about("Verify workspace configuration and journal integrity")
            .arg(Arg::new("path").required(true)),
        Command::new("recover")
            .about("Recover an interrupted workspace or journal update")
            .arg(Arg::new("path").required(true)),
    ]
}

pub(crate) fn run(operation: &str, matches: &ArgMatches, json: bool) -> Result<String, String> {
    let args = if operation == "index" {
        matches
            .subcommand_matches("rebuild")
            .expect("clap requires an index subcommand")
    } else {
        matches
    };
    let path = Path::new(args.get_one::<String>("path").expect("required path"));
    match operation {
        "init" => eqms_core::workspace::Workspace::initialize(path)
            .map(|workspace| {
                if json {
                    serde_json::json!({
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
            .map_err(|error| error.to_string()),
        "index" => eqms_core::workspace::Workspace::open(path)
            .map_err(|error| error.to_string())
            .and_then(|workspace| {
                eqms_core::index::Index::rebuild(&workspace)
                    .map(|count| (workspace, count))
                    .map_err(|error| error.to_string())
            })
            .map(|(workspace, count)| {
                if json {
                    serde_json::json!({
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
            }),
        "verify" => eqms_core::workspace::Workspace::open(path)
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
                        "path": workspace.path().display().to_string(), "journal_records": count
                    })
                    .to_string()
                } else {
                    format!(
                        "Verified workspace {} ({count} journal records)",
                        workspace.path().display()
                    )
                }
            }),
        "recover" => eqms_core::workspace::Workspace::open(path)
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
                        "path": workspace.path().display().to_string()
                    })
                    .to_string()
                } else {
                    format!(
                        "Workspace recovery complete at {}",
                        workspace.path().display()
                    )
                }
            }),
        _ => unreachable!("known workspace operation"),
    }
}
