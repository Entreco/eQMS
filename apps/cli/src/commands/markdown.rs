use clap::ArgMatches;
use eqms_core::markdown::{Markdown, SCHEMA_VERSION};
use serde_json::json;
use std::io::Read;

pub(crate) fn run(
    operation: &str,
    args: &ArgMatches,
    json_output: bool,
    input: &mut dyn Read,
) -> Result<String, String> {
    let path = args.get_one::<String>("path").expect("required input path");
    let bytes = if path == "-" {
        let mut bytes = Vec::new();
        input
            .read_to_end(&mut bytes)
            .map_err(|cause| format!("cannot read Markdown stdin: {cause}"))?;
        bytes
    } else {
        std::fs::read(path).map_err(|cause| format!("cannot read Markdown file {path}: {cause}"))?
    };
    let document = if operation == "validate" {
        Markdown::validate(&bytes)
    } else {
        Markdown::parse(&bytes)
    }
    .map_err(|cause| cause.to_string())?;
    let hash = document.content_hash().map_err(|cause| cause.to_string())?;
    if operation == "format" && !json_output {
        return String::from_utf8(
            document
                .canonical_bytes()
                .map_err(|cause| cause.to_string())?,
        )
        .map_err(|cause| cause.to_string());
    }
    let mut data = json!({
        "id": document.id(),
        "schema_version": SCHEMA_VERSION,
        "canonical": true,
        "content_hash": hash,
    });
    if operation == "format" {
        data["content"] = json!(
            String::from_utf8(
                document
                    .canonical_bytes()
                    .map_err(|cause| cause.to_string())?
            )
            .map_err(|cause| cause.to_string())?
        );
    }
    if json_output {
        serde_json::to_string(&data).map_err(|cause| cause.to_string())
    } else {
        Ok(format!(
            "Canonical Markdown format v{SCHEMA_VERSION}: {}\nContent hash: {hash}",
            document.id()
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::commands::document;
    use std::io;

    #[test]
    fn stdin_read_failure_never_returns_partial_content() {
        struct FailingReader;
        impl Read for FailingReader {
            fn read(&mut self, _: &mut [u8]) -> io::Result<usize> {
                Err(io::Error::other("injected input failure"))
            }
        }
        for operation in ["validate", "format"] {
            let matches = document::command()
                .try_get_matches_from(["document", operation, "-"])
                .unwrap();
            let failure = document::run(&matches, true, &mut FailingReader).unwrap_err();
            assert!(failure.contains("cannot read Markdown stdin"));
            assert!(failure.contains("injected input failure"));
        }
    }
}
