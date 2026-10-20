use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::error::Error;
use std::fmt::{self, Display, Formatter};
use std::fs::{self, File, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::time::{SystemTime, UNIX_EPOCH};

const CONFIG_PATH: &str = ".eqms/config.json";
const JOURNAL_PATH: &str = ".eqms/journal.jsonl";
const MODULE_MANIFEST_PATH: &str = "modules/manifest.json";
const WORKSPACE_SCHEMA_VERSION: u32 = 1;

#[derive(Debug)]
pub struct WorkspaceError(String);

impl Display for WorkspaceError {
    fn fmt(&self, formatter: &mut Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.0)
    }
}

impl Error for WorkspaceError {}

impl From<io::Error> for WorkspaceError {
    fn from(error: io::Error) -> Self {
        Self(error.to_string())
    }
}

fn error(message: impl Into<String>) -> WorkspaceError {
    WorkspaceError(message.into())
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct WorkspaceConfig {
    pub schema_version: u32,
    pub document_schema_version: u32,
    pub module_manifest_schema_version: u32,
    pub journal_schema_version: u32,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
struct ModuleManifest {
    schema_version: u32,
    modules: Vec<ModuleEntry>,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
struct ModuleEntry {
    id: String,
    version: String,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct JournalRecord {
    pub schema_version: u32,
    pub sequence: u64,
    pub previous_hash: Option<String>,
    pub timestamp: String,
    pub event_type: String,
    pub payload: BTreeMap<String, String>,
    pub content_hashes: BTreeMap<String, String>,
    pub hash: String,
}

#[derive(Serialize)]
struct JournalEvent<'a> {
    schema_version: u32,
    sequence: u64,
    previous_hash: &'a Option<String>,
    timestamp: &'a str,
    event_type: &'a str,
    payload: &'a BTreeMap<String, String>,
    content_hashes: &'a BTreeMap<String, String>,
}

#[derive(Clone, Debug)]
pub struct Workspace {
    path: PathBuf,
}

impl Workspace {
    pub fn initialize(path: impl AsRef<Path>) -> Result<Self, WorkspaceError> {
        let requested_path = path.as_ref();
        fs::create_dir_all(requested_path).map_err(|e| {
            error(format!(
                "cannot create workspace directory {}: {e}",
                requested_path.display()
            ))
        })?;
        let path = requested_path.canonicalize().map_err(|e| {
            error(format!(
                "cannot resolve workspace directory {}: {e}",
                requested_path.display()
            ))
        })?;
        let entries = fs::read_dir(&path)?
            .map(|entry| entry.map(|entry| entry.file_name()))
            .collect::<Result<Vec<_>, _>>()?;
        let empty_git_repository = entries.len() == 1 && entries[0] == ".git" && !has_head(&path);
        if !entries.is_empty() && !empty_git_repository {
            return Err(error(format!(
                "workspace directory is not empty: {}",
                path.display()
            )));
        }

        git(&path, &["init"])?;
        ensure_git_identity(&path)?;
        fs::create_dir_all(path.join(".eqms"))?;
        fs::create_dir_all(path.join("documents"))?;
        fs::create_dir_all(path.join("modules"))?;
        fs::write(
            path.join(".gitignore"),
            ".eqms/index.sqlite3\n.eqms/index.sqlite3-shm\n.eqms/index.sqlite3-wal\n",
        )?;
        fs::write(path.join("documents/.gitkeep"), "")?;
        fs::write(path.join("modules/.gitkeep"), "")?;

        let config = WorkspaceConfig {
            schema_version: WORKSPACE_SCHEMA_VERSION,
            document_schema_version: 1,
            module_manifest_schema_version: 1,
            journal_schema_version: WORKSPACE_SCHEMA_VERSION,
        };
        write_json(&path.join(CONFIG_PATH), &config)?;
        write_json(
            &path.join(MODULE_MANIFEST_PATH),
            &ModuleManifest {
                schema_version: 1,
                modules: Vec::new(),
            },
        )?;

        let event = JournalRecord {
            schema_version: WORKSPACE_SCHEMA_VERSION,
            sequence: 0,
            previous_hash: None,
            timestamp: timestamp()?,
            event_type: "workspace.genesis".to_owned(),
            payload: BTreeMap::new(),
            content_hashes: BTreeMap::new(),
            hash: String::new(),
        };
        let record = with_hash(event)?;
        write_journal(&path.join(JOURNAL_PATH), &[record])?;
        let initial_paths = [
            ".gitignore",
            CONFIG_PATH,
            JOURNAL_PATH,
            "documents/.gitkeep",
            MODULE_MANIFEST_PATH,
            "modules/.gitkeep",
        ];
        let mut add_args = vec!["add", "--"];
        add_args.extend(initial_paths);
        git(&path, &add_args)?;
        let mut commit_args = vec!["commit", "--only", "-m", "Initialize eQMS workspace", "--"];
        commit_args.extend(initial_paths);
        git(&path, &commit_args)?;

        let workspace = Self { path };
        workspace.verify()?;
        Ok(workspace)
    }

    pub fn open(path: impl AsRef<Path>) -> Result<Self, WorkspaceError> {
        let path = path.as_ref().canonicalize().map_err(|e| {
            error(format!(
                "cannot open workspace {}: {e}",
                path.as_ref().display()
            ))
        })?;
        for relative_path in [CONFIG_PATH, JOURNAL_PATH, MODULE_MANIFEST_PATH] {
            if !path.join(relative_path).is_file() {
                return Err(error(format!(
                    "workspace is missing {relative_path}: {}",
                    path.display()
                )));
            }
        }
        Ok(Self { path })
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn index_path(&self) -> PathBuf {
        self.path.join(".eqms/index.sqlite3")
    }

    pub fn verify(&self) -> Result<Vec<JournalRecord>, WorkspaceError> {
        let _lock = self.acquire_lock()?;
        self.verify_unlocked()
    }

    pub(crate) fn verify_unlocked(&self) -> Result<Vec<JournalRecord>, WorkspaceError> {
        let config_bytes = fs::read(self.path.join(CONFIG_PATH))?;
        let config: WorkspaceConfig = serde_json::from_slice(&config_bytes)
            .map_err(|e| error(format!("invalid workspace config: {e}")))?;
        if config.schema_version != WORKSPACE_SCHEMA_VERSION
            || config.document_schema_version != 1
            || config.module_manifest_schema_version != 1
            || config.journal_schema_version != WORKSPACE_SCHEMA_VERSION
        {
            return Err(error(format!(
                "unsupported workspace schema versions in {}",
                CONFIG_PATH
            )));
        }
        let manifest_bytes = fs::read(self.path.join(MODULE_MANIFEST_PATH))?;
        let manifest: ModuleManifest = serde_json::from_slice(&manifest_bytes)
            .map_err(|e| error(format!("invalid module manifest: {e}")))?;
        if manifest.schema_version != config.module_manifest_schema_version {
            return Err(error(format!(
                "unsupported module manifest schema version {}",
                manifest.schema_version
            )));
        }

        let journal_bytes = fs::read(self.path.join(JOURNAL_PATH))?;
        let records = parse_and_verify_journal(&journal_bytes)?;
        let git_root = git_text(&self.path, &["rev-parse", "--show-toplevel"])?;
        let git_root = Path::new(git_root.trim()).canonicalize()?;
        if git_root != self.path {
            return Err(error(format!(
                "workspace Git root {} does not match workspace path {}",
                git_root.display(),
                self.path.display()
            )));
        }
        if !has_head(&self.path) {
            return Err(error(
                "workspace has no initial Git commit; run `qms recover <path>`",
            ));
        }
        ensure_matches_head(&self.path, CONFIG_PATH, &config_bytes)?;
        ensure_matches_head(&self.path, JOURNAL_PATH, &journal_bytes)?;
        ensure_matches_head(&self.path, MODULE_MANIFEST_PATH, &manifest_bytes)?;
        verify_head_content_hashes(
            &self.path,
            &records
                .last()
                .expect("genesis record is required")
                .content_hashes,
        )?;
        Ok(records)
    }

    pub fn record_event(
        &self,
        event_type: &str,
        payload: BTreeMap<String, String>,
        content_hashes: BTreeMap<String, String>,
    ) -> Result<JournalRecord, WorkspaceError> {
        if event_type.is_empty() {
            return Err(error("journal event type must not be empty"));
        }
        validate_content_hashes(&content_hashes)?;
        let _lock = self.lock()?;
        self.verify_unlocked()?;

        let journal_path = self.path.join(JOURNAL_PATH);
        let bytes = fs::read(&journal_path)?;
        let records = parse_and_verify_journal(&bytes)?;
        let previous = records.last().expect("genesis record is required");
        verify_workspace_content_hashes(&self.path, &content_hashes)?;
        let event = JournalRecord {
            schema_version: WORKSPACE_SCHEMA_VERSION,
            sequence: previous
                .sequence
                .checked_add(1)
                .ok_or_else(|| error("journal sequence number overflow"))?,
            previous_hash: Some(previous.hash.clone()),
            timestamp: timestamp()?,
            event_type: event_type.to_owned(),
            payload,
            content_hashes,
            hash: String::new(),
        };
        let record = with_hash(event)?;
        let line = serde_json::to_vec(&record)
            .map_err(|e| error(format!("cannot encode journal event: {e}")))?;
        let write_result = (|| -> Result<(), io::Error> {
            let mut file = OpenOptions::new().append(true).open(&journal_path)?;
            file.write_all(&line)?;
            file.write_all(b"\n")?;
            file.sync_all()
        })();
        if let Err(write_error) = write_result {
            return Err(error(format!(
                "journal append failed; run `qms recover {}` before continuing: {write_error}",
                self.path.display()
            )));
        }

        let mut commit_args = vec![
            "commit".to_owned(),
            "--only".to_owned(),
            "-m".to_owned(),
            format!("Record {event_type} event"),
            "--".to_owned(),
            JOURNAL_PATH.to_owned(),
        ];
        commit_args.extend(record.content_hashes.keys().cloned());
        let commit_args = commit_args.iter().map(String::as_str).collect::<Vec<_>>();
        let mut add_args = vec!["add".to_owned(), "--".to_owned(), JOURNAL_PATH.to_owned()];
        add_args.extend(record.content_hashes.keys().cloned());
        let add_args = add_args.iter().map(String::as_str).collect::<Vec<_>>();
        if let Err(commit_error) = git(&self.path, &add_args) {
            return Err(error(format!(
                "journal event was appended but not staged; run `qms recover {}`: {commit_error}",
                self.path.display()
            )));
        }
        if let Err(commit_error) = git(&self.path, &commit_args) {
            return Err(error(format!(
                "journal event was appended but not committed; run `qms recover {}`: {commit_error}",
                self.path.display()
            )));
        }
        verify_head_content_hashes(&self.path, &record.content_hashes)?;
        Ok(record)
    }

    pub fn recover(&self) -> Result<(), WorkspaceError> {
        let _lock = self.lock()?;
        let journal_path = self.path.join(JOURNAL_PATH);
        let mut bytes = fs::read(&journal_path)?;
        if !bytes.ends_with(b"\n") {
            if let Some(last_newline) = bytes.iter().rposition(|byte| *byte == b'\n') {
                bytes.truncate(last_newline + 1);
                let file = OpenOptions::new().write(true).open(&journal_path)?;
                file.set_len(bytes.len() as u64)?;
                file.sync_all()?;
            } else {
                return Err(error(
                    "journal has no complete records; refusing automatic recovery",
                ));
            }
        }
        let records = parse_and_verify_journal(&bytes)?;

        if !has_head(&self.path) {
            let config_bytes = fs::read(self.path.join(CONFIG_PATH))?;
            let config: WorkspaceConfig = serde_json::from_slice(&config_bytes)
                .map_err(|e| error(format!("invalid workspace config: {e}")))?;
            if config.schema_version != WORKSPACE_SCHEMA_VERSION
                || config.document_schema_version != 1
                || config.module_manifest_schema_version != 1
                || config.journal_schema_version != WORKSPACE_SCHEMA_VERSION
            {
                return Err(error("unsupported workspace schema version"));
            }
            let manifest: ModuleManifest =
                serde_json::from_slice(&fs::read(self.path.join(MODULE_MANIFEST_PATH))?)
                    .map_err(|e| error(format!("invalid module manifest: {e}")))?;
            if manifest.schema_version != config.module_manifest_schema_version {
                return Err(error("unsupported module manifest schema version"));
            }
            let initial_paths = [
                ".gitignore",
                CONFIG_PATH,
                JOURNAL_PATH,
                "documents/.gitkeep",
                MODULE_MANIFEST_PATH,
                "modules/.gitkeep",
            ];
            let mut add_args = vec!["add", "--"];
            add_args.extend(initial_paths);
            git(&self.path, &add_args)?;
            let mut commit_args = vec![
                "commit",
                "--only",
                "-m",
                "Recover workspace initialization",
                "--",
            ];
            commit_args.extend(initial_paths);
            git(&self.path, &commit_args)?;
            self.verify_unlocked()?;
            return Ok(());
        }

        ensure_matches_head(
            &self.path,
            CONFIG_PATH,
            &fs::read(self.path.join(CONFIG_PATH))?,
        )?;
        ensure_matches_head(
            &self.path,
            MODULE_MANIFEST_PATH,
            &fs::read(self.path.join(MODULE_MANIFEST_PATH))?,
        )?;
        let committed = git(&self.path, &["show", &format!("HEAD:{JOURNAL_PATH}")])?;
        if committed.stdout == bytes {
            self.verify_unlocked()?;
            return Ok(());
        }
        if !bytes.starts_with(&committed.stdout) {
            return Err(error(
                "journal does not extend committed history; refusing recovery",
            ));
        }
        let last_record = records.last().expect("genesis record is required");
        verify_workspace_content_hashes(&self.path, &last_record.content_hashes)?;
        let mut commit_args = vec![
            "commit".to_owned(),
            "--only".to_owned(),
            "-m".to_owned(),
            "Recover audit journal".to_owned(),
            "--".to_owned(),
            JOURNAL_PATH.to_owned(),
        ];
        commit_args.extend(last_record.content_hashes.keys().cloned());
        let commit_args = commit_args.iter().map(String::as_str).collect::<Vec<_>>();
        let mut add_paths = vec![JOURNAL_PATH.to_owned()];
        add_paths.extend(last_record.content_hashes.keys().cloned());
        let mut add_args = vec!["add".to_owned(), "--".to_owned()];
        add_args.extend(add_paths);
        let add_args = add_args.iter().map(String::as_str).collect::<Vec<_>>();
        git(&self.path, &add_args)?;
        git(&self.path, &commit_args)?;
        self.verify_unlocked()?;
        Ok(())
    }

    pub(crate) fn acquire_lock(&self) -> Result<File, WorkspaceError> {
        let lock_path = self.path.join(".git/eqms-workspace.lock");
        let file = OpenOptions::new()
            .create(true)
            .truncate(false)
            .read(true)
            .write(true)
            .open(lock_path)?;
        file.lock()?;
        Ok(file)
    }

    fn lock(&self) -> Result<File, WorkspaceError> {
        self.acquire_lock()
    }
}

fn write_json(path: &Path, value: &impl Serialize) -> Result<(), WorkspaceError> {
    let bytes = serde_json::to_vec_pretty(value)
        .map_err(|e| error(format!("cannot encode config: {e}")))?;
    fs::write(path, [bytes.as_slice(), b"\n"].concat())?;
    Ok(())
}

fn write_journal(path: &Path, records: &[JournalRecord]) -> Result<(), WorkspaceError> {
    let mut file = File::create(path)?;
    for record in records {
        serde_json::to_writer(&mut file, record)
            .map_err(|e| error(format!("cannot encode journal record: {e}")))?;
        file.write_all(b"\n")?;
    }
    file.sync_all()?;
    Ok(())
}

fn timestamp() -> Result<String, WorkspaceError> {
    let duration = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| error(format!("system clock is before Unix epoch: {e}")))?;
    Ok(format!(
        "{}.{:09}",
        duration.as_secs(),
        duration.subsec_nanos()
    ))
}

fn with_hash(mut record: JournalRecord) -> Result<JournalRecord, WorkspaceError> {
    let event = JournalEvent {
        schema_version: record.schema_version,
        sequence: record.sequence,
        previous_hash: &record.previous_hash,
        timestamp: &record.timestamp,
        event_type: &record.event_type,
        payload: &record.payload,
        content_hashes: &record.content_hashes,
    };
    let bytes = serde_json::to_vec(&event)
        .map_err(|e| error(format!("cannot encode journal event: {e}")))?;
    record.hash = format!("{:x}", Sha256::digest(bytes));
    Ok(record)
}

fn validate_content_hashes(
    content_hashes: &BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    for (path, hash) in content_hashes {
        let valid_path = (path.starts_with("documents/") || path.starts_with("modules/"))
            && !path.contains('\\')
            && path
                .split('/')
                .all(|component| !component.is_empty() && component != "." && component != "..");
        let valid_hash = hash.strip_prefix("sha256:").is_some_and(|hex| {
            hex.len() == 64
                && hex
                    .bytes()
                    .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
        });
        if !valid_path || !valid_hash {
            return Err(error(format!(
                "invalid canonical content hash reference: {path}"
            )));
        }
    }
    Ok(())
}

fn verify_workspace_content_hashes(
    root: &Path,
    content_hashes: &BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    for (relative_path, expected_hash) in content_hashes {
        let mut path = root.to_path_buf();
        let components = relative_path.split('/').collect::<Vec<_>>();
        for (index, component) in components.iter().enumerate() {
            path.push(component);
            let metadata = fs::symlink_metadata(&path).map_err(|e| {
                error(format!(
                    "cannot inspect referenced canonical content {relative_path}: {e}"
                ))
            })?;
            if metadata.file_type().is_symlink()
                || (index + 1 < components.len() && !metadata.is_dir())
                || (index + 1 == components.len() && !metadata.is_file())
            {
                return Err(error(format!(
                    "referenced canonical content is not a regular workspace file: {relative_path}"
                )));
            }
        }
        if !path.starts_with(root) {
            return Err(error(format!(
                "referenced canonical content is outside workspace: {relative_path}"
            )));
        }
        let bytes = fs::read(&path).map_err(|e| {
            error(format!(
                "cannot read referenced canonical content {relative_path}: {e}"
            ))
        })?;
        let actual_hash = format!("sha256:{:x}", Sha256::digest(bytes));
        if actual_hash != *expected_hash {
            return Err(error(format!(
                "content hash does not match canonical file {relative_path}"
            )));
        }
    }
    Ok(())
}

fn verify_head_content_hashes(
    root: &Path,
    content_hashes: &BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    for (relative_path, expected_hash) in content_hashes {
        let output = git(root, &["show", &format!("HEAD:{relative_path}")])?;
        let actual_hash = format!("sha256:{:x}", Sha256::digest(output.stdout));
        if actual_hash != *expected_hash {
            return Err(error(format!(
                "Git commit does not contain the declared content for {relative_path}"
            )));
        }
    }
    Ok(())
}

fn parse_and_verify_journal(bytes: &[u8]) -> Result<Vec<JournalRecord>, WorkspaceError> {
    if !bytes.ends_with(b"\n") {
        return Err(error("journal ends with an incomplete record"));
    }
    let mut records = Vec::new();
    for (index, line) in bytes.split(|byte| *byte == b'\n').enumerate() {
        if line.is_empty() {
            if index == bytes.split(|byte| *byte == b'\n').count() - 1 {
                break;
            }
            return Err(error(format!(
                "journal contains an empty record at line {index}"
            )));
        }
        let record: JournalRecord = serde_json::from_slice(line)
            .map_err(|e| error(format!("invalid journal record at line {index}: {e}")))?;
        validate_content_hashes(&record.content_hashes)?;
        let canonical_line = serde_json::to_vec(&record)
            .map_err(|e| error(format!("cannot encode journal record: {e}")))?;
        if canonical_line != line {
            return Err(error(format!(
                "journal record at line {index} is not canonically encoded"
            )));
        }
        let expected_sequence = records.len() as u64;
        let expected_previous = records
            .last()
            .map(|previous: &JournalRecord| previous.hash.clone());
        if record.schema_version != WORKSPACE_SCHEMA_VERSION
            || record.sequence != expected_sequence
            || record.previous_hash != expected_previous
            || record.hash
                != with_hash(JournalRecord {
                    hash: String::new(),
                    ..record.clone()
                })?
                .hash
        {
            return Err(error(format!(
                "journal integrity check failed at sequence {}",
                record.sequence
            )));
        }
        records.push(record);
    }
    if records.first().is_none_or(|record| {
        record.sequence != 0
            || record.event_type != "workspace.genesis"
            || record.previous_hash.is_some()
    }) {
        return Err(error("journal is missing a valid genesis record"));
    }
    Ok(records)
}

fn ensure_git_identity(path: &Path) -> Result<(), WorkspaceError> {
    for variable in ["GIT_AUTHOR_IDENT", "GIT_COMMITTER_IDENT"] {
        let output = git(path, &["var", variable])?;
        let identity = String::from_utf8_lossy(&output.stdout);
        if !identity.contains('<') || !identity.contains('>') {
            return Err(error(
                "Git author and committer identity must be configured; set user.name and user.email before initializing",
            ));
        }
    }
    Ok(())
}

fn ensure_matches_head(
    path: &Path,
    relative_path: &str,
    bytes: &[u8],
) -> Result<(), WorkspaceError> {
    let output = git(path, &["show", &format!("HEAD:{relative_path}")])?;
    if output.stdout != bytes {
        return Err(error(format!(
            "{relative_path} differs from committed Git history; run `qms recover {}` if this is an interrupted journal update",
            path.display()
        )));
    }
    Ok(())
}

fn has_head(path: &Path) -> bool {
    Command::new("git")
        .arg("-C")
        .arg(path)
        .args(["rev-parse", "--verify", "HEAD"])
        .output()
        .is_ok_and(|output| output.status.success())
}

fn git_text(path: &Path, args: &[&str]) -> Result<String, WorkspaceError> {
    let output = git(path, args)?;
    String::from_utf8(output.stdout)
        .map_err(|e| error(format!("Git returned non-UTF-8 output: {e}")))
}

fn git(path: &Path, args: &[&str]) -> Result<Output, WorkspaceError> {
    let output = Command::new("git")
        .arg("-C")
        .arg(path)
        .args(args)
        .output()
        .map_err(|e| error(format!("cannot execute Git: {e}")))?;
    if output.status.success() {
        Ok(output)
    } else {
        let stderr = String::from_utf8_lossy(&output.stderr).trim().to_owned();
        Err(error(format!(
            "Git {} failed{}",
            args.join(" "),
            if stderr.is_empty() {
                String::new()
            } else {
                format!(": {stderr}")
            }
        )))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static NEXT: AtomicUsize = AtomicUsize::new(0);

    fn temp_dir() -> PathBuf {
        let path = std::env::temp_dir().join(format!(
            "eqms-workspace-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&path).unwrap();
        path
    }

    fn configure_git(path: &Path) {
        git(path, &["init"]).unwrap();
        git(path, &["config", "user.name", "Test User"]).unwrap();
        git(path, &["config", "user.email", "test@example.invalid"]).unwrap();
    }

    #[test]
    fn initializes_and_verifies_genesis_workspace() {
        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        let records = workspace.verify().unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].event_type, "workspace.genesis");
        assert!(workspace.index_path().ends_with(".eqms/index.sqlite3"));
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn records_and_recovers_events_without_replacing_history() {
        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        let document_content = b"# DOC-1\n";
        fs::write(path.join("documents/DOC-1.md"), document_content).unwrap();
        let event = workspace
            .record_event(
                "document.updated",
                BTreeMap::from([("id".into(), "DOC-1".into())]),
                BTreeMap::from([(
                    "documents/DOC-1.md".into(),
                    format!("sha256:{:x}", Sha256::digest(document_content)),
                )]),
            )
            .unwrap();
        assert_eq!(event.sequence, 1);
        assert_eq!(workspace.verify().unwrap().len(), 2);
        assert!(path.join("documents/DOC-1.md").is_file());
        workspace.recover().unwrap();
        assert_eq!(workspace.verify().unwrap().len(), 2);
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn rejects_nonempty_initialization_and_tampered_journal() {
        let path = temp_dir();
        fs::write(path.join("keep.txt"), "keep").unwrap();
        assert!(Workspace::initialize(&path).is_err());
        fs::remove_file(path.join("keep.txt")).unwrap();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        fs::write(path.join(JOURNAL_PATH), b"{}\n").unwrap();
        assert!(workspace.verify().is_err());
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn recovery_truncates_only_an_incomplete_final_line() {
        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        let journal_path = path.join(JOURNAL_PATH);
        let mut file = OpenOptions::new().append(true).open(&journal_path).unwrap();
        file.write_all(b"{\"partial\":").unwrap();
        file.sync_all().unwrap();
        workspace.recover().unwrap();
        assert_eq!(workspace.verify().unwrap().len(), 1);
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn recovery_commits_a_complete_pending_event() {
        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        let previous = workspace.verify().unwrap().pop().unwrap();
        let record = with_hash(JournalRecord {
            schema_version: WORKSPACE_SCHEMA_VERSION,
            sequence: 1,
            previous_hash: Some(previous.hash),
            timestamp: timestamp().unwrap(),
            event_type: "workspace.recovered".into(),
            payload: BTreeMap::new(),
            content_hashes: BTreeMap::new(),
            hash: String::new(),
        })
        .unwrap();
        let mut file = OpenOptions::new()
            .append(true)
            .open(path.join(JOURNAL_PATH))
            .unwrap();
        serde_json::to_writer(&mut file, &record).unwrap();
        file.write_all(b"\n").unwrap();
        file.sync_all().unwrap();

        workspace.recover().unwrap();
        assert_eq!(workspace.verify().unwrap().len(), 2);
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn concurrent_journal_mutations_are_serialized() {
        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        let first = workspace.clone();
        let second = workspace.clone();
        let first_thread = std::thread::spawn(move || {
            first.record_event("test.first", BTreeMap::new(), BTreeMap::new())
        });
        let second_thread = std::thread::spawn(move || {
            second.record_event("test.second", BTreeMap::new(), BTreeMap::new())
        });
        first_thread.join().unwrap().unwrap();
        second_thread.join().unwrap().unwrap();
        let records = workspace.verify().unwrap();
        assert_eq!(records.len(), 3);
        assert_eq!(records[1].sequence, 1);
        assert_eq!(records[2].sequence, 2);
        fs::remove_dir_all(path).unwrap();
    }
}
