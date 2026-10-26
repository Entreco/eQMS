use serde::Serialize;
use serde_json::{Value, json};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Serialize)]
struct Envelope {
    schema_version: u32,
    status: &'static str,
    command: Option<String>,
    timestamp_unix_ms: i64,
    findings: Vec<Value>,
    references: Vec<Value>,
    data: Option<Value>,
    error: Option<ErrorDetail>,
}

#[derive(Serialize)]
struct ErrorDetail {
    code: &'static str,
    message: String,
}

pub(crate) struct Response {
    pub(crate) code: u8,
    pub(crate) text: String,
    pub(crate) error: bool,
    pub(crate) trailing_newline: bool,
}

impl Response {
    pub(crate) fn success(text: String) -> Self {
        Self {
            code: 0,
            text,
            error: false,
            trailing_newline: true,
        }
    }

    pub(crate) fn exact_output(mut self) -> Self {
        if !self.error {
            self.trailing_newline = false;
        }
        self
    }

    fn failure(
        code: u8,
        error_code: &'static str,
        message: &str,
        json: bool,
        command: Option<&str>,
    ) -> Self {
        let text = if json {
            serde_json::to_string(&Envelope {
                schema_version: 2,
                status: "error",
                command: command.map(str::to_owned),
                timestamp_unix_ms: timestamp_unix_ms(),
                findings: Vec::new(),
                references: Vec::new(),
                data: None,
                error: Some(ErrorDetail {
                    code: error_code,
                    message: message.to_owned(),
                }),
            })
            .expect("serializing the JSON error envelope cannot fail")
        } else {
            message.to_owned()
        };
        Self {
            code,
            text,
            error: true,
            trailing_newline: true,
        }
    }

    pub(crate) fn usage_error(message: &str, json: bool) -> Self {
        Self::failure(2, "INVALID_ARGUMENT", message, json, None)
    }

    pub(crate) fn from_result(result: Result<String, String>, json: bool, command: &str) -> Self {
        match result {
            Ok(text) if !json => Self::success(text),
            Ok(text) => match success_envelope(&text, command) {
                Ok(text) => Self::success(text),
                Err(message) => Self::failure(1, "INVALID_RESPONSE", &message, true, Some(command)),
            },
            Err(message) => Self::failure(1, "OPERATION_FAILED", &message, json, Some(command)),
        }
    }
}

pub(crate) fn version(json: bool) -> String {
    if json {
        json!({ "version": eqms_core::version() }).to_string()
    } else {
        format!("qms {}", eqms_core::version())
    }
}

fn success_envelope(text: &str, command: &str) -> Result<String, String> {
    let data: Value = serde_json::from_str(text)
        .map_err(|error| format!("command produced invalid JSON output: {error}"))?;
    serde_json::to_string(&Envelope {
        schema_version: 2,
        status: "ok",
        command: Some(command.to_owned()),
        timestamp_unix_ms: timestamp_unix_ms(),
        findings: Vec::new(),
        references: Vec::new(),
        data: Some(data),
        error: None,
    })
    .map_err(|error| format!("cannot serialize JSON response: {error}"))
}

fn timestamp_unix_ms() -> i64 {
    match SystemTime::now().duration_since(UNIX_EPOCH) {
        Ok(duration) => duration.as_millis() as i64,
        Err(error) => -(error.duration().as_millis() as i64),
    }
}

pub(crate) fn document(data: serde_json::Value, json: bool) -> Result<String, String> {
    if json {
        serde_json::to_string(&data).map_err(|error| error.to_string())
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
    fn exact_output_only_suppresses_newlines_for_success() {
        assert!(Response::success("normal".to_owned()).trailing_newline);
        assert!(
            !Response::success("exact".to_owned())
                .exact_output()
                .trailing_newline
        );
        let error = Response::from_result(Err("invalid Markdown".into()), false, "document.format")
            .exact_output();
        assert!(error.error);
        assert!(error.trailing_newline);
    }

    #[test]
    fn errors_preserve_exit_codes_stream_selection_and_envelopes() {
        let usage = Response::usage_error("missing argument", true);
        assert_eq!(usage.code, 2);
        assert!(usage.error);
        let usage_json: Value = serde_json::from_str(&usage.text).unwrap();
        assert_envelope(&usage_json, "error");
        assert_eq!(usage_json["schema_version"], 2);
        assert_eq!(usage_json["command"], Value::Null);
        assert_eq!(usage_json["data"], Value::Null);
        assert_eq!(usage_json["error"]["code"], "INVALID_ARGUMENT");

        let operation = Response::from_result(Err("read failed".into()), true, "verify");
        assert_eq!(operation.code, 1);
        assert!(operation.error);
        let operation_json: Value = serde_json::from_str(&operation.text).unwrap();
        assert_envelope(&operation_json, "error");
        assert_eq!(operation_json["command"], "verify");
        assert_eq!(operation_json["error"]["code"], "OPERATION_FAILED");
        assert_eq!(operation_json["error"]["message"], "read failed");
        assert_eq!(
            Response::usage_error("plain error", false).text,
            "plain error"
        );
        let success = Response::from_result(Ok("saved".into()), false, "save");
        assert_eq!(success.code, 0);
        assert!(!success.error);
        assert_eq!(success.text, "saved");
    }

    #[test]
    fn success_envelope_wraps_command_data_and_reports_invalid_internal_json() {
        let response = Response::from_result(
            Ok(json!({ "path": "/workspace", "journal_records": 2 }).to_string()),
            true,
            "verify",
        );
        assert_eq!(response.code, 0);
        let value: Value = serde_json::from_str(&response.text).unwrap();
        assert_envelope(&value, "ok");
        assert_eq!(value["command"], "verify");
        assert_eq!(
            value["data"],
            json!({ "path": "/workspace", "journal_records": 2 })
        );
        assert_eq!(value["error"], Value::Null);

        let invalid = Response::from_result(Ok("not json".to_owned()), true, "verify");
        assert_eq!(invalid.code, 1);
        let value: Value = serde_json::from_str(&invalid.text).unwrap();
        assert_envelope(&value, "error");
        assert_eq!(value["error"]["code"], "INVALID_RESPONSE");
    }

    #[test]
    fn document_rendering_preserves_raw_diff_and_json_data() {
        let diff = "-old\n+new\n";
        assert_eq!(document(json!(diff), false).unwrap(), diff);
        assert_eq!(
            serde_json::from_str::<serde_json::Value>(&document(json!(diff), true).unwrap())
                .unwrap(),
            json!(diff),
        );
        let data = json!({ "title": "Policy" });
        assert_eq!(
            document(data.clone(), false).unwrap(),
            serde_json::to_string_pretty(&data).unwrap()
        );
    }

    fn assert_envelope(value: &Value, status: &str) {
        assert_eq!(value["schema_version"], 2);
        assert_eq!(value["status"], status);
        assert!(value["timestamp_unix_ms"].as_i64().is_some());
        assert!(value["findings"].is_array());
        assert!(value["references"].is_array());
        assert!(value.get("data").is_some());
        assert!(value.get("error").is_some());
    }
}
