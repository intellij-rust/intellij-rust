/// Source of informations about a group of packages.
///
/// See also `core::Source`.
pub trait Registry {
    /// Attempt to find the packages that match a dependency request.
    fn query(&mut self, name: &Dependency) -> CargoResult<Vec<Summary>>;
}

impl Registry for Vec<Summary> {
    fn query(&mut self, dep: &Dependency) -> CargoResult<Vec<Summary>> {
        Ok(self.iter().filter(|summary| dep.matches(*summary))
               .map(|summary| summary.clone()).collect())
    }
}

impl Registry for Vec<Package> {
    fn query(&mut self, dep: &Dependency) -> CargoResult<Vec<Summary>> {
        Ok(self.iter().filter(|pkg| dep.matches(pkg.summary()))
               .map(|pkg| pkg.summary().clone()).collect())
    }
}

impl<'cfg> PackageRegistry<'cfg> {
    pub fn new(config: &'cfg Config) -> PackageRegistry<'cfg> {
        // ...
    }

    pub fn get(&mut self, package_ids: &[PackageId]) -> CargoResult<Vec<Package>> {
        // ...
    }
}
