use clap::{Arg, ArgAction, ArgMatches, Command};
use eqms_core::branding::{
    Branding, BrandingStore, Logo, MAX_LOGO_BYTES, ProjectBranding, generated_logo, on_accent,
    validate_png,
};
use eqms_core::workspace::Workspace;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::io::Read;
use std::path::Path;

pub(crate) fn command() -> Command {
    Command::new("project")
        .about("Project identity (white-label branding)")
        .subcommand_required(true)
        .subcommand(
            Command::new("branding")
                .about("Show or change project name, accent colour and logo")
                .subcommand_required(true)
                .subcommand(
                    Command::new("show")
                        .about("Show the branding in effect (derived defaults if never set)")
                        .arg(Arg::new("path").required(true)),
                )
                .subcommand(branding_options(
                    Command::new("set")
                        .about("Record branding as a hashed, journaled change")
                        .arg(Arg::new("path").required(true)),
                )),
        )
}

pub(crate) fn branding_options(command: Command) -> Command {
    command
        .arg(
            Arg::new("name")
                .long("name")
                .allow_hyphen_values(true)
                .help("Project name (1-60 characters)"),
        )
        .arg(
            Arg::new("accent")
                .long("accent")
                .help("Accent colour #RRGGBB, or `auto` to derive it from the logo seed"),
        )
        .arg(
            Arg::new("seed")
                .long("seed")
                .allow_hyphen_values(true)
                .conflicts_with("logo-file")
                .help("Use a generated logo derived from this text"),
        )
        .arg(
            Arg::new("logo-file")
                .long("logo-file")
                .help("Use this PNG as the logo (16-1024 px, at most 512 KiB)"),
        )
        .arg(
            Arg::new("shuffle")
                .long("shuffle")
                .action(ArgAction::SetTrue)
                .hide(true),
        )
}

fn describe(project: &ProjectBranding, warnings_for: &Branding) -> Value {
    let branding = &project.branding;
    let logo = match &branding.logo {
        Logo::Generated { seed } => {
            json!({"kind": "generated", "seed": seed, "generated": generated_logo(seed)})
        }
        Logo::Png { sha256 } => json!({
            "kind": "png",
            "sha256": sha256,
            "path": format!("branding/logos/{sha256}.png"),
        }),
    };
    json!({
        "name": branding.name,
        "accent": branding.accent,
        "on_accent": on_accent(&branding.accent).unwrap_or("#000000"),
        "logo": logo,
        "persisted": project.persisted,
        "content_hash": project.content_hash,
        "warnings": warnings_for.contrast_warnings(),
    })
}

fn read_logo(path: &str) -> Result<Vec<u8>, String> {
    let mut bytes = Vec::new();
    std::fs::File::open(path)
        .map_err(|e| format!("cannot read logo file: {e}"))?
        .take(MAX_LOGO_BYTES as u64 + 1)
        .read_to_end(&mut bytes)
        .map_err(|e| format!("cannot read logo file: {e}"))?;
    let (width, height) = validate_png(&bytes).map_err(|e| e.to_string())?;
    let decoded = image::load_from_memory_with_format(&bytes, image::ImageFormat::Png)
        .map_err(|e| format!("logo is not a decodable PNG: {e}"))?;
    if (decoded.width(), decoded.height()) != (width, height) {
        return Err("logo dimensions are inconsistent".to_owned());
    }
    Ok(bytes)
}

/// Applies the supplied options over `base`; returns the new branding and any PNG to store.
pub(crate) fn apply_options(
    base: Branding,
    args: &ArgMatches,
) -> Result<(Branding, Option<Vec<u8>>), String> {
    let mut png = None;
    let mut logo = base.logo.clone();
    if let Some(path) = args.get_one::<String>("logo-file") {
        let bytes = read_logo(path)?;
        logo = Logo::Png {
            sha256: format!("{:x}", Sha256::digest(&bytes)),
        };
        png = Some(bytes);
    } else if let Some(seed) = args.get_one::<String>("seed") {
        logo = Logo::Generated { seed: seed.clone() };
    }
    let name = args.get_one::<String>("name").unwrap_or(&base.name);
    let accent = match args.get_one::<String>("accent").map(String::as_str) {
        Some("auto") => match &logo {
            Logo::Generated { seed } => generated_logo(seed).color,
            Logo::Png { .. } => return Err("--accent auto needs a generated logo".to_owned()),
        },
        Some(value) => value.to_owned(),
        None => base.accent.clone(),
    };
    Ok((
        Branding::new(name, &accent, logo).map_err(|e| e.to_string())?,
        png,
    ))
}

pub(crate) fn run(matches: &ArgMatches, json: bool) -> Result<String, String> {
    let (_, branding_args) = matches.subcommand().expect("required project subcommand");
    let (operation, args) = branding_args
        .subcommand()
        .expect("required branding operation");
    let path = Path::new(args.get_one::<String>("path").expect("required path"));
    let workspace = Workspace::open(path).map_err(|e| e.to_string())?;
    let store = BrandingStore::new(&workspace);
    let current = store.show().map_err(|e| e.to_string())?;
    let result = if operation == "set" {
        let (next, png) = apply_options(current.branding.clone(), args)?;
        store
            .set(&next, png.as_deref())
            .map_err(|e| e.to_string())?
    } else {
        current
    };
    let data = describe(&result, &result.branding);
    if json {
        return Ok(data.to_string());
    }
    let mut text = format!(
        "{}\nName: {}\nAccent: {}\nLogo: {}\nStored: {}",
        if operation == "set" {
            "Branding recorded"
        } else {
            "Branding"
        },
        data["name"].as_str().unwrap_or_default(),
        data["accent"].as_str().unwrap_or_default(),
        data["logo"]["kind"].as_str().unwrap_or_default(),
        if result.persisted {
            "yes"
        } else {
            "no (derived defaults)"
        },
    );
    for warning in data["warnings"].as_array().into_iter().flatten() {
        text.push_str(&format!(
            "\nWarning: {}",
            warning.as_str().unwrap_or_default()
        ));
    }
    Ok(text)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn run_args(args: &[&str]) -> Result<Value, String> {
        let matches = command().try_get_matches_from(args).unwrap();
        run(&matches, true).map(|text| serde_json::from_str(&text).unwrap())
    }

    #[test]
    fn set_then_show_round_trips_and_rejects_bad_input() {
        let dir = std::env::temp_dir().join(format!("eqms-brand-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        Workspace::initialize(&dir).unwrap();
        let path = dir.to_str().unwrap();
        assert_eq!(
            run_args(&["project", "branding", "show", path]).unwrap()["persisted"],
            false
        );
        let set = run_args(&[
            "project", "branding", "set", path, "--name", "Acme", "--accent", "#112233",
        ])
        .unwrap();
        assert_eq!(set["persisted"], true);
        let shown = run_args(&["project", "branding", "show", path]).unwrap();
        assert_eq!(shown["name"], "Acme");
        assert_eq!(shown["content_hash"], set["content_hash"]);
        assert!(run_args(&["project", "branding", "set", path, "--accent", "red"]).is_err());
        assert!(
            run_args(&[
                "project",
                "branding",
                "set",
                path,
                "--logo-file",
                "/nonexistent"
            ])
            .is_err()
        );
        std::fs::remove_dir_all(&dir).unwrap();
    }
}
