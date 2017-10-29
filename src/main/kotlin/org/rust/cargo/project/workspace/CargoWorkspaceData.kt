/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

typealias PackageId = String

/**
 * A POD-style representation of [CargoWorkspace] used as an intermediate representation
 * between `cargo metadata` JSON and [CargoWorkspace] object graph.
 *
 * Dependency graph is represented via adjacency list, where `Index` is the order of a particular
 * package in `packages` list.
 */
data class CargoWorkspaceData(
    val packages: List<Package>,
    val dependencies: Map<PackageId, Set<PackageId>>
) {
    data class DependencyNode(
        val packageIndex: Int,
        val dependenciesIndexes: Collection<Int>
    )

    data class Package(
        val id: PackageId,
        val contentRootUrl: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?,
        val origin: PackageOrigin
    )

    data class Target(
        val crateRootUrl: String,
        val name: String,
        val kind: CargoWorkspace.TargetKind
    )
}
