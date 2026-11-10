//! Role model and approval matrix (EQMS-013).
//!
//! Roles are asserted by the caller; nothing here authenticates identity.

use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};

pub const MATRIX_SCHEMA_VERSION: u32 = 1;
pub const MAX_MATRIX_BYTES: usize = 64 * 1024;
pub const MAX_ARTIFACT_TYPES: usize = 256;

#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Role {
    Author,
    Reviewer,
    Approver,
    Qa,
    Regulatory,
    SecurityOfficer,
    ReleaseAuthority,
    Administrator,
}

impl Role {
    pub const ALL: [Role; 8] = [
        Role::Author,
        Role::Reviewer,
        Role::Approver,
        Role::Qa,
        Role::Regulatory,
        Role::SecurityOfficer,
        Role::ReleaseAuthority,
        Role::Administrator,
    ];

    pub fn as_str(self) -> &'static str {
        match self {
            Role::Author => "author",
            Role::Reviewer => "reviewer",
            Role::Approver => "approver",
            Role::Qa => "qa",
            Role::Regulatory => "regulatory",
            Role::SecurityOfficer => "security_officer",
            Role::ReleaseAuthority => "release_authority",
            Role::Administrator => "administrator",
        }
    }
}

impl std::str::FromStr for Role {
    type Err = String;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        Role::ALL
            .into_iter()
            .find(|role| role.as_str() == value)
            .ok_or_else(|| {
                let known: Vec<_> = Role::ALL.iter().map(|r| r.as_str()).collect();
                format!(
                    "unknown role `{value}`; expected one of: {}",
                    known.join(", ")
                )
            })
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ApprovalMatrix {
    pub schema_version: u32,
    pub artifacts: BTreeMap<String, Vec<Role>>,
    pub release: Vec<Role>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ApprovalSubject<'a> {
    Artifact(&'a str),
    Release,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub struct ApprovalCheck {
    pub subject: String,
    pub required: Vec<Role>,
    pub present: Vec<Role>,
    pub missing: Vec<Role>,
    pub permitted: bool,
}

impl ApprovalMatrix {
    pub fn parse(bytes: &[u8]) -> Result<Self, String> {
        if bytes.len() > MAX_MATRIX_BYTES {
            return Err(format!("approval matrix exceeds {MAX_MATRIX_BYTES} bytes"));
        }
        let matrix: ApprovalMatrix = serde_json::from_slice(bytes)
            .map_err(|cause| format!("invalid approval matrix: {cause}"))?;
        matrix.validate()?;
        Ok(matrix)
    }

    pub fn validate(&self) -> Result<(), String> {
        if self.schema_version != MATRIX_SCHEMA_VERSION {
            return Err(format!(
                "unsupported approval matrix schema_version {}; expected {MATRIX_SCHEMA_VERSION}",
                self.schema_version
            ));
        }
        if self.artifacts.len() > MAX_ARTIFACT_TYPES {
            return Err(format!(
                "approval matrix exceeds {MAX_ARTIFACT_TYPES} artifact types"
            ));
        }
        for (name, roles) in &self.artifacts {
            if !valid_artifact_type(name) {
                return Err(format!(
                    "invalid artifact type `{name}`; use lowercase letters, digits, `_` or `-`"
                ));
            }
            validate_roles(&format!("artifact `{name}`"), roles)?;
        }
        validate_roles("release", &self.release)
    }

    pub fn check(
        &self,
        subject: ApprovalSubject<'_>,
        held: &[Role],
    ) -> Result<ApprovalCheck, String> {
        let (label, required) = match subject {
            ApprovalSubject::Release => ("release".to_owned(), &self.release),
            ApprovalSubject::Artifact(name) => match self.artifacts.get(name) {
                Some(roles) => (format!("artifact:{name}"), roles),
                None => {
                    return Err(format!(
                        "no approval rule for artifact type `{name}`; approval denied"
                    ));
                }
            },
        };
        let held: BTreeSet<Role> = held.iter().copied().collect();
        let required: BTreeSet<Role> = required.iter().copied().collect();
        let missing: Vec<Role> = required.difference(&held).copied().collect();
        Ok(ApprovalCheck {
            subject: label,
            present: required.intersection(&held).copied().collect(),
            permitted: missing.is_empty(),
            required: required.into_iter().collect(),
            missing,
        })
    }
}

fn valid_artifact_type(name: &str) -> bool {
    let mut chars = name.chars();
    matches!(chars.next(), Some(c) if c.is_ascii_lowercase() || c.is_ascii_digit())
        && name.len() <= 64
        && chars.all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_' || c == '-')
}

fn validate_roles(label: &str, roles: &[Role]) -> Result<(), String> {
    if roles.is_empty() {
        return Err(format!("{label} requires at least one role"));
    }
    let unique: BTreeSet<_> = roles.iter().collect();
    if unique.len() != roles.len() {
        return Err(format!("{label} lists a role more than once"));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    const VALID: &str = r#"{"schema_version":1,
        "artifacts":{"policy":["approver","reviewer"],"sop-1":["qa"]},
        "release":["qa","release_authority"]}"#;

    #[test]
    fn test_013_001_roles_round_trip_and_reject_unknown() {
        assert_eq!(Role::ALL.len(), 8);
        for role in Role::ALL {
            assert_eq!(role.as_str().parse::<Role>().unwrap(), role);
            let json = serde_json::to_string(&role).unwrap();
            assert_eq!(json, format!("\"{}\"", role.as_str()));
        }
        assert!("Author".parse::<Role>().is_err());
        assert!(
            "owner"
                .parse::<Role>()
                .unwrap_err()
                .contains("expected one of")
        );
    }

    #[test]
    fn test_013_002_matrix_validation() {
        let matrix = ApprovalMatrix::parse(VALID.as_bytes()).unwrap();
        assert_eq!(matrix.artifacts.len(), 2);
        let bad = [
            VALID.replace("\"schema_version\":1", "\"schema_version\":2"),
            VALID.replace("\"release\"", "\"extra\":1,\"release\""),
            VALID.replace("[\"qa\",\"release_authority\"]", "[]"),
            VALID.replace("[\"qa\",\"release_authority\"]", "[\"qa\",\"qa\"]"),
            VALID.replace("\"policy\"", "\"Policy\""),
            VALID.replace("\"policy\"", "\"-policy\""),
            VALID.replace("\"approver\"", "\"owner\""),
            "{".to_owned(),
        ];
        for text in bad {
            assert!(ApprovalMatrix::parse(text.as_bytes()).is_err(), "{text}");
        }
        let many: BTreeMap<_, _> = (0..=MAX_ARTIFACT_TYPES)
            .map(|i| (format!("t{i}"), vec![Role::Approver]))
            .collect();
        let matrix = ApprovalMatrix {
            schema_version: 1,
            artifacts: many,
            release: vec![Role::Qa],
        };
        assert!(matrix.validate().is_err());
        assert!(ApprovalMatrix::parse(&vec![b' '; MAX_MATRIX_BYTES + 1]).is_err());
    }

    #[test]
    fn test_013_003_check_reports_missing_roles() {
        let matrix = ApprovalMatrix::parse(VALID.as_bytes()).unwrap();
        let policy = ApprovalSubject::Artifact("policy");
        let ok = matrix
            .check(
                policy,
                &[Role::Approver, Role::Reviewer, Role::Administrator],
            )
            .unwrap();
        assert!(ok.permitted && ok.missing.is_empty());
        let blocked = matrix.check(policy, &[Role::Administrator]).unwrap();
        assert!(!blocked.permitted);
        assert_eq!(blocked.missing, vec![Role::Reviewer, Role::Approver]);
        assert!(blocked.present.is_empty());
        let release = matrix.check(ApprovalSubject::Release, &[Role::Qa]).unwrap();
        assert!(!release.permitted);
        assert_eq!(release.missing, vec![Role::ReleaseAuthority]);
        assert_eq!(release.subject, "release");
    }

    #[test]
    fn test_013_004_unknown_artifact_type_is_denied() {
        let matrix = ApprovalMatrix::parse(VALID.as_bytes()).unwrap();
        let error = matrix
            .check(ApprovalSubject::Artifact("sop"), &Role::ALL)
            .unwrap_err();
        assert!(error.contains("approval denied"));
    }
}
