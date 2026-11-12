//! Advisory conformance of a document body to a template's structure (EQMS-114).
//! Findings are warnings only; they never block saving and do not certify compliance.

use crate::template::Template;
use serde::Serialize;

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub struct Finding {
    pub code: &'static str,
    pub severity: &'static str,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub section: Option<String>,
}

impl Finding {
    pub fn warning(code: &'static str, message: String, section: Option<&str>) -> Self {
        Self {
            code,
            severity: "warning",
            message,
            section: section.map(str::to_owned),
        }
    }
}

/// ATX headings (`#` to `######`) outside fenced code, as (level, title).
fn headings(body: &str) -> Vec<(u8, String)> {
    let mut found = Vec::new();
    let mut fence: Option<(char, usize)> = None;
    for line in body.lines() {
        let trimmed = line.trim_start();
        if line.len() - trimmed.len() > 3 {
            continue;
        }
        let marker = trimmed.chars().next().filter(|c| matches!(c, '`' | '~'));
        if let Some(c) = marker {
            let run = trimmed.chars().take_while(|x| *x == c).count();
            if run >= 3 {
                match fence {
                    None => fence = Some((c, run)),
                    Some((open, len))
                        if open == c && run >= len && trimmed[run..].trim().is_empty() =>
                    {
                        fence = None
                    }
                    _ => {}
                }
                continue;
            }
        }
        if fence.is_some() {
            continue;
        }
        let level = trimmed.chars().take_while(|c| *c == '#').count();
        if (1..=6).contains(&level) {
            let rest = &trimmed[level..];
            if rest.is_empty() || rest.starts_with([' ', '\t']) {
                let title = rest.trim().trim_end_matches('#').trim_end();
                found.push((level as u8, title.trim().to_owned()));
            }
        }
    }
    found
}

pub fn check_structure(template: &Template, body: &str) -> Vec<Finding> {
    let headings = headings(body);
    let mut findings = Vec::new();
    let mut furthest: Option<usize> = None;
    for section in &template.structure.sections {
        let position = headings
            .iter()
            .position(|(_, title)| *title == section.title);
        let Some(position) = position else {
            if section.required {
                findings.push(Finding::warning(
                    "structure.section_missing",
                    format!("required section `{}` is missing", section.title),
                    Some(&section.title),
                ));
            }
            continue;
        };
        let level = headings
            .iter()
            .filter(|(_, title)| *title == section.title)
            .map(|(level, _)| *level)
            .collect::<Vec<_>>();
        if !level.contains(&section.level) {
            findings.push(Finding::warning(
                "structure.section_level",
                format!(
                    "section `{}` is level {} but the template expects level {}",
                    section.title, level[0], section.level
                ),
                Some(&section.title),
            ));
        }
        match furthest {
            Some(seen) if position < seen => findings.push(Finding::warning(
                "structure.section_order",
                format!(
                    "section `{}` appears before a section the template lists earlier",
                    section.title
                ),
                Some(&section.title),
            )),
            _ => furthest = Some(position),
        }
    }
    findings
}

#[cfg(test)]
mod tests {
    use super::*;

    fn codes(body: &str) -> Vec<(&'static str, Option<String>)> {
        check_structure(&Template::builtin_generic(), body)
            .into_iter()
            .map(|f| (f.code, f.section))
            .collect()
    }

    #[test]
    fn conforming_body_has_no_findings() {
        assert!(codes("## Purpose\n\n## Scope\n\n## Content\n").is_empty());
        assert!(codes("## Purpose\n## Scope\n## Content\n\n### Extra\n# Title\n").is_empty());
    }

    #[test]
    fn missing_required_sections_are_reported() {
        let found = codes("## Purpose\n");
        assert_eq!(
            found,
            vec![
                ("structure.section_missing", Some("Scope".to_owned())),
                ("structure.section_missing", Some("Content".to_owned())),
            ]
        );
        assert_eq!(codes("").len(), 3);
    }

    #[test]
    fn optional_sections_may_be_absent() {
        let mut template = Template::builtin_generic();
        template.structure.sections[1].required = false;
        assert!(check_structure(&template, "## Purpose\n## Content\n").is_empty());
        let out_of_order = check_structure(&template, "## Content\n## Scope\n## Purpose\n");
        assert_eq!(out_of_order.len(), 2);
    }

    #[test]
    fn wrong_level_and_order_are_reported() {
        assert_eq!(
            codes("# Purpose\n## Scope\n## Content\n"),
            vec![("structure.section_level", Some("Purpose".to_owned()))]
        );
        assert_eq!(
            codes("## Scope\n## Purpose\n## Content\n"),
            vec![("structure.section_order", Some("Scope".to_owned()))]
        );
    }

    #[test]
    fn headings_in_fenced_code_are_ignored() {
        let body = "## Purpose\n```\n## Scope\n```\n~~~\n## Content\n~~~\n";
        assert_eq!(
            codes(body),
            vec![
                ("structure.section_missing", Some("Scope".to_owned())),
                ("structure.section_missing", Some("Content".to_owned())),
            ]
        );
        assert!(codes("## Purpose ##\n##Scope\n## Scope\n## Content\n").is_empty());
    }

    #[test]
    fn findings_are_warnings() {
        let findings = check_structure(&Template::builtin_generic(), "");
        assert!(findings.iter().all(|f| f.severity == "warning"));
    }
}
