use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::error::Error;
use std::fmt::{self, Display, Formatter};

pub const SCHEMA_VERSION: u32 = 1;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct MarkdownError(String);

impl Display for MarkdownError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.0)
    }
}

impl Error for MarkdownError {}

fn error(message: impl Into<String>) -> MarkdownError {
    MarkdownError(message.into())
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
struct Metadata {
    schema_version: u32,
    id: String,
    title: String,
    status: String,
}

/// Supported v1 Draft content, independent of saved revisions and audit history.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Markdown {
    id: String,
    title: String,
    body: String,
}

pub fn validate_id(id: &str) -> Result<(), MarkdownError> {
    if !id.strip_prefix("DOC-").is_some_and(|value| {
        value.len() == 32
            && value
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    }) {
        return Err(error("invalid document ID"));
    }
    Ok(())
}

impl Markdown {
    pub fn new(id: &str, title: &str, body: &str) -> Result<Self, MarkdownError> {
        validate_id(id)?;
        let title = title.trim();
        if title.is_empty() || title.chars().any(char::is_control) {
            return Err(error("document title must be a nonblank single line"));
        }
        Ok(Self {
            id: id.to_owned(),
            title: title.to_owned(),
            body: body.replace("\r\n", "\n").replace('\r', "\n"),
        })
    }

    /// Parse supported metadata and normalize only documented whitespace rules.
    pub fn parse(bytes: &[u8]) -> Result<Self, MarkdownError> {
        let text = std::str::from_utf8(bytes)
            .map_err(|cause| error(format!("invalid UTF-8 Markdown: {cause}")))?;
        let text = text.replace("\r\n", "\n").replace('\r', "\n");
        let (metadata, body) = text
            .strip_prefix("---\n")
            .and_then(|value| value.split_once("\n---\n"))
            .ok_or_else(|| error("invalid canonical document header"))?;
        let metadata: Metadata = serde_json::from_str(metadata)
            .map_err(|cause| error(format!("invalid document metadata: {cause}")))?;
        if metadata.schema_version != SCHEMA_VERSION {
            return Err(error(format!(
                "unsupported document schema version {}",
                metadata.schema_version
            )));
        }
        if metadata.status != "Draft" {
            return Err(error("unsupported document status; expected Draft"));
        }
        Self::new(&metadata.id, &metadata.title, body)
    }

    /// Validate exact canonical bytes, without checking any saved history.
    pub fn validate(bytes: &[u8]) -> Result<Self, MarkdownError> {
        let document = Self::parse(bytes)?;
        if document.canonical_bytes()? != bytes {
            return Err(error(
                "noncanonical Markdown; use qms document format to normalize",
            ));
        }
        Ok(document)
    }

    pub fn id(&self) -> &str {
        &self.id
    }

    pub fn title(&self) -> &str {
        &self.title
    }

    pub fn body(&self) -> &str {
        &self.body
    }

    pub fn canonical_bytes(&self) -> Result<Vec<u8>, MarkdownError> {
        let metadata = Metadata {
            schema_version: SCHEMA_VERSION,
            id: self.id.clone(),
            title: self.title.clone(),
            status: "Draft".to_owned(),
        };
        let line = serde_json::to_string(&metadata).map_err(|cause| error(cause.to_string()))?;
        Ok(format!("---\n{line}\n---\n{}", self.body).into_bytes())
    }

    pub fn content_hash(&self) -> Result<String, MarkdownError> {
        Ok(hash(&self.canonical_bytes()?))
    }
}

pub fn hash(bytes: &[u8]) -> String {
    format!("sha256:{:x}", Sha256::digest(bytes))
}

#[cfg(test)]
mod tests {
    use super::*;

    const ID: &str = "DOC-0123456789abcdef0123456789abcdef";
    const GOLDEN: &str = "---\n{\"schema_version\":1,\"id\":\"DOC-0123456789abcdef0123456789abcdef\",\"title\":\"Policy\",\"status\":\"Draft\"}\n---\nBody";

