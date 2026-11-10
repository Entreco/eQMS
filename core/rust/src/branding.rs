//! Project branding (white-label identity): name, accent colour and logo.
//!
//! Branding is canonical, Git-tracked project content under `branding/`. Every change is
//! recorded as a hashed `project.branding_set` journal event. The DutchDoxx product brand is
//! separate and never replaced by project branding.

use crate::workspace::{Workspace, WorkspaceError, error};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;

pub const BRANDING_PATH: &str = "branding/project.json";
pub const BRANDING_SCHEMA_VERSION: u32 = 1;
pub const MAX_NAME_CHARS: usize = 60;
pub const MAX_LOGO_BYTES: usize = 512 * 1024;
pub const MIN_LOGO_PIXELS: u32 = 16;
pub const MAX_LOGO_PIXELS: u32 = 1024;
const EVENT_TYPE: &str = "project.branding_set";
const LIGHT_SURFACE: &str = "#FFFFFF";
const DARK_SURFACE: &str = "#2B2D30";

/// Curated accents that keep at least 3:1 contrast on one theme surface and 4.5:1 text on accent.
const PALETTE: [&str; 12] = [
    "#C2410C", "#B91C1C", "#0F766E", "#1D4ED8", "#6D28D9", "#BE185D", "#15803D", "#0369A1",
    "#A16207", "#7C2D12", "#4338CA", "#9D174D",
];

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case", deny_unknown_fields)]
pub enum Logo {
    /// Deterministic logo derived from the seed (normally the project name).
    Generated { seed: String },
    /// Uploaded PNG stored content-addressed at `branding/logos/<sha256>.png`.
    Png { sha256: String },
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Branding {
    pub schema_version: u32,
    pub name: String,
    pub accent: String,
    pub logo: Logo,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum LogoShape {
    Circle,
    Square,
    Hexagon,
}

/// Rendering instructions for a generated logo; a pure function of the seed.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub struct GeneratedLogo {
    pub initials: String,
    pub color: String,
    pub shape: LogoShape,
}

fn seed_digest(seed: &str) -> [u8; 32] {
    Sha256::digest(seed.as_bytes()).into()
}

pub fn generated_logo(seed: &str) -> GeneratedLogo {
    let digest = seed_digest(seed);
    let mut initials: String = seed
        .split_whitespace()
        .filter_map(|word| word.chars().find(|c| c.is_alphanumeric()))
        .take(2)
        .flat_map(char::to_uppercase)
        .collect();
    if initials.is_empty() {
        initials.push('?');
    }
    GeneratedLogo {
        initials,
        color: PALETTE[usize::from(digest[0]) % PALETTE.len()].to_owned(),
        shape: match digest[1] % 3 {
            0 => LogoShape::Circle,
            1 => LogoShape::Square,
            _ => LogoShape::Hexagon,
        },
    }
}

fn parse_hex(color: &str) -> Option<[u8; 3]> {
    let hex = color.strip_prefix('#')?;
    if hex.len() != 6
        || !hex
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'A'..=b'F').contains(&b))
    {
        return None;
    }
    let value = u32::from_str_radix(hex, 16).ok()?;
    Some([(value >> 16) as u8, (value >> 8) as u8, value as u8])
}

fn luminance(rgb: [u8; 3]) -> f64 {
    let channel = |value: u8| {
        let c = f64::from(value) / 255.0;
        if c <= 0.03928 {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).powf(2.4)
        }
    };
    0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2])
}

/// WCAG contrast ratio between two `#RRGGBB` colours.
pub fn contrast_ratio(a: &str, b: &str) -> Result<f64, WorkspaceError> {
    let parse =
        |c: &str| parse_hex(c).ok_or_else(|| error("colour must be #RRGGBB (uppercase hex)"));
    let (la, lb) = (luminance(parse(a)?), luminance(parse(b)?));
    let (hi, lo) = if la >= lb { (la, lb) } else { (lb, la) };
    Ok((hi + 0.05) / (lo + 0.05))
}

/// `#FFFFFF` or `#000000`, whichever is more readable on the accent.
pub fn on_accent(accent: &str) -> Result<&'static str, WorkspaceError> {
    Ok(
        if contrast_ratio(accent, "#FFFFFF")? >= contrast_ratio(accent, "#000000")? {
            "#FFFFFF"
        } else {
            "#000000"
        },
    )
}

