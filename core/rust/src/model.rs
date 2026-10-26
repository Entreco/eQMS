use serde::de::Error as _;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use serde_json::Value;
use std::error::Error;
use std::fmt::{self, Display, Formatter};
use std::str::FromStr;
use time::format_description::well_known::Rfc3339;
use time::{OffsetDateTime, UtcOffset};
use uuid::Uuid;

pub const MODEL_SCHEMA_VERSION: u32 = 1;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ModelError(String);

impl Display for ModelError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.0)
    }
}

impl Error for ModelError {}

fn model_error(message: impl Into<String>) -> ModelError {
    ModelError(message.into())
}

#[derive(Clone, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct ArtifactTypeId(String);

impl ArtifactTypeId {
    pub fn new(value: impl Into<String>) -> Result<Self, ModelError> {
        let value = value.into();
        let Some((namespace, type_name)) = value.split_once(':') else {
            return Err(model_error(
                "artifact type ID must use the namespace:type form",
            ));
        };
        if type_name.contains(':')
            || !valid_type_component(namespace)
            || !valid_type_component(type_name)
        {
            return Err(model_error("artifact type ID contains invalid characters"));
        }
        Ok(Self(value))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl Display for ArtifactTypeId {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.0)
    }
}

impl FromStr for ArtifactTypeId {
    type Err = ModelError;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        Self::new(value)
    }
}

fn valid_type_component(value: &str) -> bool {
    let Some(first) = value.as_bytes().first() else {
        return false;
    };
    let Some(last) = value.as_bytes().last() else {
        return false;
    };
    (first.is_ascii_lowercase() || first.is_ascii_digit())
        && (last.is_ascii_lowercase() || last.is_ascii_digit())
        && value.bytes().all(|byte| {
            byte.is_ascii_lowercase() || byte.is_ascii_digit() || matches!(byte, b'.' | b'-' | b'_')
        })
}

impl Serialize for ArtifactTypeId {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(&self.0)
    }
}

impl<'de> Deserialize<'de> for ArtifactTypeId {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let value = String::deserialize(deserializer)?;
        Self::new(value).map_err(D::Error::custom)
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub struct Timestamp(OffsetDateTime);

impl Timestamp {
    pub fn parse(value: &str) -> Result<Self, ModelError> {
        if !matches!(value.as_bytes().get(10), Some(b'T' | b't')) {
            return Err(model_error(
                "RFC 3339 timestamp must separate date and time with T",
            ));
        }
        if value.ends_with("-00:00") {
            return Err(model_error(
                "RFC 3339 timestamp has an unknown local UTC offset",
            ));
        }
        let parsed = OffsetDateTime::parse(value, &Rfc3339)
            .map_err(|error| model_error(format!("invalid RFC 3339 timestamp: {error}")))?;
        Ok(Self(parsed.to_offset(UtcOffset::UTC)))
    }

    pub fn from_datetime(value: OffsetDateTime) -> Self {
        Self(value.to_offset(UtcOffset::UTC))
    }

    pub fn as_datetime(self) -> OffsetDateTime {
        self.0
    }

    pub fn to_rfc3339(self) -> Result<String, ModelError> {
        self.0
            .format(&Rfc3339)
            .map_err(|error| model_error(format!("cannot format RFC 3339 timestamp: {error}")))
    }
}

impl Display for Timestamp {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        match self.to_rfc3339() {
            Ok(value) => formatter.write_str(&value),
            Err(_) => Err(fmt::Error),
        }
    }
}

impl Serialize for Timestamp {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        let value = self.to_rfc3339().map_err(serde::ser::Error::custom)?;
        serializer.serialize_str(&value)
    }
}

impl<'de> Deserialize<'de> for Timestamp {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let value = String::deserialize(deserializer)?;
        Self::parse(&value).map_err(D::Error::custom)
    }
}

#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(deny_unknown_fields)]
pub struct ModelContract<T> {
    schema_version: u32,
    data: T,
}

impl<T> ModelContract<T> {
    pub fn new(data: T) -> Self {
        Self {
            schema_version: MODEL_SCHEMA_VERSION,
            data,
        }
    }

    pub fn schema_version(&self) -> u32 {
        self.schema_version
    }

    pub fn data(&self) -> &T {
        &self.data
    }

    pub fn into_data(self) -> T {
        self.data
    }
}

