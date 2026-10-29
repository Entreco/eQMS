use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet};
use std::error::Error;
use std::fmt::{self, Display, Formatter};
#[cfg(not(unix))]
use std::fs::OpenOptions;
use std::fs::{self, File};
use std::io::{self, Read, Write};
use std::path::{Component, Path, PathBuf};
use std::process::{Command, Output, Stdio};
use std::time::{SystemTime, UNIX_EPOCH};

#[cfg(unix)]
use rustix::fs::{Mode, OFlags};

const CONFIG_PATH: &str = ".eqms/config.json";
const JOURNAL_PATH: &str = ".eqms/journal.jsonl";
const MODULE_MANIFEST_PATH: &str = "modules/manifest.json";
const WORKSPACE_SCHEMA_VERSION: u32 = 1;

/// Stable relative paths for the logical workspace resources created by initialization.
pub const INITIALIZED_RESOURCES: &[&str] = &[
    ".git/",
    ".gitignore",
    ".eqms/",
    ".eqms/config.json",
    ".eqms/journal.jsonl",
    "documents/",
    "modules/",
    "modules/manifest.json",
];

#[derive(Clone, Copy)]
enum WorkspaceFileMode {
    Read,
    Write,
    Append,
    Create,
    Lock,
}

fn checked_components(path: &Path) -> io::Result<Vec<&std::ffi::OsStr>> {
    let components = path
        .components()
        .map(|component| match component {
            Component::Normal(name) => Ok(name),
            _ => Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "workspace path must be relative and contain only normal components",
            )),
        })
        .collect::<io::Result<Vec<_>>>()?;
    if components.is_empty() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "workspace path must name a file or directory",
        ));
    }
    Ok(components)
}

#[cfg(unix)]
fn rustix_io_error(error: rustix::io::Errno) -> io::Error {
    io::Error::from_raw_os_error(error.raw_os_error())
}

#[cfg(unix)]
fn open_workspace_file(root: &Path, relative: &Path, mode: WorkspaceFileMode) -> io::Result<File> {
    use rustix::fs::{open, openat};

    let components = checked_components(relative)?;
    let directory_flags = OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC;
    let root_fd = open(root, directory_flags, Mode::empty()).map_err(rustix_io_error)?;
    let mut directory = File::from(root_fd);
    for component in &components[..components.len() - 1] {
        let fd = openat(&directory, *component, directory_flags, Mode::empty())
            .map_err(rustix_io_error)?;
        directory = File::from(fd);
    }

    let (flags, create_mode) = match mode {
        WorkspaceFileMode::Read => (OFlags::RDONLY | OFlags::NONBLOCK, Mode::empty()),
        WorkspaceFileMode::Write => (OFlags::WRONLY, Mode::empty()),
        WorkspaceFileMode::Append => (OFlags::WRONLY | OFlags::APPEND, Mode::empty()),
        WorkspaceFileMode::Create => (
            OFlags::WRONLY | OFlags::CREATE | OFlags::EXCL,
            Mode::from_raw_mode(0o644),
        ),
        WorkspaceFileMode::Lock => (OFlags::RDWR | OFlags::CREATE, Mode::from_raw_mode(0o600)),
    };
    let fd = openat(
        &directory,
        components[components.len() - 1],
        flags | OFlags::NOFOLLOW | OFlags::CLOEXEC,
        create_mode,
    )
    .map_err(rustix_io_error)?;
    let file = File::from(fd);
    if !file.metadata()?.is_file() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "workspace path is not a regular file",
        ));
    }
    Ok(file)
}

#[cfg(not(unix))]
fn open_workspace_file(root: &Path, relative: &Path, mode: WorkspaceFileMode) -> io::Result<File> {
    let components = checked_components(relative)?;
    let mut path = root.to_path_buf();
    for (index, component) in components.iter().enumerate() {
        path.push(component);
        match fs::symlink_metadata(&path) {
            Ok(metadata) => {
                if metadata.file_type().is_symlink()
                    || (index + 1 < components.len() && !metadata.is_dir())
                    || (index + 1 == components.len() && !metadata.is_file())
                {
                    return Err(io::Error::new(
                        io::ErrorKind::InvalidData,
                        "workspace path contains a symlink or non-regular file",
                    ));
                }
            }
            Err(error)
                if index + 1 == components.len()
                    && error.kind() == io::ErrorKind::NotFound
                    && matches!(mode, WorkspaceFileMode::Create | WorkspaceFileMode::Lock) => {}
            Err(error) => return Err(error),
        }
    }
    let mut options = OpenOptions::new();
    match mode {
        WorkspaceFileMode::Read => {
            options.read(true);
        }
        WorkspaceFileMode::Write => {
            options.write(true);
        }
        WorkspaceFileMode::Append => {
            options.append(true);
        }
        WorkspaceFileMode::Create => {
            options.write(true).create_new(true);
        }
        WorkspaceFileMode::Lock => {
            options.read(true).write(true).create(true);
        }
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt;
        options.custom_flags(0x0020_0000);
    }
    let file = options.open(path)?;
    if !file.metadata()?.is_file() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "workspace path is not a regular file",
        ));
    }
    Ok(file)
}

#[cfg(unix)]
fn ensure_workspace_directory(root: &Path, relative: &Path) -> io::Result<bool> {
    use rustix::fs::{mkdirat, open, openat};

    let components = checked_components(relative)?;
    let directory_flags = OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC;
    let root_fd = open(root, directory_flags, Mode::empty()).map_err(rustix_io_error)?;
    let mut directory = File::from(root_fd);
    let mut created = false;
    for component in components {
        let next = match openat(&directory, component, directory_flags, Mode::empty()) {
            Ok(fd) => fd,
            Err(error) if rustix_io_error(error).kind() == io::ErrorKind::NotFound => {
                match mkdirat(&directory, component, Mode::from_raw_mode(0o755)) {
                    Ok(()) => created = true,
                    Err(error) if rustix_io_error(error).kind() == io::ErrorKind::AlreadyExists => {
                    }
                    Err(error) => return Err(rustix_io_error(error)),
                }
                openat(&directory, component, directory_flags, Mode::empty())
                    .map_err(rustix_io_error)?
            }
            Err(error) => return Err(rustix_io_error(error)),
        };
        directory = File::from(next);
    }
    Ok(created)
}

#[cfg(not(unix))]
fn ensure_workspace_directory(root: &Path, relative: &Path) -> io::Result<bool> {
    let components = checked_components(relative)?;
    let mut path = root.to_path_buf();
    let mut created = false;
    for component in components {
        path.push(component);
        match fs::symlink_metadata(&path) {
            Ok(metadata) if metadata.is_dir() && !metadata.file_type().is_symlink() => {}
            Ok(_) => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    "workspace directory contains a symlink or non-directory",
                ));
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => {
                fs::create_dir(&path)?;
                created = true;
            }
            Err(error) => return Err(error),
        }
    }
    Ok(created)
}