fn validate_name(name: &str) -> Result<(), WorkspaceError> {
    let count = name.chars().count();
    if name.trim() != name
        || count == 0
        || count > MAX_NAME_CHARS
        || name.chars().any(char::is_control)
    {
        return Err(error(format!(
            "project name must be 1-{MAX_NAME_CHARS} characters, without control characters or surrounding spaces"
        )));
    }
    Ok(())
}

fn is_sha256_hex(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}

fn logo_path(sha256: &str) -> String {
    format!("branding/logos/{sha256}.png")
}

/// Validates PNG structure and bounds only; the image is not decoded or re-encoded.
pub fn validate_png(bytes: &[u8]) -> Result<(u32, u32), WorkspaceError> {
    const SIGNATURE: [u8; 8] = [0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
    const IEND: [u8; 12] = [0, 0, 0, 0, b'I', b'E', b'N', b'D', 0xAE, 0x42, 0x60, 0x82];
    if bytes.len() > MAX_LOGO_BYTES {
        return Err(error(format!(
            "logo must be at most {MAX_LOGO_BYTES} bytes"
        )));
    }
    if bytes.len() < 33 + IEND.len()
        || bytes[..8] != SIGNATURE
        || bytes[12..16] != *b"IHDR"
        || bytes[8..12] != [0, 0, 0, 13]
        || !bytes.ends_with(&IEND)
    {
        return Err(error("logo must be a complete PNG image"));
    }
    let dimension = |at: usize| u32::from_be_bytes(bytes[at..at + 4].try_into().expect("4 bytes"));
    let (width, height) = (dimension(16), dimension(20));
    if !(MIN_LOGO_PIXELS..=MAX_LOGO_PIXELS).contains(&width)
        || !(MIN_LOGO_PIXELS..=MAX_LOGO_PIXELS).contains(&height)
    {
        return Err(error(format!(
            "logo dimensions must be {MIN_LOGO_PIXELS}-{MAX_LOGO_PIXELS} pixels per side"
        )));
    }
    Ok((width, height))
}

impl Branding {
    pub fn new(name: &str, accent: &str, logo: Logo) -> Result<Self, WorkspaceError> {
        let branding = Self {
            schema_version: BRANDING_SCHEMA_VERSION,
            name: name.to_owned(),
            accent: accent.to_owned(),
            logo,
        };
        branding.validate()?;
        Ok(branding)
    }

    /// Default identity for a new project: generated logo and accent chosen by the name.
    pub fn default_for(name: &str) -> Result<Self, WorkspaceError> {
        validate_name(name)?;
        let generated = generated_logo(name);
        Self::new(
            name,
            &generated.color,
            Logo::Generated {
                seed: name.to_owned(),
            },
        )
    }

    pub fn validate(&self) -> Result<(), WorkspaceError> {
        if self.schema_version != BRANDING_SCHEMA_VERSION {
            return Err(error(format!(
                "unsupported branding schema version {}",
                self.schema_version
            )));
        }
        validate_name(&self.name)?;
        if parse_hex(&self.accent).is_none() {
            return Err(error("accent must be #RRGGBB (uppercase hex)"));
        }
        match &self.logo {
            Logo::Generated { seed } => {
                if seed.is_empty()
                    || seed.chars().count() > MAX_NAME_CHARS
                    || seed.chars().any(char::is_control)
                {
                    return Err(error(
                        "logo seed must be 1-60 characters without control characters",
                    ));
                }
            }
            Logo::Png { sha256 } => {
                if !is_sha256_hex(sha256) {
                    return Err(error("logo hash must be 64 lowercase hex characters"));
                }
            }
        }
        Ok(())
    }

    pub fn canonical_bytes(&self) -> Result<Vec<u8>, WorkspaceError> {
        self.validate()?;
        let mut bytes = serde_json::to_vec_pretty(self).map_err(|e| error(e.to_string()))?;
        bytes.push(b'\n');
        Ok(bytes)
    }

    /// Strict decoding: unknown fields, other versions and non-canonical bytes are rejected.
    pub fn decode(bytes: &[u8]) -> Result<Self, WorkspaceError> {
        let branding: Self =
            serde_json::from_slice(bytes).map_err(|e| error(format!("invalid branding: {e}")))?;
        if branding.canonical_bytes()? != bytes {
            return Err(error("branding is not in canonical form"));
        }
        Ok(branding)
    }

    pub fn content_hash(&self) -> Result<String, WorkspaceError> {
        Ok(format!(
            "sha256:{:x}",
            Sha256::digest(self.canonical_bytes()?)
        ))
    }

    /// Non-blocking accessibility findings: accent contrast needs 3:1 on the theme surface.
    pub fn contrast_warnings(&self) -> Vec<String> {
        [("light", LIGHT_SURFACE), ("dark", DARK_SURFACE)]
            .into_iter()
            .filter_map(|(theme, surface)| {
                let ratio = contrast_ratio(&self.accent, surface).ok()?;
                (ratio < 3.0).then(|| {
                    format!("accent {} has {ratio:.2}:1 contrast on the {theme} theme (3:1 recommended)", self.accent)
                })
            })
            .collect()
    }
}

/// The branding currently in effect for a workspace.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ProjectBranding {
    pub branding: Branding,
    /// False when no `project.branding_set` event exists and the defaults are derived.
    pub persisted: bool,
    pub content_hash: Option<String>,
}

