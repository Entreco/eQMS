//! Plugin API v1: versioned manifest contract, capability model and isolated loading.
//!
//! This is a contract only. No plugin code is loaded or executed, and the core does not
//! yet invoke plugins; call sites must route privileged decisions through [`authorize`].

use crate::model::ArtifactTypeId;
use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;
use std::fmt::{self, Display, Formatter};
use std::panic::{AssertUnwindSafe, catch_unwind};

/// The only supported plugin API version.
pub const API_VERSION: u32 = 1;
const MAX_MANIFEST_BYTES: usize = 64 * 1024;
const RESERVED_IDS: &[&str] = &["core", "eqms", "qms", "system", "ledger"];

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PluginError(String);

impl Display for PluginError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.0)
    }
}

impl std::error::Error for PluginError {}

fn fail<T>(message: impl Into<String>) -> Result<T, PluginError> {
    Err(PluginError(message.into()))
}

/// Capabilities a plugin may declare. Core controls are deliberately not representable.
#[derive(Clone, Copy, Debug, Deserialize, Serialize, PartialEq, Eq, PartialOrd, Ord)]
pub enum Capability {
    #[serde(rename = "artifact-types.define")]
    DefineArtifactTypes,
    #[serde(rename = "commands.register")]
    RegisterCommands,
    #[serde(rename = "artifacts.read")]
    ReadArtifacts,
    #[serde(rename = "artifacts.migrate-drafts")]
    MigrateDrafts,
}

/// Controls owned exclusively by the core; never grantable to a plugin.
const RESERVED_CAPABILITIES: &[&str] = &[
    "ledger.append",
    "ledger.rewrite",
    "signature.create",
    "release.publish",
    "lifecycle.transition",
];

/// Operations a plugin may attempt; the first group is core-only and always denied.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Operation {
    AppendLedger,
    RewriteLedger,
    CreateSignature,
    PublishRelease,
    TransitionLifecycle,
    DefineArtifactType,
    RegisterCommand,
    ReadArtifacts,
    MigrateDraft,
}