#[cfg(unix)]
fn remove_workspace_entry(root: &Path, relative: &str, directory: bool) -> io::Result<()> {
    use rustix::fs::{AtFlags, open, openat, unlinkat};

    let components = checked_components(Path::new(relative))?;
    let directory_flags = OFlags::RDONLY | OFlags::DIRECTORY | OFlags::NOFOLLOW | OFlags::CLOEXEC;
    let root_fd = open(root, directory_flags, Mode::empty()).map_err(rustix_io_error)?;
    let mut parent = File::from(root_fd);
    for component in &components[..components.len() - 1] {
        parent = File::from(
            openat(&parent, *component, directory_flags, Mode::empty()).map_err(rustix_io_error)?,
        );
    }
    let flags = if directory {
        AtFlags::REMOVEDIR
    } else {
        AtFlags::empty()
    };
    unlinkat(&parent, components[components.len() - 1], flags).map_err(rustix_io_error)
}

#[cfg(not(unix))]
fn remove_workspace_entry(root: &Path, relative: &str, directory: bool) -> io::Result<()> {
    let components = checked_components(Path::new(relative))?;
    let mut path = root.to_path_buf();
    for component in &components[..components.len() - 1] {
        path.push(component);
        let metadata = fs::symlink_metadata(&path)?;
        if !metadata.is_dir() || metadata.file_type().is_symlink() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "workspace cleanup parent is not a regular directory",
            ));
        }
    }
    path.push(components[components.len() - 1]);
    let metadata = fs::symlink_metadata(&path)?;
    if metadata.file_type().is_symlink()
        || directory && !metadata.is_dir()
        || !directory && !metadata.is_file()
    {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "workspace cleanup target is not the expected regular entry",
        ));
    }
    if directory {
        fs::remove_dir(path)
    } else {
        fs::remove_file(path)
    }
}

fn read_workspace_file(root: &Path, relative: &str) -> io::Result<Vec<u8>> {
    let mut file = open_workspace_file(root, Path::new(relative), WorkspaceFileMode::Read)?;
    let mut bytes = Vec::new();
    file.read_to_end(&mut bytes)?;
    Ok(bytes)
}

fn create_directory_tree(path: &Path) -> Result<Vec<PathBuf>, WorkspaceError> {
    let mut current = PathBuf::new();
    let mut created = Vec::new();
    for component in path.components() {
        current.push(component.as_os_str());
        match fs::symlink_metadata(&current) {
            Ok(metadata) if metadata.is_dir() => {}
            Ok(metadata)
                if metadata.file_type().is_symlink()
                    && fs::metadata(&current).is_ok_and(|target| target.is_dir()) => {}
            Ok(_) => {
                return Err(cleanup_parent_directories(
                    created,
                    error(format!(
                        "workspace path component is not a directory: {}",
                        current.display()
                    )),
                ));
            }
            Err(io_error) if io_error.kind() == io::ErrorKind::NotFound => {
                match fs::create_dir(&current) {
                    Ok(()) => created.push(current.clone()),
                    Err(io_error) if io_error.kind() == io::ErrorKind::AlreadyExists => {
                        let metadata = fs::metadata(&current).map_err(|metadata_error| {
                            cleanup_parent_directories(
                                created.clone(),
                                error(format!(
                                    "cannot inspect workspace path {}: {metadata_error}",
                                    current.display()
                                )),
                            )
                        })?;
                        if !metadata.is_dir() {
                            return Err(cleanup_parent_directories(
                                created,
                                error(format!(
                                    "workspace path component is not a directory: {}",
                                    current.display()
                                )),
                            ));
                        }
                    }
                    Err(io_error) => {
                        return Err(cleanup_parent_directories(
                            created,
                            error(format!(
                                "cannot create workspace directory {}: {io_error}",
                                current.display()
                            )),
                        ));
                    }
                }
            }
            Err(io_error) => {
                return Err(cleanup_parent_directories(
                    created,
                    error(format!(
                        "cannot inspect workspace path {}: {io_error}",
                        current.display()
                    )),
                ));
            }
        }
    }
    Ok(created)
}

fn cleanup_parent_directories(created: Vec<PathBuf>, cause: WorkspaceError) -> WorkspaceError {
    if created.is_empty() {
        return cause;
    }
    let mut failures = Vec::new();
    for path in created.iter().rev() {
        match fs::remove_dir(path) {
            Ok(()) => {}
            Err(io_error) if io_error.kind() == io::ErrorKind::NotFound => {}
            Err(io_error) => failures.push(format!("{}: {io_error}", path.display())),
        }
    }
    if failures.is_empty() {
        error(format!(
            "{cause}; empty directories created by this attempt were removed"
        ))
    } else {
        error(format!(
            "{cause}; rollback could not remove created directories: {}",
            failures.join(", ")
        ))
    }
}

fn truncate_workspace_file(root: &Path, relative: &str, length: u64) -> io::Result<()> {
    let file = open_workspace_file(root, Path::new(relative), WorkspaceFileMode::Write)?;
    file.set_len(length)?;
    file.sync_all()
}

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

pub(crate) fn error(message: impl Into<String>) -> WorkspaceError {
    WorkspaceError(message.into())
}

struct InitializationGuard<'a> {
    root: &'a Path,
    created_files: Vec<String>,
    created_directories: Vec<String>,
}

impl<'a> InitializationGuard<'a> {
    fn new(root: &'a Path) -> Self {
        Self {
            root,
            created_files: Vec::new(),
            created_directories: Vec::new(),
        }
    }

    fn create_directory(&mut self, relative: &str) -> Result<(), WorkspaceError> {
        let created =
            ensure_workspace_directory(self.root, Path::new(relative)).map_err(|io_error| {
                error(format!(
                    "cannot create workspace directory {relative}: {io_error}"
                ))
            })?;
        if created {
            self.created_directories.push(relative.to_owned());
        }
        Ok(())
    }

    fn write_file(&mut self, relative: &str, bytes: &[u8]) -> Result<(), WorkspaceError> {
        let mut file =
            open_workspace_file(self.root, Path::new(relative), WorkspaceFileMode::Create)
                .map_err(|io_error| {
                    error(format!(
                        "cannot create workspace file {relative}: {io_error}"
                    ))
                })?;
        self.created_files.push(relative.to_owned());
        file.write_all(bytes)
            .and_then(|()| file.sync_all())
            .map_err(|io_error| {
                error(format!(
                    "cannot write workspace file {relative}: {io_error}"
                ))
            })
    }

    fn write_json(&mut self, relative: &str, value: &impl Serialize) -> Result<(), WorkspaceError> {
        let mut bytes = serde_json::to_vec_pretty(value)
            .map_err(|serde_error| error(serde_error.to_string()))?;
        bytes.push(b'\n');
        self.write_file(relative, &bytes)
    }

