use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::error::Error;
use std::fmt::{self, Display, Formatter};

pub const SCHEMA_VERSION: u32 = 2;
pub const MAX_TEMPLATE_CHARS: usize = 120;

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

#[derive(Clone, Copy, Debug, Serialize, Deserialize, PartialEq, Eq)]
pub enum PageSize {
    A4,
    Letter,
}

impl PageSize {
    /// Page width and height in tenths of a millimetre, portrait.
    fn tenths_mm(self) -> (i64, i64) {
        match self {
            PageSize::A4 => (2100, 2970),
            PageSize::Letter => (2159, 2794),
        }
    }
}

#[derive(Clone, Copy, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum Orientation {
    Portrait,
    Landscape,
}

/// Validated document page setup; part of the hashed canonical document.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
pub struct PageSetup {
    pub size: PageSize,
    pub orientation: Orientation,
    pub margin_top_mm: f64,
    pub margin_right_mm: f64,
    pub margin_bottom_mm: f64,
    pub margin_left_mm: f64,
    pub header: String,
    pub footer: String,
}

impl Eq for PageSetup {}

impl Default for PageSetup {
    fn default() -> Self {
        Self {
            size: PageSize::A4,
            orientation: Orientation::Portrait,
            margin_top_mm: 25.4,
            margin_right_mm: 25.4,
            margin_bottom_mm: 25.4,
            margin_left_mm: 25.4,
            header: "{title}".to_owned(),
            footer: "Page {page} of {total}".to_owned(),
        }
    }
}

fn margin_tenths(name: &str, value: f64) -> Result<i64, MarkdownError> {
    let tenths = value * 10.0;
    if !value.is_finite()
        || !(50.0..=500.0).contains(&tenths.round())
        || (tenths - tenths.round()).abs() > 1e-6
    {
        return Err(error(format!(
            "{name} must be between 5.0 mm and 50.0 mm in 0.1 mm steps"
        )));
    }
    Ok(tenths.round() as i64)
}

fn validate_template(name: &str, value: &str) -> Result<(), MarkdownError> {
    if value.chars().count() > MAX_TEMPLATE_CHARS || value.chars().any(char::is_control) {
        return Err(error(format!(
            "{name} must be at most {MAX_TEMPLATE_CHARS} characters without control characters"
        )));
    }
    let remainder = value
        .replace("{title}", "")
        .replace("{page}", "")
        .replace("{total}", "");
    if remainder.contains(['{', '}']) {
        return Err(error(format!(
            "{name} may only use the {{title}}, {{page}} and {{total}} placeholders"
        )));
    }
    Ok(())
}

impl PageSetup {
    /// Validate bounds and normalize margins to exact 0.1 mm values.
    pub fn validated(mut self) -> Result<Self, MarkdownError> {
        let top = margin_tenths("top margin", self.margin_top_mm)?;
        let right = margin_tenths("right margin", self.margin_right_mm)?;
        let bottom = margin_tenths("bottom margin", self.margin_bottom_mm)?;
        let left = margin_tenths("left margin", self.margin_left_mm)?;
        let (width, height) = self.size.tenths_mm();
        let (width, height) = match self.orientation {
            Orientation::Portrait => (width, height),
            Orientation::Landscape => (height, width),
        };
        if left + right > width - 400 {
            return Err(error("horizontal margins leave no editable area"));
        }
        if top + bottom > height - 600 {
            return Err(error("vertical margins leave no editable area"));
        }
        validate_template("header", &self.header)?;
        validate_template("footer", &self.footer)?;
        self.margin_top_mm = top as f64 / 10.0;
        self.margin_right_mm = right as f64 / 10.0;
        self.margin_bottom_mm = bottom as f64 / 10.0;
        self.margin_left_mm = left as f64 / 10.0;
        Ok(self)
    }
}

/// Pin to an immutable template version; part of the hashed canonical document.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct TemplateBinding {
    pub id: String,
    pub version: u32,
    /// Template content hash, `sha256:` plus 64 lowercase hex.
    pub hash: String,
}

