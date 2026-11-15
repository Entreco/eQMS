//! Golden render fixtures for the template style engine (EQMS-115).
//! Regenerate deliberately with `EQMS_UPDATE_GOLDEN=1 cargo test -p eqms-core --test style_golden`.

use eqms_core::render::Heading;
use eqms_core::style::{self, BuildInput, DocumentFacts, Origin, StyleImage};
use eqms_core::template::{Align, Family, Image, Source, TableStyle, Template, TocPlacement};
use std::collections::BTreeMap;
use std::path::PathBuf;

fn headings() -> Vec<Heading> {
    [
        (1, "scope"),
        (2, "purpose"),
        (2, "audience"),
        (1, "procedure"),
        (3, "steps"),
    ]
    .into_iter()
    .map(|(level, anchor)| Heading {
        level,
        title: anchor.to_uppercase(),
        anchor: anchor.into(),
    })
    .collect()
}

fn formal() -> Template {
    let mut t = Template::builtin_generic();
    t.id = "formal-sop".into();
    t.name = "Formal SOP".into();
    t.style.body_font = Family::Serif;
    t.style.heading_font = Family::Sans;
    t.style.body_size_pt = 12;
    t.style.heading_numbering = true;
    t.style.heading_color = "#1F3A5F".into();
    t.style.table = TableStyle::Banded;
    t.style.image_max_width_percent = 60;
    t.style.image_align = Align::Left;
    t.style.toc.enabled = true;
    t.style.toc.depth = 2;
    t.style.toc.placement = TocPlacement::AfterCover;
    t.style.cover_page = true;
    t.style.page_break_before_level = Some(1);
    t.regions.header.centre.image = Some(Image::ProjectLogo);
    t.regions.footer.left.text = "{title} - {status}".into();
    t
}

fn check(name: &str, template: &Template, origin: Origin) {
    let facts = DocumentFacts {
        title: "Document control".into(),
        id: "DOC-0001".into(),
        revision: "abcdef0123456789".into(),
        status: "Approved".into(),
    };
    let images = BTreeMap::from([(
        style::PROJECT_LOGO_KEY.to_owned(),
        StyleImage {
            width: 64,
            height: 64,
            png_base64: "AAAA".into(),
        },
    )]);
    let (model, warnings) = style::build(BuildInput {
        template,
        hash: "sha256:0000000000000000000000000000000000000000000000000000000000000000",
        source: Source::Project,
        origin,
        accent: "#C2410C",
        project: "Acme Devices",
        facts: &facts,
        headings: &headings(),
        images,
    })
    .unwrap();
    assert!(warnings.is_empty(), "{warnings:?}");
    let actual = serde_json::to_string_pretty(&model).unwrap() + "\n";
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join(format!("tests/golden/{name}.json"));
    if std::env::var_os("EQMS_UPDATE_GOLDEN").is_some() {
        std::fs::write(&path, &actual).unwrap();
    }
    let expected = std::fs::read_to_string(&path)
        .unwrap_or_else(|_| panic!("missing golden {name}; run with EQMS_UPDATE_GOLDEN=1"));
    assert_eq!(actual, expected, "golden {name} drifted");
}

#[test]
fn generic_default_style_matches_golden() {
    check(
        "style_generic_default",
        &Template::builtin_generic(),
        Origin::Default,
    );
}

#[test]
fn formal_template_style_matches_golden() {
    check("style_formal_pinned", &formal(), Origin::Pinned);
}
