/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

data class CargoOption(val name: String, val description: String) {
    val longName: String get() = "--$name"
}

enum class CargoCommands(val description: String, val options: List<CargoOption>) {
    BENCH(
        description = "Execute benchmarks of a package",
        options = listOf(
            CargoOption("no-run", """Compile, but don’t run benchmarks"""),
            CargoOption("no-fail-fast", """Run all benchmarks regardless of failure"""),
            CargoOption("package", """Benchmark only the specified packages"""),
            CargoOption("workspace", """Benchmark all members in the workspace"""),
            CargoOption("all", """Deprecated alias for --workspace"""),
            CargoOption("exclude", """Exclude the specified packages"""),
            CargoOption("lib", """Benchmark the package’s library"""),
            CargoOption("bin", """Benchmark the specified binary"""),
            CargoOption("bins", """Benchmark all binary targets"""),
            CargoOption("example", """Benchmark the specified example"""),
            CargoOption("examples", """Benchmark all example targets"""),
            CargoOption("test", """Benchmark the specified integration test"""),
            CargoOption("tests", """Benchmark all targets in test mode that have the test = true manifest flag set"""),
            CargoOption("bench", """Benchmark the specified benchmark"""),
            CargoOption("benches", """Benchmark all targets in benchmark mode that have the bench = true manifest flag set"""),
            CargoOption("all-targets", """Benchmark all targets"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Benchmark for the given architecture"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("message-format", """The output format for diagnostic messages"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("jobs", """Number of parallel jobs to run""")
        )
    ),

    BUILD(
        description = "Compile the current package",
        options = listOf(
            CargoOption("package", """Build only the specified packages"""),
            CargoOption("workspace", """Build all members in the workspace"""),
            CargoOption("all", """Deprecated alias for --workspace"""),
            CargoOption("exclude", """Exclude the specified packages"""),
            CargoOption("lib", """Build the package’s library"""),
            CargoOption("bin", """Build the specified binary"""),
            CargoOption("bins", """Build all binary targets"""),
            CargoOption("example", """Build the specified example"""),
            CargoOption("examples", """Build all example targets"""),
            CargoOption("test", """Build the specified integration test"""),
            CargoOption("tests", """Build all targets in test mode that have the test = true manifest flag set"""),
            CargoOption("bench", """Build the specified benchmark"""),
            CargoOption("benches", """Build all targets in benchmark mode that have the bench = true manifest flag set"""),
            CargoOption("all-targets", """Build all targets"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Build for the given architecture"""),
            CargoOption("release", """Build optimized artifacts with the release profile"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("out-dir", """Copy final artifacts to this directory"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("message-format", """The output format for diagnostic messages"""),
            CargoOption("build-plan", """Outputs a series of JSON messages to stdout that indicate the commands to run the build"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("jobs", """Number of parallel jobs to run""")
        )
    ),

    CHECK(
        description = "Check the current package",
        options = listOf(
            CargoOption("package", """Check only the specified packages"""),
            CargoOption("workspace", """Check all members in the workspace"""),
            CargoOption("all", """Deprecated alias for --workspace"""),
            CargoOption("exclude", """Exclude the specified packages"""),
            CargoOption("lib", """Check the package’s library"""),
            CargoOption("bin", """Check the specified binary"""),
            CargoOption("bins", """Check all binary targets"""),
            CargoOption("example", """Check the specified example"""),
            CargoOption("examples", """Check all example targets"""),
            CargoOption("test", """Check the specified integration test"""),
            CargoOption("tests", """Check all targets in test mode that have the test = true manifest flag set"""),
            CargoOption("bench", """Check the specified benchmark"""),
            CargoOption("benches", """Check all targets in benchmark mode that have the bench = true manifest flag set"""),
            CargoOption("all-targets", """Check all targets"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Check for the given architecture"""),
            CargoOption("release", """Check optimized artifacts with the release profile"""),
            CargoOption("profile", """Changes check behavior"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("message-format", """The output format for diagnostic messages"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("jobs", """Number of parallel jobs to run""")
        )
    ),

    CLEAN(
        description = "Remove generated artifacts",
        options = listOf(
            CargoOption("package", """Clean only the specified packages"""),
            CargoOption("doc", """This option will cause cargo clean to remove only the doc directory in the target directory"""),
            CargoOption("release", """Clean all artifacts that were built with the release or bench profiles"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("target", """Clean for the given architecture"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    DOC(
        description = "Build a package's documentation",
        options = listOf(
            CargoOption("open", """Open the docs in a browser after building them"""),
            CargoOption("no-deps", """Do not build documentation for dependencies"""),
            CargoOption("document-private-items", """Include non-public items in the documentation"""),
            CargoOption("package", """Document only the specified packages"""),
            CargoOption("workspace", """Document all members in the workspace"""),
            CargoOption("all", """Deprecated alias for --workspace"""),
            CargoOption("exclude", """Exclude the specified packages"""),
            CargoOption("lib", """Document the package’s library"""),
            CargoOption("bin", """Document the specified binary"""),
            CargoOption("bins", """Document all binary targets"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Document for the given architecture"""),
            CargoOption("release", """Document optimized artifacts with the release profile"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("message-format", """The output format for diagnostic messages"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("jobs", """Number of parallel jobs to run""")
        )
    ),

    FETCH(
        description = "Fetch dependencies of a package from the network",
        options = listOf(
            CargoOption("target", """Fetch for the given architecture"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    FIX(
        description = "Automatically fix lint warnings reported by rustc",
        options = listOf(
            CargoOption("broken-code", """Fix code even if it already has compiler errors"""),
            CargoOption("edition", """Apply changes that will update the code to the latest edition"""),
            CargoOption("edition-idioms", """Apply suggestions that will update code to the preferred style for the current edition"""),
            CargoOption("allow-no-vcs", """Fix code even if a VCS was not detected"""),
            CargoOption("allow-dirty", """Fix code even if the working directory has changes"""),
            CargoOption("allow-staged", """Fix code even if the working directory has staged changes"""),
            CargoOption("package", """Fix only the specified packages"""),
            CargoOption("workspace", """Fix all members in the workspace"""),
            CargoOption("all", """Deprecated alias for --workspace"""),
            CargoOption("exclude", """Exclude the specified packages"""),
            CargoOption("lib", """Fix the package’s library"""),
            CargoOption("bin", """Fix the specified binary"""),
            CargoOption("bins", """Fix all binary targets"""),
            CargoOption("example", """Fix the specified example"""),
            CargoOption("examples", """Fix all example targets"""),
            CargoOption("test", """Fix the specified integration test"""),
            CargoOption("tests", """Fix all targets in test mode that have the test = true manifest flag set"""),
            CargoOption("bench", """Fix the specified benchmark"""),
            CargoOption("benches", """Fix all targets in benchmark mode that have the bench = true manifest flag set"""),
            CargoOption("all-targets", """Fix all targets"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Fix for the given architecture"""),
            CargoOption("release", """Fix optimized artifacts with the release profile"""),
            CargoOption("profile", """Changes fix behavior"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("message-format", """The output format for diagnostic messages"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("jobs", """Number of parallel jobs to run""")
        )
    ),

    RUN(
        description = "Run the current package",
        options = listOf(
            CargoOption("package", """The package to run"""),
            CargoOption("bin", """Run the specified binary"""),
            CargoOption("example", """Run the specified example"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Run for the given architecture"""),
            CargoOption("release", """Run optimized artifacts with the release profile"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("message-format", """The output format for diagnostic messages"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("jobs", """Number of parallel jobs to run""")
        )
    ),

    RUSTC(
        description = "Compile the current package, and pass extra options to the compiler",
        options = listOf(
            CargoOption("package", """The package to build"""),
            CargoOption("lib", """Build the package’s library"""),
            CargoOption("bin", """Build the specified binary"""),
            CargoOption("bins", """Build all binary targets"""),
            CargoOption("example", """Build the specified example"""),
            CargoOption("examples", """Build all example targets"""),
            CargoOption("test", """Build the specified integration test"""),
            CargoOption("tests", """Build all targets in test mode that have the test = true manifest flag set"""),
            CargoOption("bench", """Build the specified benchmark"""),
            CargoOption("benches", """Build all targets in benchmark mode that have the bench = true manifest flag set"""),
            CargoOption("all-targets", """Build all targets"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Build for the given architecture"""),
            CargoOption("release", """Build optimized artifacts with the release profile"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("message-format", """The output format for diagnostic messages"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("jobs", """Number of parallel jobs to run""")
        )
    ),

    RUSTDOC(
        description = "Build a package's documentation, using specified custom flags",
        options = listOf(
            CargoOption("open", """Open the docs in a browser after building them"""),
            CargoOption("package", """The package to document"""),
            CargoOption("lib", """Document the package’s library"""),
            CargoOption("bin", """Document the specified binary"""),
            CargoOption("bins", """Document all binary targets"""),
            CargoOption("example", """Document the specified example"""),
            CargoOption("examples", """Document all example targets"""),
            CargoOption("test", """Document the specified integration test"""),
            CargoOption("tests", """Document all targets in test mode that have the test = true manifest flag set"""),
            CargoOption("bench", """Document the specified benchmark"""),
            CargoOption("benches", """Document all targets in benchmark mode that have the bench = true manifest flag set"""),
            CargoOption("all-targets", """Document all targets"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Document for the given architecture"""),
            CargoOption("release", """Document optimized artifacts with the release profile"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("message-format", """The output format for diagnostic messages"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("jobs", """Number of parallel jobs to run""")
        )
    ),

    TEST(
        description = "Execute unit and integration tests of a package",
        options = listOf(
            CargoOption("no-run", """Compile, but don’t run tests"""),
            CargoOption("no-fail-fast", """Run all tests regardless of failure"""),
            CargoOption("package", """Test only the specified packages"""),
            CargoOption("workspace", """Test all members in the workspace"""),
            CargoOption("all", """Deprecated alias for --workspace"""),
            CargoOption("exclude", """Exclude the specified packages"""),
            CargoOption("lib", """Test the package’s library"""),
            CargoOption("bin", """Test the specified binary"""),
            CargoOption("bins", """Test all binary targets"""),
            CargoOption("example", """Test the specified example"""),
            CargoOption("examples", """Test all example targets"""),
            CargoOption("test", """Test the specified integration test"""),
            CargoOption("tests", """Test all targets in test mode that have the test = true manifest flag set"""),
            CargoOption("bench", """Test the specified benchmark"""),
            CargoOption("benches", """Test all targets in benchmark mode that have the bench = true manifest flag set"""),
            CargoOption("all-targets", """Test all targets"""),
            CargoOption("doc", """Test only the library’s documentation"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Test for the given architecture"""),
            CargoOption("release", """Test optimized artifacts with the release profile"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("message-format", """The output format for diagnostic messages"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("jobs", """Number of parallel jobs to run""")
        )
    ),

    GENERATE_LOCKFILE(
        description = "Generate the lockfile for a package",
        options = listOf(
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    LOCATE_PROJECT(
        description = "Print a JSON representation of a Cargo.toml file's location",
        options = listOf(
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    METADATA(
        description = "Machine-readable metadata about the current package",
        options = listOf(
            CargoOption("no-deps", """Output information only about the workspace members and don’t fetch dependencies"""),
            CargoOption("format-version", """Specify the version of the output format to use"""),
            CargoOption("filter-platform", """This filters the resolve output to only include dependencies for the given target triple"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    PKGID(
        description = "Print a fully qualified package specification",
        options = listOf(
            CargoOption("package", """Get the package ID for the given package instead of the current package"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    TREE(
        description = "Display a tree visualization of a dependency graph",
        options = listOf(
            CargoOption("invert", """Show the reverse dependencies for the given package"""),
            CargoOption("no-dedupe", """Do not de-duplicate repeated dependencies"""),
            CargoOption("duplicates", """Show only dependencies which come in multiple versions (implies --invert)"""),
            CargoOption("edges", """The dependency kinds to display"""),
            CargoOption("target", """Filter dependencies matching the given target-triple"""),
            CargoOption("charset", """Chooses the character set to use for the tree"""),
            CargoOption("format", """Set the format string for each package"""),
            CargoOption("prefix", """Sets how each line is displayed"""),
            CargoOption("package", """Display only the specified packages"""),
            CargoOption("workspace", """Display all members in the workspace"""),
            CargoOption("exclude", """Exclude the specified packages"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason""")
        )
    ),

    UPDATE(
        description = "Update dependencies as recorded in the local lock file",
        options = listOf(
            CargoOption("package", """Update only the specified packages"""),
            CargoOption("aggressive", """When used with -p, dependencies of SPEC are forced to update as well"""),
            CargoOption("precise", """When used with -p, allows you to specify a specific version number to set the package to"""),
            CargoOption("dry-run", """Displays what would be updated, but doesn’t actually write the lockfile"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    VENDOR(
        description = "Vendor all dependencies locally",
        options = listOf(
            CargoOption("sync", """Specify extra Cargo.toml manifests to workspaces which should also be vendored and synced to the output"""),
            CargoOption("no-delete", """Don’t delete the "vendor" directory when vendoring, but rather keep all existing contents of the vendor directory"""),
            CargoOption("respect-source-config", """Instead of ignoring [source] configuration by default in .cargo/config.toml read it and use it when downloading crates from crates.io, for example"""),
            CargoOption("versioned-dirs", """Normally versions are only added to disambiguate multiple versions of the same package"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason""")
        )
    ),

    VERIFY_PROJECT(
        description = "Check correctness of crate manifest",
        options = listOf(
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    INIT(
        description = "Create a new Cargo package in an existing directory",
        options = listOf(
            CargoOption("bin", """Create a package with a binary target (src/main.rs)"""),
            CargoOption("lib", """Create a package with a library target (src/lib.rs)"""),
            CargoOption("edition", """Specify the Rust edition to use"""),
            CargoOption("name", """Set the package name"""),
            CargoOption("vcs", """Initialize a new VCS repository for the given version control system (git, hg, pijul, or fossil) or do not initialize any version control at all (none)"""),
            CargoOption("registry", """This sets the publish field in Cargo.toml to the given registry name which will restrict publishing only to that registry"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    INSTALL(
        description = "Build and install a Rust binary",
        options = listOf(
            CargoOption("vers", """Specify a version to install"""),
            CargoOption("version", """Specify a version to install"""),
            CargoOption("git", """Git URL to install the specified crate from"""),
            CargoOption("branch", """Branch to use when installing from git"""),
            CargoOption("tag", """Tag to use when installing from git"""),
            CargoOption("rev", """Specific commit to use when installing from git"""),
            CargoOption("path", """Filesystem path to local crate to install"""),
            CargoOption("list", """List all installed packages and their versions"""),
            CargoOption("force", """Force overwriting existing crates or binaries"""),
            CargoOption("no-track", """By default, Cargo keeps track of the installed packages with a metadata file stored in the installation root directory"""),
            CargoOption("bin", """Install only the specified binary"""),
            CargoOption("bins", """Install all binaries"""),
            CargoOption("example", """Install only the specified example"""),
            CargoOption("examples", """Install all examples"""),
            CargoOption("root", """Directory to install packages into"""),
            CargoOption("registry", """Name of the registry to use"""),
            CargoOption("index", """The URL of the registry index to use"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("target", """Install for the given architecture"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("debug", """Build with the dev profile instead the release profile"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("jobs", """Number of parallel jobs to run"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    NEW(
        description = "Create a new Cargo package",
        options = listOf(
            CargoOption("bin", """Create a package with a binary target (src/main.rs)"""),
            CargoOption("lib", """Create a package with a library target (src/lib.rs)"""),
            CargoOption("edition", """Specify the Rust edition to use"""),
            CargoOption("name", """Set the package name"""),
            CargoOption("vcs", """Initialize a new VCS repository for the given version control system (git, hg, pijul, or fossil) or do not initialize any version control at all (none)"""),
            CargoOption("registry", """This sets the publish field in Cargo.toml to the given registry name which will restrict publishing only to that registry"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    SEARCH(
        description = "Search packages in crates.io",
        options = listOf(
            CargoOption("limit", """Limit the number of results (default: 10, max: 100)"""),
            CargoOption("index", """The URL of the registry index to use"""),
            CargoOption("registry", """Name of the registry to use"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    UNINSTALL(
        description = "Remove a Rust binary",
        options = listOf(
            CargoOption("package", """Package to uninstall"""),
            CargoOption("bin", """Only uninstall the binary NAME"""),
            CargoOption("root", """Directory to uninstall packages from"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    LOGIN(
        description = "Save an API token from the registry locally",
        options = listOf(
            CargoOption("registry", """Name of the registry to use"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    OWNER(
        description = "Manage the owners of a crate on the registry",
        options = listOf(
            CargoOption("add", """Invite the given user or team as an owner"""),
            CargoOption("remove", """Remove the given user or team as an owner"""),
            CargoOption("list", """List owners of a crate"""),
            CargoOption("token", """API token to use when authenticating"""),
            CargoOption("index", """The URL of the registry index to use"""),
            CargoOption("registry", """Name of the registry to use"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    PACKAGE(
        description = "Assemble the local package into a distributable tarball",
        options = listOf(
            CargoOption("list", """Print files included in a package without making one"""),
            CargoOption("no-verify", """Don’t verify the contents by building them"""),
            CargoOption("no-metadata", """Ignore warnings about a lack of human-usable metadata (such as the description or the license)"""),
            CargoOption("allow-dirty", """Allow working directories with uncommitted VCS changes to be packaged"""),
            CargoOption("target", """Package for the given architecture"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("jobs", """Number of parallel jobs to run"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    PUBLISH(
        description = "Upload a package to the registry",
        options = listOf(
            CargoOption("dry-run", """Perform all checks without uploading"""),
            CargoOption("token", """API token to use when authenticating"""),
            CargoOption("no-verify", """Don’t verify the contents by building them"""),
            CargoOption("allow-dirty", """Allow working directories with uncommitted VCS changes to be packaged"""),
            CargoOption("index", """The URL of the registry index to use"""),
            CargoOption("registry", """Name of the registry to use"""),
            CargoOption("target", """Publish for the given architecture"""),
            CargoOption("target-dir", """Directory for all generated artifacts and intermediate files"""),
            CargoOption("features", """Space or comma separated list of features to activate"""),
            CargoOption("all-features", """Activate all available features of all selected packages"""),
            CargoOption("no-default-features", """Do not activate the default feature of the current directory’s package"""),
            CargoOption("manifest-path", """Path to the Cargo.toml file"""),
            CargoOption("frozen", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("locked", """Either of these flags requires that the Cargo.lock file is up-to-date"""),
            CargoOption("offline", """Prevents Cargo from accessing the network for any reason"""),
            CargoOption("jobs", """Number of parallel jobs to run"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    YANK(
        description = "Remove a pushed crate from the index",
        options = listOf(
            CargoOption("vers", """The version to yank or un-yank"""),
            CargoOption("undo", """Undo a yank, putting a version back into the index"""),
            CargoOption("token", """API token to use when authenticating"""),
            CargoOption("index", """The URL of the registry index to use"""),
            CargoOption("registry", """Name of the registry to use"""),
            CargoOption("verbose", """Use verbose output"""),
            CargoOption("quiet", """No output printed to stdout"""),
            CargoOption("color", """Control when colored output is used"""),
            CargoOption("help", """Prints help information""")
        )
    ),

    HELP(
        description = "Get help for a Cargo command",
        options = emptyList()
    ),

    VERSION(
        description = "Show version information",
        options = listOf(
            CargoOption("verbose", """Display additional version information""")
        )
    );

    val presentableName: String get() = name.lowercase().replace('_', '-')
}
