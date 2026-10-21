use crate::document::{Document, Documents};
use crate::index::Index;
use crate::workspace::{WorkspaceError, error, git};
use rusqlite::{Connection, OptionalExtension, params};
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet, HashSet};
use std::path::{Path, PathBuf};

const PAGE_SIZE: usize = 5;

#[derive(Default)]
pub struct SearchQuery<'a> {
    pub text: &'a str,
    pub snapshot: Option<&'a str>,
    pub offsets: [usize; 3],
}

#[derive(Clone, Debug, Serialize)]
pub struct SearchHit {
    pub id: String,
    pub title: String,
    pub revision: String,
    pub configured_author: String,
    pub local_timestamp: String,
    pub excerpt: String,
}

#[derive(Debug, Serialize)]
pub struct SearchGroup {
    pub hits: Vec<SearchHit>,
    pub next_offset: Option<usize>,
}

#[derive(Debug, Serialize)]
pub struct SearchResults {
    pub snapshot: String,
    pub historical: bool,
    pub documents: SearchGroup,
    pub authors: SearchGroup,
    pub content: SearchGroup,
}

#[derive(Debug, Serialize)]
pub struct Preparation {
    pub snapshot: String,
    pub indexed_revisions: usize,
}

pub trait SearchEngine {
    fn prepare(&self, rebuild: bool) -> Result<Preparation, WorkspaceError>;
    fn search(&self, query: &SearchQuery<'_>) -> Result<SearchResults, WorkspaceError>;
}

pub struct SqliteSearch {
    path: PathBuf,
}

fn db_error(e: impl std::fmt::Display) -> WorkspaceError {
    error(format!(
        "Search index error: {e}. Run qms search <path> --prepare --rebuild; if the SQLite file is corrupt, move only the disposable index aside before preparing."
    ))
}

impl SqliteSearch {
    pub fn new(path: impl AsRef<Path>) -> Self {
        Self {
            path: path.as_ref().to_owned(),
        }
    }
}

fn snapshot(sources: &BTreeMap<String, String>, baseline: &str) -> String {
    let mut digest = Sha256::new();
    digest.update(b"eqms-search-v1\0");
    digest.update(baseline);
    for (id, revision) in sources {
        digest.update([0]);
        digest.update(id);
        digest.update([0]);
        digest.update(revision);
    }
    format!("{:x}", digest.finalize())
}

impl SearchEngine for SqliteSearch {
    fn prepare(&self, rebuild: bool) -> Result<Preparation, WorkspaceError> {
        Documents::with_search_sources(&self.path, |documents, workspace, sources, baseline| {
            let mut index = Index::open_unlocked(workspace).map_err(db_error)?;
            let token = snapshot(sources, baseline);
            let old = indexed_sources(&index.connection).map_err(db_error)?;
            // Verify before opening the write transaction; failures preserve the previous index.
            let mut changed = BTreeMap::new();
            for (id, revision) in sources {
                if rebuild || old.get(id) != Some(revision) {
                    changed.insert(id.clone(), documents.history_at(id, revision)?);
                }
            }
            // Standard Git housekeeping preserves ref names/OIDs while avoiding a loose-file
            // read per saved document on every warm search.
            git(workspace.path(), &["pack-refs", "--all"])?;
            let transaction = index.connection.transaction().map_err(db_error)?;
            if rebuild {
                transaction
                    .execute_batch(
                        "DELETE FROM search_fts; DELETE FROM search_revisions;
                    DELETE FROM search_sources; DELETE FROM search_deletions;",
                    )
                    .map_err(db_error)?;
            } else {
                for id in old
                    .keys()
                    .filter(|id| !sources.contains_key(*id) || changed.contains_key(*id))
                {
                    transaction.execute("DELETE FROM search_fts WHERE rowid IN (SELECT rowid FROM search_revisions WHERE id=?1)", [id]).map_err(db_error)?;
                    transaction
                        .execute("DELETE FROM search_revisions WHERE id=?1", [id])
                        .map_err(db_error)?;
                    transaction
                        .execute("DELETE FROM search_sources WHERE id=?1", [id])
                        .map_err(db_error)?;
                }
            }
            for (id, history) in &changed {
                insert_history(&transaction, history).map_err(db_error)?;
                transaction
                    .execute(
                        "INSERT INTO search_sources VALUES (?1,?2)",
                        params![id, sources[id]],
                    )
                    .map_err(db_error)?;
            }
            transaction
                .execute(
                    "INSERT INTO search_state VALUES (1,?1)
                ON CONFLICT(singleton) DO UPDATE SET snapshot=excluded.snapshot",
                    [&token],
                )
                .map_err(db_error)?;
            let count: i64 = transaction
                .query_row("SELECT count(*) FROM search_revisions", [], |r| r.get(0))
                .map_err(db_error)?;
            transaction.commit().map_err(db_error)?;
            Ok(Preparation {
                snapshot: token,
                indexed_revisions: usize::try_from(count).map_err(db_error)?,
            })
        })
    }