impl<'de, T> Deserialize<'de> for ModelContract<T>
where
    T: Deserialize<'de>,
{
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        #[derive(Deserialize)]
        #[serde(deny_unknown_fields)]
        struct VersionedData<T> {
            schema_version: u32,
            data: T,
        }

        let contract = VersionedData::<T>::deserialize(deserializer)?;
        if contract.schema_version != MODEL_SCHEMA_VERSION {
            return Err(D::Error::custom(format!(
                "unsupported model schema version {}",
                contract.schema_version
            )));
        }
        Ok(Self {
            schema_version: contract.schema_version,
            data: contract.data,
        })
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Artifact {
    pub id: Uuid,
    pub artifact_type: ArtifactTypeId,
    pub extension: Value,
    pub created_at: Timestamp,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ArtifactVersion {
    pub id: Uuid,
    pub artifact_id: Uuid,
    pub version: String,
    pub content_hash: Option<String>,
    pub created_at: Timestamp,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct TraceLink {
    pub id: Uuid,
    pub source_artifact_id: Uuid,
    pub target_artifact_id: Uuid,
    pub relationship: String,
    pub created_at: Timestamp,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LedgerEvent {
    pub id: Uuid,
    pub event_type: String,
    pub occurred_at: Timestamp,
    pub payload: Value,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Release {
    pub id: Uuid,
    pub artifact_versions: Vec<Uuid>,
    pub released_at: Timestamp,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SignatureRecord {
    pub id: Uuid,
    pub subject_id: Uuid,
    pub signer_id: Uuid,
    pub algorithm: String,
    pub signature: String,
    pub signed_at: Timestamp,
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde::de::DeserializeOwned;
    use serde_json::json;

    const TIME_WITH_OFFSET: &str = "2026-10-26T20:00:00+01:00";
    const UUID: &str = "123e4567-e89b-12d3-a456-426614174000";

    fn timestamp() -> Timestamp {
        Timestamp::parse(TIME_WITH_OFFSET).unwrap()
    }

    fn round_trip<T>(value: T)
    where
        T: DeserializeOwned + PartialEq + std::fmt::Debug + Serialize,
    {
        let contract = ModelContract::new(value);
        let encoded = serde_json::to_string(&contract).unwrap();
        let decoded: ModelContract<T> = serde_json::from_str(&encoded).unwrap();
        assert_eq!(decoded.data(), contract.data());
    }

    fn sample_artifact() -> Artifact {
        Artifact {
            id: Uuid::parse_str(UUID).unwrap(),
            artifact_type: ArtifactTypeId::new("com.example:procedure").unwrap(),
            extension: json!({"owner":{"team":"quality"}}),
            created_at: timestamp(),
        }
    }

    fn assert_all_entities_round_trip() {
        let id = Uuid::parse_str(UUID).unwrap();
        let time = timestamp();
        round_trip(sample_artifact());
        round_trip(ArtifactVersion {
            id,
            artifact_id: id,
            version: "1".to_owned(),
            content_hash: Some("sha256:abc".to_owned()),
            created_at: time,
        });
        round_trip(TraceLink {
            id,
            source_artifact_id: id,
            target_artifact_id: id,
            relationship: "verifies".to_owned(),
            created_at: time,
        });
        round_trip(LedgerEvent {
            id,
            event_type: "artifact.created".to_owned(),
            occurred_at: time,
            payload: json!({"source":"test"}),
        });
        round_trip(Release {
            id,
            artifact_versions: vec![id],
            released_at: time,
        });
        round_trip(SignatureRecord {
            id,
            subject_id: id,
            signer_id: id,
            algorithm: "example-signature".to_owned(),
            signature: "opaque-value".to_owned(),
            signed_at: time,
        });
    }

    #[test]
    fn all_domain_types_round_trip_in_versioned_contracts() {
        assert_all_entities_round_trip();
    }

    #[test]
    fn model_contract_rejects_unsupported_and_malformed_versions() {
        let mut future = serde_json::to_value(ModelContract::new(sample_artifact())).unwrap();
        future["schema_version"] = json!(MODEL_SCHEMA_VERSION + 1);
        let error = serde_json::from_value::<ModelContract<Artifact>>(future).unwrap_err();
        assert!(
            error
                .to_string()
                .contains("unsupported model schema version")
        );

        let missing_data = json!({"schema_version": MODEL_SCHEMA_VERSION});
        assert!(serde_json::from_value::<ModelContract<Artifact>>(missing_data).is_err());
    }

    #[test]
    fn uuid_serialization_is_lowercase_hyphenated() {
        let id = Uuid::parse_str("123E4567-E89B-12D3-A456-426614174000").unwrap();
        assert_eq!(serde_json::to_string(&id).unwrap(), format!("\"{UUID}\""));
        assert!(serde_json::from_str::<Uuid>("\"not-a-uuid\"").is_err());
    }

    #[test]
    fn timestamps_normalize_to_utc_and_round_trip() {
        let time = timestamp();
        assert_eq!(time.to_rfc3339().unwrap(), "2026-10-26T19:00:00Z");

        let parsed = Timestamp::parse("2026-10-26T19:00:00Z").unwrap();
        assert_eq!(parsed, time);
        assert_eq!(
            serde_json::to_string(&time).unwrap(),
            "\"2026-10-26T19:00:00Z\""
        );
        assert_eq!(
            serde_json::from_str::<Timestamp>("\"2026-10-26T20:00:00+01:00\"").unwrap(),
            time
        );
    }

    #[test]
    fn timestamps_reject_invalid_rfc3339_values() {
        assert!(Timestamp::parse("2026-02-30T19:00:00Z").is_err());
        assert!(Timestamp::parse("2026-10-26 19:00:00Z").is_err());
        assert!(Timestamp::parse("2026-10-26T19:00:00-00:00").is_err());
    }

    #[test]
    fn plugin_type_ids_and_opaque_data_round_trip() {
        for valid in ["com.example:procedure", "plugin_1:custom-type"] {
            let id = ArtifactTypeId::new(valid).unwrap();
            assert_eq!(serde_json::to_string(&id).unwrap(), format!("\"{valid}\""));
            assert_eq!(
                serde_json::from_str::<ArtifactTypeId>(&format!("\"{valid}\"")).unwrap(),
                id
            );
        }
        for invalid in [
            "",
            ":type",
            "namespace:",
            "Bad:type",
            "namespace:type:extra",
            "namespace:type_",
        ] {
            assert!(ArtifactTypeId::new(invalid).is_err(), "{invalid}");
        }

        let extension = json!({"nested":[1,true,{"key":"value"}]});
        let artifact = Artifact {
            artifact_type: ArtifactTypeId::new("plugin:custom").unwrap(),
            extension: extension.clone(),
            ..sample_artifact()
        };
        let contract = serde_json::to_value(ModelContract::new(artifact)).unwrap();
        assert_eq!(contract["data"]["extension"], extension);
    }
}
