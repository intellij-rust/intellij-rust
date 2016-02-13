package org.rust.cargo.commands.impl

/**
 * Classes mirroring JSON output of `cargo metadata`.
 * Attribute names and snake_case are crucial.
 *
 * Some information available in JSON is not represented here
 */
data class Project(
    /**
     * All packages, including dependencies
     */
    val packages: List<Package>,

    /**
     * A graph of dependencies
     */
    val resolve: Resolve,

    /**
     * Version of the format (currently 1)
     */
    val version: Int
)


data class Package(
    val name: String,

    /**
     * SemVer version
     */
    val version: String,

    /**
     * Where did this package comes from? Local file system, crates.io, github repository.
     *
     * Will be `null` for the root package and path dependencies.
     */
    val source: String?,

    /**
     * A unique id.
     * There may be several packages with the same name, but different version/source.
     * The triple (name, version, source) is unique.
     */
    val id: String,

    /**
     * Path to Cargo.toml
     */
    val manifest_path: String,

    /**
     * Artifacts that can be build from this package.
     * This is a list of crates that can be build from the package.
     */
    val targets: List<Target>
)


data class Target(
    /**
     * Kind of a target. Can be a singleton list ["bin"],
     * ["example], ["test"], ["example"], ["custom-build"], ["bench"].
     *
     * Can also be a list of one or more of "lib", "rlib", "dylib", "staticlib"
     */
    val kind: List<String>,

    /**
     * Name
     */
    val name: String,

    /**
     * Path to the root module of the crate (aka crate root)
     */
    val src_path: String
)


/**
 * A rooted DAG of dependencies, represented as adjacency list
 */
data class Resolve(
    /**
     * id of the main package
     */
    val root: String,
    val nodes: List<ResolveNode>
)


data class ResolveNode(
    val id: String,

    /**
     * id's of dependent packages
     */
    val dependencies: List<String>
)