    fn search(&self, query: &SearchQuery<'_>) -> Result<SearchResults, WorkspaceError> {
        if query.offsets.iter().any(|n| *n > 0) && query.snapshot.is_none() {
            return Err(error(
                "Search pagination requires --snapshot; restart the search.",
            ));
        }
        if query.text.chars().count() > 512 {
            return Err(error("Search text must be at most 512 characters."));
        }
        Documents::with_search_sources(&self.path, |_, workspace, sources, baseline| {
            let index = Index::open_unlocked(workspace).map_err(db_error)?;
            let token = snapshot(sources, baseline);
            let prepared: Option<String> = index
                .connection
                .query_row(
                    "SELECT snapshot FROM search_state WHERE singleton=1",
                    [],
                    |r| r.get(0),
                )
                .optional()
                .map_err(db_error)?;
            if prepared.as_deref() != Some(&token) {
                return Err(error(
                    "Search index is out of date. Run qms search <path> --prepare.",
                ));
            }
            if query.snapshot.is_some_and(|s| s != token) {
                return Err(error("Search snapshot changed; restart the search."));
            }
            let tokens = tokens(query.text);
            if tokens.len() > 32 {
                return Err(error("Search text must contain at most 32 words."));
            }
            let expressions = expressions(&index.connection, &tokens).map_err(db_error)?;
            let (mut groups, found) =
                groups(&index.connection, &expressions, false, query.offsets).map_err(db_error)?;
            let historical = !tokens.is_empty() && !found;
            if historical {
                groups = self::groups(&index.connection, &expressions, true, query.offsets)
                    .map_err(db_error)?
                    .0;
            }
            let [documents, authors, content] = groups;
            Ok(SearchResults {
                snapshot: token,
                historical,
                documents,
                authors,
                content,
            })
        })
    }
}

fn indexed_sources(connection: &Connection) -> rusqlite::Result<BTreeMap<String, String>> {
    connection
        .prepare("SELECT id,revision FROM search_sources")?
        .query_map([], |r| Ok((r.get(0)?, r.get(1)?)))?
        .collect()
}

pub(crate) fn tokens(text: &str) -> Vec<String> {
    text.replace('&', " and ")
        .to_lowercase()
        .split(|c: char| !c.is_alphanumeric())
        .filter(|t| !t.is_empty())
        .map(str::to_owned)
        .collect()
}

fn deletion_keys(term: &str) -> BTreeSet<String> {
    let chars: Vec<_> = term.chars().collect();
    let mut keys = BTreeSet::from([term.to_owned()]);
    for i in 0..chars.len() {
        keys.insert(
            chars
                .iter()
                .enumerate()
                .filter_map(|(j, c)| (i != j).then_some(c))
                .collect(),
        );
    }
    keys
}

fn one_edit(left: &str, right: &str) -> bool {
    let a: Vec<_> = left.chars().collect();
    let b: Vec<_> = right.chars().collect();
    if a.len().abs_diff(b.len()) > 1 {
        return false;
    }
    let first = a
        .iter()
        .zip(&b)
        .position(|(x, y)| x != y)
        .unwrap_or(a.len().min(b.len()));
    if first == a.len().min(b.len()) {
        return true;
    }
    if a.len() == b.len() {
        a[first + 1..] == b[first + 1..]
            || (first + 1 < a.len()
                && a[first] == b[first + 1]
                && a[first + 1] == b[first]
                && a[first + 2..] == b[first + 2..])
    } else if a.len() > b.len() {
        a[first + 1..] == b[first..]
    } else {
        a[first..] == b[first + 1..]
    }
}

fn insert_history(connection: &Connection, history: &[Document]) -> rusqlite::Result<()> {
    let mut terms = BTreeSet::new();
    let mut insert = connection.prepare(
        "INSERT INTO search_revisions
        (id,revision,ordinal,title,author,timestamp,body) VALUES (?1,?2,?3,?4,?5,?6,?7)",
    )?;
    let mut fts = connection
        .prepare("INSERT INTO search_fts(rowid,title,author,body) VALUES (?1,?2,?3,?4)")?;
    for (ordinal, doc) in history.iter().enumerate() {
        insert.execute(params![
            doc.id,
            doc.revision,
            ordinal as i64,
            doc.title,
            doc.configured_author,
            doc.local_timestamp,
            doc.body
        ])?;
        let fields = [&doc.title, &doc.configured_author, &doc.body].map(|s| tokens(s));
        fts.execute(params![
            connection.last_insert_rowid(),
            fields[0].join(" "),
            fields[1].join(" "),
            fields[2].join(" ")
        ])?;
        for field in fields {
            terms.extend(field);
        }
    }
    let mut deletion =
        connection.prepare("INSERT OR IGNORE INTO search_deletions VALUES (?1,?2)")?;
    for term in terms {
        // Queries are capped at 512 characters, so longer terms cannot be one-edit candidates.
        if term.chars().count() > 513 {
            continue;
        }
        for key in deletion_keys(&term) {
            deletion.execute(params![key, term])?;
        }
    }
    Ok(())
}

fn expressions(connection: &Connection, tokens: &[String]) -> rusqlite::Result<Vec<String>> {
    if tokens.is_empty() {
        return Ok(Vec::new());
    }
    let mut exact = Vec::new();
    let mut prefix = Vec::new();
    let mut fuzzy = Vec::new();
    let mut statement = connection.prepare("SELECT term FROM search_deletions WHERE key=?1")?;
    for token in tokens {
        exact.push(format!("\"{token}\""));
        prefix.push(format!("\"{token}\"*"));
        let mut candidates = BTreeSet::new();
        if token.chars().count() >= 4 {
            for key in deletion_keys(token) {
                let matches = statement.query_map([key], |r| r.get::<_, String>(0))?;
                for term in matches {
                    let term = term?;
                    if !term.starts_with(token) && one_edit(token, &term) {
                        candidates.insert(term);
                    }
                }
            }
        }
        let mut alternatives = vec![format!("\"{token}\"*")];
        alternatives.extend(candidates.into_iter().map(|t| format!("\"{t}\"")));
        fuzzy.push(format!("({})", alternatives.join(" OR ")));
    }
    Ok(vec![
        exact.join(" AND "),
        prefix.join(" AND "),
        fuzzy.join(" AND "),
    ])
}

fn groups(
    connection: &Connection,
    expressions: &[String],
    historical: bool,
    offsets: [usize; 3],
) -> rusqlite::Result<([SearchGroup; 3], bool)> {
    let mut result = Vec::new();
    let mut found = false;
    for (field, offset) in ["title", "author", "body"].into_iter().zip(offsets) {
        let limit = offset.saturating_add(PAGE_SIZE + 1);
        let limit_sql = i64::try_from(limit).unwrap_or(i64::MAX);
        let mut hits = Vec::new();
        let mut seen = HashSet::new();
        for expression in expressions {
            let expression = format!("{field} : ({expression})");
            let sql = if !historical {
                "SELECT r.id,r.title,r.revision,r.author,r.timestamp,r.rowid
                 FROM search_fts JOIN search_revisions r INDEXED BY search_result_metadata ON r.rowid=search_fts.rowid
                 WHERE search_fts MATCH ?1 AND r.ordinal=0
                 ORDER BY bm25(search_fts),r.id,r.revision LIMIT ?2"
                    .to_owned()
            } else {
                "WITH matches AS MATERIALIZED (
                SELECT r.rowid,r.id,r.revision,r.ordinal,r.title,r.author,r.timestamp,
                    bm25(search_fts) AS score
                FROM search_fts JOIN search_revisions r INDEXED BY search_result_metadata ON r.rowid=search_fts.rowid
                WHERE search_fts MATCH ?1 AND r.ordinal > 0
            ), best AS (
                SELECT *, row_number() OVER (PARTITION BY id ORDER BY score,ordinal,revision) AS rn FROM matches
            ) SELECT id,title,revision,author,timestamp,rowid FROM best WHERE rn=1
              ORDER BY score,id,ordinal,revision LIMIT ?2".to_owned()
            };
            let mut statement = connection.prepare(&sql)?;
            let rows = statement.query_map(params![expression, limit_sql], |r| {
                Ok((
                    SearchHit {
                        id: r.get(0)?,
                        title: r.get(1)?,
                        revision: r.get(2)?,
                        configured_author: r.get(3)?,
                        local_timestamp: r.get(4)?,
                        excerpt: String::new(),
                    },
                    r.get::<_, i64>(5)?,
                ))
            })?;
            for row in rows {
                let (hit, rowid) = row?;
                if seen.insert(hit.id.clone()) {
                    hits.push((hit, rowid));
                }
            }
            if hits.len() >= limit {
                break;
            }
        }
        found |= !hits.is_empty();
        let has_more = hits.len() > offset.saturating_add(PAGE_SIZE);
        let mut page = Vec::new();
        for (mut hit, rowid) in hits.into_iter().skip(offset).take(PAGE_SIZE) {
            if field == "body" {
                let body: String = connection.query_row(
                    "SELECT body FROM search_revisions WHERE rowid=?1",
                    [rowid],
                    |r| r.get(0),
                )?;
                hit.excerpt = excerpt(&body, expressions);
            }
            page.push(hit);
        }
        result.push(SearchGroup {
            hits: page,
            next_offset: has_more.then_some(offset.saturating_add(PAGE_SIZE)),
        });
    }
    let [documents, authors, content] = result.try_into().expect("three search fields");
    Ok(([documents, authors, content], found))
}