pub struct BrandingStore<'a> {
    workspace: &'a Workspace,
}

impl<'a> BrandingStore<'a> {
    pub fn new(workspace: &'a Workspace) -> Self {
        Self { workspace }
    }

    fn default_name(&self) -> String {
        self.workspace
            .path()
            .file_name()
            .map(|n| {
                n.to_string_lossy()
                    .trim()
                    .chars()
                    .take(MAX_NAME_CHARS)
                    .collect::<String>()
            })
            .filter(|n| !n.trim().is_empty())
            .unwrap_or_else(|| "Project".to_owned())
    }

    /// Verifies the journal and that the branding file still matches its latest event.
    pub fn show(&self) -> Result<ProjectBranding, WorkspaceError> {
        let records = self.workspace.verify()?;
        let Some(event) = records.iter().rev().find(|r| r.event_type == EVENT_TYPE) else {
            let name = self.default_name();
            let branding =
                Branding::default_for(&name).or_else(|_| Branding::default_for("Project"))?;
            return Ok(ProjectBranding {
                branding,
                persisted: false,
                content_hash: None,
            });
        };
        let bytes = self.workspace.read_canonical_file(BRANDING_PATH)?;
        let branding = Branding::decode(&bytes)?;
        let hash = branding.content_hash()?;
        if event.content_hashes.get(BRANDING_PATH) != Some(&hash) {
            return Err(error("branding differs from its latest journal event"));
        }
        if let Logo::Png { sha256 } = &branding.logo {
            let logo = self.workspace.read_canonical_file(&logo_path(sha256))?;
            if format!("{:x}", Sha256::digest(&logo)) != *sha256 {
                return Err(error("logo file does not match its recorded hash"));
            }
        }
        Ok(ProjectBranding {
            branding,
            persisted: true,
            content_hash: Some(hash),
        })
    }

    pub fn read_logo(&self, branding: &Branding) -> Result<Option<Vec<u8>>, WorkspaceError> {
        match &branding.logo {
            Logo::Generated { .. } => Ok(None),
            Logo::Png { sha256 } => self
                .workspace
                .read_canonical_file(&logo_path(sha256))
                .map(Some),
        }
    }

