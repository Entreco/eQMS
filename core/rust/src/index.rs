use crate::workspace::{Workspace, WorkspaceError};
use rusqlite::{Connection, params};
use sha2::{Digest, Sha256};
use std::error::Error;
use std::fmt::{self, Display, Formatter};
use std::fs::{self, symlink_metadata};
use std::io::ErrorKind;
use std::path::{Component, Path, PathBuf};

#[derive(Debug)]
pub struct IndexError(String);

impl Display for IndexError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.0)
    }
}

impl Error for IndexError {}

impl From<rusqlite::Error> for IndexError {
    fn from(error: rusqlite::Error) -> Self {
        Self(error.to_string())
    }
}

impl From<WorkspaceError> for IndexError {
    fn from(error: WorkspaceError) -> Self {
        Self(error.to_string())
    }
}

fn error(message: impl Into<String>) -> IndexError {
    IndexError(message.into())
}

struct Migration {
    version: i64,
    name: &'static str,
    sql: &'static str,
}

const MIGRATIONS: &[Migration] = &[Migration {
    version: 1,
    name: "create_document_index",
    sql: "
        CREATE TABLE schema_migrations (
            version INTEGER PRIMARY KEY,
            name TEXT NOT NULL
        );
        CREATE TABLE documents (
            path TEXT PRIMARY KEY,
            content TEXT NOT NULL,
            content_sha256 TEXT NOT NULL
        );
    ",
}, Migration {
    version: 2,
    name: "create_saved_draft_search",
    sql: "
        CREATE TABLE search_sources (id TEXT PRIMARY KEY, revision TEXT NOT NULL);
        CREATE TABLE search_state (singleton INTEGER PRIMARY KEY CHECK(singleton=1), snapshot TEXT NOT NULL);
        CREATE TABLE search_revisions (
            rowid INTEGER PRIMARY KEY,
            id TEXT NOT NULL, revision TEXT NOT NULL, ordinal INTEGER NOT NULL,
            title TEXT NOT NULL, author TEXT NOT NULL, timestamp TEXT NOT NULL,
            body TEXT NOT NULL,
            UNIQUE(id, revision)
        );
        CREATE INDEX search_revision_document ON search_revisions(id, ordinal);
        CREATE VIRTUAL TABLE search_fts USING fts5(title, author, body,
            tokenize='unicode61 remove_diacritics 0', prefix='1 2 3 4');
        CREATE TABLE search_deletions (
            key TEXT NOT NULL, term TEXT NOT NULL, PRIMARY KEY(key, term)
        ) WITHOUT ROWID;
    ",
}, Migration {
    version: 3,
    name: "cover_search_result_metadata",
    sql: "
        CREATE INDEX search_result_metadata ON search_revisions
            (rowid, ordinal, id, revision, title, author, timestamp);
    ",
}];

pub struct Index {
    pub(crate) connection: Connection,
}

impl Index {
    pub fn open(workspace: &Workspace) -> Result<Self, IndexError> {
        let _lock = workspace.acquire_lock()?;
        workspace.verify_unlocked()?;
        Self::open_unlocked(workspace)
    }

    pub fn rebuild(workspace: &Workspace) -> Result<usize, IndexError> {
        let _lock = workspace.acquire_lock()?;
        workspace.verify_unlocked()?;
        let mut index = Self::open_unlocked(workspace)?;
        let documents = read_documents(workspace)?;
        let transaction = index.connection.transaction()?;
        transaction.execute("DELETE FROM documents", [])?;
        {
            let mut insert = transaction.prepare(
                "INSERT INTO documents (path, content, content_sha256) VALUES (?1, ?2, ?3)",
            )?;
            for (relative_path, content) in &documents {
                let digest = format!("{:x}", Sha256::digest(content.as_bytes()));
                insert.execute(params![relative_path, content, digest])?;
            }
        }
        transaction.commit()?;
        Ok(documents.len())
    }

    pub fn schema_version(&self) -> Result<i64, IndexError> {
        Ok(self
            .connection
            .pragma_query_value(None, "user_version", |row| row.get(0))?)
    }

    pub fn document_count(&self) -> Result<usize, IndexError> {
        let count: i64 =
            self.connection
                .query_row("SELECT COUNT(*) FROM documents", [], |row| row.get(0))?;
        usize::try_from(count).map_err(|e| error(format!("invalid indexed document count: {e}")))
    }