impl TemplateBinding {
    pub fn validate(&self) -> Result<(), MarkdownError> {
        let id_ok = (1..=40).contains(&self.id.len())
            && self.id.starts_with(|c: char| c.is_ascii_lowercase())
            && self
                .id
                .bytes()
                .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-')
            && !self.id.ends_with('-');
        let hash_ok = self.hash.strip_prefix("sha256:").is_some_and(|hex| {
            hex.len() == 64 && hex.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
        });
        if !id_ok || self.version == 0 || !hash_ok {
            return Err(error("invalid template binding"));
        }
        Ok(())
    }

    /// Journal label, e.g. `sop@v2`.
    pub fn label(&self) -> String {
        format!("{}@v{}", self.id, self.version)
    }
}

#[derive(Debug, Serialize, Deserialize, PartialEq)]
#[serde(deny_unknown_fields)]
struct Metadata {
    schema_version: u32,
    id: String,
    title: String,
    page_size: PageSize,
    orientation: Orientation,
    margin_top_mm: f64,
    margin_right_mm: f64,
    margin_bottom_mm: f64,
    margin_left_mm: f64,
    header: String,
    footer: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    template_id: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    template_version: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    template_hash: Option<String>,
    status: String,
}

/// Supported v2 Draft content, independent of saved revisions and audit history.
#[derive(Clone, Debug, PartialEq)]
pub struct Markdown {
    id: String,
    title: String,
    page: PageSetup,
    template: Option<TemplateBinding>,
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
        Self::with_page(id, title, body, PageSetup::default())
    }

    pub fn with_page(
        id: &str,
        title: &str,
        body: &str,
        page: PageSetup,
    ) -> Result<Self, MarkdownError> {
        Self::with_template(id, title, body, page, None)
    }

    pub fn with_template(
        id: &str,
        title: &str,
        body: &str,
        page: PageSetup,
        template: Option<TemplateBinding>,
    ) -> Result<Self, MarkdownError> {
        validate_id(id)?;
        if let Some(binding) = &template {
            binding.validate()?;
        }
        let title = title.trim();
        if title.is_empty() || title.chars().any(char::is_control) {
            return Err(error("document title must be a nonblank single line"));
        }
        Ok(Self {
            id: id.to_owned(),
            title: title.to_owned(),
            page: page.validated()?,
            template,
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
        // Check the version first so older schemas get a precise message.
        let version = serde_json::from_str::<serde_json::Value>(metadata)
            .ok()
            .and_then(|value| value.get("schema_version").cloned());
        if let Some(version) = version
            && version != serde_json::json!(SCHEMA_VERSION)
        {
            return Err(error(format!(
                "unsupported document schema version {version}; only version {SCHEMA_VERSION} is supported"
            )));
        }
        let metadata: Metadata = serde_json::from_str(metadata)
            .map_err(|cause| error(format!("invalid document metadata: {cause}")))?;
        if metadata.status != "Draft" {
            return Err(error("unsupported document status; expected Draft"));
        }
        let template = match (
            metadata.template_id,
            metadata.template_version,
            metadata.template_hash,
        ) {
            (None, None, None) => None,
            (Some(id), Some(version), Some(hash)) => Some(TemplateBinding { id, version, hash }),
            _ => return Err(error("incomplete template binding")),
        };
        Self::with_template(
            &metadata.id,
            &metadata.title,
            body,
            PageSetup {
                size: metadata.page_size,
                orientation: metadata.orientation,
                margin_top_mm: metadata.margin_top_mm,
                margin_right_mm: metadata.margin_right_mm,
                margin_bottom_mm: metadata.margin_bottom_mm,
                margin_left_mm: metadata.margin_left_mm,
                header: metadata.header,
                footer: metadata.footer,
            },
            template,
        )
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

    pub fn page(&self) -> &PageSetup {
        &self.page
    }

    pub fn template(&self) -> Option<&TemplateBinding> {
        self.template.as_ref()
    }

    pub fn body(&self) -> &str {
        &self.body
    }

    pub fn canonical_bytes(&self) -> Result<Vec<u8>, MarkdownError> {
        let metadata = Metadata {
            schema_version: SCHEMA_VERSION,
            id: self.id.clone(),
            title: self.title.clone(),
            page_size: self.page.size,
            orientation: self.page.orientation,
            margin_top_mm: self.page.margin_top_mm,
            margin_right_mm: self.page.margin_right_mm,
            margin_bottom_mm: self.page.margin_bottom_mm,
            margin_left_mm: self.page.margin_left_mm,
            header: self.page.header.clone(),
            footer: self.page.footer.clone(),
            template_id: self.template.as_ref().map(|t| t.id.clone()),
            template_version: self.template.as_ref().map(|t| t.version),
            template_hash: self.template.as_ref().map(|t| t.hash.clone()),
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
    const GOLDEN: &str = "---\n{\"schema_version\":2,\"id\":\"DOC-0123456789abcdef0123456789abcdef\",\"title\":\"Policy\",\"page_size\":\"A4\",\"orientation\":\"portrait\",\"margin_top_mm\":25.4,\"margin_right_mm\":25.4,\"margin_bottom_mm\":25.4,\"margin_left_mm\":25.4,\"header\":\"{title}\",\"footer\":\"Page {page} of {total}\",\"status\":\"Draft\"}\n---\nBody";

    #[test]
    fn v2_bytes_and_independent_sha256_are_stable() {
        let document = Markdown::new(ID, "Policy", "Body").unwrap();
        assert_eq!(document.canonical_bytes().unwrap(), GOLDEN.as_bytes());
        assert_eq!(
            document.content_hash().unwrap(),
            "sha256:5a5609835b73533faf1922b52b9290f45a3ddd74f615a1cdd3410d7ac4b1668d"
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
            "---\n{{\n \"status\": \"Draft\", \"title\": \" Policy \",\n \"id\": \"{ID}\", \"schema_version\": 2,\n \"page_size\": \"A4\", \"orientation\": \"portrait\", \"margin_top_mm\": 25.40,\n \"margin_right_mm\": 25.4, \"margin_bottom_mm\": 25.4, \"margin_left_mm\": 25.4,\n \"header\": \"{{title}}\", \"footer\": \"Page {{page}} of {{total}}\"\n}}\n---\nBody"
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

    fn binding() -> TemplateBinding {
        TemplateBinding {
            id: "sop".to_owned(),
            version: 2,
            hash: format!("sha256:{}", "ab".repeat(32)),
        }
    }

    #[test]
    fn template_pin_round_trips_in_fixed_position_and_unbound_is_unchanged() {
        let document =
            Markdown::with_template(ID, "Policy", "Body", PageSetup::default(), Some(binding()))
                .unwrap();
        let bytes = document.canonical_bytes().unwrap();
        let text = String::from_utf8(bytes.clone()).unwrap();
        assert!(text.contains(&format!(
            "\"footer\":\"Page {{page}} of {{total}}\",\"template_id\":\"sop\",\"template_version\":2,\"template_hash\":\"sha256:{}\",\"status\":\"Draft\"",
            "ab".repeat(32)
        )));
        let parsed = Markdown::validate(&bytes).unwrap();
        assert_eq!(parsed.template(), Some(&binding()));
        assert_eq!(parsed, document);
        let unbound = Markdown::new(ID, "Policy", "Body").unwrap();
        assert_eq!(unbound.canonical_bytes().unwrap(), GOLDEN.as_bytes());
        assert_eq!(
            Markdown::validate(GOLDEN.as_bytes()).unwrap().template(),
            None
        );
    }

    #[test]
    fn invalid_template_pins_are_rejected() {
        let bad = |edit: &dyn Fn(&mut TemplateBinding)| {
            let mut pin = binding();
            edit(&mut pin);
            assert!(Markdown::with_template(ID, "T", "", PageSetup::default(), Some(pin)).is_err());
        };
        bad(&|p| p.id = "Sop".to_owned());
        bad(&|p| p.id = String::new());
        bad(&|p| p.id = "sop-".to_owned());
        bad(&|p| p.version = 0);
        bad(&|p| p.hash = "ab".repeat(32));
        bad(&|p| p.hash = format!("sha256:{}", "AB".repeat(32)));
        bad(&|p| p.hash = "sha256:ab".to_owned());
        let pinned =
            Markdown::with_template(ID, "Policy", "Body", PageSetup::default(), Some(binding()))
                .unwrap();
        let text = String::from_utf8(pinned.canonical_bytes().unwrap()).unwrap();
        let partial = text.replace("\"template_version\":2,", "");
        assert!(Markdown::parse(partial.as_bytes()).is_err());
        let reordered = text.replace(
            "\"template_id\":\"sop\",\"template_version\":2,",
            "\"template_version\":2,\"template_id\":\"sop\",",
        );
        assert!(Markdown::validate(reordered.as_bytes()).is_err());
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
            GOLDEN.replace("\"schema_version\":2", "\"schema_version\":1"),
            GOLDEN.replace("\"schema_version\":2", "\"schema_version\":3"),
            GOLDEN.replace("\"schema_version\":2", "\"schema_version\":2.0"),
            GOLDEN.replace("\"schema_version\":2,", ""),
            GOLDEN.replace("\"page_size\":\"A4\",", ""),
            GOLDEN.replace("\"A4\"", "\"A5\""),
            GOLDEN.replace("\"portrait\"", "\"sideways\""),
            GOLDEN.replace("\"margin_top_mm\":25.4", "\"margin_top_mm\":4.9"),
            GOLDEN.replace("\"margin_top_mm\":25.4", "\"margin_top_mm\":50.1"),
            GOLDEN.replace("\"margin_top_mm\":25.4", "\"margin_top_mm\":25.45"),
            GOLDEN.replace("\"margin_top_mm\":25.4", "\"margin_top_mm\":\"25.4\""),
            GOLDEN.replace("\"{title}\"", "\"{unknown}\""),
            GOLDEN.replace("\"{title}\"", "\"{title\""),
            GOLDEN.replace("\"{title}\"", "\"a\\nb\""),
            GOLDEN.replace("\"{title}\"", &format!("\"{}\"", "x".repeat(121))),
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

    fn page(size: PageSize, orientation: Orientation, margin: f64) -> PageSetup {
        PageSetup {
            size,
            orientation,
            margin_top_mm: margin,
            margin_right_mm: margin,
            margin_bottom_mm: margin,
            margin_left_mm: margin,
            header: "H {title}".to_owned(),
            footer: "{page}/{total}".to_owned(),
        }
    }

    #[test]
    fn metadata_changes_alter_bytes_and_hash_and_roundtrip() {
        let base = Markdown::new(ID, "Policy", "Body").unwrap();
        let landscape = Markdown::with_page(
            ID,
            "Policy",
            "Body",
            page(PageSize::Letter, Orientation::Landscape, 10.0),
        )
        .unwrap();
        assert_ne!(
            base.content_hash().unwrap(),
            landscape.content_hash().unwrap()
        );
        let bytes = landscape.canonical_bytes().unwrap();
        assert!(String::from_utf8_lossy(&bytes).contains("\"page_size\":\"Letter\""));
        assert_eq!(Markdown::validate(&bytes).unwrap(), landscape);
        assert_eq!(landscape.page().margin_left_mm, 10.0);
    }

    #[test]
    fn page_setup_bounds_and_templates_are_enforced() {
        let ok = |setup: PageSetup| PageSetup::validated(setup).is_ok();
        assert!(ok(page(PageSize::A4, Orientation::Portrait, 5.0)));
        assert!(ok(page(PageSize::A4, Orientation::Portrait, 50.0)));
        assert!(!ok(page(PageSize::A4, Orientation::Portrait, 4.9)));
        assert!(!ok(page(PageSize::A4, Orientation::Portrait, 50.1)));
        assert!(!ok(page(PageSize::A4, Orientation::Portrait, f64::NAN)));
        assert!(!ok(page(
            PageSize::A4,
            Orientation::Portrait,
            f64::INFINITY
        )));
        assert!(!ok(page(PageSize::A4, Orientation::Portrait, 10.05)));
        let mut wide = page(PageSize::A4, Orientation::Portrait, 20.0);
        wide.margin_left_mm = 50.0;
        wide.margin_right_mm = 50.0;
        wide.margin_top_mm = 50.0;
        wide.margin_bottom_mm = 50.0;
        assert!(ok(wide.clone()));
        let mut landscape = page(PageSize::A4, Orientation::Landscape, 50.0);
        assert!(ok(landscape.clone()));
        landscape.size = PageSize::Letter;
        assert!(ok(landscape));
        for bad in [
            "{",
            "}",
            "{x}",
            "{{title}}",
            "{Title}",
            "a\tb",
            &"x".repeat(121),
        ] {
            let setup = PageSetup {
                header: bad.to_owned(),
                ..PageSetup::default()
            };
            assert!(!ok(setup), "{bad:?}");
            let setup = PageSetup {
                footer: bad.to_owned(),
                ..PageSetup::default()
            };
            assert!(!ok(setup), "{bad:?}");
        }
        let setup = PageSetup {
            header: "\u{03bb}".repeat(120),
            ..PageSetup::default()
        };
        assert!(ok(setup));
    }

    #[test]
    fn near_tenth_margins_normalize_to_exact_values() {
        let setup = PageSetup {
            margin_top_mm: 25.400000000000002,
            ..PageSetup::default()
        };
        let document = Markdown::with_page(ID, "Policy", "", setup).unwrap();
        assert_eq!(document.page().margin_top_mm, 25.4);
        assert_eq!(document.canonical_bytes().unwrap(), {
            Markdown::new(ID, "Policy", "")
                .unwrap()
                .canonical_bytes()
                .unwrap()
        });
    }

    mod properties {
        use super::*;
        use proptest::prelude::*;

        fn template() -> impl Strategy<Value = String> {
            proptest::collection::vec(
                prop_oneof![
                    Just("{title}".to_owned()),
                    Just("{page}".to_owned()),
                    Just("{total}".to_owned()),
                    "[ -z\u{00e9}\u{03bb}]{0,6}".prop_map(|text| text.replace(['{', '}'], "")),
                ],
                0..6,
            )
            .prop_map(|parts| parts.concat())
            .prop_filter("template length", |text| text.chars().count() <= 120)
        }

        proptest! {
            #[test]
            fn valid_setups_encode_deterministically_and_roundtrip(
                landscape in any::<bool>(),
                letter in any::<bool>(),
                margins in proptest::array::uniform4(50i64..=500),
                header in template(),
                footer in template(),
                title in "[A-Za-z0-9 ]{1,20}[A-Za-z0-9]",
                body in "\\PC{0,40}",
            ) {
                let setup = PageSetup {
                    size: if letter { PageSize::Letter } else { PageSize::A4 },
                    orientation: if landscape { Orientation::Landscape } else { Orientation::Portrait },
                    margin_top_mm: margins[0] as f64 / 10.0,
                    margin_right_mm: margins[1] as f64 / 10.0,
                    margin_bottom_mm: margins[2] as f64 / 10.0,
                    margin_left_mm: margins[3] as f64 / 10.0,
                    header,
                    footer,
                };
                if let Ok(document) = Markdown::with_page(ID, &title, &body, setup) {
                    let bytes = document.canonical_bytes().unwrap();
                    prop_assert_eq!(&bytes, &document.canonical_bytes().unwrap());
                    let parsed = Markdown::validate(&bytes).unwrap();
                    prop_assert_eq!(&parsed, &document);
                    prop_assert_eq!(parsed.content_hash().unwrap(), document.content_hash().unwrap());
                }
            }

            #[test]
            fn arbitrary_input_never_panics_and_valid_output_revalidates(
                bytes in proptest::collection::vec(any::<u8>(), 0..200),
            ) {
                if let Ok(document) = Markdown::parse(&bytes) {
                    let canonical = document.canonical_bytes().unwrap();
                    prop_assert!(Markdown::validate(&canonical).is_ok());
                }
            }
        }
    }
}