    #[test]
    fn legacy_bytes_and_independent_sha256_are_stable() {
        let document = Markdown::new(ID, "Policy", "Body").unwrap();
        assert_eq!(document.canonical_bytes().unwrap(), GOLDEN.as_bytes());
        assert_eq!(
            document.content_hash().unwrap(),
            "sha256:2866a39ccb18a2d807569fbbadb98f7b17a2cfc15e4155bc60efc4fc5bc44ccf"
        );
        assert_eq!(Markdown::validate(GOLDEN.as_bytes()).unwrap(), document);
        assert_ne!(
            document.content_hash().unwrap(),
            Markdown::new(ID, "Policy", "Body\n")
                .unwrap()
                .content_hash()
                .unwrap()
        );
    }

    #[test]
    fn formatting_normalizes_supported_encodings_but_validation_is_strict() {
        let reordered = format!(
            "---\n{{\n \"status\": \"Draft\", \"title\": \" Policy \",\n \"id\": \"{ID}\", \"schema_version\": 1\n}}\n---\nBody"
        );
        for input in [
            reordered.clone(),
            reordered.replace('\n', "\r\n"),
            reordered.replace('\n', "\r"),
            GOLDEN.replace('\n', "\r\n"),
        ] {
            let parsed = Markdown::parse(input.as_bytes()).unwrap();
            assert_eq!(parsed.canonical_bytes().unwrap(), GOLDEN.as_bytes());
            assert!(Markdown::validate(input.as_bytes()).is_err());
            let canonical = parsed.canonical_bytes().unwrap();
            assert_eq!(
                Markdown::parse(&canonical)
                    .unwrap()
                    .canonical_bytes()
                    .unwrap(),
                canonical
            );
        }
    }

    #[test]
    fn markdown_body_whitespace_and_quoted_unicode_titles_are_preserved() {
        for body in [
            "",
            "No final newline",
            "With final newline\n",
            "Trailing spaces  \n\n",
            "---\n{\"not\":\"metadata\"}\n---\n# Body\n",
            "Unicode: \u{03bb} \u{1f680}\n",
        ] {
            let document = Markdown::new(ID, " A \"quoted\" \u{03bb} title ", body).unwrap();
            let bytes = document.canonical_bytes().unwrap();
            let parsed = Markdown::validate(&bytes).unwrap();
            assert_eq!(parsed.title(), "A \"quoted\" \u{03bb} title");
            assert_eq!(parsed.body(), body);
            assert_eq!(parsed, document);
            assert_eq!(parsed.canonical_bytes().unwrap(), bytes);
        }
        let normalized = Markdown::new(ID, "Policy", "a\r\nb\rc  ").unwrap();
        assert_eq!(normalized.body(), "a\nb\nc  ");
    }

    #[test]
    fn invalid_or_ambiguous_metadata_is_rejected_without_lossy_fallbacks() {
        for input in [
            GOLDEN.replace("\"schema_version\":1", "\"schema_version\":2"),
            GOLDEN.replace("\"schema_version\":1", "\"schema_version\":1.0"),
            GOLDEN.replace("\"schema_version\":1,", ""),
            GOLDEN.replace("\"title\":\"Policy\"", "\"title\":\"Policy\",\"extra\":0"),
            GOLDEN.replace(
                "\"title\":\"Policy\"",
                "\"title\":\"Policy\",\"title\":\"Other\"",
            ),
            GOLDEN.replace("\"title\":\"Policy\"", "\"title\":null"),
            GOLDEN.replace("\"Policy\"", "\"   \""),
            GOLDEN.replace("\"Policy\"", "\"Control\\ncharacter\""),
            GOLDEN.replace("\"Draft\"", "\"Approved\""),
            GOLDEN.replace(ID, "../escape"),
            GOLDEN.replace(ID, "DOC-0123456789ABCDEF0123456789abcdef"),
            GOLDEN.replace(ID, "DOC-0123"),
            GOLDEN.replacen("---\n", "", 1),
            GOLDEN.replace("\n---\n", "\n---"),
            format!("\u{feff}{GOLDEN}"),
            "---\ntitle: Policy\n---\nBody".to_owned(),
            "---\n{}\n---\nBody".to_owned(),
            "---\nnot JSON\n---\nBody".to_owned(),
        ] {
            assert!(Markdown::parse(input.as_bytes()).is_err(), "{input:?}");
            assert!(Markdown::validate(input.as_bytes()).is_err());
        }
        assert!(Markdown::parse(b"\xff").is_err());
        assert!(Markdown::new(ID, " \n ", "").is_err());
        assert!(Markdown::new(ID, "Control\0character", "").is_err());
        assert!(Markdown::new("../escape", "Policy", "").is_err());
    }
}
