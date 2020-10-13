/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.crate.impl

import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.psi.RsFile
import java.util.*

class DoctestCrate(
    private val parentCrate: Crate,
    override val rootMod: RsFile,
    override val dependencies: Collection<Crate.Dependency>
) : Crate {
    override val flatDependencies: LinkedHashSet<Crate> = dependencies.flattenTopSortedDeps()

    override val reverseDependencies: List<Crate> get() = emptyList()

    override val id: CratePersistentId? get() = null
    override val cargoProject: CargoProject get() = parentCrate.cargoProject
    override val cargoTarget: CargoWorkspace.Target? get() = null
    override val cargoWorkspace: CargoWorkspace get() = parentCrate.cargoWorkspace
    override val kind: CargoWorkspace.TargetKind get() = CargoWorkspace.TargetKind.Test

    override val cfgOptions: CfgOptions get() = CfgOptions.EMPTY
    override val features: Map<String, FeatureState> get() = emptyMap()
    override val env: Map<String, String> get() = emptyMap()
    override val outDir: VirtualFile? get() = null

    override val rootModFile: VirtualFile? get() = rootMod.virtualFile
    override val origin: PackageOrigin get() = parentCrate.origin
    override val edition: CargoWorkspace.Edition get() = parentCrate.edition
    override val areDoctestsEnabled: Boolean get() = false
    override val presentableName: String get() = parentCrate.presentableName + "-doctest"
    override val normName: String get() = parentCrate.normName + "_doctest"

    override fun toString(): String = "Doctest in ${parentCrate.cargoTarget?.name}"

    companion object {
        fun inCrate(parentCrate: Crate, doctestModule: RsFile): DoctestCrate {
            return if (parentCrate.origin != PackageOrigin.STDLIB) {
                val dependencies = parentCrate.dependenciesWithCyclic +
                    Crate.Dependency(parentCrate.normName, parentCrate)
                DoctestCrate(parentCrate, doctestModule, dependencies)
            } else {
                // A doctest located in the stdlib is depending on all stdlib crates
                val stdCrates = parentCrate.cargoProject.project.crateGraph.topSortedCrates
                    .filter { it.origin == PackageOrigin.STDLIB }
                    .map { Crate.Dependency(it.normName, it) }
                    .distinctBy { it.normName }
                DoctestCrate(parentCrate, doctestModule, dependencies = stdCrates)
            }
        }
    }
}
