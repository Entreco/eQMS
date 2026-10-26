/// Version of the core contract implementation.
pub const fn version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}

pub mod document;
pub mod index;
pub mod model;
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
