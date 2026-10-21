use clap::{Arg, ArgAction, ArgMatches, Command, value_parser};
use eqms_core::search::{SearchEngine, SearchQuery, SqliteSearch};

pub(crate) fn command() -> Command {
    let mut command = Command::new("search")
        .about("Search saved project documents by title, author and content")
        .arg(Arg::new("path").required(true))
        .arg(
            Arg::new("query")
                .long("query")
                .allow_hyphen_values(true)
                .required_unless_present("prepare")
                .conflicts_with("prepare"),
        )
        .arg(
            Arg::new("prepare")
                .long("prepare")
                .action(ArgAction::SetTrue),
        )
        .arg(
            Arg::new("rebuild")
                .long("rebuild")
                .action(ArgAction::SetTrue)
                .requires("prepare"),
        )
        .arg(
            Arg::new("snapshot")
                .long("snapshot")
                .conflicts_with("prepare"),
        );
    for name in ["documents-offset", "authors-offset", "content-offset"] {
        command = command.arg(
            Arg::new(name)
                .long(name)
                .value_parser(value_parser!(usize))
                .default_value("0"),
        );
    }
    command
}

pub(crate) fn run(args: &ArgMatches, json: bool) -> Result<String, String> {
    let engine = SqliteSearch::new(args.get_one::<String>("path").expect("required path"));
    if args.get_flag("prepare") {
        let prepared = engine
            .prepare(args.get_flag("rebuild"))
            .map_err(|e| e.to_string())?;
        return if json {
            serde_json::to_string(&prepared).map_err(|e| e.to_string())
        } else {
            Ok(format!(
                "Search index ready ({} saved revisions).",
                prepared.indexed_revisions
            ))
        };
    }
    let result = engine
        .search(&SearchQuery {
            text: args.get_one::<String>("query").expect("required query"),
            snapshot: args.get_one::<String>("snapshot").map(String::as_str),
            offsets: ["documents-offset", "authors-offset", "content-offset"]
                .map(|key| *args.get_one::<usize>(key).expect("default offset")),
        })
        .map_err(|e| e.to_string())?;
    if json {
        return serde_json::to_string(&result).map_err(|e| e.to_string());
    }
    let mut text = if result.historical {
        "Historical saved revisions (read-only; configured identity/local time are claims)\n"
            .to_owned()
    } else {
        String::new()
    };
    for (label, group) in [
        ("Documents", result.documents),
        ("Authors", result.authors),
        ("Content", result.content),
    ] {
        text.push_str(&format!("{label}:\n"));
        for hit in group.hits {
            text.push_str(&format!(
                "  {} [Draft] | by {}",
                hit.title, hit.configured_author
            ));
            if !hit.excerpt.is_empty() {
                text.push_str(&format!(" | \"{}\"", hit.excerpt));
            }
            if result.historical {
                text.push_str(&format!(" | {} ({})", hit.revision, hit.local_timestamp));
            }
            text.push('\n');
        }
        if let Some(offset) = group.next_offset {
            text.push_str(&format!(
                "  More: offset {offset}, snapshot {}\n",
                result.snapshot
            ));
        }
    }
    Ok(text)
}