    fn write_journal(&mut self, records: &[JournalRecord]) -> Result<(), WorkspaceError> {
        let mut bytes = Vec::new();
        for record in records {
            serde_json::to_writer(&mut bytes, record).map_err(|serde_error| {
                error(format!("cannot encode journal record: {serde_error}"))
            })?;
            bytes.push(b'\n');
        }
        self.write_file(JOURNAL_PATH, &bytes)
    }

    fn abort(self, cause: WorkspaceError) -> WorkspaceError {
        let mut failures = Vec::new();
        for (relative, directory) in self
            .created_files
            .iter()
            .rev()
            .map(|path| (path.as_str(), false))
            .chain(
                self.created_directories
                    .iter()
                    .rev()
                    .map(|path| (path.as_str(), true)),
            )
        {
            match remove_workspace_entry(self.root, relative, directory) {
                Ok(()) => {}
                Err(error) if error.kind() == io::ErrorKind::NotFound => {}
                Err(error) => failures.push(format!("{relative}: {error}")),
            }
        }
        if failures.is_empty() {
            error(format!(
                "{cause}; attempt-created workspace files and directories were removed; the unborn Git repository was preserved. Correct the cause and retry `qms init <path>`"
            ))
        } else {
            error(format!(
                "{cause}; rollback could not remove {}; the workspace may require manual repair",
                failures.join(", ")
            ))
        }
    }
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
        let created_parent_directories = create_directory_tree(requested_path)?;
        let path = requested_path.canonicalize().map_err(|e| {
            cleanup_parent_directories(
                created_parent_directories.clone(),
                error(format!(
                    "cannot resolve workspace directory {}: {e}",
                    requested_path.display()
                )),
            )
        })?;
        if let Err(initialization_error) = ensure_initializable(&path) {
            return Err(cleanup_parent_directories(
                created_parent_directories,
                initialization_error,
            ));
        }
        let git_directory = path.join(".git");
        match fs::create_dir(&git_directory) {
            Ok(()) => {}
            Err(io_error) if io_error.kind() == io::ErrorKind::AlreadyExists => {
                let metadata = fs::symlink_metadata(&git_directory)?;
                if !metadata.is_dir() || metadata.file_type().is_symlink() {
                    return Err(error("workspace .git path is not a regular directory"));
                }
            }
            Err(io_error) => return Err(io_error.into()),
        }
        let workspace = Self { path };
        let _lock = workspace.acquire_lock().map_err(|lock_error| {
            error(format!(
                "cannot acquire initialization lock: {lock_error}; the unborn Git repository was preserved"
            ))
        })?;
        ensure_initializable(&workspace.path)?;
        if let Err(init_error) = git(&workspace.path, &["init"]) {
            return Err(error(format!(
                "cannot initialize Git repository: {init_error}; the .git directory was preserved, correct the cause and retry `qms init <path>`"
            )));
        }
        ensure_git_identity(&workspace.path).map_err(|identity_error| {
            error(format!(
                "{identity_error}; the unborn .git repository was preserved, configure explicit author and committer identity and retry `qms init <path>`"
            ))
        })?;

        let mut guard = InitializationGuard::new(&workspace.path);
        let mut commit_started = false;
        let initialization = (|| {
            guard.create_directory(".eqms")?;
            guard.create_directory("documents")?;
            guard.create_directory("modules")?;
            guard.write_file(
                ".gitignore",
                b".eqms/index.sqlite3\n.eqms/index.sqlite3-shm\n.eqms/index.sqlite3-wal\n",
            )?;
            guard.write_file("documents/.gitkeep", b"")?;
            guard.write_file("modules/.gitkeep", b"")?;

            let config = WorkspaceConfig {
                schema_version: WORKSPACE_SCHEMA_VERSION,
                document_schema_version: 1,
                module_manifest_schema_version: 1,
                journal_schema_version: WORKSPACE_SCHEMA_VERSION,
            };
            guard.write_json(CONFIG_PATH, &config)?;
            guard.write_json(
                MODULE_MANIFEST_PATH,
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
            guard.write_journal(&[record])?;
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
            git(&workspace.path, &add_args)?;
            for relative_path in initial_paths {
                let bytes = read_workspace_file(&workspace.path, relative_path)?;
                verify_staged_bytes(&workspace.path, relative_path, &bytes)?;
            }
            let mut commit_args = vec!["commit", "--only", "-m", "Initialize eQMS workspace", "--"];
            commit_args.extend(initial_paths);
            commit_started = true;
            if let Err(commit_error) = git(&workspace.path, &commit_args) {
                return Err(error(format!(
                    "cannot create the initial workspace commit: {commit_error}"
                )));
            }
            Ok(())
        })();

        if let Err(init_error) = initialization {
            if commit_started {
                return Err(error(format!(
                    "{init_error}; workspace files were preserved because the commit outcome may be uncertain; run `qms verify <path>` and, if no commit exists, correct the cause and run `qms recover <path>`"
                )));
            }
            return Err(guard.abort(init_error));
        }
        drop(guard);

        workspace.verify_unlocked().map_err(|verify_error| {
            error(format!(
                "the initial commit was created, but workspace verification failed: {verify_error}; files were preserved, inspect with `qms verify <path>`"
            ))
        })?;
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
            open_workspace_file(&path, Path::new(relative_path), WorkspaceFileMode::Read).map_err(
                |e| {
                    error(format!(
                        "cannot open workspace file {relative_path} in {}: {e}",
                        path.display()
                    ))
                },
            )?;
        }
        let git_root = git_text(&path, &["rev-parse", "--show-toplevel"])?;
        let git_root = Path::new(git_root.trim()).canonicalize()?;
        if git_root != path {
            return Err(error(format!(
                "workspace Git root {} does not match workspace path {}",
                git_root.display(),
                path.display()
            )));
        }
        Ok(Self { path })
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn index_path(&self) -> PathBuf {
        self.path.join(".eqms/index.sqlite3")
    }

    pub(crate) fn read_canonical_file(&self, relative: &str) -> Result<Vec<u8>, WorkspaceError> {
        read_workspace_file(&self.path, relative).map_err(Into::into)
    }

    #[cfg(unix)]
    pub fn read_render_asset(&self, relative: &str) -> Result<Vec<u8>, WorkspaceError> {
        if !crate::render::asset_path(relative) {
            return Err(error("Only project-local assets/ paths are supported"));
        }
        let file = open_workspace_file(&self.path, Path::new(relative), WorkspaceFileMode::Read)?;
        if !file.metadata()?.is_file() {
            return Err(error("Asset is not a regular file"));
        }
        let mut bytes = Vec::new();
        file.take(10 * 1024 * 1024 + 1).read_to_end(&mut bytes)?;
        if bytes.len() > 10 * 1024 * 1024 {
            return Err(error("Image exceeds the 10 MiB encoded limit"));
        }
        Ok(bytes)
    }

    #[cfg(not(unix))]
    pub fn read_render_asset(&self, _relative: &str) -> Result<Vec<u8>, WorkspaceError> {
        Err(error(
            "Secure local image reads are currently supported only on Unix hosts",
        ))
    }

