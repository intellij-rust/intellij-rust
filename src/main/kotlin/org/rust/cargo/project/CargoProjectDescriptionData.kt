package org.rust.cargo.project

/**
 * A POD-style representation of [CargoProjectDescription] used as intermediate representation
 * between `cargo metadata` JSON and [CargoProjectDescription] object graph.
 *
 * Dependency graph is via adjacency list, where `Index` is the order of a particular
 * package in `packages` list.
 */
data class CargoProjectDescriptionData(
    var rootPackageIndex: Int,
    val packages: List<Package>,
    val dependencies: Collection<DependencyNode>
) {
    data class DependencyNode(
        var packageIndex: Int,
        var dependenciesIndexes: Collection<Int>
    )

    data class Package(
        var contentRootUrl: String,
        var name: String,
        var version: String,
        var targets: Collection<Target>,
        var source: String?
    )

    data class Target(
        var url: String,
        var kind: CargoProjectDescription.TargetKind
    )
}

