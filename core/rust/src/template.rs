//! Document template model, schema v1.
//!
//! A template is a versioned, hashed, Git-tracked project asset for one document type. It has
//! two independent parts: `structure` (what content a document of this type must contain) and
//! `style` (how it is rendered). Style is a closed declarative schema, deliberately not CSS and
//! not executable, so every renderer produces the same result. Templates support, and never
//! certify, compliance with any standard.

use crate::markdown::{Orientation, PageSize};
use crate::workspace::{Workspace, WorkspaceError, error};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;

pub const TEMPLATE_SCHEMA_VERSION: u32 = 1;
pub const STYLE_SCHEMA_VERSION: u32 = 1;
pub const MAX_SECTIONS: usize = 60;
pub const MAX_PLACEHOLDER_BODY_CHARS: usize = 8_000;
pub const MAX_SLOT_CHARS: usize = 120;
pub const MAX_ASSETS: usize = 8;
const EVENT_TYPE: &str = "template.installed";
const PLACEHOLDERS: [&str; 7] = [
    "{title}",
    "{page}",
    "{total}",
    "{id}",
    "{revision}",
    "{status}",
    "{project}",
];

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Block {
    Paragraph,
    List,
    Table,
    Image,
    Code,
    Quote,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Section {
    pub title: String,
    pub level: u8,
    pub required: bool,
    /// Authoring guidance shown with the scaffold; never part of conformance.
    pub guidance: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Structure {
    pub sections: Vec<Section>,
    pub allowed_blocks: Vec<Block>,
    /// Markdown scaffold for new documents; validated as a bounded string only here.
    pub body_placeholder: String,
}

/// Bundled font families only; controlled output never depends on system fonts.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Family {
    Sans,
    Serif,
    Mono,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TableStyle {
    Plain,
    Grid,
    Banded,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Align {
    Left,
    Center,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TocPlacement {
    Cover,
    AfterCover,
    Start,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Toc {
    pub enabled: bool,
    pub depth: u8,
    pub leaders: bool,
    pub placement: TocPlacement,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Style {
    pub body_font: Family,
    pub heading_font: Family,
    pub body_size_pt: u8,
    pub heading_numbering: bool,
    /// `accent` (the project accent token) or `#RRGGBB`.
    pub heading_color: String,
    pub table: TableStyle,
    pub image_max_width_percent: u8,
    pub image_align: Align,
    pub toc: Toc,
    pub cover_page: bool,
    /// Start a new page before headings up to this level.
    pub page_break_before_level: Option<u8>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Page {
    pub size: PageSize,
    pub orientation: Orientation,
    /// Margins in tenths of a millimetre: top, right, bottom, left (50..=500).
    pub margins_tenths_mm: [u16; 4],
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case", deny_unknown_fields)]
pub enum Image {
    /// The project's branding logo, resolved at render time.
    ProjectLogo,
    /// A template asset stored content-addressed at `templates/assets/<sha256>.png`.
    Asset { sha256: String },
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Slot {
    pub text: String,
    pub image: Option<Image>,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Region {
    pub left: Slot,
    pub centre: Slot,
    pub right: Slot,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Regions {
    pub header: Region,
    pub footer: Region,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Template {
    pub schema_version: u32,
    pub style_schema_version: u32,
    /// Lowercase slug, e.g. `sop`; unique per project together with `version`.
    pub id: String,
    pub version: u32,
    pub name: String,
    pub document_type: String,
    pub structure: Structure,
    pub style: Style,
    pub page: Option<Page>,
    pub regions: Regions,
}

fn no_controls(name: &str, value: &str, min: usize, max: usize) -> Result<(), WorkspaceError> {
    let count = value.chars().count();
    if count < min || count > max || value.trim() != value || value.chars().any(char::is_control) {
        return Err(error(format!(
            "{name} must be {min}-{max} characters without control characters or surrounding spaces"
        )));
    }
    Ok(())
}

fn validate_placeholders(name: &str, text: &str) -> Result<(), WorkspaceError> {
    if text.chars().count() > MAX_SLOT_CHARS || text.chars().any(char::is_control) {
        return Err(error(format!(
            "{name} must be at most {MAX_SLOT_CHARS} characters without control characters"
        )));
    }
    let mut rest = text.to_owned();
    for placeholder in PLACEHOLDERS {
        rest = rest.replace(placeholder, "");
    }
    if rest.contains(['{', '}']) {
        return Err(error(format!(
            "{name} may only use the placeholders {}",
            PLACEHOLDERS.join(", ")
        )));
    }
    Ok(())
}

fn is_sha256(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
}

fn validate_color(name: &str, value: &str) -> Result<(), WorkspaceError> {
    let hex = value.len() == 7
        && value.starts_with('#')
        && value[1..]
            .bytes()
            .all(|b| matches!(b, b'0'..=b'9' | b'A'..=b'F'));
    if value != "accent" && !hex {
        return Err(error(format!(
            "{name} must be `accent` or #RRGGBB (uppercase hex)"
        )));
    }
    Ok(())
}

fn validate_slot(name: &str, slot: &Slot) -> Result<(), WorkspaceError> {
    validate_placeholders(name, &slot.text)?;
    if let Some(Image::Asset { sha256 }) = &slot.image
        && !is_sha256(sha256)
    {
        return Err(error(format!("{name} image hash must be 64 lowercase hex")));
    }
    Ok(())
}

impl Template {
    /// Every asset hash referenced by header/footer slots, sorted and de-duplicated.
    pub fn asset_hashes(&self) -> Vec<String> {
        let mut hashes: Vec<String> = [&self.regions.header, &self.regions.footer]
            .into_iter()
            .flat_map(|region| [&region.left, &region.centre, &region.right])
            .filter_map(|slot| match &slot.image {
                Some(Image::Asset { sha256 }) => Some(sha256.clone()),
                _ => None,
            })
            .collect();
        hashes.sort();
        hashes.dedup();
        hashes
    }

    pub fn validate(&self) -> Result<(), WorkspaceError> {
        if self.schema_version != TEMPLATE_SCHEMA_VERSION
            || self.style_schema_version != STYLE_SCHEMA_VERSION
        {
            return Err(error("unsupported template or style schema version"));
        }
        let id_ok = (1..=40).contains(&self.id.len())
            && self.id.starts_with(|c: char| c.is_ascii_lowercase())
            && self
                .id
                .bytes()
                .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-')
            && !self.id.ends_with('-');
        if !id_ok {
            return Err(error(
                "template id must be 1-40 lowercase letters, digits or hyphens, starting with a letter",
            ));
        }
        if self.version == 0 {
            return Err(error("template version must be at least 1"));
        }
        no_controls("template name", &self.name, 1, 80)?;
        no_controls("document type", &self.document_type, 1, 40)?;
        self.validate_structure()?;
        self.validate_style()?;
        if let Some(page) = &self.page
            && !page
                .margins_tenths_mm
                .iter()
                .all(|m| (50..=500).contains(m))
        {
            return Err(error("page margins must be 5.0-50.0 mm"));
        }
        for (region, name) in [
            (&self.regions.header, "header"),
            (&self.regions.footer, "footer"),
        ] {
            for (slot, side) in [
                (&region.left, "left"),
                (&region.centre, "centre"),
                (&region.right, "right"),
            ] {
                validate_slot(&format!("{name} {side}"), slot)?;
            }
        }
        if self.asset_hashes().len() > MAX_ASSETS {
            return Err(error(format!("at most {MAX_ASSETS} distinct assets")));
        }
        Ok(())
    }

    fn validate_structure(&self) -> Result<(), WorkspaceError> {
        let structure = &self.structure;
        if structure.sections.len() > MAX_SECTIONS {
            return Err(error(format!("at most {MAX_SECTIONS} sections")));
        }
        let mut seen = Vec::new();
        for section in &structure.sections {
            no_controls("section title", &section.title, 1, 120)?;
            if !(1..=6).contains(&section.level) {
                return Err(error("section level must be 1-6"));
            }
            if section.guidance.chars().count() > 500
                || section
                    .guidance
                    .chars()
                    .any(|c| c.is_control() && c != '\n')
            {
                return Err(error("section guidance must be at most 500 characters"));
            }
            if seen.contains(&&section.title) {
                return Err(error(format!(
                    "duplicate section title `{}`",
                    section.title
                )));
            }
            seen.push(&section.title);
        }
        let mut blocks = structure.allowed_blocks.clone();
        blocks.sort_by_key(|b| *b as u8);
        blocks.dedup();
        if blocks.len() != structure.allowed_blocks.len() || blocks.is_empty() {
            return Err(error(
                "allowed blocks must be a non-empty set without duplicates",
            ));
        }
        let body = &structure.body_placeholder;
        if body.chars().count() > MAX_PLACEHOLDER_BODY_CHARS
            || body.chars().any(|c| c.is_control() && c != '\n')
            || body.contains('\r')
        {
            return Err(error(format!(
                "body placeholder must be at most {MAX_PLACEHOLDER_BODY_CHARS} characters, LF only"
            )));
        }
        Ok(())
    }

    fn validate_style(&self) -> Result<(), WorkspaceError> {
        let style = &self.style;
        if !(8..=16).contains(&style.body_size_pt) {
            return Err(error("body size must be 8-16 pt"));
        }
        validate_color("heading colour", &style.heading_color)?;
        if !(10..=100).contains(&style.image_max_width_percent) {
            return Err(error("image width must be 10-100 percent"));
        }
        if !(1..=6).contains(&style.toc.depth) {
            return Err(error("table of contents depth must be 1-6"));
        }
        if style
            .page_break_before_level
            .is_some_and(|l| !(1..=6).contains(&l))
        {
            return Err(error("page break level must be 1-6"));
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
        let template: Self =
            serde_json::from_slice(bytes).map_err(|e| error(format!("invalid template: {e}")))?;
        if template.canonical_bytes()? != bytes {
            return Err(error("template is not in canonical form"));
        }
        Ok(template)
    }

    pub fn content_hash(&self) -> Result<String, WorkspaceError> {
        Ok(format!(
            "sha256:{:x}",
            Sha256::digest(self.canonical_bytes()?)
        ))
    }

    /// The built-in generic template; project templates may override it by id.
    pub fn builtin_generic() -> Self {
        let section = |title: &str, guidance: &str| Section {
            title: title.to_owned(),
            level: 2,
            required: true,
            guidance: guidance.to_owned(),
        };
        Self {
            schema_version: TEMPLATE_SCHEMA_VERSION,
            style_schema_version: STYLE_SCHEMA_VERSION,
            id: "generic".to_owned(),
            version: 1,
            name: "Generic controlled document".to_owned(),
            document_type: "Document".to_owned(),
            structure: Structure {
                sections: vec![
                    section("Purpose", "Why this document exists."),
                    section("Scope", "What it covers and what it does not."),
                    section("Content", "The controlled content."),
                ],
                allowed_blocks: vec![
                    Block::Paragraph,
                    Block::List,
                    Block::Table,
                    Block::Image,
                    Block::Code,
                    Block::Quote,
                ],
                body_placeholder: "## Purpose\n\n## Scope\n\n## Content\n".to_owned(),
            },
            style: Style {
                body_font: Family::Sans,
                heading_font: Family::Sans,
                body_size_pt: 11,
                heading_numbering: false,
                heading_color: "accent".to_owned(),
                table: TableStyle::Grid,
                image_max_width_percent: 100,
                image_align: Align::Center,
                toc: Toc {
                    enabled: false,
                    depth: 3,
                    leaders: true,
                    placement: TocPlacement::Start,
                },
                cover_page: false,
                page_break_before_level: None,
            },
            page: None,
            regions: Regions {
                header: Region {
                    left: Slot {
                        text: "{project}".to_owned(),
                        image: None,
                    },
                    centre: Slot::default(),
                    right: Slot {
                        text: "{id} rev {revision}".to_owned(),
                        image: None,
                    },
                },
                footer: Region {
                    left: Slot {
                        text: "{title}".to_owned(),
                        image: None,
                    },
                    centre: Slot::default(),
                    right: Slot {
                        text: "Page {page} of {total}".to_owned(),
                        image: None,
                    },
                },
            },
        }
    }
}

/// Where a template is read from.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Source {
    Builtin,
    Project,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub struct StoredTemplate {
    pub template: Template,
    pub source: Source,
    pub content_hash: String,
}

fn template_path(id: &str, version: u32) -> String {
    format!("templates/{id}/v{version}.json")
}

fn asset_path(sha256: &str) -> String {
    format!("templates/assets/{sha256}.png")
}

pub struct TemplateStore<'a> {
    workspace: &'a Workspace,
}

impl<'a> TemplateStore<'a> {
    pub fn new(workspace: &'a Workspace) -> Self {
        Self { workspace }
    }

    /// Project templates that are journaled and still match their event, plus built-ins that no
    /// project template with the same id and version replaces.
    pub fn list(&self) -> Result<Vec<StoredTemplate>, WorkspaceError> {
        let records = self.workspace.verify()?;
        let mut found: BTreeMap<(String, u32), StoredTemplate> = BTreeMap::new();
        let builtin = Template::builtin_generic();
        found.insert(
            (builtin.id.clone(), builtin.version),
            StoredTemplate {
                content_hash: builtin.content_hash()?,
                template: builtin,
                source: Source::Builtin,
            },
        );
        for event in records.iter().filter(|r| r.event_type == EVENT_TYPE) {
            for (path, hash) in &event.content_hashes {
                if !path.ends_with(".json") {
                    continue;
                }
                let template = Template::decode(&self.workspace.read_canonical_file(path)?)?;
                if template_path(&template.id, template.version) != *path
                    || template.content_hash()? != *hash
                {
                    return Err(error(format!("{path} differs from its journal event")));
                }
                for asset in template.asset_hashes() {
                    let bytes = self.workspace.read_canonical_file(&asset_path(&asset))?;
                    if format!("{:x}", Sha256::digest(&bytes)) != asset {
                        return Err(error("template asset does not match its recorded hash"));
                    }
                }
                found.insert(
                    (template.id.clone(), template.version),
                    StoredTemplate {
                        content_hash: hash.clone(),
                        template,
                        source: Source::Project,
                    },
                );
            }
        }
        Ok(found.into_values().collect())
    }

    /// Latest version of the template with this id.
    pub fn show(&self, id: &str) -> Result<StoredTemplate, WorkspaceError> {
        self.list()?
            .into_iter()
            .filter(|stored| stored.template.id == id)
            .max_by_key(|stored| stored.template.version)
            .ok_or_else(|| error(format!("unknown template `{id}`")))
    }

    /// Installs a new immutable template version as one journaled event. Existing versions are
    /// never overwritten, so documents pinned to them keep rendering identically.
    pub fn install(
        &self,
        template: &Template,
        assets: &BTreeMap<String, Vec<u8>>,
    ) -> Result<StoredTemplate, WorkspaceError> {
        template.validate()?;
        let path = template_path(&template.id, template.version);
        if self.list()?.iter().any(|stored| {
            stored.template.id == template.id && stored.template.version >= template.version
        }) {
            return Err(error(format!(
                "template `{}` already has version {} or newer",
                template.id, template.version
            )));
        }
        let wanted = template.asset_hashes();
        if assets.keys().any(|hash| !wanted.contains(hash)) {
            return Err(error(
                "an asset was supplied that the template does not use",
            ));
        }
        let mut hashes = BTreeMap::new();
        for hash in &wanted {
            let bytes = assets
                .get(hash)
                .ok_or_else(|| error(format!("asset {hash} was not supplied")))?;
            crate::branding::validate_png(bytes)?;
            if format!("{:x}", Sha256::digest(bytes)) != *hash {
                return Err(error("asset bytes do not match their hash"));
            }
            self.workspace
                .write_tracked_file(&asset_path(hash), bytes)?;
            hashes.insert(asset_path(hash), format!("sha256:{hash}"));
        }
        let content_hash = template.content_hash()?;
        self.workspace
            .write_tracked_file(&path, &template.canonical_bytes()?)?;
        hashes.insert(path, content_hash.clone());
        let payload = BTreeMap::from([
            ("id".to_owned(), template.id.clone()),
            ("version".to_owned(), template.version.to_string()),
        ]);
        self.workspace.record_event(EVENT_TYPE, payload, hashes)?;
        Ok(StoredTemplate {
            template: template.clone(),
            source: Source::Project,
            content_hash,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use proptest::prelude::*;

    fn png() -> Vec<u8> {
        let mut bytes = vec![0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13];
        bytes.extend(b"IHDR");
        bytes.extend(64u32.to_be_bytes());
        bytes.extend(64u32.to_be_bytes());
        bytes.extend([8, 6, 0, 0, 0, 0, 0, 0, 0]);
        bytes.extend([0, 0, 0, 0, b'I', b'E', b'N', b'D', 0xAE, 0x42, 0x60, 0x82]);
        bytes
    }

    fn workspace(tag: &str) -> (std::path::PathBuf, Workspace) {
        let dir = std::env::temp_dir().join(format!("eqms-tpl-{tag}-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir(&dir).unwrap();
        for args in [
            &["init", "--quiet"][..],
            &["config", "user.name", "Template Test"],
            &["config", "user.email", "template@example.invalid"],
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
    fn builtin_is_valid_canonical_and_hash_is_stable() {
        let t = Template::builtin_generic();
        let bytes = t.canonical_bytes().unwrap();
        assert_eq!(Template::decode(&bytes).unwrap(), t);
        assert_eq!(t.content_hash().unwrap(), t.content_hash().unwrap());
        assert!(bytes.ends_with(b"}\n"));
        let mut changed = t.clone();
        changed.version = 2;
        assert_ne!(changed.content_hash().unwrap(), t.content_hash().unwrap());
    }

    #[test]
    fn validation_rejects_each_bad_field() {
        let ok = Template::builtin_generic();
        type Mutation = Box<dyn Fn(&mut Template)>;
        let cases: Vec<Mutation> = vec![
            Box::new(|t| t.schema_version = 2),
            Box::new(|t| t.id = "Bad Id".into()),
            Box::new(|t| t.id = "trailing-".into()),
            Box::new(|t| t.version = 0),
            Box::new(|t| t.name = " padded ".into()),
            Box::new(|t| t.document_type = String::new()),
            Box::new(|t| t.structure.sections[0].level = 7),
            Box::new(|t| t.structure.sections[1].title = "Purpose".into()),
            Box::new(|t| t.structure.allowed_blocks = vec![]),
            Box::new(|t| t.structure.allowed_blocks = vec![Block::Table, Block::Table]),
            Box::new(|t| t.structure.body_placeholder = "a\r\nb".into()),
            Box::new(|t| t.style.body_size_pt = 30),
            Box::new(|t| t.style.heading_color = "#abcdef".into()),
            Box::new(|t| t.style.image_max_width_percent = 5),
            Box::new(|t| t.style.toc.depth = 0),
            Box::new(|t| t.style.page_break_before_level = Some(9)),
            Box::new(|t| t.regions.header.left.text = "{unknown}".into()),
            Box::new(|t| t.regions.footer.right.text = "x".repeat(121)),
            Box::new(|t| {
                t.regions.header.centre.image = Some(Image::Asset {
                    sha256: "zz".into(),
                })
            }),
            Box::new(|t| {
                t.page = Some(Page {
                    size: PageSize::A4,
                    orientation: Orientation::Portrait,
                    margins_tenths_mm: [10, 254, 254, 254],
                })
            }),
        ];
        for (index, mutate) in cases.iter().enumerate() {
            let mut t = ok.clone();
            mutate(&mut t);
            assert!(t.validate().is_err(), "case {index} should be rejected");
        }
    }

    #[test]
    fn decoding_is_strict() {
        let t = Template::builtin_generic();
        let text = String::from_utf8(t.canonical_bytes().unwrap()).unwrap();
        assert!(Template::decode(text.replace("  ", "\t").as_bytes()).is_err());
        let unknown = text.replacen('{', "{\n  \"extra\": 1,", 1);
        assert!(Template::decode(unknown.as_bytes()).is_err());
        assert!(Template::decode(b"{}").is_err());
    }

    #[test]
    fn store_installs_versions_immutably_with_assets_and_detects_tampering() {
        let (dir, workspace) = workspace("store");
        let store = TemplateStore::new(&workspace);
        assert_eq!(store.list().unwrap().len(), 1);
        assert_eq!(store.show("generic").unwrap().source, Source::Builtin);

        let bytes = png();
        let sha = format!("{:x}", Sha256::digest(&bytes));
        let mut t = Template::builtin_generic();
        t.id = "sop".into();
        t.regions.header.centre.image = Some(Image::Asset {
            sha256: sha.clone(),
        });
        assert!(store.install(&t, &BTreeMap::new()).is_err());
        let assets = BTreeMap::from([(sha.clone(), bytes.clone())]);
        let stored = store.install(&t, &assets).unwrap();
        assert_eq!(stored.source, Source::Project);
        assert_eq!(store.show("sop").unwrap().content_hash, stored.content_hash);
        assert!(workspace.verify().is_ok());
        assert!(
            store.install(&t, &assets).is_err(),
            "versions are immutable"
        );
        t.version = 2;
        assert!(store.install(&t, &assets).is_ok());
        assert_eq!(store.show("sop").unwrap().template.version, 2);
        assert!(
            store
                .install(&t, &BTreeMap::from([("0".repeat(64), bytes)]))
                .is_err()
        );

        let path = dir.join("templates/sop/v1.json");
        std::fs::write(
            &path,
            Template::builtin_generic().canonical_bytes().unwrap(),
        )
        .unwrap();
        assert!(store.list().is_err());
        std::fs::remove_dir_all(&dir).unwrap();
    }

    proptest! {
        #[test]
        fn valid_templates_round_trip(
            name in "[A-Za-z0-9][A-Za-z0-9 ]{0,30}[A-Za-z0-9]",
            size in 8u8..=16,
            depth in 1u8..=6,
            version in 1u32..1000,
        ) {
            let mut t = Template::builtin_generic();
            t.name = name;
            t.style.body_size_pt = size;
            t.style.toc.depth = depth;
            t.version = version;
            prop_assert_eq!(Template::decode(&t.canonical_bytes().unwrap()).unwrap(), t);
        }

        #[test]
        fn arbitrary_bytes_never_panic(bytes in proptest::collection::vec(any::<u8>(), 0..200)) {
            let _ = Template::decode(&bytes);
        }
    }
}