    pub(crate) fn open_unlocked(workspace: &Workspace) -> Result<Self, IndexError> {
        let index_path = workspace.index_path();
        match symlink_metadata(&index_path) {
            Ok(metadata) if metadata.file_type().is_symlink() || !metadata.is_file() => {
                return Err(error("SQLite index path must be a regular file"));
            }
            Ok(_) => {}
            Err(io_error) if io_error.kind() == ErrorKind::NotFound => {}
            Err(io_error) => {
                return Err(error(format!(
                    "cannot inspect SQLite index path {}: {io_error}",
                    index_path.display()
                )));
            }
        }
        let connection = Connection::open(index_path)?;
        connection.pragma_update(None, "journal_mode", "WAL")?;
        connection.pragma_update(None, "foreign_keys", "ON")?;
        apply_migrations(&connection, MIGRATIONS)?;
        Ok(Self { connection })
    }
}

fn apply_migrations(connection: &Connection, migrations: &[Migration]) -> Result<(), IndexError> {
    for (index, migration) in migrations.iter().enumerate() {
        let expected_version = index as i64 + 1;
        if migration.version != expected_version {
            return Err(error(format!(
                "SQLite migrations are not ordered: expected version {expected_version}, found {}",
                migration.version
            )));
        }
    }
    let current_version: i64 =
        connection.pragma_query_value(None, "user_version", |row| row.get(0))?;
    let latest_version = migrations.last().map_or(0, |migration| migration.version);
    if current_version < 0 {
        return Err(error(format!(
            "invalid SQLite index schema version {current_version}"
        )));
    }
    if current_version > latest_version {
        return Err(error(format!(
            "SQLite index schema version {current_version} is newer than supported version {latest_version}"
        )));
    }
    let mut version = current_version;
    for migration in migrations
        .iter()
        .filter(|migration| migration.version > current_version)
    {
        if migration.version != version + 1 {
            return Err(error(format!(
                "SQLite migrations are not ordered: expected {}, found {}",
                version + 1,
                migration.version
            )));
        }
        let transaction = connection.unchecked_transaction()?;
        transaction.execute_batch(migration.sql)?;
        transaction.execute(
            "INSERT INTO schema_migrations (version, name) VALUES (?1, ?2)",
            params![migration.version, migration.name],
        )?;
        transaction.pragma_update(None, "user_version", migration.version)?;
        transaction.commit()?;
        version = migration.version;
    }
    validate_migration_history(connection, migrations, version)?;
    Ok(())
}

fn validate_migration_history(
    connection: &Connection,
    migrations: &[Migration],
    current_version: i64,
) -> Result<(), IndexError> {
    let mut statement =
        connection.prepare("SELECT version, name FROM schema_migrations ORDER BY version")?;
    let applied = statement
        .query_map([], |row| {
            Ok((row.get::<_, i64>(0)?, row.get::<_, String>(1)?))
        })?
        .collect::<Result<Vec<_>, _>>()?;
    let expected = migrations
        .iter()
        .take(current_version as usize)
        .map(|migration| (migration.version, migration.name.to_owned()))
        .collect::<Vec<_>>();
    if applied != expected {
        return Err(error(
            "SQLite index migration history does not match its schema version",
        ));
    }
    Ok(())
}

fn read_documents(workspace: &Workspace) -> Result<Vec<(String, String)>, IndexError> {
    let root = workspace.path();
    let document_root = root.join("documents");
    let mut paths = Vec::new();
    collect_markdown(&document_root, &mut paths)?;
    paths.sort();
    paths
        .into_iter()
        .map(|path| {
            let relative = path
                .strip_prefix(root)
                .map_err(|e| error(format!("document is outside workspace: {e}")))?
                .components()
                .map(|component| match component {
                    Component::Normal(part) => part
                        .to_str()
                        .ok_or_else(|| error("document path is not valid UTF-8")),
                    _ => Err(error("document path contains an invalid component")),
                })
                .collect::<Result<Vec<_>, _>>()?
                .join("/");
            let bytes = workspace
                .read_canonical_file(&relative)
                .map_err(|e| error(format!("cannot read canonical document {relative}: {e}")))?;
            let content = String::from_utf8(bytes)
                .map_err(|e| error(format!("canonical document {relative} is not UTF-8: {e}")))?;
            Ok((relative, content))
        })
        .collect()
}

