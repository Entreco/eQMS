use serde::Serialize;

#[derive(Serialize)]
struct VersionOutput {
    schema_version: u32,
    status: &'static str,
    version: &'static str,
}

#[derive(Serialize)]
struct ErrorOutput<'a> {
    schema_version: u32,
    status: &'static str,
    error: ErrorDetail<'a>,
}

#[derive(Serialize)]
struct ErrorDetail<'a> {
    code: &'static str,
    message: &'a str,
}

pub(crate) struct Response {
    pub(crate) code: u8,
    pub(crate) text: String,
    pub(crate) error: bool,
}

impl Response {
    pub(crate) fn success(text: String) -> Self {
        Self {
            code: 0,
            text,
            error: false,
        }
    }

    fn failure(code: u8, error_code: &'static str, message: &str, json: bool) -> Self {
        let text = if json {
            serde_json::to_string(&ErrorOutput {
                schema_version: 1,
                status: "error",
                error: ErrorDetail {
                    code: error_code,
                    message,
                },
            })
            .expect("serializing a string-only error cannot fail")
        } else {
            message.to_owned()
        };
        Self {
            code,
            text,
            error: true,
        }
    }

    pub(crate) fn usage_error(message: &str, json: bool) -> Self {
        Self::failure(2, "INVALID_ARGUMENT", message, json)
    }

    pub(crate) fn from_result(result: Result<String, String>, json: bool) -> Self {
        match result {
            Ok(text) => Self::success(text),
            Err(message) => Self::failure(1, "OPERATION_FAILED", &message, json),
        }
    }
}

pub(crate) fn version(json: bool) -> String {
    if json {
        serde_json::to_string(&VersionOutput {
            schema_version: 1,
            status: "ok",
            version: eqms_core::version(),
        })
        .expect("serializing static version strings cannot fail")
    } else {
        format!("qms {}", eqms_core::version())
    }
}

pub(crate) fn document(
    operation: &str,
    data: serde_json::Value,
    json: bool,
) -> Result<String, String> {
    if json {
        Ok(serde_json::json!({
            "schema_version": 1, "status": "ok", "command": format!("document.{operation}"), "data": data
        }).to_string())
    } else if let Some(diff) = data.as_str() {
        Ok(diff.to_owned())
    } else {
        serde_json::to_string_pretty(&data).map_err(|e| e.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn errors_preserve_exit_codes_stream_selection_and_envelopes() {
        let usage = Response::usage_error("missing argument", true);
        assert_eq!(usage.code, 2);
        assert!(usage.error);
        assert_eq!(
            serde_json::from_str::<serde_json::Value>(&usage.text).unwrap(),
            json!({
                "schema_version": 1, "status": "error",
                "error": { "code": "INVALID_ARGUMENT", "message": "missing argument" }
            })
        );
        let operation = Response::from_result(Err("read failed".into()), true);
        assert_eq!(operation.code, 1);
        assert!(operation.error);
        assert_eq!(
            serde_json::from_str::<serde_json::Value>(&operation.text).unwrap(),
            json!({
                "schema_version": 1, "status": "error",
                "error": { "code": "OPERATION_FAILED", "message": "read failed" }
            })
        );
        assert_eq!(
            Response::usage_error("plain error", false).text,
            "plain error"
        );
        let success = Response::from_result(Ok("saved".into()), false);
        assert_eq!(success.code, 0);
        assert!(!success.error);
        assert_eq!(success.text, "saved");
    }

    #[test]
    fn document_rendering_preserves_raw_diff_and_json_data() {
        let diff = "-old\n+new\n";
        assert_eq!(document("diff", json!(diff), false).unwrap(), diff);
        assert_eq!(
            serde_json::from_str::<serde_json::Value>(
                &document("diff", json!(diff), true).unwrap()
            )
            .unwrap(),
            json!({ "schema_version": 1, "status": "ok", "command": "document.diff", "data": diff }),
        );
        let data = json!({ "title": "Policy" });
        assert_eq!(
            document("show", data.clone(), false).unwrap(),
            serde_json::to_string_pretty(&data).unwrap()
        );
    }
}