    /// Persists branding (and an uploaded PNG, when the logo is `Png`) as one journaled event.
    pub fn set(
        &self,
        branding: &Branding,
        png: Option<&[u8]>,
    ) -> Result<ProjectBranding, WorkspaceError> {
        branding.validate()?;
        let mut hashes = BTreeMap::new();
        match (&branding.logo, png) {
            (Logo::Png { sha256 }, Some(bytes)) => {
                validate_png(bytes)?;
                if format!("{:x}", Sha256::digest(bytes)) != *sha256 {
                    return Err(error("logo bytes do not match the logo hash"));
                }
                let path = logo_path(sha256);
                self.workspace.write_tracked_file(&path, bytes)?;
                hashes.insert(path, format!("sha256:{sha256}"));
            }
            (Logo::Png { sha256 }, None) => {
                // Re-selecting an existing logo is allowed only when it is already stored.
                let existing = self.workspace.read_canonical_file(&logo_path(sha256))?;
                validate_png(&existing)?;
                hashes.insert(logo_path(sha256), format!("sha256:{sha256}"));
            }
            (Logo::Generated { .. }, Some(_)) => {
                return Err(error("a PNG was supplied for a generated logo"));
            }
            (Logo::Generated { .. }, None) => {}
        }
        let hash = branding.content_hash()?;
        self.workspace
            .write_tracked_file(BRANDING_PATH, &branding.canonical_bytes()?)?;
        hashes.insert(BRANDING_PATH.to_owned(), hash.clone());
        let payload = BTreeMap::from([("name".to_owned(), branding.name.clone())]);
        self.workspace.record_event(EVENT_TYPE, payload, hashes)?;
        Ok(ProjectBranding {
            branding: branding.clone(),
            persisted: true,
            content_hash: Some(hash),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use proptest::prelude::*;

    fn png(width: u32, height: u32) -> Vec<u8> {
        let mut bytes = vec![0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13];
        bytes.extend(b"IHDR");
        bytes.extend(width.to_be_bytes());
        bytes.extend(height.to_be_bytes());
        bytes.extend([8, 6, 0, 0, 0, 0, 0, 0, 0]);
        bytes.extend([0, 0, 0, 0, b'I', b'E', b'N', b'D', 0xAE, 0x42, 0x60, 0x82]);
        bytes
    }

    fn temp_workspace(tag: &str) -> (std::path::PathBuf, Workspace) {
        let dir = std::env::temp_dir().join(format!("eqms-brand-{tag}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir(&dir).unwrap();
        for args in [
            &["init", "--quiet"][..],
            &["config", "user.name", "Branding Test"],
            &["config", "user.email", "branding@example.invalid"],
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
        let workspace = Workspace::initialize(&dir).unwrap();
        (dir, workspace)
    }

    #[test]
    fn store_round_trips_journals_and_detects_tampering() {
        let (dir, workspace) = temp_workspace("store");
        let store = BrandingStore::new(&workspace);
        let default = store.show().unwrap();
        assert!(!default.persisted);
        assert!(!dir.join(BRANDING_PATH).exists());

        let bytes = png(64, 64);
        let sha = format!("{:x}", Sha256::digest(&bytes));
        let branding = Branding::new(
            "Acme",
            "#112233",
            Logo::Png {
                sha256: sha.clone(),
            },
        )
        .unwrap();
        let saved = store.set(&branding, Some(&bytes)).unwrap();
        assert!(saved.persisted);
        assert_eq!(store.show().unwrap().branding, branding);
        assert_eq!(store.read_logo(&branding).unwrap(), Some(bytes));
        assert!(workspace.verify().is_ok());
        let journal = std::fs::read_to_string(dir.join(".eqms/journal.jsonl")).unwrap();
        assert!(journal.contains("project.branding_set"));

        let other = Branding::new("Other", "#112233", branding.logo.clone()).unwrap();
        std::fs::write(dir.join(BRANDING_PATH), other.canonical_bytes().unwrap()).unwrap();
        assert!(store.show().is_err());
        std::fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn default_branding_is_deterministic_and_name_seeded() {
        let a = Branding::default_for("Acme Devices").unwrap();
        assert_eq!(a, Branding::default_for("Acme Devices").unwrap());
        assert_eq!(generated_logo("Acme Devices").initials, "AD");
        assert_eq!(generated_logo("  ").initials, "?");
        assert_eq!(generated_logo("zeta").initials, "Z");
        assert_eq!(a.accent, generated_logo("Acme Devices").color);
        let names = [
            "Acme", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta",
        ];
        let accents: std::collections::BTreeSet<_> = names
            .iter()
            .map(|n| Branding::default_for(n).unwrap().accent)
            .collect();
        assert!(accents.len() > 1, "defaults should vary with the name");
    }

    #[test]
    fn every_palette_accent_is_readable() {
        for color in PALETTE {
            let light = contrast_ratio(color, LIGHT_SURFACE).unwrap();
            let dark = contrast_ratio(color, DARK_SURFACE).unwrap();
            assert!(light >= 3.0 || dark >= 3.0, "{color}");
            let text = contrast_ratio(color, on_accent(color).unwrap()).unwrap();
            assert!(text >= 4.5, "{color} {text}");
        }
    }

    #[test]
    fn contrast_math_and_warnings() {
        assert!((contrast_ratio("#000000", "#FFFFFF").unwrap() - 21.0).abs() < 1e-9);
        assert_eq!(on_accent("#FFFF00").unwrap(), "#000000");
        let weak = Branding::new("P", "#FFFF00", Logo::Generated { seed: "P".into() }).unwrap();
        assert_eq!(weak.contrast_warnings().len(), 1);
        let ok = Branding::new("P", "#808080", Logo::Generated { seed: "P".into() }).unwrap();
        assert!(ok.contrast_warnings().is_empty());
        assert!(contrast_ratio("#fff", "#000000").is_err());
    }

    #[test]
    fn validation_rejects_bad_input() {
        let logo = || Logo::Generated { seed: "P".into() };
        for name in ["", " x", "x ", "a\nb", &"n".repeat(61)] {
            assert!(Branding::new(name, "#1D4ED8", logo()).is_err(), "{name:?}");
        }
        assert!(Branding::new(&"n".repeat(60), "#1D4ED8", logo()).is_ok());
        for accent in ["1D4ED8", "#1d4ed8", "#12345", "#GGGGGG", "red"] {
            assert!(Branding::new("P", accent, logo()).is_err(), "{accent}");
        }
        assert!(
            Branding::new(
                "P",
                "#1D4ED8",
                Logo::Png {
                    sha256: "ABC".into()
                }
            )
            .is_err()
        );
        assert!(
            Branding::new(
                "P",
                "#1D4ED8",
                Logo::Generated {
                    seed: String::new()
                }
            )
            .is_err()
        );
    }

    #[test]
    fn canonical_encoding_is_strict() {
        let branding = Branding::default_for("Acme").unwrap();
        let bytes = branding.canonical_bytes().unwrap();
        assert_eq!(Branding::decode(&bytes).unwrap(), branding);
        let text = String::from_utf8(bytes.clone()).unwrap();
        assert!(Branding::decode(text.replace("Acme\"", "Acme\" ").as_bytes()).is_err());
        assert!(
            Branding::decode(
                text.replace("\"schema_version\": 1", "\"schema_version\": 2")
                    .as_bytes()
            )
            .is_err()
        );
        assert!(
            Branding::decode(
                text.replace("\"name\"", "\"extra\": 1,\n  \"name\"")
                    .as_bytes()
            )
            .is_err()
        );
        assert!(Branding::decode(&bytes[..bytes.len() - 1]).is_err());
    }

    #[test]
    fn png_validation_checks_structure_size_and_dimensions() {
        assert_eq!(validate_png(&png(64, 32)).unwrap(), (64, 32));
        assert!(validate_png(&png(15, 32)).is_err());
        assert!(validate_png(&png(64, 1025)).is_err());
        assert!(validate_png(b"<svg onload=alert(1)/>").is_err());
        let mut truncated = png(64, 64);
        truncated.truncate(truncated.len() - 1);
        assert!(validate_png(&truncated).is_err());
        let mut huge = png(64, 64);
        huge.splice(33..33, vec![0; MAX_LOGO_BYTES]);
        assert!(validate_png(&huge).is_err());
    }

    proptest! {
        #[test]
        fn generated_logos_are_pure_functions_of_the_seed(seed in ".{0,40}") {
            prop_assert_eq!(generated_logo(&seed), generated_logo(&seed));
            let logo = generated_logo(&seed);
            prop_assert!(PALETTE.contains(&logo.color.as_str()));
            prop_assert!(!logo.initials.is_empty() && logo.initials.chars().count() <= 4);
        }

        #[test]
        fn valid_branding_roundtrips(name in "[A-Za-z0-9][A-Za-z0-9 ]{0,30}[A-Za-z0-9]") {
            let branding = Branding::default_for(&name).unwrap();
            prop_assert_eq!(Branding::decode(&branding.canonical_bytes().unwrap()).unwrap(), branding);
        }
    }
}