fn excerpt(body: &str, expressions: &[String]) -> String {
    let terms: HashSet<_> = expressions
        .iter()
        .flat_map(|e| e.split('"').skip(1).step_by(2))
        .collect();
    let mut start = 0;
    for (index, _) in body.char_indices() {
        if index > 0
            && body[..index]
                .chars()
                .next_back()
                .is_some_and(char::is_alphanumeric)
        {
            continue;
        }
        let word: String = body[index..]
            .chars()
            .take_while(|c| c.is_alphanumeric() || *c == '&')
            .collect();
        let normalized = tokens(&word).join("");
        if !normalized.is_empty() && terms.iter().any(|t| normalized.starts_with(t)) {
            start = index;
            break;
        }
    }
    let chars: Vec<_> = body.chars().collect();
    let position = body[..start].chars().count().saturating_sub(40);
    let end = (position + 180).min(chars.len());
    format!(
        "{}{}{}",
        if position > 0 { "..." } else { "" },
        chars[position..end]
            .iter()
            .collect::<String>()
            .replace(['\r', '\n'], " "),
        if end < chars.len() { "..." } else { "" }
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::workspace::{Workspace, git};
    use std::fs;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static NEXT: AtomicUsize = AtomicUsize::new(0);
    struct Fixture(PathBuf);
    impl Fixture {
        fn new() -> Self {
            let path = std::env::temp_dir().join(format!(
                "eqms-search-{}-{}",
                std::process::id(),
                NEXT.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir(&path).unwrap();
            git(&path, &["init", "--quiet"]).unwrap();
            git(&path, &["config", "user.name", "Andi"]).unwrap();
            git(&path, &["config", "user.email", "search@example.invalid"]).unwrap();
            Workspace::initialize(&path).unwrap();
            Self(path)
        }
        fn documents(&self) -> Documents {
            Documents::open(&self.0).unwrap()
        }
        fn engine(&self) -> SqliteSearch {
            SqliteSearch::new(&self.0)
        }
        fn search(&self, text: &str) -> SearchResults {
            self.engine()
                .search(&SearchQuery {
                    text,
                    ..Default::default()
                })
                .unwrap()
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) {
            fs::remove_dir_all(&self.0).unwrap();
        }
    }

    #[test]
    fn normalization_and_one_edit_distance() {
        assert_eq!(tokens("ÄND R & D \"AND\""), ["änd", "r", "and", "d", "and"]);
        for (a, b) in [
            ("risk", "risks"),
            ("risks", "risk"),
            ("risk", "rusk"),
            ("risk", "irsk"),
            ("risk", "risk"),
            ("äbcd", "äbdc"),
        ] {
            assert!(one_edit(a, b), "{a} {b}");
            assert!(!deletion_keys(a).is_disjoint(&deletion_keys(b)));
        }
        for (a, b) in [("risk", "desk"), ("risk", "risksxx"), ("abcd", "badc")] {
            assert!(!one_edit(a, b));
        }
    }

    #[test]
    fn examples_group_by_field_and_literal_queries_are_safe() {
        let f = Fixture::new();
        for title in [
            "And - Some Document",
            "Research And Development",
            "R & D",
            "Process - pFMEA",
            "Risk Management Matrix",
        ] {
            f.documents()
                .create(
                    title,
                    if title.starts_with("Risk") {
                        "This and That"
                    } else {
                        "unrelated"
                    },
                )
                .unwrap();
        }
        f.engine().prepare(false).unwrap();
        let result = f.search("And");
        assert!(!result.historical);
        assert_eq!(result.documents.hits.len(), 3);
        assert_eq!(result.authors.hits.len(), 5);
        assert_eq!(result.content.hits.len(), 1);
        assert_eq!(result.content.hits[0].excerpt, "This and That");
        assert_eq!(f.search("R & D").documents.hits[0].title, "R & D");
        assert_eq!(f.search("\"AND\" OR * -").documents.hits.len(), 0);
        assert_eq!(f.search("Research Development").documents.hits.len(), 1);
        assert!(f.search("  ").documents.hits.is_empty());
        assert!(!f.search("  ").historical);
    }

    #[test]
    fn fuzzy_operations_and_ranking() {
        let f = Fixture::new();
        for title in ["Risk", "Risky", "Rusk", "Irsk"] {
            f.documents().create(title, "").unwrap();
        }
        f.engine().prepare(false).unwrap();
        let matches = f.search("risk").documents.hits;
        assert_eq!(matches.len(), 4);
        assert_eq!(matches[0].title, "Risk");
        assert_eq!(matches[1].title, "Risky");
        for query in ["rsk", "riskx", "riks", "rusk"] {
            let matches = f.search(query).documents.hits;
            if query == "rsk" {
                assert!(matches.is_empty());
            } else {
                assert!(matches.iter().any(|h| h.title == "Risk"), "{query}");
            }
        }
    }

    #[test]
    fn history_fallback_is_global_and_deduplicated() {
        let f = Fixture::new();
        let first = f
            .documents()
            .create("Retired procedure", "Obsolete clause")
            .unwrap();
        let second = f
            .documents()
            .save(
                &first.id,
                &first.revision,
                "Retired handbook",
                "Obsolete instructions",
            )
            .unwrap();
        f.documents()
            .save(
                &first.id,
                &second.revision,
                "Current policy",
                "Current instructions",
            )
            .unwrap();
        f.engine().prepare(false).unwrap();
        let historical = f.search("Retired");
        assert!(historical.historical);
        assert_eq!(historical.documents.hits.len(), 1);
        assert_eq!(historical.documents.hits[0].revision, second.revision);
        assert!(f.search("Obsolete").historical);
        f.documents()
            .create("Different document", "Retired content in a current draft")
            .unwrap();
        f.engine().prepare(false).unwrap();
        let result = f.search("Retired");
        assert!(!result.historical);
        assert!(result.documents.hits.is_empty());
        assert_eq!(result.content.hits.len(), 1);
    }

    #[test]
    fn pagination_freshness_deletions_and_rebuild_preserve_canonical_data() {
        let f = Fixture::new();
        for n in 0..7 {
            f.documents()
                .create(&format!("Policy {n}"), "needle")
                .unwrap();
        }
        let baseline = git(&f.0, &["rev-parse", "HEAD"]).unwrap().stdout;
        let journal = fs::read(f.0.join(".eqms/journal.jsonl")).unwrap();
        fs::write(f.0.join("untracked.txt"), "leave me").unwrap();
        let before = git(&f.0, &["status", "--porcelain=v1"]).unwrap().stdout;
        git(&f.0, &["tag", "baseline-marker"]).unwrap();
        let refs_before = git(&f.0, &["show-ref"]).unwrap().stdout;
        assert!(
            f.engine()
                .search(&SearchQuery {
                    text: "policy",
                    ..Default::default()
                })
                .unwrap_err()
                .to_string()
                .contains("--prepare")
        );
        let prepared = f.engine().prepare(false).unwrap();
        assert_eq!(prepared.indexed_revisions, 7);
        assert_eq!(git(&f.0, &["show-ref"]).unwrap().stdout, refs_before);
        assert!(f.0.join(".git/packed-refs").is_file());
        let first = f.search("policy");
        assert_eq!(first.documents.hits.len(), 5);
        let second = f
            .engine()
            .search(&SearchQuery {
                text: "policy",
                snapshot: Some(&first.snapshot),
                offsets: [5, 0, 0],
            })
            .unwrap();
        assert_eq!(second.documents.hits.len(), 2);
        assert!(second.documents.next_offset.is_none());
        assert!(
            second.documents.hits.iter().all(|h| !first
                .documents
                .hits
                .iter()
                .any(|p| p.id == h.id))
        );
        assert!(
            f.engine()
                .search(&SearchQuery {
                    text: "policy",
                    offsets: [5, 0, 0],
                    ..Default::default()
                })
                .is_err()
        );
        let deleted = &first.documents.hits[0];
        git(
            &f.0,
            &[
                "update-ref",
                "-d",
                &format!("refs/heads/eqms-drafts/{}", deleted.id),
            ],
        )
        .unwrap();
        assert!(
            f.engine()
                .search(&SearchQuery {
                    text: "policy",
                    ..Default::default()
                })
                .is_err()
        );
        assert_eq!(f.engine().prepare(false).unwrap().indexed_revisions, 6);
        assert!(
            f.engine()
                .search(&SearchQuery {
                    text: "policy",
                    snapshot: Some(&first.snapshot),
                    offsets: [5, 0, 0]
                })
                .is_err()
        );
        assert_eq!(f.engine().prepare(true).unwrap().indexed_revisions, 6);
        assert_eq!(git(&f.0, &["rev-parse", "HEAD"]).unwrap().stdout, baseline);
        assert_eq!(fs::read(f.0.join(".eqms/journal.jsonl")).unwrap(), journal);
        assert_eq!(
            git(&f.0, &["status", "--porcelain=v1"]).unwrap().stdout,
            before
        );
    }

    #[test]
    fn incremental_verification_failure_keeps_previous_index() {
        let f = Fixture::new();
        let doc = f.documents().create("Valid policy", "original").unwrap();
        f.engine().prepare(false).unwrap();
        let workspace = Workspace::open(&f.0).unwrap();
        let before = Index::open(&workspace)
            .unwrap()
            .connection
            .query_row("SELECT snapshot FROM search_state", [], |r| {
                r.get::<_, String>(0)
            })
            .unwrap();
        let head = git(&f.0, &["rev-parse", "HEAD"]).unwrap();
        let head = String::from_utf8(head.stdout).unwrap();
        git(
            &f.0,
            &[
                "update-ref",
                &format!("refs/heads/eqms-drafts/{}", doc.id),
                head.trim(),
            ],
        )
        .unwrap();
        assert!(f.engine().prepare(false).is_err());
        let index = Index::open(&workspace).unwrap();
        assert_eq!(
            index
                .connection
                .query_row("SELECT snapshot FROM search_state", [], |r| r
                    .get::<_, String>(0))
                .unwrap(),
            before
        );
        assert_eq!(
            index
                .connection
                .query_row("SELECT count(*) FROM search_revisions", [], |r| r
                    .get::<_, i64>(0))
                .unwrap(),
            1
        );
    }

    #[test]
    fn historical_author_matches_only_after_no_current_field_matches() {
        let f = Fixture::new();
        git(&f.0, &["config", "user.name", "FormerAuthor"]).unwrap();
        let original = f.documents().create("Policy", "original text").unwrap();
        git(&f.0, &["config", "user.name", "CurrentAuthor"]).unwrap();
        f.documents()
            .save(&original.id, &original.revision, "Policy", "updated text")
            .unwrap();
        f.engine().prepare(false).unwrap();
        let result = f.search("FormerAuthor");
        assert!(result.historical);
        assert_eq!(result.authors.hits[0].revision, original.revision);
        assert!(
            result.authors.hits[0]
                .configured_author
                .starts_with("FormerAuthor")
        );
        assert!(!f.search("CurrentAuthor").historical);
        f.documents()
            .create("FormerAuthor reference", "current title matches")
            .unwrap();
        f.engine().prepare(false).unwrap();
        let result = f.search("FormerAuthor");
        assert!(!result.historical);
        assert!(result.authors.hits.is_empty());
    }

    #[test]
    fn failed_sql_refresh_rolls_back_rows_and_snapshot() {
        let f = Fixture::new();
        let original = f.documents().create("Policy", "original").unwrap();
        let before = f.engine().prepare(false).unwrap();
        let workspace = Workspace::open(&f.0).unwrap();
        Index::open(&workspace)
            .unwrap()
            .connection
            .execute_batch(
                "CREATE TRIGGER reject_search BEFORE INSERT ON search_revisions
             BEGIN SELECT RAISE(ABORT, 'injected failure'); END;",
            )
            .unwrap();
        f.documents()
            .save(&original.id, &original.revision, "Policy", "updated")
            .unwrap();
        assert!(
            f.engine()
                .prepare(false)
                .unwrap_err()
                .to_string()
                .contains("injected failure")
        );
        let index = Index::open(&workspace).unwrap();
        assert_eq!(
            index
                .connection
                .query_row("SELECT snapshot FROM search_state", [], |r| r
                    .get::<_, String>(0))
                .unwrap(),
            before.snapshot
        );
        assert_eq!(
            index
                .connection
                .query_row("SELECT body FROM search_revisions", [], |r| r
                    .get::<_, String>(0))
                .unwrap(),
            "original"
        );
    }
}
