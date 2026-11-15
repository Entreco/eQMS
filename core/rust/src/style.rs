//! Template style engine (EQMS-115).
//!
//! Resolves a template's declarative `style` and `regions` into one closed [`StyleModel`] that
//! is carried in the render contract, so every renderer (desktop preview now, PDF/DOCX later)
//! produces the same result. The model is plain data: no CSS, no code, no remote resources.
//! Templates support, and never certify, compliance with any standard.

use crate::branding::{self, BrandingStore, Logo};
use crate::markdown::TemplateBinding;
use crate::render::Heading;
use crate::template::{
    Align, Family, Image, Region, Slot, Source, TableStyle, Template, TemplateStore, Toc,
};
use crate::workspace::{Workspace, WorkspaceError, error};
use base64::Engine;
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

pub const PROJECT_LOGO_KEY: &str = "project_logo";
/// WCAG AA contrast for normal text; below this a heading colour is flagged on white paper.
const MIN_HEADING_CONTRAST: f64 = 4.5;
const HEADING_SCALE: [f64; 6] = [1.8, 1.5, 1.3, 1.15, 1.05, 1.0];

/// Why this template styles the document.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Origin {
    /// The revision's own pin.
    Pinned,
    /// Explicitly chosen for a preview; the document is not changed.
    Selected,
    /// No pin: the built-in generic style, without regions.
    Default,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct StyleTemplate {
    pub id: String,
    pub name: String,
    pub version: u32,
    pub hash: String,
    pub source: Source,
    pub origin: Origin,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum Segment {
    Text { text: String },
    Page,
    Total,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct StyledSlot {
    pub segments: Vec<Segment>,
    /// Key into [`StyleModel::images`].
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub image: Option<String>,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct StyledRegion {
    pub left: StyledSlot,
    pub centre: StyledSlot,
    pub right: StyledSlot,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct StyledRegions {
    pub header: StyledRegion,
    pub footer: StyledRegion,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct StyleImage {
    pub width: u32,
    pub height: u32,
    pub png_base64: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct StyleModel {
    pub style_schema_version: u32,
    pub template: StyleTemplate,
    pub body_font: Family,
    pub heading_font: Family,
    pub body_size_pt: u8,
    /// Sizes of h1..h6 in points.
    pub heading_sizes_pt: [u8; 6],
    /// Always `#RRGGBB`; the `accent` token is resolved to the project accent.
    pub heading_color: String,
    pub heading_numbering: bool,
    pub table: TableStyle,
    pub image_max_width_percent: u8,
    pub image_align: Align,
    pub toc: Toc,
    pub cover_page: bool,
    pub page_break_before_level: Option<u8>,
    /// Present only when a template applies; `None` keeps the document's own header/footer.
    pub regions: Option<StyledRegions>,
    pub images: BTreeMap<String, StyleImage>,
    /// Heading anchor -> number, empty when numbering is off.
    pub numbers: BTreeMap<String, String>,
}

/// Document facts substituted into region text. `{page}` and `{total}` stay segments.
#[derive(Clone, Debug)]
pub struct DocumentFacts {
    pub title: String,
    pub id: String,
    pub revision: String,
    pub status: String,
}

impl DocumentFacts {
    /// Facts for a buffer that is not a saved record.
    pub fn unsaved(title: &str) -> Self {
        Self {
            title: title.to_owned(),
            id: "(not saved)".to_owned(),
            revision: "-".to_owned(),
            status: "Draft".to_owned(),
        }
    }
}

/// How the style is chosen.
#[derive(Clone, Copy, Debug)]
pub enum Selection<'a> {
    Default,
    /// The document's pin; an unresolvable or changed pin falls back to the default with a
    /// diagnostic.
    Pinned(&'a TemplateBinding),
    /// An explicit preview choice; failing to resolve it is an error.
    Chosen {
        id: &'a str,
        version: Option<u32>,
    },
}

/// Single-pass placeholder expansion: substituted values are never scanned again.
pub fn expand(text: &str, facts: &DocumentFacts, project: &str) -> Vec<Segment> {
    const NAMES: [&str; 7] = [
        "{title}",
        "{page}",
        "{total}",
        "{id}",
        "{revision}",
        "{status}",
        "{project}",
    ];
    let mut segments = Vec::new();
    let mut literal = String::new();
    let mut rest = text;
    let flush = |literal: &mut String, segments: &mut Vec<Segment>| {
        if !literal.is_empty() {
            segments.push(Segment::Text {
                text: std::mem::take(literal),
            });
        }
    };
    while let Some(ch) = rest.chars().next() {
        if let Some(name) = NAMES.iter().find(|name| rest.starts_with(**name)) {
            match *name {
                "{page}" | "{total}" => {
                    flush(&mut literal, &mut segments);
                    segments.push(if *name == "{page}" {
                        Segment::Page
                    } else {
                        Segment::Total
                    });
                }
                "{title}" => literal.push_str(if facts.title.trim().is_empty() {
                    "Untitled document"
                } else {
                    &facts.title
                }),
                "{id}" => literal.push_str(&facts.id),
                "{revision}" => literal.extend(facts.revision.chars().take(12)),
                "{status}" => literal.push_str(&facts.status),
                _ => literal.push_str(project),
            }
            rest = &rest[name.len()..];
        } else {
            literal.push(ch);
            rest = &rest[ch.len_utf8()..];
        }
    }
    flush(&mut literal, &mut segments);
    segments
}

/// Heading numbers: the shallowest level present is the root; a skipped level counts as `0`.
pub fn number_headings(headings: &[Heading]) -> BTreeMap<String, String> {
    let Some(root) = headings.iter().map(|heading| heading.level).min() else {
        return BTreeMap::new();
    };
    let mut counters: Vec<u32> = Vec::new();
    let mut numbers = BTreeMap::new();
    for heading in headings {
        let depth = usize::from(heading.level - root) + 1;
        counters.resize(depth, 0);
        counters[depth - 1] += 1;
        numbers.insert(
            heading.anchor.clone(),
            counters
                .iter()
                .map(u32::to_string)
                .collect::<Vec<_>>()
                .join("."),
        );
    }
    numbers
}

fn heading_sizes(body: u8) -> [u8; 6] {
    HEADING_SCALE.map(|factor| (f64::from(body) * factor).round() as u8)
}

fn styled_slot(
    slot: &Slot,
    facts: &DocumentFacts,
    project: &str,
    images: &BTreeMap<String, StyleImage>,
) -> StyledSlot {
    let image = slot.image.as_ref().and_then(|image| {
        let key = match image {
            Image::ProjectLogo => PROJECT_LOGO_KEY.to_owned(),
            Image::Asset { sha256 } => sha256.clone(),
        };
        images.contains_key(&key).then_some(key)
    });
    StyledSlot {
        segments: expand(&slot.text, facts, project),
        image,
    }
}

fn styled_region(
    region: &Region,
    facts: &DocumentFacts,
    project: &str,
    images: &BTreeMap<String, StyleImage>,
) -> StyledRegion {
    StyledRegion {
        left: styled_slot(&region.left, facts, project, images),
        centre: styled_slot(&region.centre, facts, project, images),
        right: styled_slot(&region.right, facts, project, images),
    }
}

/// Inputs that are already resolved against the project; [`build`] itself does no I/O.
pub struct BuildInput<'a> {
    pub template: &'a Template,
    pub hash: &'a str,
    pub source: Source,
    pub origin: Origin,
    pub accent: &'a str,
    pub project: &'a str,
    pub facts: &'a DocumentFacts,
    pub headings: &'a [Heading],
    pub images: BTreeMap<String, StyleImage>,
}

/// Pure style resolution. Returns the model and warning diagnostics.
pub fn build(input: BuildInput<'_>) -> Result<(StyleModel, Vec<String>), WorkspaceError> {
    let BuildInput {
        template,
        hash,
        source,
        origin,
        accent,
        project,
        facts,
        headings,
        images,
    } = input;
    let style = &template.style;
    let heading_color = if style.heading_color == "accent" {
        accent.to_owned()
    } else {
        style.heading_color.clone()
    };
    let mut diagnostics = Vec::new();
    let contrast = branding::contrast_ratio(&heading_color, "#FFFFFF")?;
    if contrast < MIN_HEADING_CONTRAST {
        diagnostics.push(format!(
            "Heading colour {heading_color} has low contrast on white paper ({contrast:.1}:1, below {MIN_HEADING_CONTRAST}:1)."
        ));
    }
    let regions = (origin != Origin::Default).then(|| StyledRegions {
        header: styled_region(&template.regions.header, facts, project, &images),
        footer: styled_region(&template.regions.footer, facts, project, &images),
    });
    let used: Vec<&String> = regions
        .iter()
        .flat_map(|r| [&r.header, &r.footer])
        .flat_map(|r| [&r.left, &r.centre, &r.right])
        .filter_map(|slot| slot.image.as_ref())
        .collect();
    let images = images
        .into_iter()
        .filter(|(key, _)| used.contains(&key))
        .collect();
    Ok((
        StyleModel {
            style_schema_version: template.style_schema_version,
            template: StyleTemplate {
                id: template.id.clone(),
                name: template.name.clone(),
                version: template.version,
                hash: hash.to_owned(),
                source,
                origin,
            },
            body_font: style.body_font,
            heading_font: style.heading_font,
            body_size_pt: style.body_size_pt,
            heading_sizes_pt: heading_sizes(style.body_size_pt),
            heading_color,
            heading_numbering: style.heading_numbering,
            table: style.table,
            image_max_width_percent: style.image_max_width_percent,
            image_align: style.image_align,
            toc: style.toc.clone(),
            cover_page: style.cover_page,
            page_break_before_level: style.page_break_before_level,
            regions,
            images,
            numbers: if style.heading_numbering {
                number_headings(headings)
            } else {
                BTreeMap::new()
            },
        },
        diagnostics,
    ))
}

fn embed(bytes: &[u8]) -> Result<StyleImage, WorkspaceError> {
    let (width, height) = branding::validate_png(bytes)?;
    Ok(StyleImage {
        width,
        height,
        png_base64: base64::engine::general_purpose::STANDARD.encode(bytes),
    })
}

/// Resolves the style for a document inside a project workspace.
pub fn resolve(
    workspace: &Workspace,
    selection: Selection<'_>,
    facts: &DocumentFacts,
    headings: &[Heading],
) -> Result<(StyleModel, Vec<String>), WorkspaceError> {
    let project = BrandingStore::new(workspace).show()?;
    let store = TemplateStore::new(workspace);
    let mut diagnostics = Vec::new();
    let found = match selection {
        Selection::Default => None,
        Selection::Chosen { id, version } => Some((store.get(id, version)?, Origin::Selected)),
        Selection::Pinned(pin) => match store.get(&pin.id, Some(pin.version)) {
            Ok(stored) if stored.content_hash == pin.hash => Some((stored, Origin::Pinned)),
            Ok(_) => {
                diagnostics.push(format!(
                    "Pinned template {} v{} no longer matches its recorded hash; showing the generic style.",
                    pin.id, pin.version
                ));
                None
            }
            Err(cause) => {
                diagnostics.push(format!(
                    "Pinned template {} v{} could not be used ({cause}); showing the generic style.",
                    pin.id, pin.version
                ));
                None
            }
        },
    };
    let (stored, origin) = match found {
        Some(found) => found,
        None => {
            let template = Template::builtin_generic();
            let stored = crate::template::StoredTemplate {
                content_hash: template.content_hash()?,
                template,
                source: Source::Builtin,
            };
            (stored, Origin::Default)
        }
    };
    let mut images = BTreeMap::new();
    if origin != Origin::Default {
        for sha in stored.template.asset_hashes() {
            images.insert(sha.clone(), embed(&store.read_asset(&sha)?)?);
        }
        let uses_logo = [
            &stored.template.regions.header,
            &stored.template.regions.footer,
        ]
        .iter()
        .flat_map(|region| [&region.left, &region.centre, &region.right])
        .any(|slot| slot.image == Some(Image::ProjectLogo));
        if uses_logo {
            match (&project.branding.logo, BrandingStore::new(workspace).read_logo(&project.branding)?) {
                (Logo::Png { .. }, Some(bytes)) => {
                    images.insert(PROJECT_LOGO_KEY.to_owned(), embed(&bytes)?);
                }
                _ => diagnostics.push(
                    "The project logo is generated, not an uploaded PNG; page headers and footers omit it."
                        .to_owned(),
                ),
            }
        }
    }
    let (model, warnings) = build(BuildInput {
        template: &stored.template,
        hash: &stored.content_hash,
        source: stored.source,
        origin,
        accent: &project.branding.accent,
        project: &project.branding.name,
        facts,
        headings,
        images,
    })?;
    diagnostics.extend(warnings);
    Ok((model, diagnostics))
}

/// Parses `id` or `id@version` as used by `--template`.
pub fn parse_selector(text: &str) -> Result<(String, Option<u32>), WorkspaceError> {
    match text.split_once('@') {
        None => Ok((text.to_owned(), None)),
        Some((id, version)) => {
            let version: u32 = version
                .parse()
                .ok()
                .filter(|version| *version >= 1)
                .ok_or_else(|| error("template version must be a number of at least 1"))?;
            Ok((id.to_owned(), Some(version)))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn facts() -> DocumentFacts {
        DocumentFacts {
            title: "Doc {page} {id}".into(),
            id: "DOC-1".into(),
            revision: "0123456789abcdef".into(),
            status: "Draft".into(),
        }
    }

    fn heading(level: u8, anchor: &str) -> Heading {
        Heading {
            level,
            title: anchor.into(),
            anchor: anchor.into(),
        }
    }

    fn text(value: &str) -> Segment {
        Segment::Text { text: value.into() }
    }

    #[test]
    fn expansion_is_single_pass_and_keeps_page_fields_as_segments() {
        let segments = expand(
            "{title} - {project} rev {revision} {status} {page}/{total}",
            &facts(),
            "Acme {id}",
        );
        assert_eq!(
            segments,
            vec![
                text("Doc {page} {id} - Acme {id} rev 0123456789ab Draft "),
                Segment::Page,
                text("/"),
                Segment::Total,
            ]
        );
        assert_eq!(
            expand("{title}", &DocumentFacts::unsaved(" "), "p"),
            vec![text("Untitled document")]
        );
        assert_eq!(
            expand("a {unknown} é", &facts(), "p"),
            vec![text("a {unknown} é")]
        );
        assert!(expand("", &facts(), "p").is_empty());
    }

    #[test]
    fn heading_numbers_use_shallowest_level_as_root() {
        let numbers = number_headings(&[
            heading(2, "a"),
            heading(3, "a1"),
            heading(3, "a2"),
            heading(2, "b"),
            heading(3, "b1"),
        ]);
        let get = |key: &str| numbers[key].as_str();
        assert_eq!(
            (get("a"), get("a1"), get("a2"), get("b"), get("b1")),
            ("1", "1.1", "1.2", "2", "2.1")
        );
        let skipped = number_headings(&[heading(1, "x"), heading(3, "y")]);
        assert_eq!(skipped["y"], "1.0.1");
        assert!(number_headings(&[]).is_empty());
    }

    #[test]
    fn heading_sizes_scale_from_body_size() {
        assert_eq!(heading_sizes(11), [20, 17, 14, 13, 12, 11]);
        assert_eq!(heading_sizes(8), [14, 12, 10, 9, 8, 8]);
    }

    fn build_with(template: &Template, origin: Origin, accent: &str) -> (StyleModel, Vec<String>) {
        build(BuildInput {
            template,
            hash: "sha256:x",
            source: Source::Project,
            origin,
            accent,
            project: "Acme",
            facts: &facts(),
            headings: &[heading(1, "intro")],
            images: BTreeMap::new(),
        })
        .unwrap()
    }

    #[test]
    fn accent_resolves_and_low_contrast_warns() {
        let template = Template::builtin_generic();
        let (model, warnings) = build_with(&template, Origin::Pinned, "#C2410C");
        assert_eq!(model.heading_color, "#C2410C");
        assert!(warnings.is_empty());
        assert!(model.regions.is_some());
        let (_, warnings) = build_with(&template, Origin::Pinned, "#FFFF00");
        assert_eq!(warnings.len(), 1);
        assert!(warnings[0].contains("low contrast"));
        let mut fixed = template.clone();
        fixed.style.heading_color = "#112233".into();
        assert_eq!(
            build_with(&fixed, Origin::Selected, "#FFFF00")
                .0
                .heading_color,
            "#112233"
        );
    }

    #[test]
    fn default_origin_has_no_regions_and_numbering_is_conditional() {
        let mut template = Template::builtin_generic();
        let (model, _) = build_with(&template, Origin::Default, "#C2410C");
        assert!(model.regions.is_none() && model.numbers.is_empty());
        template.style.heading_numbering = true;
        let (model, _) = build_with(&template, Origin::Default, "#C2410C");
        assert_eq!(model.numbers["intro"], "1");
    }

    #[test]
    fn selector_parsing() {
        assert_eq!(parse_selector("sop").unwrap(), ("sop".into(), None));
        assert_eq!(parse_selector("sop@3").unwrap(), ("sop".into(), Some(3)));
        for bad in ["sop@0", "sop@x", "sop@"] {
            assert!(parse_selector(bad).is_err(), "{bad}");
        }
    }
}
