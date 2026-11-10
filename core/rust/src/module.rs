//! Bundled declarative modules: versioned manifests plus Markdown templates.

use crate::document::Documents;
use crate::markdown::{Markdown, hash};
use crate::workspace::{Workspace, WorkspaceError, error};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

const SCHEMA_VERSION: u32 = 1;
const PLACEHOLDER_ID: &str = "DOC-00000000000000000000000000000000";

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct Manifest {
    schema_version: u32,
    id: String,
    version: String,
    title: String,
    #[serde(default)]
    dependencies: Vec<Dependency>,
    artifacts: Vec<ArtifactDef>,
    #[serde(default)]
    validation: Vec<Rule>,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct Dependency {
    id: String,
    min_version: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct ArtifactDef {
    key: String,
    #[serde(rename = "type")]
    artifact_type: String,
    title: String,
    template: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct Rule {
    artifact: String,
    required_headings: Vec<String>,
}

/// A validated module; construction guarantees the manifest and templates are consistent.
#[derive(Clone, Debug)]
pub struct Module {
    manifest: Manifest,
    templates: BTreeMap<String, String>,
}

#[derive(Clone, Debug, Serialize, PartialEq, Eq)]
pub struct ArtifactRef {
    pub key: String,
    #[serde(rename = "type")]
    pub artifact_type: String,
    pub document_id: String,
}

#[derive(Clone, Debug, Serialize, PartialEq, Eq)]
pub struct InstallReport {
    pub module: String,
    pub version: String,
    pub previous_version: Option<String>,
    /// `installed`, `upgraded`, `completed` or `unchanged`.
    pub status: &'static str,
    pub created: Vec<ArtifactRef>,
    pub preserved: Vec<ArtifactRef>,
    pub conflicts: Vec<ArtifactRef>,
}

#[derive(Clone, Debug, Serialize, PartialEq, Eq)]
pub struct ModuleStatus {
    pub id: String,
    pub title: String,
    pub bundled_version: String,
    pub installed_version: Option<String>,
}

fn identifier(value: &str, what: &str) -> Result<(), WorkspaceError> {
    if value.is_empty()
        || value.len() > 64
        || value.starts_with('-')
        || !value
            .bytes()
            .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-')
    {
        return Err(error(format!("invalid module {what} `{value}`")));
    }
    Ok(())
}

fn version(value: &str) -> Result<(u64, u64, u64), WorkspaceError> {
    let parts: Vec<_> = value.split('.').collect();
    let number = |part: &str| {
        (!part.is_empty()
            && part.len() <= 9
            && part.bytes().all(|b| b.is_ascii_digit())
            && (part == "0" || !part.starts_with('0')))
        .then(|| part.parse::<u64>().ok())
        .flatten()
    };
    match parts.as_slice() {
        [a, b, c] => match (number(a), number(b), number(c)) {
            (Some(a), Some(b), Some(c)) => Ok((a, b, c)),
            _ => Err(error(format!("invalid module version `{value}`"))),
        },
        _ => Err(error(format!("invalid module version `{value}`"))),
    }
}

fn artifact_id(module: &str, key: &str) -> String {
    let digest = Sha256::digest(format!("eqms-module-artifact\0{module}\0{key}"));
    format!("DOC-{}", &format!("{digest:x}")[..32])
}

impl Module {
    /// Parse and fully validate a manifest against its bundled templates.
    pub fn parse(manifest: &str, templates: &[(&str, &str)]) -> Result<Self, WorkspaceError> {
        let manifest: Manifest = serde_json::from_str(manifest)
            .map_err(|cause| error(format!("invalid module manifest: {cause}")))?;
        if manifest.schema_version != SCHEMA_VERSION {
            return Err(error("unsupported module manifest schema version"));
        }
        identifier(&manifest.id, "id")?;
        version(&manifest.version)?;
        if manifest.title.trim().is_empty() || manifest.title.chars().any(char::is_control) {
            return Err(error("module title must be a nonblank single line"));
        }
        let templates: BTreeMap<String, String> = templates
            .iter()
            .map(|(name, text)| ((*name).to_owned(), (*text).to_owned()))
            .collect();
        let mut keys = BTreeSet::new();
        let mut dependencies = BTreeSet::new();
        for dependency in &manifest.dependencies {
            identifier(&dependency.id, "dependency id")?;
            version(&dependency.min_version)?;
            if dependency.id == manifest.id || !dependencies.insert(&dependency.id) {
                return Err(error(
                    "module dependencies must be unique and not self-referential",
                ));
            }
        }
        if manifest.artifacts.is_empty() {
            return Err(error("module must declare at least one artifact"));
        }
        for artifact in &manifest.artifacts {
            identifier(&artifact.key, "artifact key")?;
            identifier(&artifact.artifact_type, "artifact type")?;
            if !keys.insert(artifact.key.as_str()) {
                return Err(error(format!("duplicate artifact key `{}`", artifact.key)));
            }
            let valid_name = artifact
                .template
                .strip_suffix(".md")
                .is_some_and(|stem| identifier(stem, "template name").is_ok());
            let text = templates
                .get(&artifact.template)
                .filter(|_| valid_name)
                .ok_or_else(|| error(format!("missing template `{}`", artifact.template)))?;
            Markdown::new(PLACEHOLDER_ID, &artifact.title, text)
                .map_err(|cause| error(format!("artifact `{}`: {cause}", artifact.key)))?;
        }
        for rule in &manifest.validation {
            let artifact = manifest
                .artifacts
                .iter()
                .find(|artifact| artifact.key == rule.artifact)
                .ok_or_else(|| {
                    error(format!(
                        "validation names unknown artifact `{}`",
                        rule.artifact
                    ))
                })?;
            let text = &templates[&artifact.template];
            for heading in &rule.required_headings {
                let present = text.lines().any(|line| {
                    let title = line.trim_start_matches('#');
                    line.starts_with('#')
                        && line.len() - title.len() <= 6
                        && title.trim() == heading
                });
                if !present {
                    return Err(error(format!(
                        "artifact `{}` lacks required heading `{heading}`",
                        artifact.key
                    )));
                }
            }
        }
        Ok(Self {
            manifest,
            templates,
        })
    }

    pub fn id(&self) -> &str {
        &self.manifest.id
    }

    pub fn version(&self) -> &str {
        &self.manifest.version
    }

    pub fn title(&self) -> &str {
        &self.manifest.title
    }

    fn template(&self, artifact: &ArtifactDef) -> &str {
        &self.templates[&artifact.template]
    }
}

/// Modules compiled into this binary.
pub fn bundled() -> Result<Vec<Module>, WorkspaceError> {
    Ok(vec![Module::parse(
        include_str!("../modules/qms-starter/manifest.json"),
        &[
            (
                "quality-policy.md",
                include_str!("../modules/qms-starter/templates/quality-policy.md"),
            ),
            (
                "document-control-procedure.md",
                include_str!("../modules/qms-starter/templates/document-control-procedure.md"),
            ),
            (
                "risk-management-procedure.md",
                include_str!("../modules/qms-starter/templates/risk-management-procedure.md"),
            ),
        ],
    )?])
}

pub fn list(path: impl AsRef<Path>) -> Result<Vec<ModuleStatus>, WorkspaceError> {
    let workspace = Workspace::open(path)?;
    workspace.verify()?;
    let installed: BTreeMap<_, _> = workspace.installed_modules()?.into_iter().collect();
    Ok(bundled()?
        .iter()
        .map(|module| ModuleStatus {
            id: module.id().to_owned(),
            title: module.title().to_owned(),
            bundled_version: module.version().to_owned(),
            installed_version: installed.get(module.id()).cloned(),
        })
        .collect())
}

/// Install a bundled module into the workspace at `path`.
pub fn add(path: impl AsRef<Path>, id: &str) -> Result<InstallReport, WorkspaceError> {
    install(path.as_ref(), &bundled()?, id)
}

pub(crate) fn install(
    path: &Path,
    available: &[Module],
    id: &str,
) -> Result<InstallReport, WorkspaceError> {
    let module = available
        .iter()
        .find(|module| module.id() == id)
        .ok_or_else(|| error(format!("unknown module `{id}`")))?;
    let workspace = Workspace::open(path)?;
    workspace.verify()?;
    let installed: BTreeMap<_, _> = workspace.installed_modules()?.into_iter().collect();
    let new = version(module.version())?;
    let previous = installed.get(module.id()).cloned();
    let status = match previous.as_deref().map(version).transpose()? {
        None => "installed",
        Some(old) if old == new => "unchanged",
        Some(old) if old.0 != new.0 => {
            return Err(error(format!(
                "module `{id}` {} cannot replace installed {} (different major version)",
                module.version(),
                previous.as_deref().unwrap_or_default()
            )));
        }
        Some(old) if old > new => {
            return Err(error(format!(
                "module `{id}` {} would downgrade installed {}",
                module.version(),
                previous.as_deref().unwrap_or_default()
            )));
        }
        Some(_) => "upgraded",
    };
    for dependency in &module.manifest.dependencies {
        let required = version(&dependency.min_version)?;
        let satisfied = installed
            .get(&dependency.id)
            .map(|found| version(found))
            .transpose()?
            .is_some_and(|found| found.0 == required.0 && found >= required);
        if !satisfied {
            return Err(error(format!(
                "module `{id}` requires installed module `{}` >= {} (same major version)",
                dependency.id, dependency.min_version
            )));
        }
    }

    let documents = Documents::open(path)?;
    let (mut created, mut preserved, mut conflicts) = (Vec::new(), Vec::new(), Vec::new());
    for artifact in &module.manifest.artifacts {
        let document_id = artifact_id(module.id(), &artifact.key);
        let reference = ArtifactRef {
            key: artifact.key.clone(),
            artifact_type: artifact.artifact_type.clone(),
            document_id: document_id.clone(),
        };
        let template_hash = hash(module.template(artifact).as_bytes());
        match documents.creation_payload(&document_id)? {
            Some(payload) => {
                let provenance = payload.get("module_id").zip(payload.get("artifact_key"));
                if provenance != Some((&module.manifest.id, &artifact.key)) {
                    return Err(error(format!(
                        "document {document_id} exists without matching module provenance"
                    )));
                }
                if payload.get("template_hash") != Some(&template_hash) {
                    conflicts.push(reference.clone());
                }
                preserved.push(reference);
            }
            None => {
                documents.create_artifact(
                    &document_id,
                    &artifact.title,
                    module.template(artifact),
                    &BTreeMap::from([
                        ("module_id".to_owned(), module.id().to_owned()),
                        ("module_version".to_owned(), module.version().to_owned()),
                        ("artifact_key".to_owned(), artifact.key.clone()),
                        ("template_hash".to_owned(), template_hash),
                    ]),
                )?;
                created.push(reference);
            }
        }
    }

    let status = if status == "unchanged" && !created.is_empty() {
        "completed"
    } else {
        status
    };
    if status != "unchanged" {
        let mut modules = installed.clone();
        modules.insert(module.id().to_owned(), module.version().to_owned());
        let ids = |items: &[ArtifactRef]| {
            items
                .iter()
                .map(|item| item.document_id.as_str())
                .collect::<Vec<_>>()
                .join(",")
        };
        workspace.record_module_event(
            (status != "completed").then(|| modules.into_iter().collect()),
            BTreeMap::from([
                ("module_id".to_owned(), module.id().to_owned()),
                ("module_version".to_owned(), module.version().to_owned()),
                (
                    "previous_version".to_owned(),
                    previous.clone().unwrap_or_else(|| "none".to_owned()),
                ),
                ("status".to_owned(), status.to_owned()),
                ("created_documents".to_owned(), ids(&created)),
                ("conflicting_documents".to_owned(), ids(&conflicts)),
            ]),
        )?;
    }
    Ok(InstallReport {
        module: module.id().to_owned(),
        version: module.version().to_owned(),
        previous_version: previous,
        status,
        created,
        preserved,
        conflicts,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::audit::verify_ledger;
    use crate::workspace::git;
    use std::fs;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static NEXT: AtomicUsize = AtomicUsize::new(0);

    struct Fixture(PathBuf);
    impl Fixture {
        fn new() -> Self {
            let path = std::env::temp_dir().join(format!(
                "eqms-module-{}-{}",
                std::process::id(),
                NEXT.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir(&path).unwrap();
            git(&path, &["init", "--quiet"]).unwrap();
            git(&path, &["config", "user.name", "Module Test"]).unwrap();
            git(&path, &["config", "user.email", "module@example.invalid"]).unwrap();
            Workspace::initialize(&path).unwrap();
            Self(path)
        }
        fn head(&self) -> String {
            crate::workspace::git_text(&self.0, &["rev-parse", "HEAD"]).unwrap()
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) {
            fs::remove_dir_all(&self.0).unwrap();
        }
    }

    fn manifest(id: &str, version: &str, dependency: Option<(&str, &str)>, extra: bool) -> String {
        let dependencies = dependency
            .map(|(id, min)| format!(r#"{{"id":"{id}","min_version":"{min}"}}"#))
            .unwrap_or_default();
        let second = if extra {
            r#",{"key":"beta","type":"procedure","title":"Beta","template":"beta.md"}"#
        } else {
            ""
        };
        format!(
            r#"{{"schema_version":1,"id":"{id}","version":"{version}","title":"Test","dependencies":[{dependencies}],
"artifacts":[{{"key":"alpha","type":"policy","title":"Alpha","template":"alpha.md"}}{second}],
"validation":[{{"artifact":"alpha","required_headings":["Purpose"]}}]}}"#
        )
    }

    fn module(id: &str, version: &str, text: &str, extra: bool) -> Module {
        Module::parse(
            &manifest(id, version, None, extra),
            &[("alpha.md", text), ("beta.md", "# Beta\n\nBody\n")],
        )
        .unwrap()
    }

    #[test]
    fn bundled_modules_are_valid_and_deterministic() {
        let modules = bundled().unwrap();
        assert_eq!(modules[0].id(), "qms-starter");
        assert_eq!(artifact_id("a", "b"), artifact_id("a", "b"));
        assert_ne!(artifact_id("a", "b"), artifact_id("a", "c"));
        assert!(
            bundled().unwrap()[0]
                .templates
                .values()
                .all(|text| text.contains("not compliance evidence"))
        );
    }

    #[test]
    fn invalid_manifests_are_rejected() {
        let ok = manifest("m", "1.0.0", None, false);
        let templates = [("alpha.md", "# Purpose\n")];
        assert!(Module::parse(&ok, &templates).is_ok());
        for (bad, why) in [
            (ok.replace("\"m\"", "\"M_\""), "id"),
            (ok.replace("1.0.0", "1.0"), "version"),
            (ok.replace("1.0.0", "01.0.0"), "version"),
            (ok.replace("alpha.md", "../alpha.md"), "template"),
            (ok.replace("\"Purpose\"", "\"Missing\""), "heading"),
            (
                ok.replace("\"artifact\":\"alpha\"", "\"artifact\":\"zzz\""),
                "artifact",
            ),
            (
                ok.replace("\"schema_version\":1", "\"schema_version\":2"),
                "schema",
            ),
            (
                ok.replace("\"title\":\"Test\"", "\"title\":\"Test\",\"run\":\"sh\""),
                "unknown field",
            ),
            (
                manifest("m", "1.0.0", Some(("m", "1.0.0")), false),
                "self dependency",
            ),
        ] {
            assert!(Module::parse(&bad, &templates).is_err(), "{why}");
        }
        assert!(Module::parse(&ok, &[]).is_err());
        let duplicate = r#"{"schema_version":1,"id":"m","version":"1.0.0","title":"T",
"artifacts":[{"key":"a","type":"p","title":"X","template":"alpha.md"},
{"key":"a","type":"p","title":"Y","template":"alpha.md"}]}"#;
        assert!(
            Module::parse(duplicate, &templates)
                .unwrap_err()
                .to_string()
                .contains("duplicate artifact key")
        );
    }

    #[test]
    fn install_creates_draft_artifacts_with_provenance_and_verifies() {
        let fixture = Fixture::new();
        let modules = [module("m", "1.0.0", "# Purpose\n\nText\n", true)];
        let report = install(&fixture.0, &modules, "m").unwrap();
        assert_eq!(report.status, "installed");
        assert_eq!(report.created.len(), 2);
        let workspace = Workspace::open(&fixture.0).unwrap();
        assert_eq!(
            workspace.installed_modules().unwrap(),
            vec![("m".to_owned(), "1.0.0".to_owned())]
        );
        let records = workspace.verify().unwrap();
        assert_eq!(records.last().unwrap().event_type, "module.installed");
        let documents = Documents::open(&fixture.0).unwrap();
        for artifact in &report.created {
            let history = documents.history(&artifact.document_id).unwrap();
            assert_eq!(history.len(), 1);
            assert_eq!(history[0].status, "Draft");
            assert!(
                documents
                    .lifecycle_history(&artifact.document_id)
                    .unwrap()
                    .is_empty()
            );
            let payload = documents
                .creation_payload(&artifact.document_id)
                .unwrap()
                .unwrap();
            assert_eq!(payload["module_id"], "m");
            assert_eq!(payload["module_version"], "1.0.0");
            assert_eq!(payload["artifact_key"], artifact.key);
        }
        let ledger = verify_ledger(&fixture.0, None).unwrap();
        assert_eq!(ledger.drafts.len(), 2);
    }

    #[test]
    fn install_is_deterministic_across_workspaces() {
        let (first, second) = (Fixture::new(), Fixture::new());
        let modules = [module("m", "1.0.0", "# Purpose\n", true)];
        let a = install(&first.0, &modules, "m").unwrap();
        let b = install(&second.0, &modules, "m").unwrap();
        assert_eq!(a.created, b.created);
        let body = |fixture: &Fixture| {
            Documents::open(&fixture.0)
                .unwrap()
                .show(&a.created[0].document_id, None)
                .unwrap()
                .body
        };
        assert_eq!(body(&first), body(&second));
    }

    #[test]
    fn repeat_install_is_unchanged_and_writes_nothing() {
        let fixture = Fixture::new();
        let modules = [module("m", "1.0.0", "# Purpose\n", false)];
        install(&fixture.0, &modules, "m").unwrap();
        let (head, records) = (
            fixture.head(),
            Workspace::open(&fixture.0).unwrap().verify().unwrap().len(),
        );
        let report = install(&fixture.0, &modules, "m").unwrap();
        assert_eq!(report.status, "unchanged");
        assert!(report.created.is_empty() && report.conflicts.is_empty());
        assert_eq!(report.preserved.len(), 1);
        assert_eq!(fixture.head(), head);
        assert_eq!(
            Workspace::open(&fixture.0).unwrap().verify().unwrap().len(),
            records
        );
    }

    #[test]
    fn interrupted_install_is_completed_by_retry() {
        let fixture = Fixture::new();
        let modules = [module("m", "1.0.0", "# Purpose\n", true)];
        let documents = Documents::open(&fixture.0).unwrap();
        let first = &modules[0].manifest.artifacts[0];
        let id = artifact_id("m", &first.key);
        documents
            .create_artifact(
                &id,
                &first.title,
                modules[0].template(first),
                &BTreeMap::from([
                    ("module_id".to_owned(), "m".to_owned()),
                    ("module_version".to_owned(), "1.0.0".to_owned()),
                    ("artifact_key".to_owned(), first.key.clone()),
                    (
                        "template_hash".to_owned(),
                        hash(modules[0].template(first).as_bytes()),
                    ),
                ]),
            )
            .unwrap();
        let before = documents.history(&id).unwrap();
        let report = install(&fixture.0, &modules, "m").unwrap();
        assert_eq!(report.status, "installed");
        assert_eq!((report.created.len(), report.preserved.len()), (1, 1));
        assert_eq!(documents.history(&id).unwrap(), before);
        assert_eq!(
            install(&fixture.0, &modules, "m").unwrap().status,
            "unchanged"
        );
    }

    #[test]
    fn upgrade_preserves_edits_and_reports_conflicts() {
        let fixture = Fixture::new();
        let v1 = [module("m", "1.0.0", "# Purpose\n\nOld\n", false)];
        let id = install(&fixture.0, &v1, "m").unwrap().created[0]
            .document_id
            .clone();
        let documents = Documents::open(&fixture.0).unwrap();
        let current = documents.show(&id, None).unwrap();
        let edited = documents
            .save(&id, &current.revision, "Alpha", "# Purpose\n\nEdited\n")
            .unwrap();
        let v2 = [module("m", "1.1.0", "# Purpose\n\nNew\n", true)];
        let report = install(&fixture.0, &v2, "m").unwrap();
        assert_eq!(report.status, "upgraded");
        assert_eq!(report.previous_version.as_deref(), Some("1.0.0"));
        assert_eq!(report.created.len(), 1);
        assert_eq!(report.conflicts.len(), 1);
        assert_eq!(documents.show(&id, None).unwrap(), edited);
        assert_eq!(
            Workspace::open(&fixture.0)
                .unwrap()
                .installed_modules()
                .unwrap()[0]
                .1,
            "1.1.0"
        );
        verify_ledger(&fixture.0, None).unwrap();
    }

    #[test]
    fn unsafe_installs_fail_without_changes() {
        let fixture = Fixture::new();
        let v1 = [module("m", "1.2.0", "# Purpose\n", false)];
        install(&fixture.0, &v1, "m").unwrap();
        let head = fixture.head();
        for (candidate, why) in [
            (module("m", "1.1.0", "# Purpose\n", false), "downgrade"),
            (module("m", "2.0.0", "# Purpose\n", false), "major"),
        ] {
            let error = install(&fixture.0, &[candidate], "m").unwrap_err();
            assert!(!error.to_string().is_empty(), "{why}");
        }
        assert!(install(&fixture.0, &v1, "unknown").is_err());
        let dependent = Module::parse(
            &manifest("n", "1.0.0", Some(("m", "1.3.0")), false),
            &[("alpha.md", "# Purpose\n")],
        )
        .unwrap();
        assert!(install(&fixture.0, std::slice::from_ref(&dependent), "n").is_err());
        let missing = Module::parse(
            &manifest("n", "1.0.0", Some(("absent", "1.0.0")), false),
            &[("alpha.md", "# Purpose\n")],
        )
        .unwrap();
        assert!(install(&fixture.0, &[missing], "n").is_err());
        assert_eq!(fixture.head(), head);
        assert_eq!(
            Documents::open(&fixture.0).unwrap().list().unwrap().len(),
            1
        );
        let satisfied = Module::parse(
            &manifest("n", "1.0.0", Some(("m", "1.2.0")), false),
            &[("alpha.md", "# Purpose\n")],
        )
        .unwrap();
        assert_eq!(
            install(&fixture.0, &[satisfied], "n").unwrap().status,
            "installed"
        );
    }
}
