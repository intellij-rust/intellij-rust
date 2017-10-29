/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

/**
 * A POD-style representation of [org.rust.cargo.project.workspace.CargoWorkspace] used as an intermediate representation
 * between `cargo metadata` JSON and [org.rust.cargo.project.workspace.CargoWorkspace] object graph.
 *
 * Dependency graph is represented via adjacency list, where `Index` is the order of a particular
 * package in `packages` list.
 */
data class CleanCargoMetadata(
    val packages: List<Package>,
    val dependencies: List<DependencyNode>
) {
    data class DependencyNode(
        val packageIndex: Int,
        val dependenciesIndexes: Collection<Int>
    )

    data class Package(
        val id: String,
        val url: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?,
        val manifestPath: String,
        val isWorkspaceMember: Boolean
    )

    data class Target(
        val url: String,
        val name: String,
        val kind: CargoWorkspace.TargetKind
    )
}
