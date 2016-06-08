package org.rust.cargo.project

/**
 * A POD-style representation of [CargoProjectDescription] used as intermediate representation
 * between `cargo metadata` JSON and [CargoProjectDescription] object graph.
 *
 * Dependency graph is represented via adjacency list, where `Index` is the order of a particular
 * package in `packages` list.
 */
data class CargoProjectDescriptionData(
    val rootPackageIndex: Int,
    val packages: List<Package>,
    val dependencies: Collection<DependencyNode>
) {
    data class DependencyNode(
        val packageIndex: Int,
        val dependenciesIndexes: Collection<Int>
    )

    data class Package(
        val contentRootUrl: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?
    )

    data class Target(
        val url: String,
        val kind: CargoProjectDescription.TargetKind
    )
}

