//! Style resolution against a real project workspace (EQMS-115).

use eqms_core::markdown::TemplateBinding;
use eqms_core::render::Heading;
use eqms_core::style::{self, DocumentFacts, Origin, Selection};
use eqms_core::template::{Image, Template, TemplateStore};
use eqms_core::workspace::Workspace;
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::path::PathBuf;

fn png() -> Vec<u8> {
    let mut bytes = vec![0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13];
    bytes.extend(b"IHDR");
    bytes.extend(64u32.to_be_bytes());
    bytes.extend(48u32.to_be_bytes());
    bytes.extend([8, 6, 0, 0, 0, 0, 0, 0, 0]);
    bytes.extend([0, 0, 0, 0, b'I', b'E', b'N', b'D', 0xAE, 0x42, 0x60, 0x82]);
    bytes
}

struct Project(PathBuf, Workspace);

impl Drop for Project {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

fn project(tag: &str) -> Project {
    let dir = std::env::temp_dir().join(format!("eqms-style-{tag}-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir(&dir).unwrap();
    for args in [
        &["init", "--quiet"][..],
        &["config", "user.name", "Style Test"],
        &["config", "user.email", "style@example.invalid"],
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
    Project(dir, workspace)
}

fn headings() -> Vec<Heading> {
    vec![Heading {
        level: 1,
        title: "Scope".into(),
        anchor: "scope".into(),
    }]
}

fn facts() -> DocumentFacts {
    DocumentFacts::unsaved("Doc")
}

fn installed(project: &Project) -> (TemplateBinding, String) {
    let bytes = png();
    let sha = format!("{:x}", Sha256::digest(&bytes));
    let mut template = Template::builtin_generic();
    template.id = "formal".into();
    template.style.heading_numbering = true;
    template.regions.header.centre.image = Some(Image::Asset {
        sha256: sha.clone(),
    });
    template.regions.header.left.image = Some(Image::ProjectLogo);
    let stored = TemplateStore::new(&project.1)
        .install(&template, &BTreeMap::from([(sha.clone(), bytes)]))
        .unwrap();
    (
        TemplateBinding {
            id: "formal".into(),
            version: 1,
            hash: stored.content_hash,
        },
        sha,
    )
}

#[test]
fn unpinned_document_gets_generic_style_without_regions() {
    let project = project("default");
    let (model, diagnostics) =
        style::resolve(&project.1, Selection::Default, &facts(), &headings()).unwrap();
    assert_eq!(model.template.id, "generic");
    assert_eq!(model.template.origin, Origin::Default);
    assert!(model.regions.is_none() && model.images.is_empty() && diagnostics.is_empty());
}

#[test]
fn pinned_template_applies_with_asset_and_generated_logo_diagnostic() {
    let project = project("pinned");
    let (pin, sha) = installed(&project);
    let (model, diagnostics) =
        style::resolve(&project.1, Selection::Pinned(&pin), &facts(), &headings()).unwrap();
    assert_eq!(model.template.origin, Origin::Pinned);
    assert_eq!(model.numbers["scope"], "1");
    assert_eq!(model.images.len(), 1);
    assert_eq!(model.images[&sha].width, 64);
    assert_eq!(model.images[&sha].height, 48);
    let regions = model.regions.unwrap();
    assert_eq!(regions.header.centre.image.as_deref(), Some(sha.as_str()));
    assert!(regions.header.left.image.is_none());
    assert!(diagnostics.iter().any(|d| d.contains("logo is generated")));
}

#[test]
fn changed_or_missing_pin_falls_back_with_diagnostic() {
    let project = project("fallback");
    let (pin, _) = installed(&project);
    let wrong_hash = TemplateBinding {
        hash: format!("sha256:{}", "0".repeat(64)),
        ..pin.clone()
    };
    let missing = TemplateBinding { version: 9, ..pin };
    for (pin, expected) in [
        (&wrong_hash, "no longer matches"),
        (&missing, "could not be used"),
    ] {
        let (model, diagnostics) =
            style::resolve(&project.1, Selection::Pinned(pin), &facts(), &headings()).unwrap();
        assert_eq!(model.template.id, "generic");
        assert_eq!(model.template.origin, Origin::Default);
        assert!(
            diagnostics.iter().any(|d| d.contains(expected)),
            "{diagnostics:?}"
        );
    }
}

#[test]
fn chosen_template_is_a_preview_and_unknown_choice_fails() {
    let project = project("chosen");
    installed(&project);
    let chosen = Selection::Chosen {
        id: "formal",
        version: None,
    };
    let (model, _) = style::resolve(&project.1, chosen, &facts(), &headings()).unwrap();
    assert_eq!(model.template.origin, Origin::Selected);
    for (id, version) in [("missing", None), ("formal", Some(2))] {
        assert!(
            style::resolve(
                &project.1,
                Selection::Chosen { id, version },
                &facts(),
                &headings()
            )
            .is_err()
        );
    }
}

#[test]
fn tampered_asset_falls_back_with_diagnostic() {
    let project = project("tamper");
    let (pin, sha) = installed(&project);
    let path = project.0.join(format!("templates/assets/{sha}.png"));
    let mut bytes = std::fs::read(&path).unwrap();
    bytes[30] ^= 1;
    std::fs::write(&path, bytes).unwrap();
    let (model, diagnostics) =
        style::resolve(&project.1, Selection::Pinned(&pin), &facts(), &headings()).unwrap();
    assert_eq!(model.template.origin, Origin::Default);
    assert!(
        diagnostics.iter().any(|d| d.contains("could not be used")),
        "{diagnostics:?}"
    );
}
