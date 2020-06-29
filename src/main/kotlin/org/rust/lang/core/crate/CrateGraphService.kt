/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.crate

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Describes the project model in terms of *crates*. Should be preferred to
 * [org.rust.cargo.project.model.CargoProjectsService] in Rust analysis code
 * (name resolution, type inference, most of the inspections, etc)
 *
 * Crate Graph is [DAG](https://en.wikipedia.org/wiki/Directed_acyclic_graph),
 * i.e. it doesn't have cycles, hence we can do
 * [topological sorting](https://en.wikipedia.org/wiki/Topological_sorting) of crates.
 *
 * Use [Project.crateGraph] to get an instance of the service.
 *
 * ## Relations to the Cargo project model
 *
 * [Crate] is usually a wrapper around [org.rust.cargo.project.workspace.CargoWorkspace.Target].
 *
 * ### Duplicated packages
 *
 * Multiple [org.rust.cargo.project.model.CargoProject]'s can refer to the *same* package, but
 * since cargo projects are different, there will be 2 copies of the packages. Each copy can have
 * specific *features* and *dependencies* (because of different `Cargo.lock` files in different
 * cargo projects). We trying to merge these packages into a single crate, but we can't merge
 * different dependencies, so only one variant is preferred for now.
 *
 * ### Cyclic dependencies
 *
 * A Cargo package can have a cycle dependency on itself through `[dev-dependencies]`. It works in
 * Cargo because Cargo package basically consists of two crates: one "production" crate and one
 * `--cfg=test` crate, so "test" crate can depends on "production" one.
 * We need to avoid cyclic dependencies because we need DAG in order to do topological sorting
 * of crates, so we just remove cyclic `[dev-dependencies]` from the graph for now.
 */
interface CrateGraphService {
    /**
     * [Topological sorted](https://en.wikipedia.org/wiki/Topological_sorting)
     * list of all crates in the project
     */
    val topSortedCrates: List<Crate>

    /** See [Crate.id] */
    fun findCrateById(id: CratePersistentId): Crate?

    fun findCrateByRootMod(rootModFile: VirtualFile): Crate?
}

val Project.crateGraph: CrateGraphService
    get() = service()
