use clap::{Arg, ArgAction, ArgMatches, Command};
use eqms_core::template::{Source, StoredTemplate, Template, TemplateStore};
use eqms_core::workspace::Workspace;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::io::Read;

const MAX_INPUT_BYTES: u64 = 1024 * 1024;

pub(crate) fn command() -> Command {
    Command::new("template")
        .about("Document templates: structure and style (schema v1)")
        .subcommand_required(true)
        .subcommand(
            Command::new("validate")
                .about("Validate a template file (no workspace needed)")
                .arg(Arg::new("file").required(true)),
        )
        .subcommand(
            Command::new("list")
                .about("List built-in and installed project templates")
                .arg(Arg::new("path").required(true)),
        )
        .subcommand(
            Command::new("show")
                .about("Show the latest version of a template")
                .arg(Arg::new("path").required(true))
                .arg(Arg::new("id").required(true)),
        )
        .subcommand(
            Command::new("install")
                .about("Install a new immutable template version as a journaled change")
                .arg(Arg::new("path").required(true))
                .arg(Arg::new("file").required(true))
                .arg(
                    Arg::new("asset")
                        .long("asset")
                        .action(ArgAction::Append)
                        .help("PNG used by a header/footer slot (repeatable)"),
                ),
        )
}

fn read_limited(path: &str) -> Result<Vec<u8>, String> {
    let mut bytes = Vec::new();
    std::fs::File::open(path)
        .map_err(|e| format!("cannot read {path}: {e}"))?
        .take(MAX_INPUT_BYTES + 1)
        .read_to_end(&mut bytes)
        .map_err(|e| format!("cannot read {path}: {e}"))?;
    if bytes.len() as u64 > MAX_INPUT_BYTES {
        return Err(format!("{path} is larger than {MAX_INPUT_BYTES} bytes"));
    }
    Ok(bytes)
}

fn parse(bytes: &[u8]) -> Result<Template, String> {
    let template: Template =
        serde_json::from_slice(bytes).map_err(|e| format!("invalid template: {e}"))?;
    template.validate().map_err(|e| e.to_string())?;
    Ok(template)
}

fn describe(stored: &StoredTemplate) -> Value {
    let t = &stored.template;
    json!({
        "id": t.id,
        "version": t.version,
        "name": t.name,
        "document_type": t.document_type,
        "source": match stored.source { Source::Builtin => "builtin", Source::Project => "project" },
        "content_hash": stored.content_hash,
        "sections": t.structure.sections.len(),
        "assets": t.asset_hashes(),
    })
}

fn line(value: &Value) -> String {
    format!(
        "{} v{} [{}] {} ({})",
        value["id"].as_str().unwrap_or_default(),
        value["version"],
        value["source"].as_str().unwrap_or_default(),
        value["name"].as_str().unwrap_or_default(),
        value["document_type"].as_str().unwrap_or_default(),
    )
}

pub(crate) fn run(matches: &ArgMatches, json: bool) -> Result<String, String> {
    let (operation, args) = matches.subcommand().expect("required template operation");
    let value = |name: &str| args.get_one::<String>(name).expect("required argument");
    let data = match operation {
        "validate" => {
            let template = parse(&read_limited(value("file"))?)?;
            let hash = template.content_hash().map_err(|e| e.to_string())?;
            let canonical = template.canonical_bytes().map_err(|e| e.to_string())?
                == read_limited(value("file"))?;
            json!({"id": template.id, "version": template.version, "content_hash": hash, "canonical": canonical})
        }
        _ => {
            let workspace = Workspace::open(value("path")).map_err(|e| e.to_string())?;
            let store = TemplateStore::new(&workspace);
            match operation {
                "list" => Value::Array(
                    store
                        .list()
                        .map_err(|e| e.to_string())?
                        .iter()
                        .map(describe)
                        .collect(),
                ),
                "show" => {
                    let stored = store.show(value("id")).map_err(|e| e.to_string())?;
                    let mut data = describe(&stored);
                    data["template"] =
                        serde_json::to_value(&stored.template).map_err(|e| e.to_string())?;
                    data
                }
                _ => {
                    let template = parse(&read_limited(value("file"))?)?;
                    let mut assets = BTreeMap::new();
                    for path in args.get_many::<String>("asset").into_iter().flatten() {
                        let bytes = read_limited(path)?;
                        assets.insert(format!("{:x}", Sha256::digest(&bytes)), bytes);
                    }
                    describe(
                        &store
                            .install(&template, &assets)
                            .map_err(|e| e.to_string())?,
                    )
                }
            }
        }
    };
    if json {
        return Ok(data.to_string());
    }
    Ok(match (operation, &data) {
        ("validate", d) => format!(
            "Valid template {} v{}\nHash: {}{}",
            d["id"].as_str().unwrap_or_default(),
            d["version"],
            d["content_hash"].as_str().unwrap_or_default(),
            if d["canonical"] == true {
                ""
            } else {
                "\nNote: file is not in canonical form"
            }
        ),
        ("list", Value::Array(items)) => items.iter().map(line).collect::<Vec<_>>().join("\n"),
        (_, d) => line(d),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn run_args(args: &[&str]) -> Result<Value, String> {
        let matches = command().try_get_matches_from(args).unwrap();
        run(&matches, true).map(|text| serde_json::from_str(&text).unwrap())
    }

    #[test]
    fn validate_install_list_and_show_round_trip() {
        let dir = std::env::temp_dir().join(format!("eqms-tpl-cli-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir(&dir).unwrap();
        for args in [
            &["init", "--quiet"][..],
            &["config", "user.name", "Template CLI"],
            &["config", "user.email", "template-cli@example.invalid"],
        ] {
            assert!(
                std::process::Command::new("git")
                    .arg("-C")
                    .arg(&dir)
                    .args(args)
                    .output()
                    .unwrap()
                    .status
                    .success()
            );
        }
        Workspace::initialize(&dir).unwrap();
        let path = dir.to_str().unwrap();

        let mut template = Template::builtin_generic();
        template.id = "sop".into();
        let file = dir.join("sop.json");
        std::fs::write(&file, template.canonical_bytes().unwrap()).unwrap();
        let file = file.to_str().unwrap();

        assert_eq!(
            run_args(&["template", "validate", file]).unwrap()["canonical"],
            true
        );
        assert_eq!(
            run_args(&["template", "list", path])
                .unwrap()
                .as_array()
                .unwrap()
                .len(),
            1
        );
        let installed = run_args(&["template", "install", path, file]).unwrap();
        assert_eq!(installed["source"], "project");
        assert_eq!(
            run_args(&["template", "list", path])
                .unwrap()
                .as_array()
                .unwrap()
                .len(),
            2
        );
        let shown = run_args(&["template", "show", path, "sop"]).unwrap();
        assert_eq!(shown["content_hash"], installed["content_hash"]);
        assert_eq!(shown["template"]["id"], "sop");
        assert!(run_args(&["template", "install", path, file]).is_err());
        assert!(run_args(&["template", "show", path, "missing"]).is_err());
        assert!(run_args(&["template", "validate", "/nonexistent"]).is_err());
        std::fs::remove_dir_all(&dir).unwrap();
    }
}
