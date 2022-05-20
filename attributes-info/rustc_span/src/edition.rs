#[derive(Copy, Clone, Debug)]
pub enum Edition {
    // When adding new editions, be sure to do the following:
    //
    // - update the `ALL_EDITIONS` const
    // - update the `EDITION_NAME_LIST` const
    // - add a `rust_####()` function to the session
    // - update the enum in Cargo's sources as well
    //
    // Editions *must* be kept in order, oldest to newest.
    /// The 2015 edition
    Edition2015,
    /// The 2018 edition
    Edition2018,
    /// The 2021 edition
    Edition2021,
    /// The 2024 edition
    Edition2024,
}
