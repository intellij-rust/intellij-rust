/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import org.rust.cargo.CfgOptions

typealias PackageId = String

/** Refers to [org.rust.cargo.project.workspace.PackageImpl.rootDirectory] */
typealias PackageRoot = String

/**
 * A POD-style representation of [CargoWorkspace] used as an intermediate representation
 * between `cargo metadata` JSON and [CargoWorkspace] object graph.
 *
 * Dependency graph is represented via adjacency list, where `Index` is the order of a particular
 * package in `packages` list.
 */
data class CargoWorkspaceData(
    val packages: List<Package>,
    val dependencies: Map<PackageId, Set<Dependency>>,
    val workspaceRoot: String? = null
) {
    data class Package(
        val id: PackageId,
        val contentRootUrl: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?,
        val origin: PackageOrigin,
        val edition: CargoWorkspace.Edition,
        val features: Map<String, List<String>>,
        val defaultFeatures: Set<String>,
        val cfgOptions: CfgOptions,
        val env: Map<String, String>,
        val outDirUrl: String?
    )

    data class Target(
        val crateRootUrl: String,
        val name: String,
        val kind: CargoWorkspace.TargetKind,
        val edition: CargoWorkspace.Edition,
        val doctest: Boolean
    )

    data class Dependency(
        val id: PackageId,
        val name: String? = null,
        val depKinds: List<CargoWorkspace.DepKindInfo> = listOf(CargoWorkspace.DepKindInfo(CargoWorkspace.DepKind.Unclassified))
    )
}
