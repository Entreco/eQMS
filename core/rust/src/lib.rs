/// Version of the core contract implementation.
pub const fn version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}

pub mod audit;
pub mod branding;
pub mod document;
pub mod import;
pub mod index;
pub mod markdown;
pub mod model;
pub mod module;
pub mod plugin;
pub mod render;
pub mod roles;
pub mod search;
pub mod workspace;

#[cfg(test)]
mod tests {
    #[test]
    fn core_version_is_available() {
        assert_eq!(super::version(), env!("CARGO_PKG_VERSION"));
        assert!(!super::version().is_empty());
    }
}