fn collect_markdown(directory: &Path, paths: &mut Vec<PathBuf>) -> Result<(), IndexError> {
    let metadata = symlink_metadata(directory).map_err(|e| {
        error(format!(
            "cannot inspect canonical documents directory {}: {e}",
            directory.display()
        ))
    })?;
    if metadata.file_type().is_symlink() || !metadata.is_dir() {
        return Err(error(format!(
            "canonical documents path is not a regular directory: {}",
            directory.display()
        )));
    }
    let entries = fs::read_dir(directory).map_err(|e| {
        error(format!(
            "cannot read canonical documents directory {}: {e}",
            directory.display()
        ))
    })?;
    for entry in entries {
        let entry = entry.map_err(|e| error(format!("cannot inspect document entry: {e}")))?;
        let file_type = entry
            .file_type()
            .map_err(|e| error(format!("cannot inspect {}: {e}", entry.path().display())))?;
        let path = entry.path();
        if file_type.is_dir() {
            collect_markdown(&path, paths)?;
        } else if file_type.is_file() && path.extension().is_some_and(|extension| extension == "md")
        {
            paths.push(path);
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use rusqlite::Connection;
    use std::process::Command;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static NEXT: AtomicUsize = AtomicUsize::new(0);

    fn temp_dir() -> PathBuf {
        let path = std::env::temp_dir().join(format!(
            "eqms-index-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&path).unwrap();
        path
    }

    fn git(path: &Path, args: &[&str]) {
        let output = Command::new("git")
            .arg("-C")
            .arg(path)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    fn workspace(path: &Path) -> Workspace {
        git(path, &["init"]);
        git(path, &["config", "user.name", "Test User"]);
        git(path, &["config", "user.email", "test@example.invalid"]);
        Workspace::initialize(path).unwrap()
    }

    #[test]
    fn migrations_are_transactional_versioned_and_repeatable() {
        let path = temp_dir().join("index.sqlite3");
        let connection = Connection::open(&path).unwrap();
        apply_migrations(&connection, MIGRATIONS).unwrap();
        apply_migrations(&connection, MIGRATIONS).unwrap();
        let version: i64 = connection
            .pragma_query_value(None, "user_version", |row| row.get(0))
            .unwrap();
        let migration_count: i64 = connection
            .query_row("SELECT COUNT(*) FROM schema_migrations", [], |row| {
                row.get(0)
            })
            .unwrap();
        assert_eq!(version, 3);
        assert_eq!(migration_count, 3);
        drop(connection);
        fs::remove_dir_all(path.parent().unwrap()).unwrap();
    }

    #[test]
    fn upgrades_baseline_index_without_losing_existing_rows() {
        let connection = Connection::open_in_memory().unwrap();
        apply_migrations(&connection, &MIGRATIONS[..1]).unwrap();
        connection
            .execute(
                "INSERT INTO documents VALUES ('documents/a.md','# Original','digest')",
                [],
            )
            .unwrap();
        apply_migrations(&connection, MIGRATIONS).unwrap();
        assert_eq!(
            connection
                .query_row("SELECT content FROM documents", [], |row| row
                    .get::<_, String>(0))
                .unwrap(),
            "# Original"
        );
        connection
            .execute(
                "INSERT INTO search_fts(title,author,body) VALUES ('and','andi','this and that')",
                [],
            )
            .unwrap();
        assert_eq!(
            connection
                .query_row(
                    "SELECT count(*) FROM search_fts WHERE search_fts MATCH 'and*'",
                    [],
                    |row| row.get::<_, i64>(0)
                )
                .unwrap(),
            1
        );
    }

    #[test]
    fn failed_migration_rolls_back_schema_and_version() {
        let path = temp_dir().join("index.sqlite3");
        let connection = Connection::open(&path).unwrap();
        let failing_migration = [Migration {
            version: 1,
            name: "failing_migration",
            sql: "CREATE TABLE should_rollback (id INTEGER); INVALID SQL;",
        }];
        assert!(apply_migrations(&connection, &failing_migration).is_err());
        let version: i64 = connection
            .pragma_query_value(None, "user_version", |row| row.get(0))
            .unwrap();
        assert_eq!(version, 0);
        assert!(connection.prepare("SELECT 1 FROM should_rollback").is_err());
        drop(connection);
        fs::remove_dir_all(path.parent().unwrap()).unwrap();
    }

    #[test]
    fn rejects_a_future_schema_version() {
        let path = temp_dir().join("index.sqlite3");
        let connection = Connection::open(&path).unwrap();
        connection.pragma_update(None, "user_version", 4).unwrap();
        let error = apply_migrations(&connection, MIGRATIONS).unwrap_err();
        assert!(error.to_string().contains("newer than supported"));
        drop(connection);
        fs::remove_dir_all(path.parent().unwrap()).unwrap();
    }

    #[test]
    fn index_open_rejects_a_future_schema_version() {
        let path = temp_dir();
        let workspace = workspace(&path);
        let connection = Connection::open(workspace.index_path()).unwrap();
        connection.pragma_update(None, "user_version", 4).unwrap();
        drop(connection);
        let error = match Index::open(&workspace) {
            Ok(_) => panic!("future schema version should be rejected"),
            Err(error) => error,
        };
        assert!(error.to_string().contains("newer than supported"));
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn rebuilds_from_documents_without_mutating_git_or_journal_history() {
        let path = temp_dir();
        let workspace = workspace(&path);
        fs::write(path.join("documents/one.md"), "# One\n").unwrap();
        fs::create_dir(path.join("documents/nested")).unwrap();
        fs::write(path.join("documents/nested/two.md"), "# Two\n").unwrap();
        fs::write(path.join("documents/ignored.txt"), "ignored").unwrap();
        let head_before = {
            let output = Command::new("git")
                .arg("-C")
                .arg(&path)
                .args(["rev-parse", "HEAD"])
                .output()
                .unwrap();
            String::from_utf8(output.stdout).unwrap()
        };
        let journal_before = fs::read(path.join(".eqms/journal.jsonl")).unwrap();

        assert_eq!(Index::rebuild(&workspace).unwrap(), 2);
        assert_eq!(Index::rebuild(&workspace).unwrap(), 2);
        let index = Index::open(&workspace).unwrap();
        assert_eq!(index.schema_version().unwrap(), 3);
        assert_eq!(index.document_count().unwrap(), 2);
        assert_eq!(
            fs::read(path.join(".eqms/journal.jsonl")).unwrap(),
            journal_before
        );
        let head_after = {
            let output = Command::new("git")
                .arg("-C")
                .arg(&path)
                .args(["rev-parse", "HEAD"])
                .output()
                .unwrap();
            String::from_utf8(output.stdout).unwrap()
        };
        assert_eq!(head_before, head_after);
        fs::remove_dir_all(path).unwrap();
    }

    #[cfg(unix)]
    #[test]
    fn index_preserves_literal_backslashes_in_unix_filenames() {
        let path = temp_dir();
        let workspace = workspace(&path);
        fs::write(path.join("documents/a\\b.md"), "# Literal backslash\n").unwrap();
        fs::create_dir(path.join("documents/a")).unwrap();
        fs::write(path.join("documents/a/b.md"), "# Nested path\n").unwrap();

        assert_eq!(Index::rebuild(&workspace).unwrap(), 2);
        let connection = Connection::open(workspace.index_path()).unwrap();
        let rows = connection
            .prepare("SELECT path, content FROM documents ORDER BY path")
            .unwrap()
            .query_map([], |row| {
                Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?))
            })
            .unwrap()
            .collect::<Result<Vec<_>, _>>()
            .unwrap();
        assert_eq!(
            rows,
            vec![
                ("documents/a/b.md".into(), "# Nested path\n".into()),
                ("documents/a\\b.md".into(), "# Literal backslash\n".into()),
            ]
        );

        fs::remove_dir_all(path).unwrap();
    }

    #[cfg(unix)]
    #[test]
    fn rebuild_does_not_follow_symlinked_document_paths() {
        use std::os::unix::fs::symlink;

        let path = temp_dir();
        let workspace = workspace(&path);
        let outside = temp_dir();
        fs::write(outside.join("secret.md"), "# External\n").unwrap();
        symlink(
            outside.join("secret.md"),
            path.join("documents/file-link.md"),
        )
        .unwrap();
        symlink(&outside, path.join("documents/directory-link")).unwrap();

        assert_eq!(Index::rebuild(&workspace).unwrap(), 0);
        assert_eq!(
            Index::open(&workspace).unwrap().document_count().unwrap(),
            0
        );

        fs::remove_dir_all(outside).unwrap();
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn failed_rebuild_preserves_the_previous_index_contents() {
        let path = temp_dir();
        let workspace = workspace(&path);
        fs::write(path.join("documents/one.md"), "# One\n").unwrap();
        assert_eq!(Index::rebuild(&workspace).unwrap(), 1);
        let connection = Connection::open(workspace.index_path()).unwrap();
        connection
            .execute_batch(
                "CREATE TRIGGER reject_document BEFORE INSERT ON documents
                 BEGIN SELECT RAISE(ABORT, 'test failure'); END;",
            )
            .unwrap();
        drop(connection);

        assert!(Index::rebuild(&workspace).is_err());
        let index = Index::open(&workspace).unwrap();
        assert_eq!(index.document_count().unwrap(), 1);
        fs::remove_dir_all(path).unwrap();
    }
}