    pub fn verify(&self) -> Result<Vec<JournalRecord>, WorkspaceError> {
        let _lock = self.acquire_lock()?;
        self.verify_unlocked()
    }

    pub(crate) fn verify_unlocked(&self) -> Result<Vec<JournalRecord>, WorkspaceError> {
        self.verify_unlocked_with_manifest_update(None)
    }

    fn verify_unlocked_with_manifest_update(
        &self,
        pending_manifest_hash: Option<&str>,
    ) -> Result<Vec<JournalRecord>, WorkspaceError> {
        let config_bytes = read_workspace_file(&self.path, CONFIG_PATH)?;
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
        let manifest_bytes = read_workspace_file(&self.path, MODULE_MANIFEST_PATH)?;
        let manifest: ModuleManifest = serde_json::from_slice(&manifest_bytes)
            .map_err(|e| error(format!("invalid module manifest: {e}")))?;
        if manifest.schema_version != config.module_manifest_schema_version {
            return Err(error(format!(
                "unsupported module manifest schema version {}",
                manifest.schema_version
            )));
        }

        let journal_bytes = read_workspace_file(&self.path, JOURNAL_PATH)?;
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
        let content_hashes = &records
            .last()
            .expect("genesis record is required")
            .content_hashes;
        let mut paths = BTreeSet::from([CONFIG_PATH, JOURNAL_PATH, MODULE_MANIFEST_PATH]);
        paths.extend(content_hashes.keys().map(String::as_str));
        let head_blobs = read_head_blobs(&self.path, &paths.into_iter().collect::<Vec<_>>())?;
        for (path, bytes) in [(CONFIG_PATH, &config_bytes), (JOURNAL_PATH, &journal_bytes)] {
            ensure_matches_committed(&self.path, path, bytes, &head_blobs[path])?;
        }
        if let Some(expected_hash) = pending_manifest_hash {
            let actual_hash = format!("sha256:{:x}", Sha256::digest(&manifest_bytes));
            if actual_hash != expected_hash {
                return Err(error(
                    "module manifest does not match the declared pending content hash",
                ));
            }
        } else {
            ensure_matches_committed(
                &self.path,
                MODULE_MANIFEST_PATH,
                &manifest_bytes,
                &head_blobs[MODULE_MANIFEST_PATH],
            )?;
        }
        verify_blob_hashes(&head_blobs, content_hashes)?;
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
        self.verify_unlocked_with_manifest_update(
            content_hashes.get(MODULE_MANIFEST_PATH).map(String::as_str),
        )?;

        let bytes = read_workspace_file(&self.path, JOURNAL_PATH)?;
        let records = parse_and_verify_journal(&bytes)?;
        let previous = records.last().expect("genesis record is required");
        verify_workspace_content_hashes(&self.path, &content_hashes)?;
        ensure_git_preserves_content_hashes(&self.path, &content_hashes)?;
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
        let mut new_journal = bytes;
        new_journal.extend_from_slice(&line);
        new_journal.push(b'\n');
        ensure_git_preserves_bytes(&self.path, JOURNAL_PATH, &new_journal)?;
        let write_result = (|| -> Result<(), io::Error> {
            let mut file = open_workspace_file(
                &self.path,
                Path::new(JOURNAL_PATH),
                WorkspaceFileMode::Append,
            )?;
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
        if let Err(commit_error) = verify_staged_content_hashes(&self.path, &record.content_hashes)
            .and_then(|()| verify_staged_bytes(&self.path, JOURNAL_PATH, &new_journal))
        {
            return Err(error(format!(
                "journal event was staged but Git would store different canonical bytes; refusing to commit: {commit_error}"
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
        let original = read_workspace_file(&self.path, JOURNAL_PATH)?;
        let needs_truncation = !original.ends_with(b"\n");
        let candidate = if needs_truncation {
            let Some(last_newline) = original.iter().rposition(|byte| *byte == b'\n') else {
                return Err(error(
                    "journal has no complete records; refusing automatic recovery",
                ));
            };
            original[..=last_newline].to_vec()
        } else {
            original.clone()
        };
        let records = parse_and_verify_journal(&candidate)?;

        if !has_head(&self.path) {
            if needs_truncation {
                return Err(error(
                    "cannot truncate an incomplete journal without committed history",
                ));
            }
            validate_initialization_state(&self.path)?;
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
            for relative_path in initial_paths {
                verify_staged_bytes(
                    &self.path,
                    relative_path,
                    &read_workspace_file(&self.path, relative_path)?,
                )?;
            }
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

        let config_bytes = read_workspace_file(&self.path, CONFIG_PATH)?;
        let config: WorkspaceConfig = serde_json::from_slice(&config_bytes)
            .map_err(|e| error(format!("invalid workspace config: {e}")))?;
        if config.schema_version != WORKSPACE_SCHEMA_VERSION
            || config.document_schema_version != 1
            || config.module_manifest_schema_version != 1
            || config.journal_schema_version != WORKSPACE_SCHEMA_VERSION
        {
            return Err(error("unsupported workspace schema version"));
        }
        ensure_matches_head(&self.path, CONFIG_PATH, &config_bytes)?;

        let manifest_bytes = read_workspace_file(&self.path, MODULE_MANIFEST_PATH)?;
        let manifest: ModuleManifest = serde_json::from_slice(&manifest_bytes)
            .map_err(|e| error(format!("invalid module manifest: {e}")))?;
        if manifest.schema_version != config.module_manifest_schema_version {
            return Err(error("unsupported module manifest schema version"));
        }

        let committed = git(&self.path, &["show", &format!("HEAD:{JOURNAL_PATH}")])?;
        parse_and_verify_journal(&committed.stdout)?;
        if candidate == committed.stdout {
            ensure_matches_head(&self.path, MODULE_MANIFEST_PATH, &manifest_bytes)?;
            let last = records.last().expect("genesis record is required");
            verify_head_content_hashes(&self.path, &last.content_hashes)?;
            if needs_truncation {
                truncate_workspace_file(&self.path, JOURNAL_PATH, candidate.len() as u64)?;
            }
            self.verify_unlocked()?;
            return Ok(());
        }
        if !candidate.starts_with(&committed.stdout) {
            return Err(error(
                "journal does not extend committed history; refusing recovery",
            ));
        }

        let last_record = records.last().expect("genesis record is required");
        if !last_record
            .content_hashes
            .contains_key(MODULE_MANIFEST_PATH)
        {
            ensure_matches_head(&self.path, MODULE_MANIFEST_PATH, &manifest_bytes)?;
        }
        verify_workspace_content_hashes(&self.path, &last_record.content_hashes)?;
        ensure_git_preserves_content_hashes(&self.path, &last_record.content_hashes)?;
        ensure_git_preserves_bytes(&self.path, JOURNAL_PATH, &candidate)?;
        ensure_git_identity(&self.path)?;
        if needs_truncation {
            truncate_workspace_file(&self.path, JOURNAL_PATH, candidate.len() as u64)?;
        }

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
        verify_staged_content_hashes(&self.path, &last_record.content_hashes)?;
        verify_staged_bytes(&self.path, JOURNAL_PATH, &candidate)?;
        git(&self.path, &commit_args)?;
        self.verify_unlocked()?;
        Ok(())
    }

    pub(crate) fn acquire_lock(&self) -> Result<File, WorkspaceError> {
        let file = open_workspace_file(
            &self.path,
            Path::new(".git/eqms-workspace.lock"),
            WorkspaceFileMode::Lock,
        )?;
        file.lock()?;
        Ok(file)
    }

    fn lock(&self) -> Result<File, WorkspaceError> {
        self.acquire_lock()
    }
}

pub(crate) fn timestamp() -> Result<String, WorkspaceError> {
    let duration = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| error(format!("system clock is before Unix epoch: {e}")))?;
    Ok(format!(
        "{}.{:09}",
        duration.as_secs(),
        duration.subsec_nanos()
    ))
}

pub(crate) fn with_hash(mut record: JournalRecord) -> Result<JournalRecord, WorkspaceError> {
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

fn ensure_initializable(path: &Path) -> Result<(), WorkspaceError> {
    let entries = fs::read_dir(path)?
        .map(|entry| entry.map(|entry| entry.file_name()))
        .collect::<Result<Vec<_>, _>>()?;
    if entries.is_empty() {
        return Ok(());
    }
    let only_unborn_git = entries.len() == 1
        && entries[0] == ".git"
        && !has_head(path)
        && fs::symlink_metadata(path.join(".git"))
            .is_ok_and(|metadata| metadata.is_dir() && !metadata.file_type().is_symlink());
    if only_unborn_git {
        Ok(())
    } else {
        Err(error(format!(
            "workspace directory is not empty or is not an unborn Git repository: {}",
            path.display()
        )))
    }
}

fn ensure_exact_directory_entries(
    directory: &Path,
    expected: &[&str],
) -> Result<(), WorkspaceError> {
    let metadata = fs::symlink_metadata(directory)?;
    if !metadata.is_dir() || metadata.file_type().is_symlink() {
        return Err(error(format!(
            "initialization path is not a regular directory: {}",
            directory.display()
        )));
    }
    let mut actual = fs::read_dir(directory)?
        .map(|entry| entry.map(|entry| entry.file_name().to_string_lossy().into_owned()))
        .collect::<Result<Vec<_>, _>>()?;
    actual.sort();
    let mut expected = expected
        .iter()
        .map(|name| name.to_string())
        .collect::<Vec<_>>();
    expected.sort();
    if actual != expected {
        return Err(error(format!(
            "incomplete or unexpected initialization contents in {}",
            directory.display()
        )));
    }
    Ok(())
}

fn validate_initialization_state(root: &Path) -> Result<(), WorkspaceError> {
    ensure_exact_directory_entries(
        root,
        &[".git", ".gitignore", ".eqms", "documents", "modules"],
    )?;
    let git_metadata = fs::symlink_metadata(root.join(".git"))?;
    if !git_metadata.is_dir() || git_metadata.file_type().is_symlink() {
        return Err(error(
            "initialization Git metadata path is not a regular directory",
        ));
    }
    ensure_exact_directory_entries(&root.join(".eqms"), &["config.json", "journal.jsonl"])?;
    ensure_exact_directory_entries(&root.join("documents"), &[".gitkeep"])?;
    ensure_exact_directory_entries(&root.join("modules"), &[".gitkeep", "manifest.json"])?;

    let config_bytes = read_workspace_file(root, CONFIG_PATH)?;
    let config: WorkspaceConfig = serde_json::from_slice(&config_bytes)
        .map_err(|e| error(format!("invalid workspace config: {e}")))?;
    if config.schema_version != WORKSPACE_SCHEMA_VERSION
        || config.document_schema_version != 1
        || config.module_manifest_schema_version != 1
        || config.journal_schema_version != WORKSPACE_SCHEMA_VERSION
    {
        return Err(error("unsupported workspace schema version"));
    }
    let manifest_bytes = read_workspace_file(root, MODULE_MANIFEST_PATH)?;
    let manifest: ModuleManifest = serde_json::from_slice(&manifest_bytes)
        .map_err(|e| error(format!("invalid module manifest: {e}")))?;
    if manifest.schema_version != config.module_manifest_schema_version {
        return Err(error("unsupported module manifest schema version"));
    }
    let records = parse_and_verify_journal(&read_workspace_file(root, JOURNAL_PATH)?)?;
    if records.len() != 1 || !records[0].content_hashes.is_empty() {
        return Err(error(
            "interrupted initialization must contain only the valid genesis record",
        ));
    }
    if read_workspace_file(root, ".gitignore")?
        != b".eqms/index.sqlite3\n.eqms/index.sqlite3-shm\n.eqms/index.sqlite3-wal\n"
        || !read_workspace_file(root, "documents/.gitkeep")?.is_empty()
        || !read_workspace_file(root, "modules/.gitkeep")?.is_empty()
    {
        return Err(error("incomplete initialization files"));
    }
    ensure_git_identity(root)?;
    for (relative, bytes) in [
        (CONFIG_PATH, config_bytes),
        (JOURNAL_PATH, read_workspace_file(root, JOURNAL_PATH)?),
        (MODULE_MANIFEST_PATH, manifest_bytes),
    ] {
        ensure_git_preserves_bytes(root, relative, &bytes)?;
    }
    Ok(())
}

fn verify_workspace_content_hashes(
    root: &Path,
    content_hashes: &BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    for (relative_path, expected_hash) in content_hashes {
        let bytes = read_workspace_file(root, relative_path).map_err(|e| {
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

fn ensure_git_preserves_content_hashes(
    root: &Path,
    content_hashes: &BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    for (relative_path, expected_hash) in content_hashes {
        let bytes = read_workspace_file(root, relative_path)?;
        let actual_hash = format!("sha256:{:x}", Sha256::digest(&bytes));
        if actual_hash != *expected_hash {
            return Err(error(format!(
                "content hash does not match canonical file {relative_path}"
            )));
        }
        ensure_git_preserves_bytes(root, relative_path, &bytes)?;
    }
    Ok(())
}

fn ensure_git_preserves_bytes(
    root: &Path,
    relative_path: &str,
    bytes: &[u8],
) -> Result<(), WorkspaceError> {
    let raw_hash = git_blob_hash(root, relative_path, bytes, false)?;
    let filtered_hash = git_blob_hash(root, relative_path, bytes, true)?;
    if raw_hash != filtered_hash {
        return Err(error(format!(
            "Git filters would change canonical bytes for {relative_path}"
        )));
    }
    Ok(())
}

fn git_blob_hash(
    root: &Path,
    relative_path: &str,
    bytes: &[u8],
    apply_filters: bool,
) -> Result<String, WorkspaceError> {
    let mut command = Command::new("git");
    command
        .arg("--literal-pathspecs")
        .arg("-C")
        .arg(root)
        .arg("hash-object");
    if apply_filters {
        command.arg(format!("--path={relative_path}"));
    } else {
        command.arg("--no-filters");
    }
    let mut child = command
        .arg("--stdin")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| error(format!("cannot execute Git hash-object: {e}")))?;
    let mut stdin = child
        .stdin
        .take()
        .ok_or_else(|| error("cannot write to Git hash-object"))?;
    stdin.write_all(bytes)?;
    drop(stdin);
    let output = child
        .wait_with_output()
        .map_err(|e| error(format!("cannot read Git hash-object result: {e}")))?;
    if !output.status.success() {
        return Err(error(format!(
            "Git hash-object failed: {}",
            String::from_utf8_lossy(&output.stderr).trim()
        )));
    }
    String::from_utf8(output.stdout)
        .map(|hash| hash.trim().to_owned())
        .map_err(|e| error(format!("Git returned a non-UTF-8 blob hash: {e}")))
}

fn verify_staged_bytes(
    root: &Path,
    relative_path: &str,
    expected: &[u8],
) -> Result<(), WorkspaceError> {
    let output = git(root, &["show", &format!(":{relative_path}")])?;
    if output.stdout != expected {
        return Err(error(format!(
            "Git index does not contain the exact canonical bytes for {relative_path}"
        )));
    }
    Ok(())
}

fn verify_staged_content_hashes(
    root: &Path,
    content_hashes: &BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    for (relative_path, expected_hash) in content_hashes {
        let output = git(root, &["show", &format!(":{relative_path}")])?;
        let actual_hash = format!("sha256:{:x}", Sha256::digest(output.stdout));
        if actual_hash != *expected_hash {
            return Err(error(format!(
                "Git index does not contain the declared content for {relative_path}"
            )));
        }
    }
    Ok(())
}

fn verify_head_content_hashes(
    root: &Path,
    content_hashes: &BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    let paths: Vec<_> = content_hashes.keys().map(String::as_str).collect();
    verify_blob_hashes(&read_head_blobs(root, &paths)?, content_hashes)
}

fn verify_blob_hashes(
    blobs: &BTreeMap<String, Vec<u8>>,
    content_hashes: &BTreeMap<String, String>,
) -> Result<(), WorkspaceError> {
    for (relative_path, expected_hash) in content_hashes {
        let actual_hash = format!("sha256:{:x}", Sha256::digest(&blobs[relative_path]));
        if actual_hash != *expected_hash {
            return Err(error(format!(
                "Git commit does not contain the declared content for {relative_path}"
            )));
        }
    }
    Ok(())
}

fn read_head_blobs(
    root: &Path,
    paths: &[&str],
) -> Result<BTreeMap<String, Vec<u8>>, WorkspaceError> {
    read_git_blobs(root, Some("HEAD"), paths)
}

pub(crate) fn read_git_blobs(
    root: &Path,
    revision: Option<&str>,
    paths: &[&str],
) -> Result<BTreeMap<String, Vec<u8>>, WorkspaceError> {
    if paths.is_empty() {
        return Ok(BTreeMap::new());
    }
    let mut requests = Vec::new();
    for path in paths {
        if path.contains('\0') {
            return Err(error("Git content path contains NUL"));
        }
        let selector = match revision {
            Some(revision) => format!("{revision}:{path}"),
            None => (*path).to_owned(),
        };
        requests.extend_from_slice(selector.as_bytes());
        requests.push(0);
    }
    let mut child = Command::new("git")
        .env("GIT_NO_LAZY_FETCH", "1")
        .arg("--literal-pathspecs")
        .arg("-C")
        .arg(root)
        .args(["cat-file", "--batch", "-z"])
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| error(format!("cannot execute Git: {e}")))?;
    let mut input = child
        .stdin
        .take()
        .ok_or_else(|| error("Git stdin unavailable"))?;
    // Drain stdout concurrently with request writes, including large manifest batches.
    let bytes = std::thread::scope(|scope| {
        let writer = scope.spawn(move || input.write_all(&requests));
        let output = child.wait_with_output();
        let written = writer
            .join()
            .map_err(|_| error("Git batch input writer failed"))?;
        let output = output?;
        if !output.status.success() {
            return Err(error(format!(
                "Git cat-file failed: {}",
                String::from_utf8_lossy(&output.stderr)
            )));
        }
        written?;
        Ok(output.stdout)
    })?;
    let mut remaining = bytes.as_slice();
    let mut blobs = BTreeMap::new();
    for path in paths {
        let newline = remaining
            .iter()
            .position(|b| *b == b'\n')
            .ok_or_else(|| error("incomplete Git batch header"))?;
        let header =
            std::str::from_utf8(&remaining[..newline]).map_err(|e| error(e.to_string()))?;
        let parts: Vec<_> = header.split_whitespace().collect();
        if parts.len() != 3 || parts[1] != "blob" {
            return Err(error(format!(
                "Git does not contain a readable blob for {path}"
            )));
        }
        let size: usize = parts[2]
            .parse()
            .map_err(|_| error("invalid Git batch blob size"))?;
        remaining = &remaining[newline + 1..];
        let content = remaining
            .get(..size)
            .ok_or_else(|| error("incomplete Git batch blob"))?;
        if remaining.get(size) != Some(&b'\n') {
            return Err(error("missing Git batch blob delimiter"));
        }
        blobs.insert((*path).to_owned(), content.to_vec());
        remaining = &remaining[size + 1..];
    }
    if !remaining.is_empty() {
        return Err(error("unexpected extra Git batch output"));
    }
    Ok(blobs)
}

pub(crate) fn parse_and_verify_journal(bytes: &[u8]) -> Result<Vec<JournalRecord>, WorkspaceError> {
    let mut records = Vec::new();
    let mut lines = bytes.split(|byte| *byte == b'\n').peekable();
    let mut index = 0;
    while let Some(line) = lines.next() {
        if lines.peek().is_none() {
            if !line.is_empty() || bytes.is_empty() {
                return Err(error(format!(
                    "journal ends with an incomplete record at line {index}"
                )));
            }
            break;
        }
        if line.is_empty() {
            return Err(error(format!(
                "journal contains an empty record at line {index}"
            )));
        }
        let record: JournalRecord = serde_json::from_slice(line)
            .map_err(|e| error(format!("invalid journal record at line {index}: {e}")))?;
        validate_content_hashes(&record.content_hashes)
            .map_err(|cause| error(format!("journal line {index}: {cause}")))?;
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
                "journal integrity check failed at line {index}, expected sequence {expected_sequence} (recorded {})",
                record.sequence,
            )));
        }
        records.push(record);
        index += 1;
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

pub(crate) fn ensure_git_identity(path: &Path) -> Result<(), WorkspaceError> {
    for variable in ["GIT_AUTHOR_IDENT", "GIT_COMMITTER_IDENT"] {
        let output = git(path, &["-c", "user.useConfigOnly=true", "var", variable])?;
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
    ensure_matches_committed(path, relative_path, bytes, &output.stdout)
}

fn ensure_matches_committed(
    path: &Path,
    relative_path: &str,
    bytes: &[u8],
    committed: &[u8],
) -> Result<(), WorkspaceError> {
    if committed != bytes {
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

pub(crate) fn git_text(path: &Path, args: &[&str]) -> Result<String, WorkspaceError> {
    let output = git(path, args)?;
    String::from_utf8(output.stdout)
        .map_err(|e| error(format!("Git returned non-UTF-8 output: {e}")))
}

pub(crate) fn git(path: &Path, args: &[&str]) -> Result<Output, WorkspaceError> {
    let output = Command::new("git")
        .env("GIT_NO_LAZY_FETCH", "1")
        .arg("--literal-pathspecs")
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
    use std::fs::OpenOptions;
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
    fn initialization_rollback_removes_only_attempt_resources_and_allows_retry() {
        let path = temp_dir();
        configure_git(&path);
        let git_config = fs::read(path.join(".git/config")).unwrap();
        let mut guard = InitializationGuard::new(&path);
        guard.create_directory(".eqms").unwrap();
        guard.create_directory("documents").unwrap();
        guard.write_file(".eqms/partial.json", b"partial").unwrap();

        let failure = guard.abort(error("injected initialization failure"));
        assert!(failure.to_string().contains("attempt-created"));
        assert!(!path.join(".eqms").exists());
        assert!(!path.join("documents").exists());
        assert_eq!(fs::read(path.join(".git/config")).unwrap(), git_config);
        assert!(!has_head(&path));

        let workspace = Workspace::initialize(&path).unwrap();
        assert_eq!(workspace.verify().unwrap().len(), 1);
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn batch_blob_reads_are_binary_safe_and_drain_large_requests() {
        let path = temp_dir();
        configure_git(&path);
        Workspace::initialize(&path).unwrap();
        let bytes = [b'a', 0, b'\n', 255].repeat(32);
        fs::write(path.join("documents/batch.md"), &bytes).unwrap();
        git(&path, &["add", "documents/batch.md"]).unwrap();
        git(&path, &["commit", "--quiet", "-m", "Batch read fixture"]).unwrap();
        let paths = vec!["documents/batch.md"; 5000];
        let blobs = read_head_blobs(&path, &paths).unwrap();
        assert_eq!(blobs["documents/batch.md"], bytes);
        assert!(read_head_blobs(&path, &["documents/missing.md"]).is_err());
        assert!(read_head_blobs(&path, &["documents"]).is_err());
        assert!(read_head_blobs(&path, &["documents/\0bad"]).is_err());
        fs::remove_dir_all(path).unwrap();
    }

    #[cfg(unix)]
    #[test]
    fn batch_blob_reads_preserve_newlines_in_paths() {
        let path = temp_dir();
        configure_git(&path);
        Workspace::initialize(&path).unwrap();
        let relative = "documents/line\nbreak.md";
        fs::write(path.join(relative), b"original\n").unwrap();
        git(&path, &["add", relative]).unwrap();
        git(&path, &["commit", "--quiet", "-m", "Literal path fixture"]).unwrap();
        assert_eq!(
            read_head_blobs(&path, &[relative]).unwrap()[relative],
            b"original\n"
        );
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn concurrent_initializations_recheck_state_under_the_workspace_lock() {
        let path = temp_dir();
        configure_git(&path);
        let barrier = std::sync::Arc::new(std::sync::Barrier::new(2));
        let first_path = path.clone();
        let first_barrier = barrier.clone();
        let first = std::thread::spawn(move || {
            first_barrier.wait();
            Workspace::initialize(first_path)
        });
        let second_path = path.clone();
        let second = std::thread::spawn(move || {
            barrier.wait();
            Workspace::initialize(second_path)
        });

        let successes = [first.join().unwrap(), second.join().unwrap()]
            .into_iter()
            .filter(Result::is_ok)
            .count();
        assert_eq!(successes, 1);
        assert_eq!(Workspace::open(&path).unwrap().verify().unwrap().len(), 1);
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
    fn records_a_valid_module_manifest_update() {
        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        let manifest = serde_json::to_vec(&ModuleManifest {
            schema_version: 1,
            modules: vec![ModuleEntry {
                id: "iso-13485".into(),
                version: "1".into(),
            }],
        })
        .unwrap();
        fs::write(path.join(MODULE_MANIFEST_PATH), &manifest).unwrap();
        let hash = format!("sha256:{:x}", Sha256::digest(&manifest));

        let event = workspace
            .record_event(
                "module.updated",
                BTreeMap::new(),
                BTreeMap::from([(MODULE_MANIFEST_PATH.into(), hash.clone())]),
            )
            .unwrap();

        assert_eq!(event.content_hashes[MODULE_MANIFEST_PATH], hash);
        assert_eq!(workspace.verify().unwrap().len(), 2);
        assert_eq!(
            git(&path, &["show", &format!("HEAD:{MODULE_MANIFEST_PATH}")])
                .unwrap()
                .stdout,
            manifest
        );
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn recovery_commits_a_valid_pending_manifest_update() {
        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        let previous = workspace.verify().unwrap().pop().unwrap();
        let manifest = serde_json::to_vec(&ModuleManifest {
            schema_version: 1,
            modules: vec![ModuleEntry {
                id: "iso-13485".into(),
                version: "1".into(),
            }],
        })
        .unwrap();
        fs::write(path.join(MODULE_MANIFEST_PATH), &manifest).unwrap();
        let hash = format!("sha256:{:x}", Sha256::digest(&manifest));
        let record = with_hash(JournalRecord {
            schema_version: WORKSPACE_SCHEMA_VERSION,
            sequence: 1,
            previous_hash: Some(previous.hash),
            timestamp: timestamp().unwrap(),
            event_type: "module.updated".into(),
            payload: BTreeMap::new(),
            content_hashes: BTreeMap::from([(MODULE_MANIFEST_PATH.into(), hash)]),
            hash: String::new(),
        })
        .unwrap();
        let mut journal = OpenOptions::new()
            .append(true)
            .open(path.join(JOURNAL_PATH))
            .unwrap();
        serde_json::to_writer(&mut journal, &record).unwrap();
        journal.write_all(b"\n").unwrap();
        journal.sync_all().unwrap();

        workspace.recover().unwrap();

        assert_eq!(workspace.verify().unwrap().len(), 2);
        assert_eq!(
            git(&path, &["show", &format!("HEAD:{MODULE_MANIFEST_PATH}")])
                .unwrap()
                .stdout,
            manifest
        );
        fs::remove_dir_all(path).unwrap();
    }

    #[cfg(unix)]
    #[test]
    fn opening_and_recovery_reject_symlinked_canonical_paths() {
        use std::os::unix::fs::symlink;

        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        let outside = temp_dir();
        let sentinel = outside.join("sentinel");
        fs::write(&sentinel, "unchanged").unwrap();

        let config = path.join(CONFIG_PATH);
        let saved_config = path.join(".eqms/config.saved");
        fs::rename(&config, &saved_config).unwrap();
        symlink(&sentinel, &config).unwrap();
        assert!(Workspace::open(&path).is_err());
        assert!(workspace.recover().is_err());
        assert_eq!(fs::read(&sentinel).unwrap(), b"unchanged");
        fs::remove_file(&config).unwrap();
        fs::rename(saved_config, config).unwrap();

        let eqms = path.join(".eqms");
        let saved_eqms = path.join(".eqms.saved");
        fs::rename(&eqms, &saved_eqms).unwrap();
        symlink(&outside, &eqms).unwrap();
        assert!(Workspace::open(&path).is_err());
        assert!(workspace.recover().is_err());
        assert_eq!(fs::read(&sentinel).unwrap(), b"unchanged");
        fs::remove_file(&eqms).unwrap();
        fs::rename(saved_eqms, eqms).unwrap();

        fs::remove_dir_all(outside).unwrap();
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
    fn open_rejects_a_nested_directory_inside_another_git_repository() {
        let path = temp_dir();
        configure_git(&path);
        let nested = path.join("nested");
        fs::create_dir_all(nested.join(".eqms")).unwrap();
        fs::create_dir_all(nested.join("modules")).unwrap();
        fs::write(nested.join(CONFIG_PATH), b"{}").unwrap();
        fs::write(nested.join(JOURNAL_PATH), b"{}\n").unwrap();
        fs::write(nested.join(MODULE_MANIFEST_PATH), b"{}").unwrap();

        let result = Workspace::open(&nested);

        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Git root"));
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
    fn recovery_preserves_a_committed_record_missing_its_newline() {
        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        workspace
            .record_event("test.record", BTreeMap::new(), BTreeMap::new())
            .unwrap();
        let journal_path = path.join(JOURNAL_PATH);
        let mut bytes = fs::read(&journal_path).unwrap();
        bytes.pop();
        fs::write(&journal_path, &bytes).unwrap();
        let head_before = git(&path, &["rev-parse", "HEAD"]).unwrap().stdout;

        assert!(workspace.recover().is_err());
        assert_eq!(fs::read(&journal_path).unwrap(), bytes);
        assert_eq!(
            git(&path, &["rev-parse", "HEAD"]).unwrap().stdout,
            head_before
        );
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn recovery_refuses_invalid_partial_initialization_without_committing() {
        let path = temp_dir();
        configure_git(&path);
        fs::create_dir_all(path.join(".eqms")).unwrap();
        fs::create_dir_all(path.join("modules")).unwrap();
        fs::write(path.join(CONFIG_PATH), b"{}").unwrap();
        fs::write(path.join(MODULE_MANIFEST_PATH), b"{}").unwrap();
        fs::write(path.join(JOURNAL_PATH), b"{}\n").unwrap();
        let original_journal = fs::read(path.join(JOURNAL_PATH)).unwrap();
        let workspace = Workspace::open(&path).unwrap();

        assert!(workspace.recover().is_err());
        assert!(!has_head(&path));
        assert_eq!(fs::read(path.join(JOURNAL_PATH)).unwrap(), original_journal);
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn recording_refuses_git_filters_that_change_hashed_bytes() {
        let path = temp_dir();
        configure_git(&path);
        git(&path, &["config", "core.autocrlf", "true"]).unwrap();
        let workspace = Workspace::initialize(&path).unwrap();
        let content = b"# Filtered document\r\n";
        fs::write(path.join("documents/filtered.md"), content).unwrap();
        let journal_before = read_workspace_file(&path, JOURNAL_PATH).unwrap();
        let head_before = git(&path, &["rev-parse", "HEAD"]).unwrap().stdout;

        let result = workspace.record_event(
            "document.updated",
            BTreeMap::new(),
            BTreeMap::from([(
                "documents/filtered.md".into(),
                format!("sha256:{:x}", Sha256::digest(content)),
            )]),
        );

        assert!(result.is_err());
        assert_eq!(
            read_workspace_file(&path, JOURNAL_PATH).unwrap(),
            journal_before
        );
        assert_eq!(
            git(&path, &["rev-parse", "HEAD"]).unwrap().stdout,
            head_before
        );
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn recovery_refuses_filtered_content_before_advancing_head() {
        let path = temp_dir();
        configure_git(&path);
        git(&path, &["config", "core.autocrlf", "true"]).unwrap();
        let workspace = Workspace::initialize(&path).unwrap();
        let content = b"# Filtered document\r\n";
        fs::write(path.join("documents/filtered.md"), content).unwrap();
        let previous = workspace.verify().unwrap().pop().unwrap();
        let record = with_hash(JournalRecord {
            schema_version: WORKSPACE_SCHEMA_VERSION,
            sequence: 1,
            previous_hash: Some(previous.hash),
            timestamp: timestamp().unwrap(),
            event_type: "document.updated".into(),
            payload: BTreeMap::new(),
            content_hashes: BTreeMap::from([(
                "documents/filtered.md".into(),
                format!("sha256:{:x}", Sha256::digest(content)),
            )]),
            hash: String::new(),
        })
        .unwrap();
        let mut journal = OpenOptions::new()
            .append(true)
            .open(path.join(JOURNAL_PATH))
            .unwrap();
        serde_json::to_writer(&mut journal, &record).unwrap();
        journal.write_all(b"\n").unwrap();
        journal.sync_all().unwrap();
        let head_before = git(&path, &["rev-parse", "HEAD"]).unwrap().stdout;

        assert!(workspace.recover().is_err());
        assert_eq!(
            git(&path, &["rev-parse", "HEAD"]).unwrap().stdout,
            head_before
        );
        fs::remove_dir_all(path).unwrap();
    }

    #[test]
    fn event_paths_with_wildcards_do_not_stage_siblings() {
        let path = temp_dir();
        configure_git(&path);
        let workspace = Workspace::initialize(&path).unwrap();
        let selected = b"# Selected\n";
        fs::write(path.join("documents/selected*.md"), selected).unwrap();
        fs::write(path.join("documents/selected-extra.md"), "# Unselected\n").unwrap();

        workspace
            .record_event(
                "document.updated",
                BTreeMap::new(),
                BTreeMap::from([(
                    "documents/selected*.md".into(),
                    format!("sha256:{:x}", Sha256::digest(selected)),
                )]),
            )
            .unwrap();

        assert_eq!(
            git(&path, &["show", "HEAD:documents/selected*.md"])
                .unwrap()
                .stdout,
            selected
        );
        assert!(git(&path, &["show", "HEAD:documents/selected-extra.md"]).is_err());
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