impl Operation {
    fn required(self) -> Option<Capability> {
        match self {
            Self::DefineArtifactType => Some(Capability::DefineArtifactTypes),
            Self::RegisterCommand => Some(Capability::RegisterCommands),
            Self::ReadArtifacts => Some(Capability::ReadArtifacts),
            Self::MigrateDraft => Some(Capability::MigrateDrafts),
            _ => None,
        }
    }
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ArtifactTypeDef {
    pub id: String,
    pub title: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CommandDef {
    pub name: String,
    pub title: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ValidatorDef {
    pub name: String,
    pub artifact_type: String,
    pub title: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ReportDef {
    pub name: String,
    pub title: String,
}

#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct MigrationDef {
    pub name: String,
    pub artifact_type: String,
    pub from_version: u32,
    pub to_version: u32,
}

#[derive(Clone, Debug, Default, Deserialize)]
#[serde(default, deny_unknown_fields)]
pub struct Contributions {
    pub artifact_types: Vec<ArtifactTypeDef>,
    pub commands: Vec<CommandDef>,
    pub validators: Vec<ValidatorDef>,
    pub reports: Vec<ReportDef>,
    pub migrations: Vec<MigrationDef>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RawManifest {
    api_version: u32,
    id: String,
    version: String,
    title: String,
    #[serde(default)]
    capabilities: Vec<String>,
    #[serde(default)]
    contributions: Contributions,
}

/// A validated plugin manifest.
#[derive(Clone, Debug)]
pub struct PluginManifest {
    id: String,
    version: String,
    title: String,
    capabilities: BTreeSet<Capability>,
    contributions: Contributions,
}

fn name(value: &str, what: &str) -> Result<(), PluginError> {
    if value.is_empty()
        || value.len() > 64
        || value.starts_with('-')
        || !value
            .bytes()
            .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-')
    {
        return fail(format!("invalid {what} `{value}`"));
    }
    Ok(())
}

fn semver(value: &str) -> Result<(), PluginError> {
    let valid = value.split('.').count() == 3
        && value.split('.').all(|part| {
            !part.is_empty()
                && part.len() <= 9
                && part.bytes().all(|b| b.is_ascii_digit())
                && (part == "0" || !part.starts_with('0'))
        });
    if valid {
        Ok(())
    } else {
        fail(format!("invalid plugin version `{value}`"))
    }
}

fn title(value: &str) -> Result<(), PluginError> {
    if value.trim().is_empty() || value.len() > 200 || value.chars().any(char::is_control) {
        return fail("invalid title");
    }
    Ok(())
}

fn unique<'a>(names: impl Iterator<Item = &'a str>, what: &str) -> Result<(), PluginError> {
    let mut seen = BTreeSet::new();
    for item in names {
        name(item, what)?;
        if !seen.insert(item) {
            return fail(format!("duplicate {what} `{item}`"));
        }
    }
    Ok(())
}

impl PluginManifest {
    pub fn parse(text: &str) -> Result<Self, PluginError> {
        if text.len() > MAX_MANIFEST_BYTES {
            return fail("plugin manifest exceeds 64 KiB");
        }
        let raw: RawManifest = serde_json::from_str(text)
            .map_err(|err| PluginError(format!("invalid plugin manifest: {err}")))?;
        if raw.api_version != API_VERSION {
            return fail(format!(
                "unsupported plugin api_version {}; supported: {API_VERSION}",
                raw.api_version
            ));
        }
        name(&raw.id, "plugin id")?;
        if RESERVED_IDS.contains(&raw.id.as_str()) {
            return fail(format!("plugin id `{}` is reserved", raw.id));
        }
        semver(&raw.version)?;
        title(&raw.title)?;

        let mut capabilities = BTreeSet::new();
        for declared in &raw.capabilities {
            if RESERVED_CAPABILITIES.contains(&declared.as_str()) {
                return fail(format!(
                    "capability `{declared}` is reserved for the core and cannot be granted"
                ));
            }
            let capability: Capability =
                serde_json::from_value(serde_json::Value::String(declared.clone()))
                    .map_err(|_| PluginError(format!("unknown capability `{declared}`")))?;
            capabilities.insert(capability);
        }

        let manifest = Self {
            id: raw.id,
            version: raw.version,
            title: raw.title,
            capabilities,
            contributions: raw.contributions,
        };
        manifest.validate_contributions()?;
        Ok(manifest)
    }

    fn require(&self, capability: Capability, what: &str) -> Result<(), PluginError> {
        if self.capabilities.contains(&capability) {
            Ok(())
        } else {
            fail(format!("{what} requires capability {capability:?}"))
        }
    }

    fn validate_contributions(&self) -> Result<(), PluginError> {
        let c = &self.contributions;
        let mut types = BTreeSet::new();
        if !c.artifact_types.is_empty() {
            self.require(Capability::DefineArtifactTypes, "artifact_types")?;
        }
        for def in &c.artifact_types {
            let id = ArtifactTypeId::new(def.id.clone())
                .map_err(|err| PluginError(format!("artifact type `{}`: {err}", def.id)))?;
            let namespace = id.as_str().split(':').next().unwrap_or_default();
            if namespace != self.id {
                return fail(format!(
                    "artifact type `{}` must use namespace `{}`",
                    def.id, self.id
                ));
            }
            if !types.insert(def.id.as_str()) {
                return fail(format!("duplicate artifact type `{}`", def.id));
            }
            title(&def.title)?;
        }

        if !c.commands.is_empty() {
            self.require(Capability::RegisterCommands, "commands")?;
        }
        unique(c.commands.iter().map(|d| d.name.as_str()), "command name")?;
        if !c.validators.is_empty() {
            self.require(Capability::ReadArtifacts, "validators")?;
        }
        unique(
            c.validators.iter().map(|d| d.name.as_str()),
            "validator name",
        )?;
        if !c.reports.is_empty() {
            self.require(Capability::ReadArtifacts, "reports")?;
        }
        unique(c.reports.iter().map(|d| d.name.as_str()), "report name")?;
        if !c.migrations.is_empty() {
            self.require(Capability::MigrateDrafts, "migrations")?;
        }
        unique(
            c.migrations.iter().map(|d| d.name.as_str()),
            "migration name",
        )?;

        let titles = c
            .commands
            .iter()
            .map(|d| &d.title)
            .chain(c.validators.iter().map(|d| &d.title))
            .chain(c.reports.iter().map(|d| &d.title));
        for text in titles {
            title(text)?;
        }
        for validator in &c.validators {
            if !types.contains(validator.artifact_type.as_str()) {
                return fail(format!(
                    "validator `{}` targets undefined artifact type `{}`",
                    validator.name, validator.artifact_type
                ));
            }
        }
        for migration in &c.migrations {
            if !types.contains(migration.artifact_type.as_str()) {
                return fail(format!(
                    "migration `{}` targets undefined artifact type `{}`",
                    migration.name, migration.artifact_type
                ));
            }
            if migration.to_version <= migration.from_version {
                return fail(format!(
                    "migration `{}` must increase the version",
                    migration.name
                ));
            }
        }
        Ok(())
    }

    pub fn id(&self) -> &str {
        &self.id
    }

    pub fn version(&self) -> &str {
        &self.version
    }

    pub fn title(&self) -> &str {
        &self.title
    }

    pub fn capabilities(&self) -> &BTreeSet<Capability> {
        &self.capabilities
    }

    pub fn contributions(&self) -> &Contributions {
        &self.contributions
    }
}

/// The single gate for plugin-requested operations. Core controls are always denied.
pub fn authorize(manifest: &PluginManifest, operation: Operation) -> Result<(), PluginError> {
    match operation.required() {
        None => fail(format!(
            "plugin `{}` may not perform {operation:?}: reserved for the core",
            manifest.id
        )),
        Some(capability) if manifest.capabilities.contains(&capability) => Ok(()),
        Some(capability) => fail(format!(
            "plugin `{}` lacks capability {capability:?} for {operation:?}",
            manifest.id
        )),
    }
}

#[derive(Clone, Debug, Serialize, PartialEq, Eq)]
pub struct PluginSummary {
    pub id: String,
    pub version: String,
    pub title: String,
}

#[derive(Clone, Debug, Serialize, PartialEq, Eq)]
pub struct LoadFailure {
    pub source: String,
    pub error: String,
}

#[derive(Clone, Debug, Serialize, PartialEq, Eq)]
pub struct LoadReport {
    pub api_version: u32,
    pub loaded: Vec<PluginSummary>,
    pub failures: Vec<LoadFailure>,
}

#[derive(Debug, Default)]
pub struct PluginRegistry {
    plugins: Vec<PluginManifest>,
}

impl PluginRegistry {
    /// Loads `(source label, manifest text)` pairs; one failure never affects the others.
    pub fn load<'a>(sources: impl IntoIterator<Item = (&'a str, &'a str)>) -> (Self, LoadReport) {
        let mut registry = Self::default();
        let mut loaded = Vec::new();
        let mut failures = Vec::new();
        for (source, text) in sources {
            let parsed = catch_unwind(AssertUnwindSafe(|| PluginManifest::parse(text)))
                .unwrap_or_else(|_| fail("plugin validation panicked"));
            let outcome = parsed.and_then(|manifest| {
                if registry.get(manifest.id()).is_some() {
                    fail(format!("duplicate plugin id `{}`", manifest.id()))
                } else {
                    Ok(manifest)
                }
            });
            match outcome {
                Ok(manifest) => {
                    loaded.push(PluginSummary {
                        id: manifest.id.clone(),
                        version: manifest.version.clone(),
                        title: manifest.title.clone(),
                    });
                    registry.plugins.push(manifest);
                }
                Err(err) => failures.push(LoadFailure {
                    source: source.to_string(),
                    error: err.to_string(),
                }),
            }
        }
        let report = LoadReport {
            api_version: API_VERSION,
            loaded,
            failures,
        };
        (registry, report)
    }

    pub fn get(&self, id: &str) -> Option<&PluginManifest> {
        self.plugins.iter().find(|plugin| plugin.id == id)
    }

    pub fn plugins(&self) -> &[PluginManifest] {
        &self.plugins
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::{Value, json};

    fn full() -> Value {
        json!({
            "api_version": 1, "id": "acme", "version": "1.2.3", "title": "Acme",
            "capabilities": ["artifact-types.define", "commands.register",
                "artifacts.read", "artifacts.migrate-drafts"],
            "contributions": {
                "artifact_types": [{"id": "acme:widget", "title": "Widget"}],
                "commands": [{"name": "sync", "title": "Sync"}],
                "validators": [{"name": "check", "artifact_type": "acme:widget", "title": "Check"}],
                "reports": [{"name": "summary", "title": "Summary"}],
                "migrations": [{"name": "m1", "artifact_type": "acme:widget",
                    "from_version": 1, "to_version": 2}]
            }
        })
    }

    fn parse(value: &Value) -> Result<PluginManifest, PluginError> {
        PluginManifest::parse(&value.to_string())
    }

    fn rejected(value: &Value, needle: &str) {
        let err = parse(value)
            .expect_err("manifest must be rejected")
            .to_string();
        assert!(err.contains(needle), "`{err}` should contain `{needle}`");
    }

    #[test]
    fn valid_manifest_with_all_contribution_kinds_parses() {
        let manifest = parse(&full()).unwrap();
        assert_eq!(manifest.id(), "acme");
        assert_eq!(manifest.version(), "1.2.3");
        assert_eq!(manifest.capabilities().len(), 4);
        let c = manifest.contributions();
        assert_eq!(c.artifact_types.len(), 1);
        assert_eq!(c.commands.len(), 1);
        assert_eq!(c.validators.len(), 1);
        assert_eq!(c.reports.len(), 1);
        assert_eq!(c.migrations.len(), 1);
    }

    #[test]
    fn api_version_is_required_and_must_be_supported() {
        for version in [json!(0), json!(2)] {
            let mut manifest = full();
            manifest["api_version"] = version;
            rejected(&manifest, "unsupported plugin api_version");
        }
        let mut manifest = full();
        manifest.as_object_mut().unwrap().remove("api_version");
        rejected(&manifest, "api_version");
    }

    #[test]
    fn unknown_fields_and_oversized_input_are_rejected() {
        let mut manifest = full();
        manifest["extra"] = json!(1);
        rejected(&manifest, "unknown field");
        let huge = format!("{{\"pad\":\"{}\"}}", "a".repeat(MAX_MANIFEST_BYTES));
        assert!(
            PluginManifest::parse(&huge)
                .unwrap_err()
                .to_string()
                .contains("64 KiB")
        );
        assert!(PluginManifest::parse("not json").is_err());
    }

    #[test]
    fn identifiers_namespaces_and_references_are_validated() {
        let cases: Vec<(&str, Value, &str)> = vec![
            ("id", json!("Bad_Id"), "invalid plugin id"),
            ("id", json!("core"), "reserved"),
            ("id", json!("-lead"), "invalid plugin id"),
            ("version", json!("1.0"), "invalid plugin version"),
            ("version", json!("01.0.0"), "invalid plugin version"),
            ("title", json!(" "), "invalid title"),
        ];
        for (field, value, needle) in cases {
            let mut manifest = full();
            manifest[field] = value;
            rejected(&manifest, needle);
        }

        let mut manifest = full();
        manifest["contributions"]["artifact_types"][0]["id"] = json!("other:widget");
        rejected(&manifest, "must use namespace");

        let mut manifest = full();
        manifest["contributions"]["artifact_types"] = json!([
            {"id": "acme:widget", "title": "A"}, {"id": "acme:widget", "title": "B"}
        ]);
        rejected(&manifest, "duplicate artifact type");

        let mut manifest = full();
        manifest["contributions"]["commands"] = json!([
            {"name": "sync", "title": "A"}, {"name": "sync", "title": "B"}
        ]);
        rejected(&manifest, "duplicate command name");

        let mut manifest = full();
        manifest["contributions"]["commands"][0]["name"] = json!("../evil");
        rejected(&manifest, "invalid command name");

        let mut manifest = full();
        manifest["contributions"]["validators"][0]["artifact_type"] = json!("acme:missing");
        rejected(&manifest, "undefined artifact type");

        let mut manifest = full();
        manifest["contributions"]["migrations"][0]["to_version"] = json!(1);
        rejected(&manifest, "must increase");
    }

    #[test]
    fn reserved_and_unknown_capabilities_reject_the_manifest() {
        for capability in RESERVED_CAPABILITIES {
            let mut manifest = full();
            manifest["capabilities"]
                .as_array_mut()
                .unwrap()
                .push(json!(capability));
            rejected(&manifest, "reserved for the core");
        }
        let mut manifest = full();
        manifest["capabilities"]
            .as_array_mut()
            .unwrap()
            .push(json!("root"));
        rejected(&manifest, "unknown capability");
    }

    #[test]
    fn each_contribution_requires_its_capability() {
        let cases = [
            ("artifact_types", "artifact-types.define"),
            ("commands", "commands.register"),
            ("migrations", "artifacts.migrate-drafts"),
        ];
        for (kind, capability) in cases {
            let mut manifest = full();
            manifest["capabilities"]
                .as_array_mut()
                .unwrap()
                .retain(|c| c != capability);
            rejected(&manifest, kind);
        }
        for kind in ["validators", "reports"] {
            let mut manifest = full();
            let other = if kind == "validators" {
                "reports"
            } else {
                "validators"
            };
            manifest["contributions"]
                .as_object_mut()
                .unwrap()
                .remove(other);
            manifest["capabilities"]
                .as_array_mut()
                .unwrap()
                .retain(|c| c != "artifacts.read");
            rejected(&manifest, kind);
        }
    }

    #[test]
    fn authorize_denies_core_controls_and_checks_capabilities() {
        let all = parse(&full()).unwrap();
        for operation in [
            Operation::AppendLedger,
            Operation::RewriteLedger,
            Operation::CreateSignature,
            Operation::PublishRelease,
            Operation::TransitionLifecycle,
        ] {
            let err = authorize(&all, operation).unwrap_err().to_string();
            assert!(err.contains("reserved for the core"), "{err}");
        }
        for operation in [
            Operation::DefineArtifactType,
            Operation::RegisterCommand,
            Operation::ReadArtifacts,
            Operation::MigrateDraft,
        ] {
            assert!(authorize(&all, operation).is_ok());
        }
        let none = parse(&json!({
            "api_version": 1, "id": "bare", "version": "0.1.0", "title": "Bare"
        }))
        .unwrap();
        assert!(authorize(&none, Operation::ReadArtifacts).is_err());
        assert!(authorize(&none, Operation::MigrateDraft).is_err());
    }

    #[test]
    fn load_failures_are_isolated_and_reported() {
        let good = full().to_string();
        let other = json!({"api_version": 1, "id": "beta", "version": "0.1.0", "title": "Beta"})
            .to_string();
        let bad_version = json!({"api_version": 9, "id": "gamma", "version": "0.1.0",
            "title": "G"})
        .to_string();
        let privileged = json!({"api_version": 1, "id": "evil", "version": "0.1.0",
            "title": "E", "capabilities": ["ledger.append"]})
        .to_string();
        let (registry, report) = PluginRegistry::load([
            ("a.json", good.as_str()),
            ("bad.json", "{"),
            ("v9.json", bad_version.as_str()),
            ("evil.json", privileged.as_str()),
            ("dup.json", good.as_str()),
            ("b.json", other.as_str()),
        ]);
        let ids: Vec<_> = report.loaded.iter().map(|p| p.id.as_str()).collect();
        assert_eq!(ids, ["acme", "beta"]);
        let sources: Vec<_> = report.failures.iter().map(|f| f.source.as_str()).collect();
        assert_eq!(sources, ["bad.json", "v9.json", "evil.json", "dup.json"]);
        assert!(report.failures[3].error.contains("duplicate plugin id"));
        assert_eq!(report.api_version, API_VERSION);
        assert_eq!(registry.plugins().len(), 2);
        assert!(registry.get("evil").is_none());
    }

    #[test]
    fn empty_load_is_empty_report() {
        let (registry, report) = PluginRegistry::load([]);
        assert!(registry.plugins().is_empty());
        assert!(report.loaded.is_empty() && report.failures.is_empty());
    }
}
