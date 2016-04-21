package org.rust.cargo.project

import java.util.*

/**
 * A serialization friendly representation of CargoProjectDescription.
 * All classes have default constructors and mutable nullable fields. This is
 * necessary because XmlSerializer restores an object by mutating it from the
 * default state.
 *
 * Dependency graph is via adjacency list, where `Index` is the order of a particular
 * package in `packages` list.
 */
data class CargoProjectDescriptionData(
    var rootPackageIndex: Int = 0,
    val packages: MutableList<Package> = ArrayList(),
    val dependencies: MutableList<DependencyNode> = ArrayList()
) {
    data class DependencyNode(
        var packageIndex: Int = 0,
        var dependenciesIndexes: MutableList<Int> = ArrayList()
    )

    data class Package(
        var contentRootUrl: String? = null,
        var name: String? = null,
        var version: String? = null,
        var targets: Collection<Target> = ArrayList(),
        var source: String? = null
    )

    data class Target(
        var url: String? = null,
        var kind: CargoProjectDescription.TargetKind? = null
    )
}

